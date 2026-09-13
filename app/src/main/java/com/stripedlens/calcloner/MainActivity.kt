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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.stripedlens.calcloner.ui.components.CalendarDropdown
import com.stripedlens.calcloner.ui.dialogs.ClearCloneEventsDialog
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
    val savedFromId by repo.fromCalendarIdFlow.collectAsState(initial = null)
    val savedToId by repo.toCalendarIdFlow.collectAsState(initial = null)
    val savedInterval by repo.syncIntervalFlow.collectAsState(initial = 60)
    val customDaysPast by repo.customDaysPastFlow.collectAsState(initial = null)
    val customDaysFuture by repo.customDaysFutureFlow.collectAsState(initial = null)
    val lastSyncTime by repo.lastSyncTimeFlow.collectAsState(initial = null)
    val lastSyncStatus by repo.lastSyncStatusFlow.collectAsState(initial = null)
    val isDisclaimerAccepted by repo.disclaimerAcceptedFlow.collectAsState(initial = true)

    // Automatically ensure background sync is enqueued whenever valid calendars are configured
    LaunchedEffect(savedFromId, savedToId, savedInterval) {
        if (savedFromId != null && savedToId != null && savedFromId != savedToId && savedInterval > 0) {
            CalendarSyncScheduler.scheduleSync(context, savedInterval)
        } else if (savedInterval <= 0) {
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

    // Operation & Progress state
    var isOperating by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var isClearing by remember { mutableStateOf(false) }
    var isNuking by remember { mutableStateOf(false) }
    var operationDone by remember { mutableStateOf(false) }
    var progressFraction by remember { mutableFloatStateOf(0f) }
    var progressStatusText by remember { mutableStateOf("") }

    // Warning Popups state
    var pendingTargetCalendar by remember { mutableStateOf<CalendarInfo?>(null) }
    var showTargetWarningDialog by remember { mutableStateOf(false) }
    var targetCalendarEventCount by remember { mutableIntStateOf(0) }

    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var clearTargetEventCount by remember { mutableIntStateOf(0) }

    var showNukeConfirmDialog by remember { mutableStateOf(false) }

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

    val selectedFromCalendar = availableCalendars.find { it.id == savedFromId }
    val selectedToCalendar = availableCalendars.find { it.id == savedToId }

    val isSyncEnabled = hasPermissions &&
                        !isOperating &&
                        selectedFromCalendar != null &&
                        selectedToCalendar != null &&
                        selectedToCalendar.canWrite &&
                        selectedFromCalendar.id != selectedToCalendar.id

    val isClearEnabled = hasPermissions &&
                         !isOperating &&
                         selectedToCalendar != null &&
                         selectedToCalendar.canWrite &&
                         (selectedFromCalendar == null || selectedToCalendar.id != selectedFromCalendar.id)

    // Motion Animation Transition Specs
    val infiniteTransition = rememberInfiniteTransition(label = "syncSpinTransition")
    val fabSpinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fabSpinAngle"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "animatedProgress"
    )

    // First-Run Consent / Disclaimer Popup
    if (!isDisclaimerAccepted) {
        DisclaimerConsentDialog(
            onAccept = {
                scope.launch { repo.acceptDisclaimer() }
            }
        )
    }

    // Target Calendar Warning Dialog with 5s countdown + Swipe slider + Event Count
    if (showTargetWarningDialog && pendingTargetCalendar != null) {
        TargetCalendarWarningDialog(
            calendar = pendingTargetCalendar!!,
            eventCount = targetCalendarEventCount,
            onDismiss = {
                showTargetWarningDialog = false
                pendingTargetCalendar = null
            },
            onConfirmSelection = {
                val cal = pendingTargetCalendar
                showTargetWarningDialog = false
                pendingTargetCalendar = null
                if (cal != null) {
                    scope.launch {
                        repo.saveToCalendar(cal.id, cal.displayName)
                    }
                }
            }
        )
    }

    // Delete (Clear) Clone Calendar Events Dialog with 5s countdown + Swipe slider + Event Count
    if (showClearConfirmDialog && selectedToCalendar != null) {
        ClearCloneEventsDialog(
            calendar = selectedToCalendar,
            eventCount = clearTargetEventCount,
            onDismiss = {
                if (!isOperating) showClearConfirmDialog = false
            },
            onConfirmDelete = {
                showClearConfirmDialog = false
                isOperating = true
                isClearing = true
                isSyncing = false
                operationDone = false
                progressFraction = 0f
                progressStatusText = "Connecting to clone calendar..."

                scope.launch {
                    try {
                        val deleted = withContext(Dispatchers.IO) {
                            CalendarSyncEngine.clearTargetCalendarEvents(
                                context = context,
                                toCalendarId = selectedToCalendar.id,
                                fromCalendarId = selectedFromCalendar?.id,
                                onProgress = { msg ->
                                    scope.launch(Dispatchers.Main) {
                                        progressStatusText = msg
                                    }
                                }
                            )
                        }
                        progressStatusText = if (deleted > 0) {
                            "Cleared $deleted event(s) from clone calendar \"${selectedToCalendar.displayName}\"."
                        } else {
                            "Clone calendar \"${selectedToCalendar.displayName}\" is already empty (0 events)."
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

    // Nuke Calendar Events Dialog with 5s countdown + Swipe slider
    if (showNukeConfirmDialog && selectedToCalendar != null) {
        NukeCalendarEventsDialog(
            calendar = selectedToCalendar,
            onDismiss = {
                if (!isOperating) showNukeConfirmDialog = false
            },
            onConfirmNuke = {
                showNukeConfirmDialog = false
                isOperating = true
                isNuking = true
                isClearing = false
                isSyncing = false
                operationDone = false
                progressFraction = 0f
                progressStatusText = "Step 1/3: Forcing Google Cloud download..."

                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            CalendarSyncEngine.nukeTargetCalendarEvents(
                                context = context,
                                toCalendarId = selectedToCalendar.id,
                                fromCalendarId = selectedFromCalendar?.id
                            ) { msg ->
                                scope.launch(Dispatchers.Main) {
                                    progressStatusText = msg
                                }
                            }
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
                    // Theme Switcher Button with Crossfade Animation
                    FilledTonalButton(
                        onClick = {
                            scope.launch { onCycleTheme() }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Crossfade(
                            targetState = themeMode,
                            animationSpec = tween(durationMillis = 200),
                            label = "themeCrossfade"
                        ) { mode ->
                            val (iconRes: Int, label: String) = when (mode) {
                                ThemeMode.AUTO -> Pair(R.drawable.ic_theme_auto, "Auto")
                                ThemeMode.DARK -> Pair(R.drawable.ic_theme_dark, "Dark")
                                ThemeMode.LIGHT -> Pair(R.drawable.ic_theme_light, "Light")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = iconRes),
                                    contentDescription = "Theme: $label",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
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
                    if (!isSyncEnabled) return@ExtendedFloatingActionButton
                    val fromCal = selectedFromCalendar ?: return@ExtendedFloatingActionButton
                    val toCal = selectedToCalendar ?: return@ExtendedFloatingActionButton

                    isOperating = true
                    isSyncing = true
                    isClearing = false
                    operationDone = false
                    progressFraction = 0f
                    progressStatusText = "Reading calendar events..."

                    scope.launch {
                        try {
                            val daysPast = repo.syncDaysPastFlow.first()
                            val daysFuture = repo.syncDaysFutureFlow.first()
                            val result = withContext(Dispatchers.IO) {
                                CalendarSyncEngine.syncEventsToTarget(
                                    context = context,
                                    fromCalendarId = fromCal.id,
                                    toCalendarId = toCal.id,
                                    daysPast = daysPast,
                                    daysFuture = daysFuture,
                                    onProgress = { current, total, msg ->
                                        scope.launch(Dispatchers.Main) {
                                            progressFraction = if (total > 0) current.toFloat() / total else 0f
                                            progressStatusText = msg
                                        }
                                    }
                                )
                            }
                            val statusMsg = when {
                                result.totalSourceEvents == 0 && result.deletedCount == 0 -> "Source calendar is empty (0 events)."
                                result.totalSourceEvents == 0 && result.deletedCount > 0 -> "Source calendar is empty. Removed ${result.deletedCount} clone event(s)."
                                result.insertedCount == 0 && result.updatedCount == 0 && result.deletedCount == 0 -> "Sync complete: 0 changes found."
                                else -> {
                                    val parts = mutableListOf<String>()
                                    if (result.insertedCount > 0) parts.add("${result.insertedCount} added")
                                    if (result.updatedCount > 0) parts.add("${result.updatedCount} updated")
                                    if (result.deletedCount > 0) parts.add("${result.deletedCount} removed")
                                    "Sync complete: ${parts.joinToString(", ")}."
                                }
                            }

                            progressFraction = 1f
                            operationDone = true
                            progressStatusText = statusMsg
                            repo.saveLastSync(System.currentTimeMillis(), statusMsg)
                        } catch (e: Exception) {
                            progressStatusText = "Sync failed: ${e.message}"
                            repo.saveLastSync(System.currentTimeMillis(), "Failed: ${e.message}")
                        } finally {
                            isOperating = false
                            isSyncing = false
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sync",
                        modifier = Modifier
                            .size(22.dp)
                            .rotate(if (isSyncing) fabSpinAngle else 0f)
                    )
                },
                text = {
                    Text(
                        text = if (isSyncing) "Syncing..." else "Sync Now",
                        fontWeight = FontWeight.Bold
                    )
                },
                containerColor = if (isSyncEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isSyncEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
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

            // CARD 1: SYNC ROUTE (SOURCE -> TARGET)
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Safe",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Sync Route",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Safeguard Pill Badge
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "Source Protected",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Text(
                        text = "Source calendar is strictly read-only. Events are cloned one-way into target.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Divider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    // Source Calendar Dropdown
                    CalendarDropdown(
                        label = "SOURCE Calendar (Read Only)",
                        calendars = availableCalendars,
                        selectedCalendar = selectedFromCalendar,
                        onCalendarSelected = { cal ->
                            scope.launch {
                                repo.saveFromCalendar(cal?.id, cal?.displayName)
                            }
                        }
                    )

                    AnimatedVisibility(
                        visible = selectedFromCalendar == null,
                        enter = fadeIn(tween(150)) + expandVertically(tween(200)),
                        exit = fadeOut(tween(100)) + shrinkVertically(tween(150))
                    ) {
                        val isMissing = savedFromId != null
                        Text(
                            text = if (isMissing) "Calendar Not Found: Saved source calendar was not found on this device."
                                   else "Please select a source calendar to clone events from.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isMissing) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    // Directional Indicator: Source -> Target Flow
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Flow Down",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "clones one-way into",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Target (Clone) Calendar Dropdown
                    CalendarDropdown(
                        label = "TARGET (Clone) Calendar",
                        calendars = availableCalendars,
                        selectedCalendar = selectedToCalendar,
                        filterWritable = true,
                        onCalendarSelected = { cal ->
                            if (cal == null) {
                                scope.launch { repo.saveToCalendar(null, null) }
                            } else {
                                scope.launch {
                                    targetCalendarEventCount = withContext(Dispatchers.IO) {
                                        CalendarSyncEngine.getCalendarEventCount(context, cal.id)
                                    }
                                    pendingTargetCalendar = cal
                                    showTargetWarningDialog = true
                                }
                            }
                        }
                    )

                    AnimatedVisibility(
                        visible = selectedToCalendar == null || !selectedToCalendar.canWrite || (selectedFromCalendar != null && selectedFromCalendar.id == selectedToCalendar.id),
                        enter = fadeIn(tween(150)) + expandVertically(tween(200)),
                        exit = fadeOut(tween(100)) + shrinkVertically(tween(150))
                    ) {
                        val errorText = when {
                            selectedToCalendar == null -> {
                                if (savedToId != null) "Calendar Not Found: Saved target calendar was deleted or not found."
                                else "Please select a target clone calendar to enable sync."
                            }
                            !selectedToCalendar.canWrite -> "Selected target calendar is read-only. Select a writable calendar."
                            selectedFromCalendar != null && selectedFromCalendar.id == selectedToCalendar.id -> "Source and Target cannot be the same calendar!"
                            else -> ""
                        }
                        Text(
                            text = errorText,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // CARD 2: SYNC SCHEDULE & DATE WINDOW
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Schedule & Date Window",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Interval selector
                    val intervalOptions = listOf(0, 15, 30, 60, 120, 360, 1440)
                    var intervalExpanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = intervalExpanded,
                        onExpandedChange = { if (!isOperating) intervalExpanded = !intervalExpanded }
                    ) {
                        OutlinedTextField(
                            value = DateTimeUtils.formatInterval(savedInterval),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Background Sync Interval") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = intervalExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = intervalExpanded,
                            onDismissRequest = { intervalExpanded = false }
                        ) {
                            intervalOptions.forEach { interval ->
                                DropdownMenuItem(
                                    text = { Text(DateTimeUtils.formatInterval(interval)) },
                                    onClick = {
                                        intervalExpanded = false
                                        scope.launch {
                                            repo.saveSyncInterval(interval)
                                            CalendarSyncScheduler.scheduleSync(context, interval)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Sync Window Range (Days Back / Days Forward)
                    val focusManager = LocalFocusManager.current
                    var isPastFocused by remember { mutableStateOf(false) }
                    var hadPastFocus by remember { mutableStateOf(false) }
                    var pastInputText by remember { mutableStateOf("") }

                    var isFutureFocused by remember { mutableStateOf(false) }
                    var hadFutureFocus by remember { mutableStateOf(false) }
                    var futureInputText by remember { mutableStateOf("") }

                    LaunchedEffect(customDaysPast) {
                        if (!isPastFocused) {
                            pastInputText = if (customDaysPast != null && customDaysPast != 30) customDaysPast.toString() else ""
                        }
                    }

                    LaunchedEffect(customDaysFuture) {
                        if (!isFutureFocused) {
                            futureInputText = if (customDaysFuture != null && customDaysFuture != 30) customDaysFuture.toString() else ""
                        }
                    }

                    val isPastDefault = when {
                        isPastFocused -> pastInputText.isEmpty() || pastInputText == "30"
                        else -> customDaysPast == null || customDaysPast == 30
                    }

                    val pastDisplayValue = when {
                        isPastFocused -> pastInputText
                        isPastDefault -> "30"
                        else -> if (pastInputText.isNotEmpty()) pastInputText else (customDaysPast?.toString() ?: "30")
                    }

                    val isFutureDefault = when {
                        isFutureFocused -> futureInputText.isEmpty() || futureInputText == "30"
                        else -> customDaysFuture == null || customDaysFuture == 30
                    }

                    val futureDisplayValue = when {
                        isFutureFocused -> futureInputText
                        isFutureDefault -> "30"
                        else -> if (futureInputText.isNotEmpty()) futureInputText else (customDaysFuture?.toString() ?: "30")
                    }

                    val defaultTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    val normalTextColor = MaterialTheme.colorScheme.onSurface

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Days Back
                        OutlinedTextField(
                            value = pastDisplayValue,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isDigit() }.take(3)
                                pastInputText = filtered
                                val num = filtered.toIntOrNull()
                                if (num != null && num in 1..999) {
                                    if (num == 30) {
                                        scope.launch { repo.saveSyncDaysPast(null) }
                                    } else {
                                        scope.launch { repo.saveSyncDaysPast(num) }
                                    }
                                } else if (filtered.isEmpty()) {
                                    scope.launch { repo.saveSyncDaysPast(null) }
                                }
                            },
                            label = { Text("Days Back") },
                            supportingText = {
                                Text(if (isPastDefault) "Default: 30" else "1 - 999 days", fontSize = 11.sp)
                            },
                            singleLine = true,
                            enabled = !isOperating,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = if (isPastDefault) defaultTextColor else normalTextColor,
                                unfocusedTextColor = if (isPastDefault) defaultTextColor else normalTextColor
                            ),
                            textStyle = LocalTextStyle.current.copy(
                                color = if (isPastDefault) defaultTextColor else normalTextColor
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = {
                                    focusManager.moveFocus(FocusDirection.Next)
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { focusState ->
                                    val previouslyFocused = hadPastFocus
                                    hadPastFocus = focusState.isFocused
                                    isPastFocused = focusState.isFocused
                                    if (previouslyFocused && !focusState.isFocused) {
                                        val num = pastInputText.toIntOrNull()
                                        if (num == null || num <= 0 || num == 30) {
                                            pastInputText = ""
                                            scope.launch { repo.saveSyncDaysPast(null) }
                                        } else {
                                            scope.launch { repo.saveSyncDaysPast(num) }
                                        }
                                    }
                                }
                        )

                        // Days Forward
                        OutlinedTextField(
                            value = futureDisplayValue,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isDigit() }.take(3)
                                futureInputText = filtered
                                val num = filtered.toIntOrNull()
                                if (num != null && num in 1..999) {
                                    if (num == 30) {
                                        scope.launch { repo.saveSyncDaysFuture(null) }
                                    } else {
                                        scope.launch { repo.saveSyncDaysFuture(num) }
                                    }
                                } else if (filtered.isEmpty()) {
                                    scope.launch { repo.saveSyncDaysFuture(null) }
                                }
                            },
                            label = { Text("Days Forward") },
                            supportingText = {
                                Text(if (isFutureDefault) "Default: 30" else "1 - 999 days", fontSize = 11.sp)
                            },
                            singleLine = true,
                            enabled = !isOperating,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = if (isFutureDefault) defaultTextColor else normalTextColor,
                                unfocusedTextColor = if (isFutureDefault) defaultTextColor else normalTextColor
                            ),
                            textStyle = LocalTextStyle.current.copy(
                                color = if (isFutureDefault) defaultTextColor else normalTextColor
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { focusState ->
                                    val previouslyFocused = hadFutureFocus
                                    hadFutureFocus = focusState.isFocused
                                    isFutureFocused = focusState.isFocused
                                    if (previouslyFocused && !focusState.isFocused) {
                                        val num = futureInputText.toIntOrNull()
                                        if (num == null || num <= 0 || num == 30) {
                                            futureInputText = ""
                                            scope.launch { repo.saveSyncDaysFuture(null) }
                                        } else {
                                            scope.launch { repo.saveSyncDaysFuture(num) }
                                        }
                                    }
                                }
                        )
                    }

                    Text(
                        text = "Events outside this date window are automatically pruned from the clone calendar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )

                    // Unrestricted Battery Notice (displayed if scheduled background sync is enabled but battery is optimized)
                    AnimatedVisibility(
                        visible = savedInterval > 0 && !isIgnoringBatteryOptimizations,
                        enter = fadeIn(tween(200)) + expandVertically(tween(250)),
                        exit = fadeOut(tween(150)) + shrinkVertically(tween(200))
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
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
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Unrestricted Battery Recommended",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                Text(
                                    text = "Android may put CalCloner to sleep and delay background syncs. Set battery to Unrestricted for reliable intervals.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
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

            // CARD 3: TELEMETRY & PROGRESS STATUS
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (operationDone) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                     else MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Last Synchronized",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Last Synchronized:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = DateTimeUtils.formatLastSync(lastSyncTime),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (lastSyncTime != null && lastSyncTime!! > 0L) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!lastSyncStatus.isNullOrEmpty()) {
                        Text(
                            text = lastSyncStatus!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (lastSyncStatus!!.startsWith("Failed") || lastSyncStatus!!.contains("denied") || lastSyncStatus!!.contains("Not Found")) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                            }
                        )
                    }

                    // Active Progress & Live Feedback Section
                    AnimatedVisibility(
                        visible = isOperating || operationDone || progressStatusText.isNotEmpty(),
                        enter = fadeIn(tween(200)) + expandVertically(tween(250)),
                        exit = fadeOut(tween(150)) + shrinkVertically(tween(200))
                    ) {
                        Column(
                            modifier = Modifier.padding(top = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when {
                                        isSyncing -> "Cloning in Progress"
                                        isNuking -> "Nuking Calendar (Deep Clean)"
                                        isClearing -> "Clearing Clone Events"
                                        operationDone -> "Operation Completed"
                                        else -> "Live Status"
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

                            // Native Material 3 Animated Linear Progress Bar
                            if (isSyncing && progressFraction > 0f) {
                                LinearProgressIndicator(
                                    progress = animatedProgress.coerceIn(0f, 1f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
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

                            if (operationDone) {
                                if (!progressStatusText.contains("Operation Completed")) {
                                    Text(
                                        text = "Operation finished. You can safely close the app.",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                if (progressStatusText.contains("Cleared") || progressStatusText.contains("purging") ||
                                    progressStatusText.contains("Force") || progressStatusText.contains("wiped") ||
                                    progressStatusText.contains("Pushed deletions")) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    FilledTonalButton(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = { SystemUtils.openNotificationShade(context) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Notifications,
                                            contentDescription = "Notification",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Open Notification Shade (Confirm Cloud Deletes)", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // CARD 4: DATA MANAGEMENT (3A - DISCRETE BOTTOM SECTION)
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ),
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
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Data Management",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Data Management",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "Clone Only",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Text(
                        text = "Clean or reset the target clone calendar. Source events are strictly protected and never touched.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Divider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )

                    // Clear Cloned Events (M3 Outlined Error Button)
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isClearEnabled,
                        onClick = {
                            val toCal = selectedToCalendar ?: return@OutlinedButton
                            scope.launch {
                                clearTargetEventCount = withContext(Dispatchers.IO) {
                                    CalendarSyncEngine.getCalendarEventCount(context, toCal.id)
                                }
                                showClearConfirmDialog = true
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isClearEnabled) MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isClearing) "Deleting Cloned Events..." else "Delete Cloned Events from Target",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    // Nuke All Events (Force Cloud Resync & Deep Clean)
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isClearEnabled,
                        onClick = {
                            showNukeConfirmDialog = true
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Nuke",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isNuking) "Nuking Calendar..." else "Nuke ALL Events (Force Cloud Resync)",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // Bottom Spacer to prevent Extended FAB occlusion
            Spacer(modifier = Modifier.height(84.dp))
        }
    }
}
