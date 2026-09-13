package com.stripedlens.calcloner.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stripedlens.calcloner.CalendarInfo
import com.stripedlens.calcloner.SyncPair

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
    onSavePair: (SyncPair) -> Unit
) {
    var selectedFromCal by remember {
        mutableStateOf(availableCalendars.find { it.id == pairToEdit?.fromCalendarId })
    }
    var selectedToCal by remember {
        mutableStateOf(availableCalendars.find { it.id == pairToEdit?.toCalendarId })
    }

    var daysPastInput by remember {
        mutableStateOf(pairToEdit?.daysPast?.toString() ?: "")
    }
    var daysFutureInput by remember {
        mutableStateOf(pairToEdit?.daysFuture?.toString() ?: "")
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

    // Overlap checks
    val otherPairs = existingPairs.filter { it.id != pairToEdit?.id }
    val alreadySourcePair = otherPairs.find { it.fromCalendarId == fromId }
    val alreadyTargetPair = otherPairs.find { it.toCalendarId == toId }
    val sourceIsTargetPair = otherPairs.find { it.toCalendarId == fromId }

    val canSave = fromId != null &&
            toId != null &&
            !isSameCalendar &&
            !hasCycle &&
            (selectedToCal?.canWrite == true)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (pairToEdit != null) "Edit Sync Pair" else "Add Sync Pair",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Configure one-way calendar replication",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 1. SOURCE CALENDAR DROPDOWN
            CalendarDropdown(
                label = "SOURCE Calendar (Read Only)",
                calendars = availableCalendars,
                selectedCalendar = selectedFromCal,
                filterWritable = false,
                onCalendarSelected = { selectedFromCal = it }
            )

            // 2. TARGET CALENDAR DROPDOWN
            CalendarDropdown(
                label = "CLONE Calendar (Destination)",
                calendars = availableCalendars,
                selectedCalendar = selectedToCal,
                filterWritable = true,
                onCalendarSelected = { selectedToCal = it }
            )

            // 3. REAL-TIME VALIDATION & WARNING BANNERS
            if (isSameCalendar) {
                AlertBanner(
                    icon = Icons.Default.Warning,
                    title = "Invalid Route",
                    message = "Source and Clone cannot be the same calendar.",
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            } else if (hasCycle) {
                AlertBanner(
                    icon = Icons.Default.Warning,
                    title = "Circular Sync Loop Detected",
                    message = "Syncing '${selectedFromCal?.displayName}' to '${selectedToCal?.displayName}' creates an infinite clone loop. One-way sync requires a directed path without loops.",
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            } else {
                // Informational Overlap Banners
                if (alreadySourcePair != null) {
                    AlertBanner(
                        icon = Icons.Default.Info,
                        title = "Mirroring to Multiple Destinations",
                        message = "'${selectedFromCal?.displayName}' is already syncing to '${alreadySourcePair.toCalendarName}'. Its events will mirror to both destinations.",
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                if (alreadyTargetPair != null) {
                    AlertBanner(
                        icon = Icons.Default.Warning,
                        title = "Destination Merging",
                        message = "'${selectedToCal?.displayName}' already receives clones from '${alreadyTargetPair.fromCalendarName}'. Both sources will be safely merged into this calendar.",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }

                if (sourceIsTargetPair != null) {
                    AlertBanner(
                        icon = Icons.Default.Info,
                        title = "Chain Sync Source",
                        message = "'${selectedFromCal?.displayName}' is a target for another pair. CalCloner will only sync original events created on it (clones will not be re-cloned).",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // 4. TIMEFRAME (DAYS BACK & FORWARD)
            OutlinedCard(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Sync Timeframe",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Leave empty or enter 30 to use default 30-day window.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = daysPastInput,
                            onValueChange = { input ->
                                if (input.isEmpty() || input.all { it.isDigit() }) {
                                    daysPastInput = input
                                }
                            },
                            label = { Text("Days Past") },
                            placeholder = { Text("30") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = daysFutureInput,
                            onValueChange = { input ->
                                if (input.isEmpty() || input.all { it.isDigit() }) {
                                    daysFutureInput = input
                                }
                            },
                            label = { Text("Days Future") },
                            placeholder = { Text("30") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }

            // 5. SAVE & CANCEL BUTTONS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        val from = selectedFromCal ?: return@Button
                        val to = selectedToCal ?: return@Button
                        val pastParsed = daysPastInput.toIntOrNull()
                        val futureParsed = daysFutureInput.toIntOrNull()

                        val pair = SyncPair(
                            id = pairToEdit?.id ?: java.util.UUID.randomUUID().toString(),
                            fromCalendarId = from.id,
                            fromCalendarName = from.displayName,
                            toCalendarId = to.id,
                            toCalendarName = to.displayName,
                            daysPast = if (pastParsed == 30) null else pastParsed,
                            daysFuture = if (futureParsed == 30) null else futureParsed,
                            isEnabled = pairToEdit?.isEnabled ?: true,
                            lastSyncTime = pairToEdit?.lastSyncTime,
                            lastSyncStatus = pairToEdit?.lastSyncStatus
                        )
                        onSavePair(pair)
                    },
                    enabled = canSave,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (pairToEdit != null) "Update Pair" else "Save Pair")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
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
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
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
                modifier = Modifier.size(20.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
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
