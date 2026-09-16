package com.stripedlens.calcloner.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stripedlens.calcloner.SyncPair
import com.stripedlens.calcloner.ui.components.BackgroundBatteryProtectionBanner
import com.stripedlens.calcloner.ui.components.SyncOverviewCard
import com.stripedlens.calcloner.ui.components.SyncEngineSettingsCard
import com.stripedlens.calcloner.ui.components.SyncPairCard
import com.stripedlens.calcloner.viewmodel.MainUiState

/**
 * Main Sync screen presenting configured calendar sync pairs, master sync controls,
 * empty state callouts, and engine configuration settings.
 */
@Composable
fun SyncScreen(
    uiState: MainUiState,
    onSyncAll: () -> Unit,
    onToggleAll: (Boolean) -> Unit,
    onSyncPair: (SyncPair) -> Unit,
    onEditPair: (SyncPair) -> Unit,
    onTogglePairEnabled: (String, Boolean) -> Unit,
    onDeletePair: (SyncPair) -> Unit,
    onAddNewPair: () -> Unit,
    onSaveSyncInterval: (Int) -> Unit,
    onSaveSyncOnLowBattery: (Boolean) -> Unit,
    onOpenBatterySettings: () -> Unit,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val infiniteTransition = rememberInfiniteTransition(label = "syncSpin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinAngle"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Overview Sync Controls Card
            SyncOverviewCard(
                syncPairs = uiState.syncPairs,
                isSyncingAll = uiState.isSyncingAll,
                isOperating = uiState.isOperating,
                spinAngle = spinAngle,
                onToggleAll = onToggleAll,
                onSyncAll = onSyncAll
            )

            // Universal Background Battery Guidance Banner
            BackgroundBatteryProtectionBanner(
                isIgnoringBatteryOptimizations = uiState.isIgnoringBatteryOptimizations,
                onOpenBatterySettings = onOpenBatterySettings
            )

            // Calendar Permission Alert Banner
            AnimatedVisibility(
                visible = !uiState.hasCalendarPermissions,
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
                        Button(onClick = onRequestPermissions) {
                            Text("Grant Permissions")
                        }
                    }
                }
            }

            // Empty State Card
            if (uiState.syncPairs.isEmpty()) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Text(
                            text = "No Calendar Pairs Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Add your first pair to begin safe, one-way calendar replication with zero battery drain.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Button(onClick = onAddNewPair) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add First Sync Pair")
                        }
                    }
                }
            } else {
                // List of Configured Sync Pairs
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    uiState.syncPairs.forEach { pair ->
                        val fromCal = uiState.availableCalendars.find { it.id == pair.fromCalendarId }
                        val toCal = uiState.availableCalendars.find { it.id == pair.toCalendarId }
                        val isPairSyncing = uiState.isSyncing && (uiState.isSyncingAll || uiState.syncingPairId == pair.id)

                        SyncPairCard(
                            pair = pair,
                            fromCalendar = fromCal,
                            toCalendar = toCal,
                            isSyncing = isPairSyncing,
                            onSyncNow = { onSyncPair(pair) },
                            onEdit = { onEditPair(pair) },
                            onToggleEnabled = { enabled -> onTogglePairEnabled(pair.id, enabled) },
                            onDeletePair = { onDeletePair(pair) }
                        )
                    }
                }
            }

            // Engine & Battery Settings Card
            SyncEngineSettingsCard(
                syncIntervalMinutes = uiState.syncIntervalMinutes,
                syncOnLowBattery = uiState.syncOnLowBattery,
                isIgnoringBatteryOptimizations = uiState.isIgnoringBatteryOptimizations,
                onSaveSyncInterval = onSaveSyncInterval,
                onSaveSyncOnLowBattery = onSaveSyncOnLowBattery,
                onOpenBatterySettings = onOpenBatterySettings
            )

            // Bottom Spacer to prevent FAB occlusion
            Spacer(modifier = Modifier.height(84.dp))
        }
    }
}
