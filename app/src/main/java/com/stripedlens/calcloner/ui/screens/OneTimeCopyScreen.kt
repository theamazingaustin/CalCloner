package com.stripedlens.calcloner.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.stripedlens.calcloner.ui.components.ScreenHeaderBanner
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stripedlens.calcloner.CalendarInfo
import com.stripedlens.calcloner.OneTimeCopyResult
import com.stripedlens.calcloner.ui.components.sheets.AnimatedConduitPipe
import com.stripedlens.calcloner.ui.components.sheets.CalendarSelectionCard
import com.stripedlens.calcloner.ui.dialogs.CalendarRoleInfoDialog
import com.stripedlens.calcloner.ui.theme.CalClonerTheme
import com.stripedlens.calcloner.ui.theme.TitaniumMint
import com.stripedlens.calcloner.viewmodel.MainUiState

/**
 * On-demand One-Time Event Copy & Move Screen.
 *
 * Implements:
 * - Staged Mode Switcher: "Copy" active, "Move" greyed out with safety explanation.
 * - Dual calendar conduit route: Source (strictly read-only) to Target (writable).
 * - Date window presets & fine-grained sliders.
 * - Source calendar protection guarantee banner.
 * - Execution trigger, progress indicator, and completion feedback dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneTimeCopyScreen(
    uiState: MainUiState,
    onNavigateBack: () -> Unit,
    onSourceCalendarSelected: (CalendarInfo) -> Unit,
    onTargetCalendarSelected: (CalendarInfo) -> Unit,
    onDateWindowChanged: (daysPast: Int, daysFuture: Int) -> Unit,
    onExecuteCopy: () -> Unit,
    onDismissCopyResult: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    var showSourceRoleInfo by remember { mutableStateOf(false) }
    var showTargetRoleInfo by remember { mutableStateOf(false) }

    val sourceCal = uiState.oneTimeSourceCalendar
    val targetCal = uiState.oneTimeTargetCalendar

    // Prioritize calendars active in sync pairs at top of list
    val prioritizedCalendars = remember(uiState.availableCalendars, uiState.syncPairs) {
        val activeCalIds = uiState.syncPairs.flatMap { listOf(it.fromCalendarId, it.toCalendarId) }.toSet()
        uiState.availableCalendars.sortedWith(
            compareByDescending<CalendarInfo> { it.id in activeCalIds }
                .thenBy { it.displayName.lowercase() }
        )
    }

    // Validation
    val isSameCalendar = sourceCal != null && targetCal != null && sourceCal.id == targetCal.id
    val isTargetWritable = targetCal?.canWrite != false
    val canExecute = sourceCal != null && targetCal != null && !isSameCalendar && isTargetWritable && !uiState.oneTimeIsCopying

    // Animation progress for conduit pipe
    val cascadeTransition = rememberInfiniteTransition(label = "oneTimeCascade")
    val cycleProgress by cascadeTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "oneTimeCycleProgress"
    )

    val pipePulse = if (cycleProgress in 0.20f..0.70f) {
        kotlin.math.sin(((cycleProgress - 0.20f) / 0.50f) * Math.PI).toFloat().coerceIn(0f, 1f)
    } else 0f

    val spinTransition = rememberInfiniteTransition(label = "spinProgress")
    val spinAngle by spinTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinAngle"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenHeaderBanner(
            icon = Icons.Default.ContentCopy,
            title = "1-Time Clone",
            subtitle = "AD-HOC 1-WAY TRANSFER",
            description = "Transfer events between any two calendars on demand. Source calendar is strictly read-only and never modified."
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Route Conduit & Calendar Selection ─────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "TRANSFER ROUTE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TitaniumMint.Mint400
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Source Calendar Card
                    CalendarSelectionCard(
                        title = "Source Calendar (Read-Only)",
                        badgeText = "READ-ONLY",
                        isSource = true,
                        calendars = prioritizedCalendars,
                        selectedCalendar = sourceCal,
                        filterWritable = false,
                        isLocked = false,
                        pulseAmount = 0f,
                        onInfoClick = { showSourceRoleInfo = true },
                        onCalendarSelected = onSourceCalendarSelected
                    )

                    // Animated Conduit Pipe
                    AnimatedConduitPipe(
                        pipeFlowProgress = cycleProgress,
                        pipeFlowActive = sourceCal != null && targetCal != null && !isSameCalendar,
                        badgePulse = pipePulse,
                        accentColor = TitaniumMint.Mint400,
                        accentSecondary = TitaniumMint.Mint500
                    )

                    // Target Calendar Card
                    CalendarSelectionCard(
                        title = "Target Calendar (Writable)",
                        badgeText = null,
                        isSource = false,
                        calendars = prioritizedCalendars,
                        selectedCalendar = targetCal,
                        filterWritable = true,
                        isLocked = false,
                        pulseAmount = 0f,
                        onInfoClick = { showTargetRoleInfo = true },
                        onCalendarSelected = onTargetCalendarSelected
                    )

                    // Conflict Warnings
                    if (isSameCalendar) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Source and Target cannot be the same calendar.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // ── 3. Date Window Selector ───────────────────────────────────────
            com.stripedlens.calcloner.ui.components.UnifiedDateRangeCard(
                daysPast = uiState.oneTimeDaysPast,
                daysFuture = uiState.oneTimeDaysFuture,
                onRangeChanged = { past, future -> onDateWindowChanged(past, future) },
                title = "EVENT DATE WINDOW",
                description = "Define date boundaries for one-time copying."
            )

            // ── 4. Strict Safety Guarantee Banner ─────────────────────────────
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = TitaniumMint.Mint500.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(1.dp, TitaniumMint.Mint500.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = TitaniumMint.Mint400,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Strict Source Protection Guarantee",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TitaniumMint.Mint400
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Your Source calendar is strictly read-only. CalCloner will NEVER modify, edit, or delete any source events.\n• Cloned events on the Target calendar are stamped with [CalCloner-ID: onetime] tags so you can easily identify and delete them via the Delete Tool at any time.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // ── 5. Progress Indicator (When copying) ──────────────────────────
            AnimatedVisibility(
                visible = uiState.oneTimeIsCopying,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TitaniumMint.Mint500.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = TitaniumMint.Mint400,
                                modifier = Modifier
                                    .size(20.dp)
                                    .rotate(spinAngle)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = uiState.oneTimeCopyProgress ?: "Transferring events...",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TitaniumMint.Mint400
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = TitaniumMint.Mint400,
                            trackColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }

            // ── 6. Action Button ──────────────────────────────────────────────
            Button(
                onClick = onExecuteCopy,
                enabled = canExecute,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TitaniumMint.Mint500,
                    contentColor = CalClonerTheme.colors.onAccent,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.oneTimeIsCopying) "Cloning Events..." else "Start 1-Time Clone",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Role Info Dialogs
    if (showSourceRoleInfo) {
        CalendarRoleInfoDialog(
            type = "source",
            onDismiss = { showSourceRoleInfo = false }
        )
    }
    if (showTargetRoleInfo) {
        CalendarRoleInfoDialog(
            type = "target",
            onDismiss = { showTargetRoleInfo = false }
        )
    }

    // Success Result Dialog
    uiState.oneTimeCopyResult?.let { result ->
        AlertDialog(
            onDismissRequest = onDismissCopyResult,
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = TitaniumMint.Mint400,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "1-Time Clone Complete",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "• ${result.copiedCount} event(s) cloned to ${targetCal?.displayName ?: "Target"}.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TitaniumMint.Mint400
                    )
                    if (result.skippedCount > 0) {
                        Text(
                            text = "• ${result.skippedCount} existing event(s) skipped to prevent duplicates.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "• Total evaluated: ${result.totalMatched} event(s) in ${(result.durationMs / 1000f)}s.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "All cloned events are tagged with CalCloner tracking metadata and can be safely purged via the Delete Tool at any time.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onDismissCopyResult,
                    colors = ButtonDefaults.buttonColors(containerColor = TitaniumMint.Mint500)
                ) {
                    Text("Done", color = CalClonerTheme.colors.onAccent, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

