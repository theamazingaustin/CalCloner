package com.stripedlens.calcloner.ui.components.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stripedlens.calcloner.CalendarInfo
import com.stripedlens.calcloner.ui.theme.TitaniumMint
import kotlin.math.roundToInt

/**
 * Renders the animated conduit pipe connecting source and target calendar selection cards.
 */
@Composable
fun AnimatedConduitPipe(
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

/**
 * Card for selecting a source or target calendar, showing access permissions,
 * lock state, and dropdown menu.
 */
@Composable
fun CalendarSelectionCard(
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
