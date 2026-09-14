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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.stripedlens.calcloner.CalendarInfo
import com.stripedlens.calcloner.SyncPair
import com.stripedlens.calcloner.ui.dialogs.CalendarInfoPopoverDialog
import com.stripedlens.calcloner.ui.theme.TitaniumMint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import kotlin.math.roundToInt

/**
 * Directed Acyclic Graph (DAG) cycle detection to strictly prevent circular sync loops.
 */
fun checkHasCycle(
    existingPairs: List<SyncPair>,
    proposedFromId: Long,
    proposedToId: Long,
    currentPairId: String? = null
): Boolean {
    if (proposedFromId == proposedToId) return true

    val graph = mutableMapOf<Long, MutableList<Long>>()
    for (pair in existingPairs) {
        if (pair.id == currentPairId) continue
        graph.getOrPut(pair.fromCalendarId) { mutableListOf() }.add(pair.toCalendarId)
    }
    graph.getOrPut(proposedFromId) { mutableListOf() }.add(proposedToId)

    val visited = mutableSetOf<Long>()
    val inStack = mutableSetOf<Long>()

    fun dfs(node: Long): Boolean {
        visited.add(node)
        inStack.add(node)
        for (neighbor in graph[node] ?: emptyList()) {
            if (neighbor !in visited) {
                if (dfs(neighbor)) return true
            } else if (neighbor in inStack) {
                return true
            }
        }
        inStack.remove(node)
        return false
    }

    for (node in graph.keys) {
        if (node !in visited) {
            if (dfs(node)) return true
        }
    }
    return false
}

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
    onDeletePairWithOptions: ((SyncPair, Boolean) -> Unit)? = null,
    onClearPairEvents: (() -> Unit)? = null,
    onNukeTargetEvents: (() -> Unit)? = null
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
    var deleteClonedChoice by remember { mutableStateOf<Boolean?>(null) }

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

    // Info Popovers
    var infoModalType by remember { mutableStateOf<String?>(null) }
    if (infoModalType != null) {
        CalendarInfoPopoverDialog(
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
        AlertDialog(
            onDismissRequest = { pendingCalendarSelection = null },
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
    val hasCycle = fromId != null && toId != null && checkHasCycle(
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
            fromCalendarId = fromId!!,
            fromCalendarName = selectedFromCal?.displayName ?: "Source",
            toCalendarId = toId!!,
            toCalendarName = selectedToCal?.displayName ?: "Clone",
            daysPast = daysPast,
            daysFuture = daysFuture,
            isEnabled = isEnabled,
            syncTitle = syncTitle,
            customTitle = if (!syncTitle) customTitle.trim().ifEmpty { "-" } else null,
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
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = TitaniumMint.Amber400
                )
            },
            title = {
                Text(
                    text = "Discard Changes?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "You have unsaved changes. Discarding will revert to previous settings and return to the main screen.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep Editing")
                }
            }
        )
    }

    // Delete Sync Configuration Verification Modal with Orphan Choice & Swipe Slider
    if (showDeletePairDialog && pairToEdit != null) {
        AlertDialog(
            onDismissRequest = {
                showDeletePairDialog = false
                deleteClonedChoice = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = TitaniumMint.Rose400
                )
            },
            title = {
                Text(
                    text = "Delete Sync Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Deleting '${pairToEdit.displayName}' will permanently stop sync between '${pairToEdit.fromCalendarName}' and '${pairToEdit.toCalendarName}'.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = "What should happen to previously cloned events on '${pairToEdit.toCalendarName}'?",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Option A: Delete cloned events
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (deleteClonedChoice == true) TitaniumMint.Rose500.copy(alpha = 0.15f) else MaterialTheme.colorScheme.background,
                        border = BorderStroke(
                            1.dp,
                            if (deleteClonedChoice == true) TitaniumMint.Rose400 else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { deleteClonedChoice = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RadioButton(
                                selected = deleteClonedChoice == true,
                                onClick = { deleteClonedChoice = true },
                                colors = RadioButtonDefaults.colors(selectedColor = TitaniumMint.Rose400)
                            )
                            Column {
                                Text(
                                    text = "Delete cloned events",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (deleteClonedChoice == true) TitaniumMint.Rose400 else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Removes all events previously cloned by this sync pair.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Option B: Keep cloned events (leave orphaned)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (deleteClonedChoice == false) TitaniumMint.Mint500.copy(alpha = 0.12f) else MaterialTheme.colorScheme.background,
                        border = BorderStroke(
                            1.dp,
                            if (deleteClonedChoice == false) TitaniumMint.Mint400 else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { deleteClonedChoice = false }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RadioButton(
                                selected = deleteClonedChoice == false,
                                onClick = { deleteClonedChoice = false },
                                colors = RadioButtonDefaults.colors(selectedColor = TitaniumMint.Mint400)
                            )
                            Column {
                                Text(
                                    text = "Keep cloned events",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (deleteClonedChoice == false) TitaniumMint.Mint400 else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Leave cloned events on target calendar as standalone orphaned events.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    SwipeToConfirmSlider(
                        text = "Swipe to Delete Pair →",
                        accentColor = TitaniumMint.Rose400,
                        enabled = deleteClonedChoice != null,
                        disabledText = "Choose an option above to unlock",
                        onConfirmed = {
                            val choice = deleteClonedChoice ?: false
                            showDeletePairDialog = false
                            deleteClonedChoice = null
                            onDeletePairWithOptions?.invoke(pairToEdit, choice)
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeletePairDialog = false
                        deleteClonedChoice = null
                    }
                ) {
                    Text("Cancel")
                }
            }
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
            // Formal Sticky Header (Extends behind status bar to top of screen)
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
                                if (pairToEdit != null) {
                                    Text(
                                        text = pairToEdit.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF09090B).copy(alpha = 0.75f),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // Active Switch - Compact height and subtle scale away from system indicators
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
                        // 1. SOURCE CALENDAR BOX
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

                        // 2. CONNECTING CONDUIT CHANNEL (ANIMATED PIPE)
                        AnimatedConduitPipe(
                            pipeFlowProgress = effectivePipeFlowProgress,
                            pipeFlowActive = effectivePipeFlowActive,
                            badgePulse = effectiveBadgePulse,
                            accentColor = themeAccentColor,
                            accentSecondary = themeAccentSecondary,
                            isPaused = !isEnabled,
                            modifier = Modifier.zIndex(0f)
                        )

                        // 3. TARGET CALENDAR BOX
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

                        // Route Locked Tooltip (Appears when tapping a locked calendar)
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

                    // Combined Telemetry Card (60/40 split above Sync Range)
                    val currentPair = existingPairs.find { it.id == pairToEdit?.id } ?: pairToEdit
                    CombinedSyncTelemetryCard(
                        lastSyncTime = currentPair?.lastSyncTime,
                        syncedEventsCount = syncedEventsCount
                    )

                    // Sync Range Component (with Days/Weeks/Months dropdowns)
                    SyncRangeCard(
                        daysPast = daysPast,
                        daysFuture = daysFuture,
                        onDaysPastChange = { daysPast = it },
                        onDaysFutureChange = { daysFuture = it }
                    )

            // Selective Field Sync Options Card (Collapsible)
            var syncOptionsExpanded by remember { mutableStateOf(false) }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { syncOptionsExpanded = !syncOptionsExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = TitaniumMint.Mint400,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "Sync Options",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Choose which event fields to replicate to the target calendar.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { syncOptionsExpanded = !syncOptionsExpanded }) {
                            Icon(
                                imageVector = if (syncOptionsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (syncOptionsExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = syncOptionsExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.background,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                            // 1. Title (can be toggled; reveals custom title field when disabled)
                            FieldSyncToggleRow(
                                title = "Event Name",
                                subtitle = if (syncTitle) "Sync event title and summary" else "Use custom title for all events",
                                icon = Icons.Default.Title,
                                checked = syncTitle,
                                enabled = true,
                                onCheckedChange = { syncTitle = it }
                            )

                            // Custom Title Input (revealed when Event Name is disabled)
                            AnimatedVisibility(
                                visible = !syncTitle,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Custom Event Title",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Replaces the original event title (e.g. \"Busy\"). If left empty, defaults to \"-\".",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                    val hasControlChars = customTitle.any { it.code in 0..31 && it != '\t' && it != '\n' && it != '\r' }
                                    OutlinedTextField(
                                        value = customTitle,
                                        onValueChange = { input ->
                                            val sanitized = input.filter { it.code >= 32 || it == '\t' || it == '\n' || it == '\r' }.take(100)
                                            customTitle = sanitized
                                        },
                                        placeholder = { Text("-", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                                        singleLine = true,
                                        isError = hasControlChars,
                                        supportingText = if (hasControlChars) {
                                            { Text("Invalid characters detected. Please use standard letters/numbers.", color = MaterialTheme.colorScheme.error) }
                                        } else null,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = TitaniumMint.Mint400,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            // 2. Description
                            FieldSyncToggleRow(
                                title = "Description & Notes",
                                subtitle = "Meeting agenda, notes, and body text",
                                icon = Icons.Default.Description,
                                checked = syncDescription,
                                onCheckedChange = { syncDescription = it }
                            )
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            // 3. Location
                            FieldSyncToggleRow(
                                title = "Location",
                                subtitle = "Physical address or room details",
                                icon = Icons.Default.LocationOn,
                                checked = syncLocation,
                                onCheckedChange = { syncLocation = it }
                            )
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            // 4. Reminders
                            FieldSyncToggleRow(
                                title = "Notification Times & Alarms",
                                subtitle = "Replicates reminder alerts (e.g. 10m before, 1h before)",
                                icon = Icons.Default.NotificationsActive,
                                checked = syncReminders,
                                onCheckedChange = { syncReminders = it }
                            )
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            // 5. Status
                            FieldSyncToggleRow(
                                title = "Status",
                                subtitle = "Confirmed, Tentative, or Canceled",
                                icon = Icons.Default.CheckCircle,
                                checked = syncStatus,
                                onCheckedChange = { syncStatus = it }
                            )
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            // 6. Availability
                            FieldSyncToggleRow(
                                title = "Show Me As / Availability",
                                subtitle = "Busy vs. Free",
                                icon = Icons.Default.EventAvailable,
                                checked = syncAvailability,
                                onCheckedChange = { syncAvailability = it }
                            )
                        }
                    }

                    // Never Synced Notice
                    Column(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "NEVER SYNCED",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "• Guests / Attendees: Suppressed to prevent accidental invites.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "• Event Color: Calendar contract delegates palette to target calendar.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

            // Danger & Delete Zone (if editing existing pair)
            if (pairToEdit != null) {
                // Delete Sync Configuration Button (Placed above Delete Events)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = TitaniumMint.Rose500.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, TitaniumMint.Rose500.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDeletePairDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = TitaniumMint.Rose400,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Delete Sync Configuration",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TitaniumMint.Rose400
                                )
                                Text(
                                    text = "Permanently remove this sync pair.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = TitaniumMint.Rose400,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Delete Events (Expandable section, collapsed by default)
                if (onClearPairEvents != null || onNukeTargetEvents != null) {
                    var deleteEventsExpanded by remember { mutableStateOf(false) }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { deleteEventsExpanded = !deleteEventsExpanded },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = TitaniumMint.Rose400,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Delete Events",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                IconButton(onClick = { deleteEventsExpanded = !deleteEventsExpanded }) {
                                    Icon(
                                        imageVector = if (deleteEventsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (deleteEventsExpanded) "Collapse" else "Expand",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = deleteEventsExpanded,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (onClearPairEvents != null) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.background,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onClearPairEvents() }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.EventBusy,
                                                        contentDescription = null,
                                                        tint = TitaniumMint.Rose400,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Column {
                                                        Text(
                                                            text = "Delete Cloned Events from Target",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = TitaniumMint.Rose400
                                                        )
                                                        Text(
                                                            text = "Delete only events cloned by this specific pair.",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                                Icon(
                                                    imageVector = Icons.Default.ChevronRight,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (onNukeTargetEvents != null) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = TitaniumMint.Rose500.copy(alpha = 0.1f),
                                            border = BorderStroke(1.dp, TitaniumMint.Rose500.copy(alpha = 0.4f)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onNukeTargetEvents() }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Warning,
                                                        contentDescription = null,
                                                        tint = TitaniumMint.Rose400,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Column {
                                                        Text(
                                                            text = "Delete ALL calendar events",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = TitaniumMint.Rose400
                                                        )
                                                        Text(
                                                            text = "Click to view normal wipe & cloud force purge.",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                                Icon(
                                                    imageVector = Icons.Default.ChevronRight,
                                                    contentDescription = null,
                                                    tint = TitaniumMint.Rose400,
                                                    modifier = Modifier.size(18.dp)
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
                    // Left Side: SAVE (Vibrant Mint fill, dark text, tactile scale feedback)
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
                                        // Tactile click: scale down then spring back
                                        saveScaleAnim.animateTo(
                                            0.92f,
                                            animationSpec = tween(80, easing = FastOutSlowInEasing)
                                        )
                                        saveScaleAnim.animateTo(
                                            1.0f,
                                            animationSpec = spring(
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

                    // Right Side: SYNC (Visually distinct: darker spruce background with TitaniumMint.Mint400 border color)
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

@Composable
private fun AnimatedConduitPipe(
    pipeFlowProgress: Float,
    pipeFlowActive: Boolean,
    badgePulse: Float,
    accentColor: Color = TitaniumMint.Mint400,
    accentSecondary: Color = TitaniumMint.Mint500,
    isPaused: Boolean = false,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val pulseHeight = 28.dp
    val pulseHeightPx = with(density) { pulseHeight.toPx() }
    val pipeHeightPx = with(density) { 52.dp.toPx() }
    val totalTravelPx = pipeHeightPx + (pulseHeightPx * 2f)

    val pipeTrackColor = if (isPaused) accentColor.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        contentAlignment = Alignment.Center
    ) {
        // Vertical Pipe
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(pipeTrackColor)
        ) {
            // Flowing Pulse Glow
            if (pipeFlowActive && !isPaused) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(pulseHeight)
                        .offset {
                            val yOffset = -pulseHeightPx + (pipeFlowProgress * totalTravelPx)
                            IntOffset(0, yOffset.roundToInt())
                        }
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    accentColor,
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        }

        val badgeBorderColor = if (isPaused) {
            accentColor.copy(alpha = 0.7f)
        } else {
            androidx.compose.ui.graphics.lerp(
                accentSecondary.copy(alpha = 0.4f),
                accentColor,
                badgePulse
            )
        }
        val badgeOffset = if (isPaused) 0 else (-3f * badgePulse).roundToInt()

        // Floating Pill Badge
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke((1f + (if (isPaused) 0.3f else badgePulse * 0.8f)).dp, badgeBorderColor),
            shadowElevation = (4 + (if (isPaused) 0f else 4 * badgePulse)).dp,
            modifier = Modifier.offset { IntOffset(0, badgeOffset) }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "Clones one-way into",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPaused) accentColor else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun CalendarSelectionCard(
    title: String,
    badgeText: String,
    isSource: Boolean,
    calendars: List<CalendarInfo>,
    selectedCalendar: CalendarInfo?,
    filterWritable: Boolean,
    isLocked: Boolean = false,
    pulseAmount: Float = 0f,
    accentColor: Color = TitaniumMint.Mint400,
    accentSecondary: Color = TitaniumMint.Mint500,
    containerTint: Color = TitaniumMint.Mint500.copy(alpha = 0.12f),
    containerBorder: Color = TitaniumMint.Mint500.copy(alpha = 0.3f),
    isPaused: Boolean = false,
    modifier: Modifier = Modifier,
    onInfoClick: () -> Unit,
    onLockedClick: (() -> Unit)? = null,
    onCalendarSelected: (CalendarInfo) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val borderColor = if (isLocked) {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    } else if (isPaused) {
        accentColor.copy(alpha = 0.55f)
    } else {
        androidx.compose.ui.graphics.lerp(
            MaterialTheme.colorScheme.outlineVariant,
            accentColor,
            pulseAmount
        )
    }
    val borderWidth = (1f + (if (!isLocked && !isPaused) 1.2f * pulseAmount else if (!isLocked && isPaused) 0.3f else 0f)).dp
    val glowBackground = if (!isLocked) {
        if (isPaused) accentSecondary.copy(alpha = 0.04f) else accentSecondary.copy(alpha = 0.07f * pulseAmount)
    } else Color.Transparent

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(borderWidth, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .background(glowBackground, RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Title on left, Read Only / Full Access chip on right
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
                        imageVector = if (isSource) Icons.Default.DateRange else Icons.Default.Refresh,
                        contentDescription = null,
                        tint = if (isLocked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = title.uppercase(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                }

                // Info Button (Always visible: Read Only / Full Access)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = containerTint,
                    border = BorderStroke(1.dp, containerBorder),
                    modifier = Modifier.clickable { onInfoClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor
                        )
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = accentColor,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // Dropdown Selector
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isLocked) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.background,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isLocked) 0.4f else 0.8f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isLocked) {
                                onLockedClick?.invoke()
                            } else {
                                expanded = true
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedCalendar != null) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedCalendar.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = selectedCalendar.accountName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        } else {
                            Text(
                                text = "Select calendar...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.ArrowDropDown,
                            contentDescription = if (isLocked) "Locked" else "Dropdown",
                            tint = if (isLocked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(if (isLocked) 16.dp else 24.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = expanded && !isLocked,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    val filteredList = if (filterWritable) calendars.filter { it.canWrite } else calendars
                    if (filteredList.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No eligible calendars found") },
                            onClick = { expanded = false }
                        )
                    } else {
                        filteredList.forEach { cal ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = cal.displayName,
                                            fontWeight = if (cal.id == selectedCalendar?.id) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Text(
                                            text = cal.accountName,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                },
                                onClick = {
                                    expanded = false
                                    onCalendarSelected(cal)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldSyncToggleRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val titleColor = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val subtitleColor = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val iconTint = if (checked) TitaniumMint.Mint400 else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                    color = titleColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = subtitleColor,
                    fontSize = 11.sp
                )
            }
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.height(24.dp)
        )
    }
}

@Composable
private fun AlertBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .size(18.dp)
                    .padding(top = 1.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor
                )
            }
        }
    }
}
