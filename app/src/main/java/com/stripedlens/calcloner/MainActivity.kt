package com.stripedlens.calcloner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.stripedlens.calcloner.ui.components.BatteryOptimizationStatusCard
import com.stripedlens.calcloner.ui.components.MasterSyncCard
import com.stripedlens.calcloner.ui.components.SamsungBatteryBanner
import com.stripedlens.calcloner.ui.components.SyncPairCard
import com.stripedlens.calcloner.ui.components.TopAppBarOverflowMenu
import com.stripedlens.calcloner.ui.dialogs.ClearPairClonedEventsDialog
import com.stripedlens.calcloner.ui.dialogs.DeletePairDialog
import com.stripedlens.calcloner.ui.dialogs.DisclaimerConsentDialog
import com.stripedlens.calcloner.ui.dialogs.NukeAllTargetEventsDialog
import com.stripedlens.calcloner.ui.theme.CalClonerTheme
import com.stripedlens.calcloner.ui.theme.TitaniumMint
import com.stripedlens.calcloner.util.SystemUtils
import com.stripedlens.calcloner.viewmodel.MainViewModel
import kotlinx.coroutines.launch

/**
 * Main Activity serving as the primary entry point for CalCloner.
 *
 * Responsibilities are strictly confined to:
 * - Activity and Edge-to-Edge window styling setup.
 * - Instantiating [MainViewModel] and wiring Jetpack Compose UI.
 * - Registering Android ActivityResult contracts (calendar permissions, file import).
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

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
                        viewModel = viewModel,
                        themeMode = themeMode,
                        onCycleTheme = {
                            val nextMode = when (themeMode) {
                                ThemeMode.AUTO -> ThemeMode.DARK
                                ThemeMode.DARK -> ThemeMode.LIGHT
                                ThemeMode.LIGHT -> ThemeMode.AUTO
                            }
                            viewModel.saveThemeMode(nextMode)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Main declarative composable hierarchy for CalCloner.
 * Observes [MainViewModel.uiState] and renders headers, cards, and dialogs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSyncApp(
    viewModel: MainViewModel,
    themeMode: ThemeMode,
    onCycleTheme: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // Permissions verification & launcher
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.READ_CALENDAR] == true &&
                result[Manifest.permission.WRITE_CALENDAR] == true
        viewModel.setCalendarPermissions(granted)
    }

    fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionsLauncher.launch(permissions.toTypedArray())
    }

    LaunchedEffect(Unit) {
        val hasPerms = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        viewModel.setCalendarPermissions(hasPerms)
        if (!hasPerms) {
            requestPermissions()
        }
    }

    // Battery optimization status observer on lifecycle resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkBatteryOptimizationStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // User feedback toasts emitted from ViewModel
    LaunchedEffect(uiState.userToastMessage) {
        uiState.userToastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearToastMessage()
        }
    }

    // JSON configuration import launcher
    val importConfigLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().use { it.readText() }
                }
                if (!jsonString.isNullOrBlank()) {
                    viewModel.importConfiguration(jsonString)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
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
        targetValue = uiState.progressFraction,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "animatedProgress"
    )

    val scrollState = rememberScrollState()

    // ─────────────────────────────────────────────────────────────────────────
    // Dialogs & Modals
    // ─────────────────────────────────────────────────────────────────────────

    // First-Run Consent / Disclaimer Popup
    if (!uiState.isDisclaimerAccepted) {
        DisclaimerConsentDialog(onAccept = { viewModel.acceptDisclaimer() })
    }

    // Add / Edit Sync Pair Modal Bottom Sheet
    if (uiState.showAddEditSheet) {
        val currentPairToEdit = uiState.syncPairs.find { it.id == uiState.pairToEdit?.id } ?: uiState.pairToEdit
        AddEditSyncPairSheet(
            pairToEdit = currentPairToEdit,
            existingPairs = uiState.syncPairs,
            availableCalendars = uiState.availableCalendars,
            onDismiss = { viewModel.dismissAddEditSheet() },
            onSavePair = { pair -> viewModel.upsertPair(pair) },
            onSaveAndSync = { pair -> viewModel.saveAndSyncPair(pair) },
            onSyncNow = { pair -> viewModel.syncSinglePair(pair) },
            isSyncing = uiState.isSyncing && (uiState.syncingPairId == currentPairToEdit?.id),
            onDeletePairWithOptions = { pair, deleteClonedEvents ->
                viewModel.deletePair(pair, deleteClonedEvents)
            },
            onClearPairEvents = {
                currentPairToEdit?.let { viewModel.prepareClearEvents(it) }
            },
            onNukeTargetEvents = {
                currentPairToEdit?.let { viewModel.promptNukeTarget(it) }
            }
        )
    }

    // Delete Pair Confirmation Dialog
    uiState.pairToDelete?.let { pair ->
        DeletePairDialog(
            pair = pair,
            onDismiss = { viewModel.dismissDeletePairDialog() },
            onDeleteKeepEvents = { viewModel.deletePair(pair, deleteClonedEvents = false) },
            onDeleteAndClearEvents = { viewModel.deletePair(pair, deleteClonedEvents = true) }
        )
    }

    // Clear Cloned Events Modal (5-second countdown lock)
    uiState.pairToClear?.let { pair ->
        ClearPairClonedEventsDialog(
            targetCalendarName = pair.toCalendarName,
            onDismiss = { viewModel.dismissClearEventsDialog() },
            onConfirmDelete = { viewModel.executeClearEvents(pair) }
        )
    }

    // Destructive Nuke All Target Events Modal
    uiState.pairToNuke?.let { pair ->
        val targetCal = uiState.availableCalendars.find { it.id == pair.toCalendarId }
        if (targetCal != null) {
            NukeAllTargetEventsDialog(
                calendar = targetCal,
                onDismiss = { viewModel.dismissNukeTargetDialog() },
                onConfirmNormalDelete = { viewModel.executeNukeTargetNormal(pair) },
                onConfirmForceNuke = { viewModel.executeNukeTargetForce(pair) }
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scaffold UI
    // ─────────────────────────────────────────────────────────────────────────

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
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
                    // Header Row: Branding, Theme Toggle, Overflow Menu
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = TitaniumMint.Mint500,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_calcloner),
                                        contentDescription = "CalCloner Logo",
                                        tint = Color(0xFF003824),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "CalCloner",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "v2.1",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = TitaniumMint.Mint400
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(onClick = onCycleTheme) {
                                val (icon, desc) = when (themeMode) {
                                    ThemeMode.AUTO -> Icons.Default.BrightnessAuto to "Auto Theme"
                                    ThemeMode.DARK -> Icons.Default.Brightness4 to "Dark Theme"
                                    ThemeMode.LIGHT -> Icons.Default.BrightnessHigh to "Light Theme"
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = desc,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            TopAppBarOverflowMenu(
                                isIgnoringBatteryOptimizations = uiState.isIgnoringBatteryOptimizations,
                                onExportConfig = { viewModel.exportConfiguration(context) },
                                onImportConfig = { importConfigLauncher.launch("application/json") },
                                onOpenBatterySettings = { openBatterySettings() }
                            )
                        }
                    }

                    // Divider separating app branding from master sync
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    )

                    // Master Sync Controls Card
                    MasterSyncCard(
                        syncPairs = uiState.syncPairs,
                        isSyncingAll = uiState.isSyncingAll,
                        isOperating = uiState.isOperating,
                        spinAngle = topBarSpinAngle,
                        onToggleAll = { checked -> viewModel.toggleAllPairs(checked) },
                        onSyncAll = { viewModel.syncAll() }
                    )
                }
            }
        },
        floatingActionButton = {
            Surface(
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 8.dp,
                color = Color.Transparent,
                modifier = Modifier.clickable { viewModel.openAddPairSheet() }
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
                SamsungBatteryBanner(
                    isIgnoringBatteryOptimizations = uiState.isIgnoringBatteryOptimizations,
                    onOpenBatterySettings = { openBatterySettings() }
                )

                // Permission Alert Banner
                AnimatedVisibility(
                    visible = !uiState.hasCalendarPermissions,
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
                            Button(onClick = { requestPermissions() }) {
                                Text("Grant Permissions")
                            }
                        }
                    }
                }

                // Live Telemetry & Progress Card (Only shown for destructive clearing/nuking actions)
                AnimatedVisibility(
                    visible = (uiState.isClearing || uiState.isNuking) && (uiState.isOperating || uiState.operationDone || uiState.progressStatusText.isNotEmpty()),
                    enter = fadeIn(tween(200)) + expandVertically(tween(250)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(200))
                ) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (uiState.operationDone) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
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
                                        uiState.isSyncing -> "Sync in Progress"
                                        uiState.isNuking -> "Nuking Calendar"
                                        uiState.isClearing -> "Clearing Events"
                                        uiState.operationDone -> "Operation Completed"
                                        else -> "Status"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.operationDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )

                                if (uiState.isSyncing && uiState.progressFraction > 0f) {
                                    Text(
                                        text = "${(animatedProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (uiState.isSyncing && uiState.progressFraction > 0f) {
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
                                text = uiState.progressStatusText,
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (uiState.operationDone && (uiState.progressStatusText.contains("Cleared") || uiState.progressStatusText.contains("purging") ||
                                        uiState.progressStatusText.contains("Force") || uiState.progressStatusText.contains("wiped") ||
                                        uiState.progressStatusText.contains("Pushed deletions"))) {
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
                if (uiState.syncPairs.isEmpty()) {
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

                            Button(onClick = { viewModel.openAddPairSheet() }) {
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
                        uiState.syncPairs.forEach { pair ->
                            val fromCal = uiState.availableCalendars.find { it.id == pair.fromCalendarId }
                            val toCal = uiState.availableCalendars.find { it.id == pair.toCalendarId }
                            val isPairSyncing = uiState.isSyncing && (uiState.isSyncingAll || uiState.syncingPairId == pair.id)

                            SyncPairCard(
                                pair = pair,
                                fromCalendar = fromCal,
                                toCalendar = toCal,
                                isSyncing = isPairSyncing,
                                onSyncNow = { viewModel.syncSinglePair(pair) },
                                onEdit = { viewModel.openEditPairSheet(pair) },
                                onToggleEnabled = { enabled -> viewModel.togglePairEnabled(pair.id, enabled) },
                                onClearPairEvents = { viewModel.prepareClearEvents(pair) },
                                onNukeTarget = { viewModel.promptNukeTarget(pair) },
                                onDeletePair = { viewModel.promptDeletePair(pair) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // SECTION 2: AUTOMATIC & REACTIVE SYNC SETTINGS
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

                    val savedInterval = uiState.syncIntervalMinutes
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
                                        viewModel.saveSyncInterval(mins)
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
                                checked = uiState.syncOnLowBattery,
                                onCheckedChange = { checked ->
                                    viewModel.saveSyncOnLowBattery(checked)
                                }
                            )
                        }
                    }

                    // Battery Optimization Status & Guidance
                    BatteryOptimizationStatusCard(
                        isIgnoringBatteryOptimizations = uiState.isIgnoringBatteryOptimizations,
                        syncIntervalMinutes = uiState.syncIntervalMinutes,
                        onOpenBatterySettings = { openBatterySettings() }
                    )
                }
            }

            // Bottom Spacer to prevent FAB occlusion
            Spacer(modifier = Modifier.height(84.dp))
        }
    }
}
