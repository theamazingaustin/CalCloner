package com.stripedlens.calcloner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.stripedlens.calcloner.ui.components.AddEditSyncPairSheet
import com.stripedlens.calcloner.ui.components.SyncPairCard
import com.stripedlens.calcloner.ui.dialogs.ClearPairClonedEventsDialog
import com.stripedlens.calcloner.ui.dialogs.DeletePairDialog
import com.stripedlens.calcloner.ui.dialogs.DisclaimerConsentDialog
import com.stripedlens.calcloner.ui.dialogs.NukeAllTargetEventsDialog
import com.stripedlens.calcloner.ui.theme.CalClonerTheme
import com.stripedlens.calcloner.ui.theme.TitaniumMint
import com.stripedlens.calcloner.util.DateTimeUtils
import com.stripedlens.calcloner.util.SystemUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        setContent {
            val context = LocalContext.current
            val repo = remember { SettingsRepository(context) }
            val themeMode by repo.themeModeFlow.collectAsState(initial = ThemeMode.AUTO)

            CalClonerTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CalendarSyncApp(
                        themeMode = themeMode,
                        onCycleTheme = {
                            val nextMode = when (themeMode) {
                                ThemeMode.AUTO -> ThemeMode.DARK
                                ThemeMode.DARK -> ThemeMode.LIGHT
                                ThemeMode.LIGHT -> ThemeMode.AUTO
                            }
                            repo.saveThemeMode(nextMode)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSyncApp(
    themeMode: ThemeMode,
    onCycleTheme: suspend () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { SettingsRepository(context) }

    var hasPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermissions = result[Manifest.permission.READ_CALENDAR] == true &&
                         result[Manifest.permission.WRITE_CALENDAR] == true
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            val permissions = mutableListOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionsLauncher.launch(permissions.toTypedArray())
        }
    }

    var availableCalendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    val syncPairs by repo.syncPairsFlow.collectAsState(initial = emptyList())
    val savedInterval by repo.syncIntervalFlow.collectAsState(initial = 0)
    val syncOnLowBattery by repo.syncOnLowBatteryFlow.collectAsState(initial = false)
    val isDisclaimerAccepted by repo.disclaimerAcceptedFlow.collectAsState(initial = true)

    // Automatically manage reactive and periodic background sync based on user's mode
    LaunchedEffect(syncPairs, savedInterval, syncOnLowBattery) {
        val enabledPairs = syncPairs.filter { it.isEnabled }
        if (enabledPairs.isEmpty() || savedInterval == -1) {
            // "Never (manual only)": Cancel ALL auto and instant sync
            CalendarSyncScheduler.cancelReactiveSync(context)
            CalendarSyncScheduler.cancelSync(context)
        } else if (savedInterval == 0) {
            // "Instant": Enable reactive sync, cancel periodic sync
            CalendarSyncScheduler.scheduleReactiveSync(context, syncOnLowBattery)
            CalendarSyncScheduler.cancelSync(context)
        } else {
            // Specific interval: Disable instant reactive sync, schedule periodic sync
            CalendarSyncScheduler.cancelReactiveSync(context)
            CalendarSyncScheduler.scheduleSync(context, savedInterval, syncOnLowBattery)
        }
    }

    // Battery Optimization check for background sync reliability
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnoringBatteryOptimizations = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Modal & Sheet state
    var showAddEditSheet by remember { mutableStateOf(false) }
    var pairToEdit by remember { mutableStateOf<SyncPair?>(null) }
    var pairToDelete by remember { mutableStateOf<SyncPair?>(null) }
    var pairToClear by remember { mutableStateOf<SyncPair?>(null) }
    var pairToNuke by remember { mutableStateOf<SyncPair?>(null) }
    var clearTargetEventCount by remember { mutableIntStateOf(0) }

    // Operation & Progress state
    var isOperating by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var isSyncingAll by remember { mutableStateOf(false) }
    var syncingPairId by remember { mutableStateOf<String?>(null) }
    var isClearing by remember { mutableStateOf(false) }
    var isNuking by remember { mutableStateOf(false) }
    var operationDone by remember { mutableStateOf(false) }
    var progressFraction by remember { mutableFloatStateOf(0f) }
    var progressStatusText by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()
    var showOptionsMenu by remember { mutableStateOf(false) }

    val importConfigLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val jsonString = context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().use { it.readText() }
                    }
                    if (!jsonString.isNullOrBlank()) {
                        val importedPairs = SyncPair.listFromJsonString(jsonString)
                        if (importedPairs.isNotEmpty()) {
                            val currentPairs = repo.syncPairsFlow.first().toMutableList()
                            var addedCount = 0
                            var updatedCount = 0
                            for (imported in importedPairs) {
                                val idx = currentPairs.indexOfFirst { it.id == imported.id }
                                if (idx >= 0) {
                                    currentPairs[idx] = imported
                                    updatedCount++
                                } else {
                                    currentPairs.add(imported)
                                    addedCount++
                                }
                            }
                            repo.saveSyncPairs(currentPairs)
                            Toast.makeText(context, "Imported ${importedPairs.size} pair(s) ($addedCount new, $updatedCount updated)", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "No valid sync pairs found in file", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun exportConfiguration() {
        scope.launch {
            try {
                val pairs = repo.syncPairsFlow.first()
                if (pairs.isEmpty()) {
                    Toast.makeText(context, "No sync pairs to export", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val jsonString = SyncPair.listToJsonString(pairs)
                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                val file = File(exportDir, "calcloner_backup_$timeStamp.json")
                file.writeText(jsonString)

                val fileUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    putExtra(Intent.EXTRA_SUBJECT, "CalCloner Backup $timeStamp")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(sendIntent, "Export CalCloner Configuration"))
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun openBatterySettings() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, "Could not open battery settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Button feedback animations for Sync All
    val syncAllPulseAnim = remember { Animatable(1f) }
    val syncAllGlowAnim = remember { Animatable(0f) }
    var prevSyncingAll by remember { mutableStateOf(false) }

    LaunchedEffect(isSyncingAll) {
        if (prevSyncingAll && !isSyncingAll) {
            launch {
                syncAllPulseAnim.animateTo(1.05f, tween(180, easing = FastOutSlowInEasing))
                syncAllPulseAnim.animateTo(1.0f, tween(250, easing = LinearOutSlowInEasing))
            }
            launch {
                syncAllGlowAnim.snapTo(1f)
                syncAllGlowAnim.animateTo(0f, tween(1500, easing = FastOutSlowInEasing))
            }
        }
        prevSyncingAll = isSyncingAll
    }

    fun refreshCalendars() {
        if (hasPermissions) {
            availableCalendars = CalendarSyncEngine.getAvailableCalendars(context)
        }
    }

    LaunchedEffect(hasPermissions) {
        if (hasPermissions) {
            refreshCalendars()
        }
    }

    // Motion Animation Transition Specs
    val infiniteTransition = rememberInfiniteTransition(label = "topBarSpinTransition")
    val topBarSpinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "topBarSpinAngle"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "animatedProgress"
    )

    // Sync All Orchestrator
    fun syncAll() {
        val enabledPairs = syncPairs.filter { it.isEnabled }
        if (enabledPairs.isEmpty() || isOperating) return

        isOperating = true
        isSyncing = true
        isSyncingAll = true
        operationDone = false
        progressFraction = 0f
        progressStatusText = "Starting sync for ${enabledPairs.size} pair(s)..."

        scope.launch {
            val startTime = System.currentTimeMillis()
            try {
                val results = withContext(Dispatchers.IO) {
                    CalendarSyncEngine.syncAllPairs(
                        context = context,
                        pairs = enabledPairs,
                        onPairProgress = { pair, idx, total, _, msg ->
                            scope.launch(Dispatchers.Main) {
                                progressFraction = idx.toFloat() / total.toFloat()
                                progressStatusText = "[$idx/$total] ${pair.fromCalendarName} → ${pair.toCalendarName}: ${msg ?: ""}"
                            }
                        }
                    )
                }
                val totalInserted = results.values.sumOf { it.insertedCount }
                val totalUpdated = results.values.sumOf { it.updatedCount }
                val totalDeleted = results.values.sumOf { it.deletedCount }

                val summary = if (totalInserted == 0 && totalUpdated == 0 && totalDeleted == 0) {
                    "Sync complete: 0 changes across ${enabledPairs.size} pair(s)."
                } else {
                    val parts = mutableListOf<String>()
                    if (totalInserted > 0) parts.add("$totalInserted added")
                    if (totalUpdated > 0) parts.add("$totalUpdated updated")
                    if (totalDeleted > 0) parts.add("$totalDeleted removed")
                    "Sync complete (${enabledPairs.size} pairs): ${parts.joinToString(", ")}."
                }
                repo.saveLastSync(System.currentTimeMillis(), summary)
                progressFraction = 1f
                operationDone = true
                progressStatusText = summary
            } catch (e: Exception) {
                progressStatusText = "Sync failed: ${e.message}"
            } finally {
                val elapsed = System.currentTimeMillis() - startTime
                val remaining = 2000L - elapsed
                if (remaining > 0) {
                    kotlinx.coroutines.delay(remaining)
                }
                isOperating = false
                isSyncing = false
                isSyncingAll = false
            }
        }
    }

    // Sync Single Pair Orchestrator
    fun syncSinglePair(pair: SyncPair) {
        if (isOperating) return
        isOperating = true
        isSyncing = true
        syncingPairId = pair.id
        operationDone = false
        progressFraction = 0f
        progressStatusText = "Syncing '${pair.fromCalendarName} → ${pair.toCalendarName}'..."

        scope.launch {
            val startTime = System.currentTimeMillis()
            try {
                val res = withContext(Dispatchers.IO) {
                    CalendarSyncEngine.syncEventsToTarget(
                        context = context,
                        fromCalendarId = pair.fromCalendarId,
                        toCalendarId = pair.toCalendarId,
                        daysPast = pair.daysPast,
                        daysFuture = pair.daysFuture,
                        pairId = pair.id,
                        syncTitle = pair.syncTitle,
                        customTitle = pair.customTitle,
                        syncDescription = pair.syncDescription,
                        syncLocation = pair.syncLocation,
                        syncReminders = pair.syncReminders,
                        syncAvailability = pair.syncAvailability,
                        syncStatus = pair.syncStatus,
                        activePairIds = syncPairs.map { it.id }.toSet(),
                        onProgress = { cur, tot, msg ->
                            scope.launch(Dispatchers.Main) {
                                progressFraction = if (tot > 0) cur.toFloat() / tot else 0f
                                progressStatusText = msg
                            }
                        }
                    )
                }
                val durationMs = System.currentTimeMillis() - startTime
                val parts = mutableListOf<String>()
                if (res.insertedCount > 0) parts.add("${res.insertedCount} added")
                if (res.updatedCount > 0) parts.add("${res.updatedCount} updated")
                if (res.deletedCount > 0) parts.add("${res.deletedCount} removed")
                val statusMsg = if (parts.isEmpty()) "Sync complete: 0 changes." else "Sync complete: ${parts.joinToString(", ")}."
                repo.updatePairSyncStatus(
                    pairId = pair.id,
                    timestamp = System.currentTimeMillis(),
                    status = statusMsg,
                    insertedCount = res.insertedCount,
                    updatedCount = res.updatedCount,
                    deletedCount = res.deletedCount,
                    durationMs = res.durationMs.takeIf { it > 0 } ?: durationMs
                )
                progressFraction = 1f
                operationDone = true
                progressStatusText = "${pair.fromCalendarName} → ${pair.toCalendarName}: $statusMsg"
            } catch (e: Exception) {
                val errorMsg = "Sync failed: ${e.message}"
                repo.updatePairSyncStatus(pair.id, System.currentTimeMillis(), errorMsg)
                progressStatusText = errorMsg
            } finally {
                val elapsed = System.currentTimeMillis() - startTime
                val remaining = 2000L - elapsed
                if (remaining > 0) {
                    kotlinx.coroutines.delay(remaining)
                }
                isOperating = false
                isSyncing = false
                syncingPairId = null
            }
        }
    }

    // First-Run Consent / Disclaimer Popup
    if (!isDisclaimerAccepted) {
        DisclaimerConsentDialog(
            onAccept = {
                scope.launch { repo.acceptDisclaimer() }
            }
        )
    }

    // Add / Edit Sync Pair Modal Bottom Sheet
    if (showAddEditSheet) {
        val currentPairToEdit = syncPairs.find { it.id == pairToEdit?.id } ?: pairToEdit
        AddEditSyncPairSheet(
            pairToEdit = currentPairToEdit,
            existingPairs = syncPairs,
            availableCalendars = availableCalendars,
            onDismiss = {
                showAddEditSheet = false
                pairToEdit = null
            },
            onSavePair = { pair ->
                showAddEditSheet = false
                pairToEdit = null
                scope.launch {
                    repo.upsertSyncPair(pair)
                }
            },
            onSaveAndSync = { pair ->
                pairToEdit = pair
                scope.launch {
                    repo.upsertSyncPair(pair)
                    syncSinglePair(pair)
                }
            },
            onSyncNow = { pair ->
                syncSinglePair(pair)
            },
            isSyncing = isSyncing && (syncingPairId == currentPairToEdit?.id),
            onDeletePairWithOptions = { pair, deleteClonedEvents ->
                showAddEditSheet = false
                pairToEdit = null
                scope.launch {
                    if (deleteClonedEvents) {
                        isOperating = true
                        isClearing = true
                        operationDone = false
                        progressFraction = 0f
                        progressStatusText = "Deleting cloned events for '${pair.displayName}'..."
                        val activeIds = syncPairs.map { it.id }.toSet()
                        val deleted = withContext(Dispatchers.IO) {
                            CalendarSyncEngine.clearTargetCalendarEvents(
                                context = context,
                                toCalendarId = pair.toCalendarId,
                                fromCalendarId = pair.fromCalendarId,
                                pairId = pair.id,
                                activePairIds = activeIds,
                                onProgress = { msg -> scope.launch(Dispatchers.Main) { progressStatusText = msg } }
                            )
                        }
                        repo.deleteSyncPair(pair.id)
                        isOperating = false
                        isClearing = false
                        operationDone = true
                        progressStatusText = "Deleted pair '${pair.displayName}' and removed $deleted cloned event(s)."
                    } else {
                        repo.deleteSyncPair(pair.id)
                        progressStatusText = "Deleted sync pair '${pair.displayName}'. Cloned events were kept on target calendar."
                        operationDone = true
                    }
                }
            },
            onClearPairEvents = {
                val pair = currentPairToEdit
                if (pair != null) {
                    scope.launch {
                        val activeIds = syncPairs.map { it.id }.toSet()
                        clearTargetEventCount = withContext(Dispatchers.IO) {
                            CalendarSyncEngine.getTargetClonedEventCount(context, pair.toCalendarId, pair.id, activeIds)
                        }
                        pairToClear = pair
                    }
                }
            },
            onNukeTargetEvents = {
                val pair = pairToEdit
                if (pair != null) {
                    pairToNuke = pair
                }
            }
        )
    }

    // Delete Pair Dialog (Prompt to keep vs delete cloned events)
    if (pairToDelete != null) {
        val pair = pairToDelete!!
        DeletePairDialog(
            pair = pair,
            onDismiss = { pairToDelete = null },
            onDeleteKeepEvents = {
                scope.launch {
                    repo.deleteSyncPair(pair.id)
                    pairToDelete = null
                    progressStatusText = "Deleted sync pair '${pair.fromCalendarName} → ${pair.toCalendarName}'. Existing cloned events kept on target."
                    operationDone = true
                }
            },
            onDeleteAndClearEvents = {
                scope.launch {
                    isOperating = true
                    isClearing = true
                    progressStatusText = "Deleting pair and clearing cloned events from '${pair.toCalendarName}'..."
                    try {
                        withContext(Dispatchers.IO) {
                            CalendarSyncEngine.clearTargetCalendarEvents(
                                context = context,
                                toCalendarId = pair.toCalendarId,
                                fromCalendarId = pair.fromCalendarId,
                                pairId = pair.id
                            )
                        }
                    } catch (_: Exception) {}
                    repo.deleteSyncPair(pair.id)
                    isOperating = false
                    isClearing = false
                    pairToDelete = null
                    progressStatusText = "Deleted sync pair and cleared all its cloned events from '${pair.toCalendarName}'."
                    operationDone = true
                }
            }
        )
    }

    // Dedicated Clear Cloned Events Modal (5-second countdown lock)
    if (pairToClear != null) {
        val pair = pairToClear!!
        ClearPairClonedEventsDialog(
            targetCalendarName = pair.toCalendarName,
            onDismiss = { if (!isOperating) pairToClear = null },
            onConfirmDelete = {
                val pairId = pair.id
                val toCalId = pair.toCalendarId
                val fromCalId = pair.fromCalendarId
                val toCalName = pair.toCalendarName
                val fromCalName = pair.fromCalendarName
                pairToClear = null
                isOperating = true
                isClearing = true
                operationDone = false
                progressFraction = 0f
                progressStatusText = "Clearing cloned events for '$fromCalName → $toCalName'..."

                scope.launch {
                    try {
                        val deleted = withContext(Dispatchers.IO) {
                            CalendarSyncEngine.clearTargetCalendarEvents(
                                context = context,
                                toCalendarId = toCalId,
                                fromCalendarId = fromCalId,
                                pairId = pairId,
                                onProgress = { msg -> scope.launch(Dispatchers.Main) { progressStatusText = msg } }
                            )
                        }
                        progressStatusText = if (deleted > 0) {
                            "Cleared $deleted cloned event(s) from '$toCalName'."
                        } else {
                            "Target calendar '$toCalName' has 0 cloned events for this pair."
                        }
                        operationDone = true
                    } catch (e: Exception) {
                        progressStatusText = "Clear failed: ${e.message}"
                    } finally {
                        isOperating = false
                        isClearing = false
                    }
                }
            }
        )
    }

    // Destructive Nuke All Target Events Modal (Dual Swipe Sliders)
    if (pairToNuke != null) {
        val pair = pairToNuke!!
        val targetCal = availableCalendars.find { it.id == pair.toCalendarId }
        if (targetCal != null) {
            NukeAllTargetEventsDialog(
                calendar = targetCal,
                onDismiss = { if (!isOperating) pairToNuke = null },
                onConfirmNormalDelete = {
                    val toCalId = pair.toCalendarId
                    val toCalName = pair.toCalendarName
                    pairToNuke = null
                    isOperating = true
                    isNuking = true
                    operationDone = false
                    progressFraction = 0f
                    progressStatusText = "Deleting all events from '$toCalName'..."

                    scope.launch {
                        try {
                            val deleted = withContext(Dispatchers.IO) {
                                CalendarSyncEngine.clearTargetCalendarEvents(
                                    context = context,
                                    toCalendarId = toCalId,
                                    fromCalendarId = null,
                                    pairId = null,
                                    onProgress = { msg -> scope.launch(Dispatchers.Main) { progressStatusText = msg } }
                                )
                            }
                            progressStatusText = "Normal wipe complete: $deleted event(s) deleted from '$toCalName'."
                            operationDone = true
                        } catch (e: Exception) {
                            progressStatusText = "Wipe failed: ${e.message}"
                        } finally {
                            isOperating = false
                            isNuking = false
                            refreshCalendars()
                        }
                    }
                },
                onConfirmForceNuke = {
                    val toCalId = pair.toCalendarId
                    val fromCalId = pair.fromCalendarId
                    pairToNuke = null
                    isOperating = true
                    isNuking = true
                    operationDone = false
                    progressFraction = 0f
                    progressStatusText = "Step 1/3: Forcing Google Cloud download..."

                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                CalendarSyncEngine.nukeTargetCalendarEvents(
                                    context = context,
                                    toCalendarId = toCalId,
                                    fromCalendarId = fromCalId,
                                    onProgress = { msg -> scope.launch(Dispatchers.Main) { progressStatusText = msg } }
                                )
                            }
                            operationDone = true
                        } catch (e: Exception) {
                            progressStatusText = "Force deep wipe failed: ${e.message}"
                        } finally {
                            isOperating = false
                            isNuking = false
                            refreshCalendars()
                        }
                    }
                }
            )
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            val anyEnabled = syncPairs.any { it.isEnabled }
            val isScrolled = scrollState.value > 0
            val headerElevation by animateDpAsState(if (isScrolled) 6.dp else 0.dp, label = "headerElevation")
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = headerElevation,
                border = BorderStroke(1.dp, if (isScrolled) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    // Header Row: CalCloner Title & Branding + Theme Toggle + Overflow Menu
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = Color.Transparent,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            brush = Brush.linearGradient(
                                                colors = listOf(Color(0xFF34D399), Color(0xFF059669))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = Color(0xFF09090B),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Text(
                                text = "CalCloner",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 3-State Theme Toggle (Dark -> Light -> Auto)
                            IconButton(
                                onClick = { scope.launch { onCycleTheme() } }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Crossfade(
                                        targetState = themeMode,
                                        animationSpec = tween(durationMillis = 200),
                                        label = "themeCrossfade"
                                    ) { mode ->
                                        val iconRes = when (mode) {
                                            ThemeMode.AUTO -> R.drawable.ic_theme_auto
                                            ThemeMode.DARK -> R.drawable.ic_theme_dark
                                            ThemeMode.LIGHT -> R.drawable.ic_theme_light
                                        }
                                        Icon(
                                            painter = painterResource(id = iconRes),
                                            contentDescription = "Theme: ${mode.name}",
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (themeMode == ThemeMode.AUTO) {
                                        Surface(
                                            shape = CircleShape,
                                            color = TitaniumMint.Mint500,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .offset(x = 4.dp, y = 4.dp)
                                        ) {
                                            Text(
                                                text = "A",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF09090B),
                                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Overflow Options Menu (Export / Import / Samsung Deep Sleep)
                            Box {
                                IconButton(
                                    onClick = { showOptionsMenu = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Options",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                DropdownMenu(
                                    expanded = showOptionsMenu,
                                    onDismissRequest = { showOptionsMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Export Configuration") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                                        },
                                        onClick = {
                                            showOptionsMenu = false
                                            exportConfiguration()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Import Configuration") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                        },
                                        onClick = {
                                            showOptionsMenu = false
                                            importConfigLauncher.launch("application/json")
                                        }
                                    )
                                    val isSamsung = Build.MANUFACTURER.contains("samsung", ignoreCase = true)
                                    if (isSamsung && !isIgnoringBatteryOptimizations) {
                                        Divider()
                                        DropdownMenuItem(
                                            text = { Text("Disable Samsung Deep Sleep") },
                                            leadingIcon = {
                                                Icon(Icons.Default.Warning, contentDescription = null, tint = TitaniumMint.Amber400, modifier = Modifier.size(20.dp))
                                            },
                                            onClick = {
                                                showOptionsMenu = false
                                                openBatterySettings()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Divider separating app branding from master sync
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    )

                    // Master Sync Controls (Full-width in the same visual container!)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(7.dp),
                                    color = TitaniumMint.Mint500.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, TitaniumMint.Mint500.copy(alpha = 0.25f)),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = TitaniumMint.Mint400,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "Master Sync",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (anyEnabled) "${syncPairs.count { it.isEnabled }} of ${syncPairs.size} pairs active" else "All pairs paused",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Switch(
                                checked = anyEnabled,
                                enabled = syncPairs.isNotEmpty(),
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        syncPairs.forEach { pair ->
                                            repo.togglePairEnabled(pair.id, checked)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .scale(0.85f)
                                    .height(24.dp)
                            )
                        }

                        val syncAllBorderColor = androidx.compose.ui.graphics.lerp(
                            TitaniumMint.Mint500.copy(alpha = 0.3f),
                            TitaniumMint.Mint400,
                            syncAllGlowAnim.value
                        )
                        val syncAllBgColor = androidx.compose.ui.graphics.lerp(
                            TitaniumMint.Mint500.copy(alpha = 0.12f),
                            TitaniumMint.Mint500.copy(alpha = 0.28f),
                            syncAllGlowAnim.value
                        )

                        FilledTonalButton(
                            onClick = { syncAll() },
                            enabled = anyEnabled && !isOperating,
                            modifier = Modifier
                                .fillMaxWidth()
                                .scale(syncAllPulseAnim.value),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = syncAllBgColor,
                                contentColor = TitaniumMint.Mint400
                            ),
                            border = BorderStroke((1f + syncAllGlowAnim.value).dp, syncAllBorderColor)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync All",
                                modifier = Modifier
                                    .size(16.dp)
                                    .rotate(if (isSyncingAll) topBarSpinAngle else 0f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            AnimatedContent(
                                targetState = isSyncingAll,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
                                },
                                label = "syncAllButtonFade"
                            ) { syncing ->
                                Text(
                                    text = if (syncing) "Syncing..." else "Sync All Now",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            Surface(
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 8.dp,
                color = Color.Transparent,
                modifier = Modifier.clickable {
                    pairToEdit = null
                    showAddEditSheet = true
                }
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFF10B981), Color(0xFF34D399))
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color(0xFF09090B),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "New Sync Pair",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color(0xFF09090B)
                        )
                    }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
            // Samsung Deep Sleep Guidance Banner
            val isSamsungDevice = remember { Build.MANUFACTURER.contains("samsung", ignoreCase = true) }
            var samsungBannerDismissed by remember { mutableStateOf(false) }
            AnimatedVisibility(
                visible = isSamsungDevice && !isIgnoringBatteryOptimizations && !samsungBannerDismissed,
                enter = fadeIn(tween(200)) + expandVertically(tween(250)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(200))
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)),
                    border = BorderStroke(1.dp, TitaniumMint.Amber400.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = TitaniumMint.Amber400,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Samsung Background Protection",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "Samsung One UI may put CalCloner into 'Deep Sleep' when unused, stopping automated background syncs. Set battery to 'Unrestricted' to prevent sleep.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { openBatterySettings() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TitaniumMint.Amber400,
                                    contentColor = Color(0xFF09090B)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = "Unrestrict Battery",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            OutlinedButton(
                                onClick = { samsungBannerDismissed = true },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = "Dismiss",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            // Permission Alert Banner
            AnimatedVisibility(
                visible = !hasPermissions,
                enter = fadeIn(tween(200)) + expandVertically(tween(250)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(200))
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Calendar permissions are required to read and sync calendars.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val permissions = mutableListOf(
                                    Manifest.permission.READ_CALENDAR,
                                    Manifest.permission.WRITE_CALENDAR
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                permissionsLauncher.launch(permissions.toTypedArray())
                            }
                        ) {
                            Text("Grant Permissions")
                        }
                    }
                }
            }

            // Live Telemetry & Progress Card (Only shown for destructive clearing/nuking actions)
            AnimatedVisibility(
                visible = (isClearing || isNuking) && (isOperating || operationDone || progressStatusText.isNotEmpty()),
                enter = fadeIn(tween(200)) + expandVertically(tween(250)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(200))
            ) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (operationDone) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when {
                                    isSyncing -> "Sync in Progress"
                                    isNuking -> "Nuking Calendar"
                                    isClearing -> "Clearing Events"
                                    operationDone -> "Operation Completed"
                                    else -> "Status"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (operationDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )

                            if (isSyncing && progressFraction > 0f) {
                                Text(
                                    text = "${(animatedProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (isSyncing && progressFraction > 0f) {
                            LinearProgressIndicator(
                                progress = animatedProgress.coerceIn(0f, 1f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }

                        Text(
                            text = progressStatusText,
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (operationDone && (progressStatusText.contains("Cleared") || progressStatusText.contains("purging") ||
                            progressStatusText.contains("Force") || progressStatusText.contains("wiped") ||
                            progressStatusText.contains("Pushed deletions"))) {
                            FilledTonalButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { SystemUtils.openNotificationShade(context) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open Notification Shade (Confirm Cloud Deletes)", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }


            // Empty State Card
            if (syncPairs.isEmpty()) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Text(
                            text = "No Calendar Pairs Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Add your first pair to begin safe, one-way calendar replication with zero battery drain.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Button(
                            onClick = {
                                pairToEdit = null
                                showAddEditSheet = true
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add First Sync Pair")
                        }
                    }
                }
            } else {
                // List of Sync Pair Cards
                Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    syncPairs.forEach { pair ->
                        val fromCal = availableCalendars.find { it.id == pair.fromCalendarId }
                        val toCal = availableCalendars.find { it.id == pair.toCalendarId }
                        val isPairSyncing = isSyncing && (isSyncingAll || syncingPairId == pair.id)

                        SyncPairCard(
                            pair = pair,
                            fromCalendar = fromCal,
                            toCalendar = toCal,
                            isSyncing = isPairSyncing,
                            onSyncNow = { syncSinglePair(pair) },
                            onEdit = {
                                pairToEdit = pair
                                showAddEditSheet = true
                            },
                            onToggleEnabled = { enabled ->
                                scope.launch {
                                    repo.togglePairEnabled(pair.id, enabled)
                                }
                            },
                            onClearPairEvents = {
                                scope.launch {
                                    clearTargetEventCount = withContext(Dispatchers.IO) {
                                        CalendarSyncEngine.getCalendarEventCount(context, pair.toCalendarId)
                                    }
                                    pairToClear = pair
                                }
                            },
                            onNukeTarget = {
                                pairToNuke = pair
                            },
                            onDeletePair = {
                                pairToDelete = pair
                            }
                        )
                    }
                }
            }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // SECTION 2: AUTOMATIC & REACTIVE SYNC SETTINGS (Full-width matching header)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BatteryChargingFull,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Sync Engine & Battery",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Sync Interval Selector
                    var showIntervalMenu by remember { mutableStateOf(false) }
                    val intervalOptions = listOf(
                        -1 to "Never (manual only)",
                        0 to "Instant",
                        15 to "Every 15 minutes",
                        30 to "Every 30 minutes",
                        60 to "Every 1 hour",
                        180 to "Every 3 hours",
                        360 to "Every 6 hours",
                        720 to "Every 12 hours",
                        1440 to "Every 24 hours (1x a day)"
                    )

                    val currentIntervalLabel = intervalOptions.find { it.first == savedInterval }?.second
                        ?: if (savedInterval > 0) "Every $savedInterval minutes" else "Never (manual only)"

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedCard(
                            onClick = { showIntervalMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Sync Interval",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = currentIntervalLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select"
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showIntervalMenu,
                            onDismissRequest = { showIntervalMenu = false }
                        ) {
                            intervalOptions.forEach { (mins, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = label,
                                            fontWeight = if (mins == savedInterval) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        showIntervalMenu = false
                                        scope.launch { repo.saveSyncInterval(mins) }
                                    }
                                )
                            }
                        }
                    }

                    // Low Battery / Power Saver Setting
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Sync on Low Battery / Power Saver",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f).padding(end = 12.dp)
                            )
                            Switch(
                                checked = syncOnLowBattery,
                                onCheckedChange = { checked ->
                                    scope.launch { repo.saveSyncOnLowBattery(checked) }
                                }
                            )
                        }
                    }

                    // Battery Optimization Status
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isIgnoringBatteryOptimizations) {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    try {
                                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = TitaniumMint.Mint500.copy(alpha = 0.1f),
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = TitaniumMint.Mint400,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Battery Optimization",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (isIgnoringBatteryOptimizations) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = TitaniumMint.Mint500.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, TitaniumMint.Mint500.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = TitaniumMint.Mint400,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "Unrestricted",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TitaniumMint.Mint400
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = TitaniumMint.Amber500.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, TitaniumMint.Amber500.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = TitaniumMint.Amber400,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "Optimized (Tap to fix)",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TitaniumMint.Amber400
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Battery Optimization Warning Banner
                    AnimatedVisibility(
                        visible = savedInterval != -1 && !isIgnoringBatteryOptimizations,
                        enter = fadeIn(tween(200)) + expandVertically(tween(250)),
                        exit = fadeOut(tween(150)) + shrinkVertically(tween(200))
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Unrestricted Battery Recommended",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                Text(
                                    text = "Android may defer background tasks during deep sleep. Set battery to Unrestricted for instant reactive sync.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                FilledTonalButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        try {
                                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            try {
                                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                                context.startActivity(intent)
                                            } catch (_: Exception) {}
                                        }
                                    }
                                ) {
                                    Text("Set Battery to Unrestricted", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Spacer to prevent FAB occlusion
            Spacer(modifier = Modifier.height(84.dp))
        }
    }
}
