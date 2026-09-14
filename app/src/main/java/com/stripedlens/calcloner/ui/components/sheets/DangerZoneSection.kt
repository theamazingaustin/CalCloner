package com.stripedlens.calcloner.ui.components.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stripedlens.calcloner.SyncPair
import com.stripedlens.calcloner.ui.components.SwipeToConfirmSlider
import com.stripedlens.calcloner.ui.theme.TitaniumMint

/**
 * Danger Zone section containing destructive operations:
 * - Delete sync configuration
 * - Delete cloned events from target
 * - Wipe all events from target calendar
 */
@Composable
fun DangerZoneSection(
    pairToEdit: SyncPair?,
    onDeleteConfigClick: () -> Unit,
    onClearPairEvents: (() -> Unit)?,
    onNukeTargetEvents: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    if (pairToEdit == null && onClearPairEvents == null && onNukeTargetEvents == null) {
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Danger Zone Section Header
        Text(
            text = "DANGER ZONE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = TitaniumMint.Rose400,
            letterSpacing = 1.sp
        )

        // Delete Configuration Card (Only visible when editing an existing pair)
        if (pairToEdit != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, TitaniumMint.Rose500.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDeleteConfigClick() }
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
}

/**
 * Confirmation dialog shown when the user attempts to discard unsaved edits.
 */
@Composable
fun DiscardChangesDialog(
    onConfirmDiscard: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
                onClick = onConfirmDiscard,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Discard", color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep Editing")
            }
        }
    )
}

/**
 * Dialog to verify deletion of an entire sync pair, giving the user the choice
 * to either delete cloned events from the target calendar or leave them orphaned.
 */
@Composable
fun DeletePairDialog(
    pair: SyncPair,
    onConfirmDelete: (deleteClonedEvents: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var deleteClonedChoice by remember { mutableStateOf<Boolean?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                    text = "Deleting '${pair.displayName}' will permanently stop sync between '${pair.fromCalendarName}' and '${pair.toCalendarName}'.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "What should happen to previously cloned events on '${pair.toCalendarName}'?",
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
                        onConfirmDelete(choice)
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
