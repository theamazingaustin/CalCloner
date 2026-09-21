package com.stripedlens.calcloner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import com.stripedlens.calcloner.util.CalendarContentObserver
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.stripedlens.calcloner.BuildConfig
import com.stripedlens.calcloner.ui.components.AppBottomNavigationBar
import com.stripedlens.calcloner.ui.components.CalClonerTopBar
import com.stripedlens.calcloner.ui.dialogs.MainDialogHost
import com.stripedlens.calcloner.ui.screens.DeleteScreen
import com.stripedlens.calcloner.ui.screens.IcsImportExportScreen
import com.stripedlens.calcloner.ui.screens.OneTimeCopyScreen
import com.stripedlens.calcloner.ui.screens.OneTimeOperationsScreen
import com.stripedlens.calcloner.ui.screens.SyncScreen
import com.stripedlens.calcloner.ui.theme.CalClonerTheme
import com.stripedlens.calcloner.ui.theme.TitaniumMint
import com.stripedlens.calcloner.viewmodel.AppTab
import com.stripedlens.calcloner.viewmodel.MainViewModel

/**
 * Main Activity serving as the primary entry point for CalCloner.
 *
 * Responsibilities are strictly confined to:
 * - Activity and Edge-to-Edge window styling setup.
 * - Instantiating [MainViewModel] and wiring Jetpack Compose UI.
 * - Registering Android ActivityResult contracts (calendar permissions, file import).
 */
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_PAIR_ID = "open_pair_id"
    }

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val pairId = intent?.getStringExtra(EXTRA_OPEN_PAIR_ID)
        if (!pairId.isNullOrBlank()) {
            viewModel.openEditPairSheetById(pairId)
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

    // Battery optimization status & foreground calendar content observer
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val calendarObserver = CalendarContentObserver(coroutineScope) {
            viewModel.onForegroundCalendarChanged()
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkBatteryOptimizationStatus()
                try {
                    context.contentResolver.registerContentObserver(
                        CalendarContract.Events.CONTENT_URI,
                        true,
                        calendarObserver
                    )
                } catch (_: Exception) {}
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                try {
                    context.contentResolver.unregisterContentObserver(calendarObserver)
                } catch (_: Exception) {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            try {
                context.contentResolver.unregisterContentObserver(calendarObserver)
            } catch (_: Exception) {}
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
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

    var showBatteryInfoDialog by remember { mutableStateOf(false) }

    fun launchBatteryOptimizationIntent() {
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

    fun openBatterySettings() {
        if (!uiState.isIgnoringBatteryOptimizations) {
            showBatteryInfoDialog = true
        } else {
            launchBatteryOptimizationIntent()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dialogs & Modals
    // ─────────────────────────────────────────────────────────────────────────

    MainDialogHost(
        uiState = uiState,
        showBatteryInfoDialog = showBatteryInfoDialog,
        onAcceptDisclaimer = { viewModel.acceptDisclaimer() },
        onContinueBatteryDialog = { launchBatteryOptimizationIntent() },
        onDismissBatteryDialog = { showBatteryInfoDialog = false },
        onDismissAddEditSheet = { viewModel.dismissAddEditSheet() },
        onSavePair = { pair -> viewModel.upsertPair(pair) },
        onSaveAndSync = { pair -> viewModel.saveAndSyncPair(pair) },
        onSyncSinglePair = { pair -> viewModel.syncSinglePair(pair) },
        onOpenBatterySettings = { openBatterySettings() },
        onDeletePairWithOptions = { pair, deleteClonedEvents ->
            viewModel.deletePair(pair, deleteClonedEvents)
        },
        onDismissDeletePairDialog = { viewModel.dismissDeletePairDialog() },
        onApplyImportPreview = { viewModel.applyImportPreview() },
        onDismissImportPreview = { viewModel.dismissImportPreview() }
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Scaffold UI
    // ─────────────────────────────────────────────────────────────────────────

    BackHandler(enabled = uiState.selectedTab == AppTab.DELETE || uiState.selectedTab == AppTab.ONE_TIME_COPY || uiState.selectedTab == AppTab.ONE_TIME_ICS) {
        viewModel.selectTab(AppTab.ONE_TIME)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            CalClonerTopBar(
                themeMode = themeMode,
                isIgnoringBatteryOptimizations = uiState.isIgnoringBatteryOptimizations,
                availableUpdate = uiState.availableUpdate,
                isDownloadingUpdate = uiState.isDownloadingUpdate,
                updateDownloadProgress = uiState.updateDownloadProgress,
                onTriggerUpdate = { viewModel.startAppUpdate(context) },
                onCycleTheme = onCycleTheme,
                onExportConfig = { viewModel.exportConfiguration(context) },
                onImportConfig = { importConfigLauncher.launch("application/json") },
                onOpenBatterySettings = { openBatterySettings() },
                onNavigateBack = if (uiState.selectedTab == AppTab.DELETE || uiState.selectedTab == AppTab.ONE_TIME_COPY || uiState.selectedTab == AppTab.ONE_TIME_ICS) {
                    { viewModel.selectTab(AppTab.ONE_TIME) }
                } else null
            )
        },
        bottomBar = {
            AppBottomNavigationBar(
                currentTab = uiState.selectedTab,
                onTabSelected = { viewModel.selectTab(it) }
            )
        },
        floatingActionButton = {
            if (uiState.selectedTab == AppTab.SYNC) {
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
                                text = "New Clone Pair",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = Color(0xFF09090B)
                            )
                        }
                    }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.selectedTab) {
                AppTab.SYNC -> {
                    SyncScreen(
                        uiState = uiState,
                        onSyncAll = { viewModel.syncAll() },
                        onToggleAll = { checked -> viewModel.toggleAllPairs(checked) },
                        onSyncPair = { pair -> viewModel.syncSinglePair(pair) },
                        onEditPair = { pair -> viewModel.openEditPairSheet(pair) },
                        onTogglePairEnabled = { pairId, enabled -> viewModel.togglePairEnabled(pairId, enabled) },
                        onDeletePair = { pair -> viewModel.promptDeletePair(pair) },
                        onAddNewPair = { viewModel.openAddPairSheet() },
                        onSaveSyncInterval = { mins -> viewModel.saveSyncInterval(mins) },
                        onSaveSyncOnLowBattery = { onLow -> viewModel.saveSyncOnLowBattery(onLow) },
                        onOpenBatterySettings = { openBatterySettings() },
                        onRequestPermissions = { requestPermissions() }
                    )
                }
                AppTab.ONE_TIME -> {
                    OneTimeOperationsScreen(
                        uiState = uiState,
                        onOpenDeleteScreen = { viewModel.selectTab(AppTab.DELETE) },
                        onOpenCopyScreen = { viewModel.selectTab(AppTab.ONE_TIME_COPY) },
                        onOpenIcsScreen = { viewModel.selectTab(AppTab.ONE_TIME_ICS) }
                    )
                }
                AppTab.DELETE -> {
                    DeleteScreen(
                        uiState = uiState,
                        onCalendarSelected = { calendar -> viewModel.selectDeleteCalendar(calendar) },
                        onOperationTypeSelected = { type -> viewModel.setDeleteOperationType(type) },
                        onConfirmationTextChanged = { text -> viewModel.setDeleteConfirmationText(text) },
                        onDeleteConfirmed = { viewModel.executeCalendarDelete() },
                        onPurgeTombstones = { calendar -> viewModel.executePurgeTombstones(calendar) }
                    )
                }
                AppTab.ONE_TIME_COPY -> {
                    OneTimeCopyScreen(
                        uiState = uiState,
                        onNavigateBack = { viewModel.selectTab(AppTab.ONE_TIME) },
                        onSourceCalendarSelected = { viewModel.selectOneTimeSourceCalendar(it) },
                        onTargetCalendarSelected = { viewModel.selectOneTimeTargetCalendar(it) },
                        onDateWindowChanged = { past, future -> viewModel.setOneTimeDateWindow(past, future) },
                        onExecuteCopy = { viewModel.executeOneTimeCopy() },
                        onDismissCopyResult = { viewModel.dismissOneTimeCopyResult() }
                    )
                }
                AppTab.ONE_TIME_ICS -> {
                    IcsImportExportScreen(
                        uiState = uiState,
                        onNavigateBack = { viewModel.selectTab(AppTab.ONE_TIME) },
                        onSelectExportCalendar = { calId, selected -> viewModel.toggleIcsCalendarSelected(calId, selected) },
                        onSelectAllExportCalendars = { selectAll -> viewModel.selectAllIcsCalendars(selectAll) },
                        onSetExportFormat = { format -> viewModel.setIcsExportFormat(format) },
                        onSetExportAsZip = { asZip -> viewModel.setIcsExportAsZip(asZip) },
                        onSetDateWindow = { past, future -> viewModel.setIcsDateWindow(past, future) },
                        onExportToFolder = { treeUri -> viewModel.executeIcsExportToFolder(treeUri) },
                        onExportToZip = { zipUri -> viewModel.executeIcsExportToZip(zipUri) },
                        onDismissExportResult = { viewModel.dismissIcsExportResult() },
                        onPickImportFile = { fileUri, fileName -> viewModel.inspectIcsImportFile(fileUri, fileName) },
                        onSelectImportTargetCalendar = { calendar -> viewModel.selectIcsImportTargetCalendar(calendar) },
                        onExecuteImport = { viewModel.executeIcsImport() },
                        onDismissImportResult = { viewModel.dismissIcsImportResult() }
                    )
                }
            }
        }
    }
}
