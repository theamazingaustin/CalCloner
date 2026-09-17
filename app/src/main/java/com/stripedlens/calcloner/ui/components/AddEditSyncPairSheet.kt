package com.stripedlens.calcloner.ui.components

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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.stripedlens.calcloner.CalendarInfo
import com.stripedlens.calcloner.SyncPair
import com.stripedlens.calcloner.domain.routing.CycleDetector
import com.stripedlens.calcloner.ui.components.BackgroundBatteryProtectionBanner
import com.stripedlens.calcloner.ui.components.sheets.AnimatedConduitPipe
import com.stripedlens.calcloner.ui.components.sheets.CalendarSelectionCard
import com.stripedlens.calcloner.ui.components.sheets.PairDeleteSection
import com.stripedlens.calcloner.ui.components.sheets.SyncFieldOptionsCard
import com.stripedlens.calcloner.ui.dialogs.CalendarRoleInfoDialog
import com.stripedlens.calcloner.ui.dialogs.DeletePairDialog
import com.stripedlens.calcloner.ui.dialogs.DiscardChangesDialog
import com.stripedlens.calcloner.ui.dialogs.ApplyDialogBlurEffect
import com.stripedlens.calcloner.ui.theme.TitaniumMint
import com.stripedlens.calcloner.ui.theme.UiDimensions
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full bottom sheet workflow for creating or modifying a sync pair.
 * Encapsulates the animated route conduit, calendar pickers, sync range sliders,
 * granular field toggles, and deletion flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditSyncPairSheet(
    pairToEdit: SyncPair? = null,
    existingPairs: List<SyncPair>,
    availableCalendars: List<CalendarInfo>,
    onDismiss: () -> Unit,
    onSavePair: (SyncPair) -> Unit,
    onSaveAndSync: ((SyncPair) -> Unit)? = null,
    onSyncNow: ((SyncPair) -> Unit)? = null,
    isSyncing: Boolean = false,
    isIgnoringBatteryOptimizations: Boolean = true,
    onOpenBatterySettings: (() -> Unit)? = null,
    onDeletePairWithOptions: ((SyncPair, Boolean) -> Unit)? = null
) {
    var nickname by remember { mutableStateOf(pairToEdit?.nickname ?: "") }
    var isEnabled by remember { mutableStateOf(pairToEdit?.isEnabled ?: true) }

    var selectedFromCal by remember {
        mutableStateOf(availableCalendars.find { it.id == pairToEdit?.fromCalendarId })
    }
    var selectedToCal by remember {
        mutableStateOf(availableCalendars.find { it.id == pairToEdit?.toCalendarId })
    }

    var daysPast by remember { mutableIntStateOf(pairToEdit?.daysPast ?: 30) }
    var daysFuture by remember { mutableIntStateOf(pairToEdit?.daysFuture ?: 30) }

    // Selective Field Sync Toggles
    var syncTitle by remember { mutableStateOf(pairToEdit?.syncTitle ?: true) }
    var customTitle by remember { mutableStateOf(pairToEdit?.customTitle ?: "") }
    var syncDescription by remember { mutableStateOf(pairToEdit?.syncDescription ?: true) }
    var syncLocation by remember { mutableStateOf(pairToEdit?.syncLocation ?: true) }
    var syncReminders by remember { mutableStateOf(pairToEdit?.syncReminders ?: true) }
    var syncAvailability by remember { mutableStateOf(pairToEdit?.syncAvailability ?: true) }
    var syncStatus by remember { mutableStateOf(pairToEdit?.syncStatus ?: true) }

    val context = LocalContext.current
    var syncedEventsCount by remember { mutableIntStateOf(0) }

    // Calendar Lock After First Sync
    val isCalendarsLocked = pairToEdit != null && (pairToEdit.lastSyncTime != null && pairToEdit.lastSyncTime > 0)

    // Dirty state tracking for safe discard prompt
    val isDirty = remember(
        nickname, isEnabled, selectedFromCal, selectedToCal,
        daysPast, daysFuture, syncTitle, customTitle, syncDescription,
        syncLocation, syncReminders, syncAvailability, syncStatus, pairToEdit
    ) {
        if (pairToEdit != null) {
            nickname != (pairToEdit.nickname ?: "") ||
            isEnabled != pairToEdit.isEnabled ||
            selectedFromCal?.id != pairToEdit.fromCalendarId ||
            selectedToCal?.id != pairToEdit.toCalendarId ||
            daysPast != pairToEdit.daysPast ||
            daysFuture != pairToEdit.daysFuture ||
            syncTitle != pairToEdit.syncTitle ||
            customTitle != (pairToEdit.customTitle ?: "") ||
            syncDescription != pairToEdit.syncDescription ||
            syncLocation != pairToEdit.syncLocation ||
            syncReminders != pairToEdit.syncReminders ||
            syncAvailability != pairToEdit.syncAvailability ||
            syncStatus != pairToEdit.syncStatus
        } else {
            nickname.isNotBlank() ||
            selectedFromCal != null ||
            selectedToCal != null ||
            daysPast != 30 ||
            daysFuture != 30 ||
            !syncTitle ||
            customTitle.isNotBlank() ||
            !syncDescription ||
            !syncLocation ||
            !syncReminders ||
            !syncAvailability ||
            !syncStatus
        }
    }

    var showDiscardDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { false }
    )

    fun attemptDismiss() {
        if (isDirty) {
            showDiscardDialog = true
        } else {
            scope.launch {
                sheetState.hide()
                onDismiss()
            }
        }
    }

    // Delete sync configuration popup state
    var showDeletePairDialog by remember { mutableStateOf(false) }

    // Button feedback animations for Sync button
    val sheetSpinTransition = rememberInfiniteTransition(label = "sheetSpinTransition")
    val sheetSpinAngle by sheetSpinTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sheetSpinAngle"
    )

    val editSyncPulseAnim = remember { Animatable(1f) }
    val editSyncGlowAnim = remember { Animatable(0f) }
    val saveScaleAnim = remember { Animatable(1f) }
    var prevSheetSyncing by remember { mutableStateOf(isSyncing) }

    LaunchedEffect(isSyncing) {
        if (prevSheetSyncing && !isSyncing) {
            launch {
                editSyncPulseAnim.animateTo(1.06f, tween(180, easing = FastOutSlowInEasing))
                editSyncPulseAnim.animateTo(1.0f, tween(250, easing = LinearOutSlowInEasing))
            }
            launch {
                editSyncGlowAnim.snapTo(1f)
                editSyncGlowAnim.animateTo(0f, tween(1500, easing = FastOutSlowInEasing))
            }
        }
        prevSheetSyncing = isSyncing
    }

    LaunchedEffect(selectedToCal?.id, pairToEdit?.id) {
        val toCalId = selectedToCal?.id
        if (toCalId != null) {
            withContext(Dispatchers.IO) {
                val count = com.stripedlens.calcloner.CalendarSyncEngine.getTargetClonedEventCount(
                    context,
                    toCalId,
                    pairToEdit?.id,
                    existingPairs.map { it.id }.toSet()
                )
                withContext(Dispatchers.Main) {
                    syncedEventsCount = count
                }
            }
        } else {
            syncedEventsCount = 0
        }
    }

    // Calendar Selection Overlap Detection Popup State
    var pendingCalendarSelection by remember { mutableStateOf<Pair<CalendarInfo, Boolean>?>(null) }

    val otherPairs = existingPairs.filter { it.id != pairToEdit?.id }

    fun handleCalendarSelection(cal: CalendarInfo, isSource: Boolean) {
        if (isCalendarsLocked) return
        val conflict = otherPairs.find { it.fromCalendarId == cal.id || it.toCalendarId == cal.id }
        if (conflict != null) {
            pendingCalendarSelection = Pair(cal, isSource)
        } else {
            if (isSource) selectedFromCal = cal else selectedToCal = cal
        }
    }

    var showRouteLockedTooltip by remember { mutableStateOf(false) }

    LaunchedEffect(showRouteLockedTooltip) {
        if (showRouteLockedTooltip) {
            kotlinx.coroutines.delay(4000L)
            showRouteLockedTooltip = false
        }
    }

    // Info Dialogs
    var infoModalType by remember { mutableStateOf<String?>(null) }
    if (infoModalType != null) {
        CalendarRoleInfoDialog(
            type = infoModalType!!,
            onDismiss = { infoModalType = null }
        )
    }

    // Calendar Overlap Confirmation Dialog
    if (pendingCalendarSelection != null) {
        val pending = pendingCalendarSelection!!
        val cal = pending.first
        val isSource = pending.second
        val conflict = otherPairs.find { it.fromCalendarId == cal.id || it.toCalendarId == cal.id }
        val role = if (conflict?.fromCalendarId == cal.id) "Source" else "Target"
        ApplyDialogBlurEffect()
        AlertDialog(
            onDismissRequest = { pendingCalendarSelection = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(UiDimensions.DialogWidthFraction),
            shape = RoundedCornerShape(UiDimensions.DialogCornerRadius),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = UiDimensions.DialogGlassAlpha),
            tonalElevation = 6.dp,
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = TitaniumMint.Amber400
                )
            },
            title = {
                Text(
                    text = "Calendar Already in Use",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "\"${cal.displayName}\" is already part of sync pair \"${conflict?.displayName}\" as a $role calendar.\n\nAre you sure you want to select it for this sync pair as well?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isSource) selectedFromCal = cal else selectedToCal = cal
                        pendingCalendarSelection = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TitaniumMint.Mint500)
                ) {
                    Text("Select Anyway", color = Color(0xFF003824), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCalendarSelection = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val fromId = selectedFromCal?.id
    val toId = selectedToCal?.id

    // Conflict & Validation Checks
    val isSameCalendar = fromId != null && toId != null && fromId == toId
    val hasCycle = fromId != null && toId != null && CycleDetector.hasCycle(
        existingPairs = existingPairs,
        proposedFromId = fromId,
        proposedToId = toId,
        currentPairId = pairToEdit?.id
    )

    val alreadySourcePair = otherPairs.find { it.fromCalendarId == fromId }
    val alreadyTargetPair = otherPairs.find { it.toCalendarId == toId }
    val sourceIsTargetPair = otherPairs.find { it.toCalendarId == fromId }

    val canSave = fromId != null &&
            toId != null &&
            !isSameCalendar &&
            !hasCycle &&
            (selectedToCal?.canWrite == true)

    fun buildUpdatedPair(): SyncPair {
        return (pairToEdit ?: SyncPair.createNew(
            fromCalendarId = fromId!!,
            fromCalendarName = selectedFromCal?.displayName ?: "Source",
            toCalendarId = toId!!,
            toCalendarName = selectedToCal?.displayName ?: "Clone"
        )).copy(
            nickname = nickname.trim().ifEmpty { null },
            isEnabled = isEnabled,
            fromCalendarId = fromId!!,
            fromCalendarName = selectedFromCal?.displayName ?: "Source",
            toCalendarId = toId!!,
            toCalendarName = selectedToCal?.displayName ?: "Clone",
            daysPast = daysPast,
            daysFuture = daysFuture,
            syncTitle = syncTitle,
            customTitle = if (syncTitle) null else customTitle.trim().ifEmpty { null },
            syncDescription = syncDescription,
            syncLocation = syncLocation,
            syncReminders = syncReminders,
            syncAvailability = syncAvailability,
            syncStatus = syncStatus
        )
    }

    // Coordinated 4.8s cascade loop for pulsing Source, Pipe, Badge, and Target
    val cascadeTransition = rememberInfiniteTransition(label = "cascadePulse")
    val cycleProgress by cascadeTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cascadeProgress"
    )

    val sourcePulse = if (cycleProgress in 0.0f..0.28f) {
        kotlin.math.sin((cycleProgress / 0.28f) * Math.PI).toFloat().coerceIn(0f, 1f)
    } else 0f

    val pipeFlowProgress = if (cycleProgress in 0.22f..0.58f) {
        ((cycleProgress - 0.22f) / 0.36f).coerceIn(0f, 1f)
    } else 0f
    val pipeFlowActive = cycleProgress in 0.22f..0.58f

    val badgePulse = if (cycleProgress in 0.35f..0.55f) {
        kotlin.math.sin(((cycleProgress - 0.35f) / 0.20f) * Math.PI).toFloat().coerceIn(0f, 1f)
    } else 0f

    val targetPulse = if (cycleProgress in 0.52f..0.80f) {
        kotlin.math.sin(((cycleProgress - 0.52f) / 0.28f) * Math.PI).toFloat().coerceIn(0f, 1f)
    } else 0f

    // 1-second transition animations when toggling Active / Paused
    val pulseMultiplier by animateFloatAsState(
        targetValue = if (isEnabled) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "pulseMultiplier"
    )

    val themeAccentColor by animateColorAsState(
        targetValue = if (isEnabled) TitaniumMint.Mint400 else TitaniumMint.Amber400,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "themeAccentColor"
    )

    val themeAccentSecondary by animateColorAsState(
        targetValue = if (isEnabled) TitaniumMint.Mint500 else TitaniumMint.Amber500,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "themeAccentSecondary"
    )

    val themeContainerTint by animateColorAsState(
        targetValue = if (isEnabled) TitaniumMint.Mint500.copy(alpha = 0.12f) else TitaniumMint.Amber500.copy(alpha = 0.14f),
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "themeContainerTint"
    )

    val themeContainerBorder by animateColorAsState(
        targetValue = if (isEnabled) TitaniumMint.Mint500.copy(alpha = 0.30f) else TitaniumMint.Amber500.copy(alpha = 0.35f),
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "themeContainerBorder"
    )

    val headerBgColor by animateColorAsState(
        targetValue = if (isEnabled) TitaniumMint.Mint500 else TitaniumMint.Amber500,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "headerBgColor"
    )

    val headerBorderColor by animateColorAsState(
        targetValue = if (isEnabled) TitaniumMint.Mint600.copy(alpha = 0.4f) else TitaniumMint.Amber500.copy(alpha = 0.4f),
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "headerBorderColor"
    )

    val effectiveSourcePulse = sourcePulse * pulseMultiplier
    val effectivePipeFlowProgress = pipeFlowProgress
    val effectivePipeFlowActive = pipeFlowActive && (pulseMultiplier > 0.05f)
    val effectiveBadgePulse = badgePulse * pulseMultiplier
    val effectiveTargetPulse = targetPulse * pulseMultiplier

    // Discard Unsaved Changes Prompt Dialog
    if (showDiscardDialog) {
        DiscardChangesDialog(
            onConfirmDiscard = {
                showDiscardDialog = false
                scope.launch {
                    sheetState.hide()
                    onDismiss()
                }
            },
            onDismiss = { showDiscardDialog = false }
        )
    }

    // Delete Sync Configuration Verification Modal with Orphan Choice & Swipe Slider
    if (showDeletePairDialog && pairToEdit != null) {
        DeletePairDialog(
            pair = pairToEdit,
            onConfirmDelete = { choice ->
                showDeletePairDialog = false
                onDeletePairWithOptions?.invoke(pairToEdit, choice)
            },
            onDismiss = { showDeletePairDialog = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = { attemptDismiss() },
        sheetState = sheetState,
        windowInsets = WindowInsets(0, 0, 0, 0),
        shape = RectangleShape,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = null
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Sticky Header
            Surface(
                color = headerBgColor,
                border = BorderStroke(1.dp, headerBorderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(5f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            IconButton(
                                onClick = { attemptDismiss() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFF09090B)
                                )
                            }
                            Column {
                                Text(
                                    text = if (pairToEdit != null) "Edit Sync Configuration" else "New Sync Configuration",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF09090B)
                                )
                                Text(
                                    text = if (pairToEdit != null) "Configure Route, Window & Filters" else "Map source calendar to target clone",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF09090B).copy(alpha = 0.8f),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Active / Paused Switch
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (isEnabled) "Active" else "Paused",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF09090B)
                            )
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { isEnabled = it },
                                modifier = Modifier
                                    .scale(0.75f)
                                    .height(20.dp),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF09090B).copy(alpha = 0.35f),
                                    uncheckedThumbColor = Color(0xFF09090B),
                                    uncheckedTrackColor = Color(0xFF09090B).copy(alpha = 0.2f)
                                )
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Battery Optimization Banner
                    if (onOpenBatterySettings != null) {
                        BackgroundBatteryProtectionBanner(
                            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                            onOpenBatterySettings = onOpenBatterySettings
                        )
                    }

                    // Sync Issue Alert Banner inside Sync Settings
                    val pairErrorMessage = when {
                        selectedFromCal == null && pairToEdit != null -> "Source calendar '${pairToEdit.fromCalendarName}' was not found on this device."
                        selectedToCal == null && pairToEdit != null -> "Target calendar '${pairToEdit.toCalendarName}' was not found on this device."
                        selectedToCal != null && !selectedToCal!!.canWrite -> "Target calendar '${selectedToCal!!.displayName}' is read-only."
                        pairToEdit?.hasSyncError == true -> pairToEdit.lastSyncStatus ?: "Recent sync operation failed."
                        else -> null
                    }

                    if (pairErrorMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Sync Issue",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "SYNC ISSUE DETECTED",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = pairErrorMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }

                    // Nickname Input
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it },
                        label = { Text("Nickname (optional)", fontSize = 12.sp) },
                        placeholder = { Text("e.g. Work, On-Call, Personal Mirror", fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = themeAccentColor,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Animated Light-Pulse Sync Route Conduit Flow
                    Column(modifier = Modifier.fillMaxWidth()) {
                        CalendarSelectionCard(
                            title = "Source Calendar",
                            badgeText = "Read Only",
                            isSource = true,
                            calendars = availableCalendars,
                            selectedCalendar = selectedFromCal,
                            filterWritable = false,
                            isLocked = isCalendarsLocked,
                            pulseAmount = effectiveSourcePulse,
                            accentColor = themeAccentColor,
                            accentSecondary = themeAccentSecondary,
                            containerTint = themeContainerTint,
                            containerBorder = themeContainerBorder,
                            isPaused = !isEnabled,
                            modifier = Modifier.zIndex(1f),
                            onInfoClick = { infoModalType = "source" },
                            onLockedClick = { showRouteLockedTooltip = true },
                            onCalendarSelected = { handleCalendarSelection(it, isSource = true) }
                        )

                        AnimatedConduitPipe(
                            pipeFlowProgress = effectivePipeFlowProgress,
                            pipeFlowActive = effectivePipeFlowActive,
                            badgePulse = effectiveBadgePulse,
                            accentColor = themeAccentColor,
                            accentSecondary = themeAccentSecondary,
                            isPaused = !isEnabled,
                            modifier = Modifier.zIndex(0f)
                        )

                        CalendarSelectionCard(
                            title = "Target Calendar",
                            badgeText = "Full Access",
                            isSource = false,
                            calendars = availableCalendars,
                            selectedCalendar = selectedToCal,
                            filterWritable = true,
                            isLocked = isCalendarsLocked,
                            pulseAmount = effectiveTargetPulse,
                            accentColor = themeAccentColor,
                            accentSecondary = themeAccentSecondary,
                            containerTint = themeContainerTint,
                            containerBorder = themeContainerBorder,
                            isPaused = !isEnabled,
                            modifier = Modifier.zIndex(1f),
                            onInfoClick = { infoModalType = "target" },
                            onLockedClick = { showRouteLockedTooltip = true },
                            onCalendarSelected = { handleCalendarSelection(it, isSource = false) }
                        )

                        AnimatedVisibility(
                            visible = showRouteLockedTooltip,
                            enter = fadeIn(tween(200)) + expandVertically(tween(250)),
                            exit = fadeOut(tween(150)) + shrinkVertically(tween(200))
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                                    .clickable { showRouteLockedTooltip = false }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Calendar route is locked after initial sync. Delete this sync configuration to change calendars.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { showRouteLockedTooltip = false },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Dismiss",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Validation & Real-time Warning Banners
                    if (isSameCalendar) {
                        AlertBanner(
                            icon = Icons.Default.Warning,
                            title = "Invalid Route",
                            message = "Source and Target cannot be the same calendar.",
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    } else if (hasCycle) {
                        AlertBanner(
                            icon = Icons.Default.Warning,
                            title = "Circular Sync Loop Detected",
                            message = "Syncing '${selectedFromCal?.displayName}' to '${selectedToCal?.displayName}' creates an infinite clone loop ($fromId → $toId). One-way sync requires a directed acyclic path.",
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    } else {
                        if (alreadySourcePair != null) {
                            AlertBanner(
                                icon = Icons.Default.Info,
                                title = "Mirroring to Multiple Destinations",
                                message = "'${selectedFromCal?.displayName}' is already syncing to '${alreadySourcePair.toCalendarName}'. Its events will mirror to both destinations cleanly.",
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        if (alreadyTargetPair != null) {
                            AlertBanner(
                                icon = Icons.Default.Warning,
                                title = "Target Calendar Merging",
                                message = "'${selectedToCal?.displayName}' already receives clones from '${alreadyTargetPair.fromCalendarName}'. Both sources will be safely isolated via pair tags.",
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        if (sourceIsTargetPair != null) {
                            AlertBanner(
                                icon = Icons.Default.Info,
                                title = "Chain Sync Source",
                                message = "'${selectedFromCal?.displayName}' is a target for another pair. CalCloner will only clone original events created on it.",
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    // Combined Telemetry Card
                    val currentPair = existingPairs.find { it.id == pairToEdit?.id } ?: pairToEdit
                    CombinedSyncTelemetryCard(
                        lastSyncTime = currentPair?.lastSyncTime,
                        syncedEventsCount = syncedEventsCount
                    )

                    // Sync Range Component
                    SyncRangeCard(
                        daysPast = daysPast,
                        daysFuture = daysFuture,
                        onDaysPastChange = { daysPast = it },
                        onDaysFutureChange = { daysFuture = it }
                    )

                    // Selective Field Sync Options Card
                    SyncFieldOptionsCard(
                        syncTitle = syncTitle,
                        onSyncTitleChange = { syncTitle = it },
                        customTitle = customTitle,
                        onCustomTitleChange = { customTitle = it },
                        syncDescription = syncDescription,
                        onSyncDescriptionChange = { syncDescription = it },
                        syncLocation = syncLocation,
                        onSyncLocationChange = { syncLocation = it },
                        syncReminders = syncReminders,
                        onSyncRemindersChange = { syncReminders = it },
                        syncAvailability = syncAvailability,
                        onSyncAvailabilityChange = { syncAvailability = it },
                        syncStatus = syncStatus,
                        onSyncStatusChange = { syncStatus = it },
                        accentColor = themeAccentColor
                    )

                    // Delete Pair Configuration Section
                    PairDeleteSection(
                        pairToEdit = pairToEdit,
                        onDeleteConfigClick = { showDeletePairDialog = true }
                    )

                    Spacer(modifier = Modifier.height(96.dp))
                }

                // Floating Split Button (Save | Sync)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.90f),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = if (canSave) TitaniumMint.Mint500 else TitaniumMint.Mint500.copy(alpha = 0.25f),
                    shadowElevation = if (canSave) 8.dp else 0.dp,
                    border = BorderStroke(
                        1.dp,
                        if (canSave) TitaniumMint.Mint400 else TitaniumMint.Mint500.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Side: SAVE
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
                                .scale(saveScaleAnim.value)
                                .background(
                                    if (canSave) TitaniumMint.Mint500
                                    else TitaniumMint.Mint500.copy(alpha = 0.25f)
                                )
                                .clickable(enabled = canSave) {
                                    if (canSave) {
                                        scope.launch {
                                            saveScaleAnim.animateTo(0.92f, tween(80, easing = FastOutSlowInEasing))
                                            saveScaleAnim.animateTo(
                                                1.0f,
                                                spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessHigh
                                                )
                                            )
                                            sheetState.hide()
                                            onSavePair(buildUpdatedPair())
                                        }
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = if (canSave) Color(0xFF003824) else Color(0xFF003824).copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Save",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (canSave) Color(0xFF003824) else Color(0xFF003824).copy(alpha = 0.4f)
                            )
                        }

                        // Vertical Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(26.dp)
                                .background(
                                    if (canSave) TitaniumMint.Mint400.copy(alpha = 0.45f)
                                    else Color(0xFF003824).copy(alpha = 0.12f)
                                )
                        )

                        // Right Side: SYNC
                        val syncSideBg = androidx.compose.ui.graphics.lerp(
                            if (canSave) Color(0xFF064E3B) else Color(0xFF064E3B).copy(alpha = 0.25f),
                            TitaniumMint.Mint500.copy(alpha = 0.45f),
                            editSyncGlowAnim.value
                        )

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp))
                                .scale(editSyncPulseAnim.value)
                                .background(syncSideBg)
                                .clickable(enabled = canSave && !isSyncing) {
                                    if (canSave && !isSyncing) {
                                        val pair = buildUpdatedPair()
                                        if (onSaveAndSync != null) {
                                            onSaveAndSync(pair)
                                        } else {
                                            onSavePair(pair)
                                            onSyncNow?.invoke(pair)
                                        }
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = if (canSave) TitaniumMint.Mint400 else TitaniumMint.Mint400.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .size(18.dp)
                                    .rotate(if (isSyncing) sheetSpinAngle else 0f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            AnimatedContent(
                                targetState = isSyncing,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
                                },
                                label = "sheetSyncTextFade"
                            ) { syncing ->
                                Text(
                                    text = if (syncing) "Syncing..." else "Sync",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (canSave) TitaniumMint.Mint400 else TitaniumMint.Mint400.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
}


