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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stripedlens.calcloner.ui.theme.TitaniumMint

/**
 * Reusable toggle row for syncing specific calendar event fields.
 */
@Composable
fun FieldSyncToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    enabled: Boolean = true,
    accentColor: Color = TitaniumMint.Mint400,
    onCheckedChange: (Boolean) -> Unit
) {
    val titleColor = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val subtitleColor = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val iconTint = if (checked) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

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

/**
 * Collapsible options card controlling selective event field synchronization.
 */
@Composable
fun SyncFieldOptionsCard(
    syncTitle: Boolean,
    onSyncTitleChange: (Boolean) -> Unit,
    customTitle: String,
    onCustomTitleChange: (String) -> Unit,
    syncDescription: Boolean,
    onSyncDescriptionChange: (Boolean) -> Unit,
    syncLocation: Boolean,
    onSyncLocationChange: (Boolean) -> Unit,
    syncReminders: Boolean,
    onSyncRemindersChange: (Boolean) -> Unit,
    syncAvailability: Boolean,
    onSyncAvailabilityChange: (Boolean) -> Unit,
    syncStatus: Boolean,
    onSyncStatusChange: (Boolean) -> Unit,
    accentColor: Color = TitaniumMint.Mint400,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
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
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Selective Field Sync",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Control what event details mirror to the target calendar.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expandable Content
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Divider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 0.5.dp
                    )

                    // Title Toggle & Custom Title Replacement
                    FieldSyncToggleRow(
                        title = "Event Title",
                        subtitle = "Mirror the event name from the source calendar",
                        icon = Icons.Default.Title,
                        checked = syncTitle,
                        accentColor = accentColor,
                        onCheckedChange = onSyncTitleChange
                    )

                    if (!syncTitle) {
                        OutlinedTextField(
                            value = customTitle,
                            onValueChange = onCustomTitleChange,
                            label = { Text("Custom Title Replacement", fontSize = 12.sp) },
                            placeholder = { Text("e.g. Busy, Reserved, Unavailable", fontSize = 13.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }

                    // Description Toggle
                    FieldSyncToggleRow(
                        title = "Description & Notes",
                        subtitle = "Mirror event descriptions, agenda notes, and meeting links",
                        icon = Icons.Default.Description,
                        checked = syncDescription,
                        accentColor = accentColor,
                        onCheckedChange = onSyncDescriptionChange
                    )

                    // Location Toggle
                    FieldSyncToggleRow(
                        title = "Location",
                        subtitle = "Mirror physical addresses or virtual conference links",
                        icon = Icons.Default.LocationOn,
                        checked = syncLocation,
                        accentColor = accentColor,
                        onCheckedChange = onSyncLocationChange
                    )

                    // Reminders Toggle
                    FieldSyncToggleRow(
                        title = "Reminders & Notifications",
                        subtitle = "Mirror alarm alerts and advance notification timing",
                        icon = Icons.Default.Alarm,
                        checked = syncReminders,
                        accentColor = accentColor,
                        onCheckedChange = onSyncRemindersChange
                    )

                    // Availability Toggle (Free / Busy)
                    FieldSyncToggleRow(
                        title = "Availability (Free / Busy)",
                        subtitle = "Mirror whether this time blocks out your schedule",
                        icon = Icons.Default.EventAvailable,
                        checked = syncAvailability,
                        accentColor = accentColor,
                        onCheckedChange = onSyncAvailabilityChange
                    )

                    // Status Toggle (Confirmed / Tentative)
                    FieldSyncToggleRow(
                        title = "Event Status",
                        subtitle = "Mirror confirmed, tentative, or cancelled status",
                        icon = Icons.Default.CheckCircleOutline,
                        checked = syncStatus,
                        accentColor = accentColor,
                        onCheckedChange = onSyncStatusChange
                    )
                }
            }
        }
    }
}
