package com.stripedlens.calcloner.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stripedlens.calcloner.CalendarInfo
import com.stripedlens.calcloner.SyncPair
import com.stripedlens.calcloner.ui.theme.TitaniumMint
import com.stripedlens.calcloner.util.DateTimeUtils
import kotlinx.coroutines.launch

@Composable
fun SyncPairCard(
    pair: SyncPair,
    fromCalendar: CalendarInfo?,
    toCalendar: CalendarInfo?,
    isSyncing: Boolean,
    onSyncNow: () -> Unit,
    onEdit: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onDeletePair: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pairSyncSpin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pairSpinAngle"
    )

    val glowAnim = remember { Animatable(0f) }
    val buttonGlowAnim = remember { Animatable(0f) }
    var prevSyncing by remember { mutableStateOf(isSyncing) }
    var prevLastSyncTime by remember { mutableStateOf(pair.lastSyncTime) }

    LaunchedEffect(isSyncing, pair.lastSyncTime) {
        if ((prevSyncing && !isSyncing) || (prevLastSyncTime != pair.lastSyncTime && pair.lastSyncTime != null)) {
            launch {
                buttonGlowAnim.snapTo(1f)
                buttonGlowAnim.animateTo(0f, tween(1500, easing = FastOutSlowInEasing))
            }
            // Smooth neon/cyberpunk illumination: smooth fade-in, hold, and smooth fade-out
            launch {
                glowAnim.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
                kotlinx.coroutines.delay(1200L)
                glowAnim.animateTo(0f, tween(1500, easing = LinearOutSlowInEasing))
            }
        }
        prevSyncing = isSyncing
        prevLastSyncTime = pair.lastSyncTime
    }

    val isTargetAccessible = toCalendar != null && toCalendar.canWrite

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onEdit() },
        shape = RoundedCornerShape(12.dp),
        color = if (pair.isEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(
            1.dp,
            if (pair.isEnabled) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Glowing Top Accent Line
            if (pair.isEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    TitaniumMint.Mint500,
                                    TitaniumMint.Mint400,
                                    Color(0xFF2DD4BF)
                                )
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(TitaniumMint.Amber400.copy(alpha = 0.55f))
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            // TOP ROW: Pair name + subtitle on left, 3-dot overflow on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pair.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-0.2).sp
                    )
                    if (!pair.nickname.isNullOrBlank()) {
                        Text(
                            text = "${pair.fromCalendarName} → ${pair.toCalendarName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }

                // 3-Dot Overflow Menu
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Sync Configuration") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Sync Configuration", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDeletePair()
                            }
                        )
                    }
                }
            }

            // Accessibility Warning if target is read-only or missing
            if (!isTargetAccessible) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (toCalendar == null) "Target calendar not found on device" else "Target calendar is read-only",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Sync Failure Warning Banner
            if (isTargetAccessible && pair.lastSyncStatus?.let { it.startsWith("Failed") || it.contains("Not Found") || it.contains("Permission denied") } == true) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Sync Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = pair.lastSyncStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // BOTTOM ROW: Timestamp + Sync Now on left | Shrunk toggle + status on right
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // LEFT: Timestamp stacked above "Sync Now" button
                val glowFraction = glowAnim.value
                val syncTextColor = androidx.compose.ui.graphics.lerp(
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    TitaniumMint.Mint400,
                    glowFraction
                )
                val syncIconColor = if (glowFraction > 0.05f) TitaniumMint.Mint400
                    else if (pair.isEnabled) TitaniumMint.Mint400
                    else TitaniumMint.Amber400

                val buttonBorderColor = androidx.compose.ui.graphics.lerp(
                    TitaniumMint.Mint500.copy(alpha = 0.35f),
                    TitaniumMint.Mint400,
                    buttonGlowAnim.value
                )
                val buttonBgColor = androidx.compose.ui.graphics.lerp(
                    TitaniumMint.Mint500.copy(alpha = 0.12f),
                    TitaniumMint.Mint500.copy(alpha = 0.30f),
                    buttonGlowAnim.value
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Timestamp (glowing text, no chip)
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = syncIconColor,
                            modifier = Modifier.size(14.dp)
                        )
                        val statusText = if (pair.lastSyncTime != null) {
                            DateTimeUtils.formatLastSync(pair.lastSyncTime)
                        } else {
                            "Ready to sync"
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                shadow = if (glowFraction > 0.05f) androidx.compose.ui.graphics.Shadow(
                                    color = TitaniumMint.Mint400.copy(alpha = 0.9f * glowFraction),
                                    blurRadius = 14f * glowFraction
                                ) else null
                            ),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (glowFraction > 0.1f) FontWeight.Bold else FontWeight.Normal,
                            color = syncTextColor,
                            fontSize = 11.sp
                        )
                    }

                    // Sync Now button — fixed width so it never resizes when text changes
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = buttonBgColor,
                        border = BorderStroke((1f + buttonGlowAnim.value).dp, buttonBorderColor),
                        modifier = Modifier
                            .defaultMinSize(minWidth = 114.dp)
                            .clickable(
                                enabled = pair.isEnabled && !isSyncing && isTargetAccessible,
                                onClick = onSyncNow
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync",
                                tint = if (pair.isEnabled && isTargetAccessible) TitaniumMint.Mint400 else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .size(14.dp)
                                    .rotate(if (isSyncing) spinAngle else 0f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            AnimatedContent(
                                targetState = isSyncing,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
                                },
                                label = "cardSyncButtonFade"
                            ) { syncing ->
                                Text(
                                    text = if (syncing) "Syncing..." else "Sync Now",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (pair.isEnabled && isTargetAccessible) TitaniumMint.Mint400 else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                // RIGHT: Shrunken toggle + ACTIVE/PAUSED label
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (pair.isEnabled) "ACTIVE" else "PAUSED",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (pair.isEnabled) TitaniumMint.Mint400 else TitaniumMint.Amber400,
                        letterSpacing = 0.5.sp
                    )
                    Switch(
                        checked = pair.isEnabled,
                        onCheckedChange = onToggleEnabled,
                        modifier = Modifier
                            .scale(0.75f)
                            .height(20.dp)
                    )
                }
            }
        }
    }
}
}
