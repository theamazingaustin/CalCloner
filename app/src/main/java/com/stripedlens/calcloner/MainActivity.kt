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
import com.stripedlens.calcloner.ui.components.AddEditSyncPairSheet
import com.stripedlens.calcloner.ui.components.AppBottomNavigationBar
import com.stripedlens.calcloner.ui.components.TopAppBarOverflowMenu
import com.stripedlens.calcloner.ui.dialogs.BatteryOptimizationInfoDialog
import com.stripedlens.calcloner.ui.dialogs.DeletePairDialog
import com.stripedlens.calcloner.ui.dialogs.DisclaimerConsentDialog
import com.stripedlens.calcloner.ui.screens.DeleteScreen
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

    // First-Run Consent / Disclaimer Popup
    if (!uiState.isDisclaimerAccepted) {
        DisclaimerConsentDialog(onAccept = { viewModel.acceptDisclaimer() })
    }

    // Battery Optimization Educational Pre-Dialog
    if (showBatteryInfoDialog) {
        BatteryOptimizationInfoDialog(
            onContinue = {
                launchBatteryOptimizationIntent()
            },
            onDismiss = {
                showBatteryInfoDialog = false
            }
        )
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
            isIgnoringBatteryOptimizations = uiState.isIgnoringBatteryOptimizations,
            onOpenBatterySettings = { openBatterySettings() },
            onDeletePairWithOptions = { pair, deleteClonedEvents ->
                viewModel.deletePair(pair, deleteClonedEvents)
            }
        )
    }

    // Delete Pair Confirmation Dialog
    uiState.pairToDelete?.let { pair ->
        DeletePairDialog(
            pair = pair,
            onDismiss = { viewModel.dismissDeletePairDialog() },
            onConfirmDelete = { deleteClonedEvents ->
                viewModel.deletePair(pair, deleteClonedEvents)
            }
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scaffold UI
    // ─────────────────────────────────────────────────────────────────────────

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
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
                                    text = "v${BuildConfig.VERSION_NAME}",
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
                }
            }
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
                                text = "New Sync Pair",
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
                AppTab.DELETE -> {
                    DeleteScreen(
                        uiState = uiState,
                        onCalendarSelected = { calendar -> viewModel.selectDeleteCalendar(calendar) },
                        onOperationTypeSelected = { type -> viewModel.setDeleteOperationType(type) },
                        onConfirmationTextChanged = { text -> viewModel.setDeleteConfirmationText(text) },
                        onDeleteConfirmed = { viewModel.executeCalendarDelete() }
                    )
                }
            }
        }
    }
}
