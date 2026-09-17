package com.stripedlens.calcloner.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Settings card configuring background sync intervals, low-power mode behavior,
 * and system battery optimization whitelisting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncEngineSettingsCard(
    syncIntervalMinutes: Int,
    syncOnLowBattery: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    onSaveSyncInterval: (Int) -> Unit,
    onSaveSyncOnLowBattery: (Boolean) -> Unit,
    onOpenBatterySettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        imageVector = Icons.Default.BatteryChargingFull,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Sync Engine & Battery",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Sync Interval Selector
            var showIntervalMenu by remember { mutableStateOf(false) }
            val intervalOptions = listOf(
                -1 to "Never (manual only)",
                0 to "Instant",
                15 to "Every 15 minutes",
                30 to "Every 30 minutes",
                60 to "Every 1 hour",
                180 to "Every 3 hours",
                360 to "Every 6 hours",
                720 to "Every 12 hours",
                1440 to "Every 24 hours (1x a day)"
            )

            val currentIntervalLabel = intervalOptions.find { it.first == syncIntervalMinutes }?.second
                ?: if (syncIntervalMinutes > 0) "Every $syncIntervalMinutes minutes" else "Never (manual only)"

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedCard(
                    onClick = { showIntervalMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Sync Interval",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = currentIntervalLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select"
                        )
                    }
                }

                DropdownMenu(
                    expanded = showIntervalMenu,
                    onDismissRequest = { showIntervalMenu = false }
                ) {
                    intervalOptions.forEach { (mins, label) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = label,
                                    fontWeight = if (mins == syncIntervalMinutes) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                showIntervalMenu = false
                                onSaveSyncInterval(mins)
                            }
                        )
                    }
                }
            }

            // Low Battery / Power Saver Setting
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Sync on Low Battery / Power Saver",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                    )
                    Switch(
                        checked = syncOnLowBattery,
                        onCheckedChange = onSaveSyncOnLowBattery
                    )
                }
            }

            // Battery Optimization Status & Guidance
            BatteryOptimizationStatusCard(
                isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                onOpenBatterySettings = onOpenBatterySettings
            )
        }
    }
}
