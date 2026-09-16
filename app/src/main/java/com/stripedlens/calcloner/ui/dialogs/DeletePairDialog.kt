package com.stripedlens.calcloner.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.stripedlens.calcloner.SyncPair
import com.stripedlens.calcloner.ui.theme.TitaniumMint
import com.stripedlens.calcloner.ui.theme.UiDimensions

/**
 * Canonical confirmation dialog for deleting a configured [SyncPair].
 *
 * Provides a clear, safe binary choice:
 * 1. Remove the sync pair and delete all events it previously created on the clone calendar.
 * 2. Remove the sync pair but keep cloned events intact on the clone calendar.
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
        modifier = Modifier.fillMaxWidth(UiDimensions.DialogWidthFraction),
        properties = DialogProperties(usePlatformDefaultWidth = false),
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
                    color = if (deleteClonedChoice == true) TitaniumMint.Rose500.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
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
                    color = if (deleteClonedChoice == false) TitaniumMint.Mint500.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
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
                                text = "Leave previously created events intact on the clone calendar.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    deleteClonedChoice?.let { onConfirmDelete(it) }
                },
                enabled = deleteClonedChoice != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (deleteClonedChoice == true) TitaniumMint.Rose500 else TitaniumMint.Mint500,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (deleteClonedChoice == true) "Delete Pair & Events" else "Delete Pair Only",
                    color = if (deleteClonedChoice != null) Color(0xFF09090B) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
