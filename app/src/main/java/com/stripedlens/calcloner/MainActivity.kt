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
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.stripedlens.calcloner.ui.components.AddEditSyncPairSheet
import com.stripedlens.calcloner.ui.components.SyncPairCard
import com.stripedlens.calcloner.ui.dialogs.ClearCloneEventsDialog
import com.stripedlens.calcloner.ui.dialogs.DeletePairDialog
import com.stripedlens.calcloner.ui.dialogs.DisclaimerConsentDialog
import com.stripedlens.calcloner.ui.dialogs.NukeCalendarEventsDialog
import com.stripedlens.calcloner.ui.dialogs.TargetCalendarWarningDialog
import com.stripedlens.calcloner.ui.theme.CalClonerTheme
import com.stripedlens.calcloner.util.DateTimeUtils
import com.stripedlens.calcloner.util.SystemUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
    val savedInterval by repo.syncIntervalFlow.collectAsState(initial = 60)
    val isDisclaimerAccepted by repo.disclaimerAcceptedFlow.collectAsState(initial = true)

    // Automatically manage reactive and periodic background sync
    LaunchedEffect(syncPairs, savedInterval) {
        val enabledPairs = syncPairs.filter { it.isEnabled }
        if (enabledPairs.isNotEmpty()) {
            CalendarSyncScheduler.scheduleReactiveSync(context)
            if (savedInterval > 0) {
                CalendarSyncScheduler.scheduleSync(context, savedInterval)
            } else {
                CalendarSyncScheduler.cancelSync(context)
            }
        } else {
            CalendarSyncScheduler.cancelReactiveSync(context)
            CalendarSyncScheduler.cancelSync(context)
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
            try {
                val res = withContext(Dispatchers.IO) {
                    CalendarSyncEngine.syncEventsToTarget(
                        context = context,
                        fromCalendarId = pair.fromCalendarId,
                        toCalendarId = pair.toCalendarId,
                        daysPast = pair.daysPast ?: 30,
                        daysFuture = pair.daysFuture ?: 30,
                        pairId = pair.id,
                        onProgress = { cur, tot, msg ->
                            scope.launch(Dispatchers.Main) {
                                progressFraction = if (tot > 0) cur.toFloat() / tot else 0f
                                progressStatusText = msg
                            }
                        }
                    )
                }
                val parts = mutableListOf<String>()
                if (res.insertedCount > 0) parts.add("${res.insertedCount} added")
                if (res.updatedCount > 0) parts.add("${res.updatedCount} updated")
                if (res.deletedCount > 0) parts.add("${res.deletedCount} removed")
                val statusMsg = if (parts.isEmpty()) "Sync complete: 0 changes." else "Sync complete: ${parts.joinToString(", ")}."
                repo.updatePairSyncStatus(pair.id, System.currentTimeMillis(), statusMsg)
                progressFraction = 1f
                operationDone = true
                progressStatusText = "${pair.fromCalendarName} → ${pair.toCalendarName}: $statusMsg"
            } catch (e: Exception) {
                val errorMsg = "Sync failed: ${e.message}"
                repo.updatePairSyncStatus(pair.id, System.currentTimeMillis(), errorMsg)
                progressStatusText = errorMsg
            } finally {
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
        AddEditSyncPairSheet(
            pairToEdit = pairToEdit,
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
                    progressStatusText = "Saved sync pair '${pair.fromCalendarName} → ${pair.toCalendarName}'."
                    operationDone = true
                }
            }
        )
    }

    // Delete Pair Dialog (Wingman Option A: Prompt to keep vs delete cloned events)
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

    // Clear Cloned Events (This Pair Only) Dialog
    if (pairToClear != null) {
        val pair = pairToClear!!
        val targetCal = availableCalendars.find { it.id == pair.toCalendarId }
        if (targetCal != null) {
            ClearCloneEventsDialog(
                calendar = targetCal,
                eventCount = clearTargetEventCount,
                onDismiss = { if (!isOperating) pairToClear = null },
                onConfirmDelete = {
                    pairToClear = null
                    isOperating = true
                    isClearing = true
                    operationDone = false
                    progressFraction = 0f
                    progressStatusText = "Clearing cloned events for '${pair.fromCalendarName} → ${pair.toCalendarName}'..."

                    scope.launch {
                        try {
                            val deleted = withContext(Dispatchers.IO) {
                                CalendarSyncEngine.clearTargetCalendarEvents(
                                    context = context,
                                    toCalendarId = pair.toCalendarId,
                                    fromCalendarId = pair.fromCalendarId,
                                    pairId = pair.id,
                                    onProgress = { msg -> scope.launch(Dispatchers.Main) { progressStatusText = msg } }
                                )
                            }
                            progressStatusText = if (deleted > 0) {
                                "Cleared $deleted cloned event(s) from '${pair.toCalendarName}'."
                            } else {
                                "Target calendar '${pair.toCalendarName}' has 0 cloned events for this pair."
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
    }

    // Nuke Calendar Events Dialog
    if (pairToNuke != null) {
        val pair = pairToNuke!!
        val targetCal = availableCalendars.find { it.id == pair.toCalendarId }
        if (targetCal != null) {
            NukeCalendarEventsDialog(
                calendar = targetCal,
                onDismiss = { if (!isOperating) pairToNuke = null },
                onConfirmNuke = {
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
                                    toCalendarId = pair.toCalendarId,
                                    fromCalendarId = pair.fromCalendarId
                                ) { msg -> scope.launch(Dispatchers.Main) { progressStatusText = msg } }
                            }
                            operationDone = true
                        } catch (e: Exception) {
                            progressStatusText = "Nuke failed: ${e.message}"
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
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "CalCloner",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Safe & Isolated Calendar Mirroring",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Sync All Button in Top Bar
                    val hasEnabledPairs = syncPairs.any { it.isEnabled }
                    FilledTonalButton(
                        onClick = { syncAll() },
                        enabled = hasEnabledPairs && !isOperating,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync All",
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(if (isSyncingAll) topBarSpinAngle else 0f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isSyncingAll) "Syncing..." else "Sync All",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Theme Switcher Button with Crossfade Animation
                    IconButton(
                        onClick = { scope.launch { onCycleTheme() } }
                    ) {
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
                                contentDescription = "Theme",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    pairToEdit = null
                    showAddEditSheet = true
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Pair"
                    )
                },
                text = {
                    Text(
                        text = "Add Sync Pair",
                        fontWeight = FontWeight.Bold
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        },
        floatingActionButtonPosition = FabPosition.End
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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

            // Live Telemetry & Progress Card
            AnimatedVisibility(
                visible = isOperating || operationDone || progressStatusText.isNotEmpty(),
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

            // SECTION 1: CONFIGURED SYNC PAIRS
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
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Sync Pairs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${syncPairs.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
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
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

            // SECTION 2: AUTOMATIC & REACTIVE SYNC SETTINGS
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
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
                                imageVector = Icons.Default.Settings,
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

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "Zero-Drain",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Reactive Sync Status Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Reactive Sync: Active",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Event-driven Content URI triggers sync changes within ~5s without polling battery drain.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    // Scheduled Background Fallback Selector
                    var showIntervalMenu by remember { mutableStateOf(false) }
                    val intervalOptions = listOf(
                        0 to "Disabled (Reactive Only)",
                        15 to "Every 15 minutes",
                        30 to "Every 30 minutes",
                        60 to "Every 1 hour (Default)",
                        180 to "Every 3 hours",
                        360 to "Every 6 hours",
                        720 to "Every 12 hours",
                        1440 to "Every 24 hours"
                    )

                    val currentIntervalLabel = intervalOptions.find { it.first == savedInterval }?.second ?: "Every $savedInterval minutes"

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
                                        text = "Periodic Safety-Net Sync",
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

                    // Battery Optimization Warning Banner
                    AnimatedVisibility(
                        visible = !isIgnoringBatteryOptimizations,
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
