package com.stripedlens.calcloner.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.stripedlens.calcloner.CalendarInfo
import com.stripedlens.calcloner.ui.components.CalendarDropdown
import com.stripedlens.calcloner.ui.theme.TitaniumMint
import com.stripedlens.calcloner.viewmodel.DeleteOperationType
import com.stripedlens.calcloner.viewmodel.MainUiState

/**
 * Dedicated calendar maintenance and event deletion screen.
 *
 * Provides safe, intentional event deletion:
 * 1. Target calendar selection (filtered to writable calendars).
 * 2. Operation mode selection: Purge Cloned Events Only (safe) vs. Wipe All Events (complete wipe).
 * 3. Exact confirmation typing lock: requires the user to type "delete <calendar name>"
 *    before the deletion action can be triggered.
 */
@Composable
fun DeleteScreen(
    uiState: MainUiState,
    onCalendarSelected: (CalendarInfo?) -> Unit,
    onOperationTypeSelected: (DeleteOperationType) -> Unit,
    onConfirmationTextChanged: (String) -> Unit,
    onDeleteConfirmed: () -> Unit,
    onPurgeTombstones: (CalendarInfo?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val selectedCalendar = uiState.selectedDeleteCalendar
    val requiredPhrase = selectedCalendar?.let { "delete ${it.displayName.trim()}" } ?: ""
    val isConfirmed = selectedCalendar != null &&
            requiredPhrase.isNotEmpty() &&
            uiState.deleteConfirmationText.trim().equals(requiredPhrase, ignoreCase = true)

    // Y-coordinate tracking for smooth scrolling
    var step2TopY by remember { mutableStateOf(0f) }
    var step3TopY by remember { mutableStateOf(0f) }

    // Track technical details expanded state for each operation
    var purgeClonedExpanded by remember { mutableStateOf(false) }
    var clearAllExpanded by remember { mutableStateOf(false) }
    var wipeAllExpanded by remember { mutableStateOf(false) }

    // Dynamic accent color based on selected operation
    val operationAccentColor = when (uiState.deleteOperationType) {
        DeleteOperationType.PURGE_CLONED -> TitaniumMint.Mint400
        DeleteOperationType.CLEAR_ALL -> TitaniumMint.Amber400
        DeleteOperationType.WIPE_ALL -> TitaniumMint.Orange500
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Header Card ──────────────────────────────────────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = TitaniumMint.Orange500.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, TitaniumMint.Orange500.copy(alpha = 0.3f)),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                tint = TitaniumMint.Orange400,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Delete Calendar Events",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "CALENDAR MAINTENANCE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TitaniumMint.Orange400,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
                Text(
                    text = "Select a calendar and an operation type. To prevent accidental data loss, deletions require typing the confirmation phrase before the action is unlocked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }

        // ── Step 1: Select Calendar ──────────────────────────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "STEP 1: SELECT TARGET CALENDAR",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp
                )

                CalendarDropdown(
                    label = "Target Calendar",
                    calendars = uiState.availableCalendars,
                    selectedCalendar = selectedCalendar,
                    filterWritable = true,
                    onCalendarSelected = { cal ->
                        onCalendarSelected(cal)
                        if (cal != null) {
                            scope.launch {
                                kotlinx.coroutines.delay(100L)
                                scrollState.animateScrollTo(step2TopY.toInt().coerceAtLeast(0))
                            }
                        }
                    }
                )

                if (selectedCalendar != null) {
                    Text(
                        text = "Account: ${selectedCalendar.accountName}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── Step 2: Select Operation Mode ────────────────────────────────────────
        val isStep2Enabled = selectedCalendar != null
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    step2TopY = coordinates.positionInParent().y
                },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "STEP 2: SELECT OPERATION TYPE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isStep2Enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        letterSpacing = 0.5.sp
                    )
                    if (!isStep2Enabled) {
                        Text(
                            text = "SELECT CALENDAR FIRST",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (isStep2Enabled) 1f else 0.42f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Option A: Purge Cloned Events Only
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isStep2Enabled && uiState.deleteOperationType == DeleteOperationType.PURGE_CLONED) {
                            TitaniumMint.Mint500.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isStep2Enabled && uiState.deleteOperationType == DeleteOperationType.PURGE_CLONED) {
                                TitaniumMint.Mint500
                            } else {
                                TitaniumMint.Mint500.copy(alpha = 0.25f)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isStep2Enabled) {
                                onOperationTypeSelected(DeleteOperationType.PURGE_CLONED)
                                scope.launch {
                                    kotlinx.coroutines.delay(100L)
                                    scrollState.animateScrollTo(step3TopY.toInt().coerceAtLeast(0))
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RadioButton(
                                selected = isStep2Enabled && uiState.deleteOperationType == DeleteOperationType.PURGE_CLONED,
                                onClick = {
                                    if (isStep2Enabled) {
                                        onOperationTypeSelected(DeleteOperationType.PURGE_CLONED)
                                        scope.launch {
                                            kotlinx.coroutines.delay(100L)
                                            scrollState.animateScrollTo(step3TopY.toInt().coerceAtLeast(0))
                                        }
                                    }
                                },
                                enabled = isStep2Enabled,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = TitaniumMint.Mint500,
                                    unselectedColor = TitaniumMint.Mint500.copy(alpha = 0.6f)
                                )
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Purge Cloned Events Only",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isStep2Enabled && uiState.deleteOperationType == DeleteOperationType.PURGE_CLONED) {
                                        TitaniumMint.Mint500
                                    } else {
                                        TitaniumMint.Mint600
                                    }
                                )
                                Text(
                                    text = "Removes only events cloned by CalCloner. Personal and manually created events in this calendar are kept safe.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                                Text(
                                    text = if (purgeClonedExpanded) "Hide technical details ▲" else "Show technical details ▼",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TitaniumMint.Mint500,
                                    modifier = Modifier
                                        .clickable(enabled = isStep2Enabled) {
                                            purgeClonedExpanded = !purgeClonedExpanded
                                        }
                                        .padding(vertical = 2.dp)
                                )
                                AnimatedVisibility(
                                    visible = purgeClonedExpanded,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "Mechanism: Scans this calendar's local database for CalCloner tracking metadata (CUSTOM_APP_URI: calcloner://... or [CalCloner-ID: ...]).",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 15.sp
                                            )
                                            Text(
                                                text = "• Safety: Preserves any events created manually, imported externally, or belonging to other active sync pairs. The source calendar is never touched.\n• Cloud Sync: Marks events for deletion so Android's sync service removes them from Google / Exchange cloud servers.\n• Best For: Routine cleanups, resetting a sync pair, or removing duplicate clones without touching your personal events.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Option B: Clear All Events (Amber / Warning Theme)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isStep2Enabled && uiState.deleteOperationType == DeleteOperationType.CLEAR_ALL) {
                            TitaniumMint.Amber500.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isStep2Enabled && uiState.deleteOperationType == DeleteOperationType.CLEAR_ALL) {
                                TitaniumMint.Amber500
                            } else {
                                TitaniumMint.Amber500.copy(alpha = 0.35f)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isStep2Enabled) {
                                onOperationTypeSelected(DeleteOperationType.CLEAR_ALL)
                                scope.launch {
                                    kotlinx.coroutines.delay(100L)
                                    scrollState.animateScrollTo(step3TopY.toInt().coerceAtLeast(0))
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RadioButton(
                                selected = isStep2Enabled && uiState.deleteOperationType == DeleteOperationType.CLEAR_ALL,
                                onClick = {
                                    if (isStep2Enabled) {
                                        onOperationTypeSelected(DeleteOperationType.CLEAR_ALL)
                                        scope.launch {
                                            kotlinx.coroutines.delay(100L)
                                            scrollState.animateScrollTo(step3TopY.toInt().coerceAtLeast(0))
                                        }
                                    }
                                },
                                enabled = isStep2Enabled,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = TitaniumMint.Amber500,
                                    unselectedColor = TitaniumMint.Amber500.copy(alpha = 0.6f)
                                )
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Clear All Calendar Events",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isStep2Enabled && uiState.deleteOperationType == DeleteOperationType.CLEAR_ALL) {
                                        TitaniumMint.Amber500
                                    } else {
                                        TitaniumMint.Amber600
                                    }
                                )
                                Text(
                                    text = "Deletes all events currently on this calendar (both cloned and personal) via standard Android cloud sync.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                                Text(
                                    text = if (clearAllExpanded) "Hide technical details ▲" else "Show technical details ▼",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TitaniumMint.Amber500,
                                    modifier = Modifier
                                        .clickable(enabled = isStep2Enabled) {
                                            clearAllExpanded = !clearAllExpanded
                                        }
                                        .padding(vertical = 2.dp)
                                )
                                AnimatedVisibility(
                                    visible = clearAllExpanded,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "Mechanism: Queries all events where CALENDAR_ID matches the selected calendar, then deletes each via ContentResolver.delete with standard cloud-sync enabled (CALLER_IS_SYNC_ADAPTER = false).",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 15.sp
                                            )
                                            Text(
                                                text = "• Scope: Wipes all events in this calendar regardless of whether they were created by CalCloner or manually added.\n• Cloud Sync: Preserves Android sync provider deleted tombstones so downstream servers (Google Calendar, Outlook) process the deletions cleanly.\n• Best For: Emptying a target calendar before reconfiguring sync pairs, or resetting a shared schedule.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Option C: Deep Clean & Cloud Wipe (Orange-Red Theme)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isStep2Enabled && uiState.deleteOperationType == DeleteOperationType.WIPE_ALL) {
                            TitaniumMint.Orange500.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isStep2Enabled && uiState.deleteOperationType == DeleteOperationType.WIPE_ALL) {
                                TitaniumMint.Orange500
                            } else {
                                TitaniumMint.Orange500.copy(alpha = 0.35f)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isStep2Enabled) {
                                onOperationTypeSelected(DeleteOperationType.WIPE_ALL)
                                scope.launch {
                                    kotlinx.coroutines.delay(100L)
                                    scrollState.animateScrollTo(step3TopY.toInt().coerceAtLeast(0))
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RadioButton(
                                selected = isStep2Enabled && uiState.deleteOperationType == DeleteOperationType.WIPE_ALL,
                                onClick = {
                                    if (isStep2Enabled) {
                                        onOperationTypeSelected(DeleteOperationType.WIPE_ALL)
                                        scope.launch {
                                            kotlinx.coroutines.delay(100L)
                                            scrollState.animateScrollTo(step3TopY.toInt().coerceAtLeast(0))
                                        }
                                    }
                                },
                                enabled = isStep2Enabled,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = TitaniumMint.Orange500,
                                    unselectedColor = TitaniumMint.Orange500.copy(alpha = 0.6f)
                                )
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Deep Clean & Cloud Wipe",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isStep2Enabled && uiState.deleteOperationType == DeleteOperationType.WIPE_ALL) {
                                        TitaniumMint.Orange500
                                    } else {
                                        TitaniumMint.Orange600
                                    }
                                )
                                Text(
                                    text = "Forces a cloud refresh to capture remote events, deletes everything locally, and commands an immediate cloud wipe.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                                Text(
                                    text = if (wipeAllExpanded) "Hide technical details ▲" else "Show technical details ▼",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TitaniumMint.Orange500,
                                    modifier = Modifier
                                        .clickable(enabled = isStep2Enabled) {
                                            wipeAllExpanded = !wipeAllExpanded
                                        }
                                        .padding(vertical = 2.dp)
                                )
                                AnimatedVisibility(
                                    visible = wipeAllExpanded,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "Mechanism: A 3-phase forced reconciliation cycle to fix desynchronized or stubborn calendars:",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 15.sp
                                            )
                                            Text(
                                                text = "1. Force Cloud Pull: Toggles sync settings to force Google servers to download all remote and orphaned events to the device.\n2. Full Local Deletion: Queries and batch-deletes all events in this calendar.\n3. Expedited Cloud Override: Dispatches an immediate high-priority sync with the OVERRIDE_TOO_MANY_DELETIONS flag, forcing Google servers to instantly purge the cloud calendar without throttling or safety holds.\n• Best For: Eliminating ghost events, clearing calendar sync drift, or completely resetting a desynced Google Calendar.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 15.sp
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

        // ── Step 3: Safety Confirmation Lock ─────────────────────────────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    step3TopY = coordinates.positionInParent().y
                },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                if (isConfirmed) operationAccentColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isConfirmed) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (isConfirmed) operationAccentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "STEP 3: CONFIRMATION LOCK",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isConfirmed) operationAccentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                }

                if (selectedCalendar == null) {
                    Text(
                        text = "Please select a calendar in Step 1 to unlock the confirmation field.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "To proceed, type the following phrase exactly as shown:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = operationAccentColor.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, operationAccentColor.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = requiredPhrase,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = operationAccentColor,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }

                        OutlinedTextField(
                            value = uiState.deleteConfirmationText,
                            onValueChange = onConfirmationTextChanged,
                            label = { Text("Type confirmation phrase") },
                            placeholder = { Text(requiredPhrase) },
                            singleLine = true,
                            enabled = !uiState.isOperating,
                            trailingIcon = {
                                if (isConfirmed) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Confirmed",
                                        tint = operationAccentColor
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (isConfirmed) operationAccentColor else MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = if (isConfirmed) operationAccentColor else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (uiState.deleteConfirmationText.isNotEmpty() && !isConfirmed) {
                            Text(
                                text = "Phrase does not match yet.",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = onDeleteConfirmed,
                        enabled = isConfirmed && !uiState.isOperating,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = operationAccentColor,
                            disabledContainerColor = operationAccentColor.copy(alpha = 0.25f),
                            contentColor = Color.White,
                            disabledContentColor = Color.White.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = when (uiState.deleteOperationType) {
                                    DeleteOperationType.PURGE_CLONED -> "Purge Cloned Events"
                                    DeleteOperationType.CLEAR_ALL -> "Clear All Calendar Events"
                                    DeleteOperationType.WIPE_ALL -> "Deep Clean & Cloud Wipe"
                                },
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // ── Database Optimization & Synced Tombstones ────────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "DATABASE OPTIMIZATION",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )
                }

                Text(
                    text = "Android calendars retain deleted events as tombstones until synchronized. If your calendar provider does not prune them, or after mass deletions, dead tombstones can bloat queries. This purges already-synchronized tombstones (DELETED=1, DIRTY=0).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )

                OutlinedButton(
                    onClick = { onPurgeTombstones(selectedCalendar) },
                    enabled = !uiState.isOperating,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedCalendar != null) {
                            "Purge Tombstones (${selectedCalendar.displayName})"
                        } else {
                            "Purge All Synced Tombstones"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // ── Progress / Result Status Card ────────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.isOperating || uiState.operationDone || uiState.progressStatusText.isNotBlank(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (uiState.progressStatusText.contains("failed", ignoreCase = true)) {
                        MaterialTheme.colorScheme.error
                    } else {
                        TitaniumMint.Mint500.copy(alpha = 0.5f)
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.isOperating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = if (uiState.progressStatusText.contains("failed", ignoreCase = true)) {
                                    Icons.Default.ErrorOutline
                                } else {
                                    Icons.Default.CheckCircle
                                },
                                contentDescription = null,
                                tint = if (uiState.progressStatusText.contains("failed", ignoreCase = true)) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Text(
                            text = if (uiState.isOperating) "OPERATION IN PROGRESS" else "OPERATION STATUS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Text(
                        text = uiState.progressStatusText,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
