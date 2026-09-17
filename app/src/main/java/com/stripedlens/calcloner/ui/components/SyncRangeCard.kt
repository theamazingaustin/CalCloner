package com.stripedlens.calcloner.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stripedlens.calcloner.ui.theme.TitaniumMint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.text.format.DateUtils

enum class SyncTimeUnit(val label: String, val multiplier: Int) {
    DAYS("days", 1),
    WEEKS("weeks", 7),
    MONTHS("months", 30)
}

@Composable
fun SyncRangeCard(
    daysPast: Int,
    daysFuture: Int,
    onDaysPastChange: (Int) -> Unit,
    onDaysFutureChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Decompose daysPast into (number, unit)
    var pastUnit by remember(daysPast) {
        mutableStateOf(
            when {
                daysPast > 0 && daysPast % 30 == 0 -> SyncTimeUnit.MONTHS
                daysPast > 0 && daysPast % 7 == 0 -> SyncTimeUnit.WEEKS
                else -> SyncTimeUnit.DAYS
            }
        )
    }
    var pastNumberString by remember(daysPast, pastUnit) {
        val count = when (pastUnit) {
            SyncTimeUnit.MONTHS -> (daysPast / 30).coerceAtLeast(1)
            SyncTimeUnit.WEEKS -> (daysPast / 7).coerceAtLeast(1)
            SyncTimeUnit.DAYS -> daysPast.coerceAtLeast(1)
        }
        mutableStateOf(count.toString())
    }

    // Decompose daysFuture into (number, unit)
    var futureUnit by remember(daysFuture) {
        mutableStateOf(
            when {
                daysFuture > 0 && daysFuture % 30 == 0 -> SyncTimeUnit.MONTHS
                daysFuture > 0 && daysFuture % 7 == 0 -> SyncTimeUnit.WEEKS
                else -> SyncTimeUnit.DAYS
            }
        )
    }
    var futureNumberString by remember(daysFuture, futureUnit) {
        val count = when (futureUnit) {
            SyncTimeUnit.MONTHS -> (daysFuture / 30).coerceAtLeast(1)
            SyncTimeUnit.WEEKS -> (daysFuture / 7).coerceAtLeast(1)
            SyncTimeUnit.DAYS -> daysFuture.coerceAtLeast(1)
        }
        mutableStateOf(count.toString())
    }

    fun updatePastNumber(newNumStr: String) {
        val clean = newNumStr.filter { it.isDigit() }.take(4)
        pastNumberString = clean
        val num = clean.toIntOrNull() ?: 1
        val computedDays = (num * pastUnit.multiplier).coerceIn(1, 3650)
        onDaysPastChange(computedDays)
    }

    fun updatePastUnit(newUnit: SyncTimeUnit) {
        val currentDays = (pastNumberString.toIntOrNull() ?: 1) * pastUnit.multiplier
        val convertedNum = when (newUnit) {
            SyncTimeUnit.MONTHS -> (currentDays / 30).coerceAtLeast(1)
            SyncTimeUnit.WEEKS -> (currentDays / 7).coerceAtLeast(1)
            SyncTimeUnit.DAYS -> currentDays.coerceAtLeast(1)
        }
        pastUnit = newUnit
        pastNumberString = convertedNum.toString()
        val computedDays = (convertedNum * newUnit.multiplier).coerceIn(1, 3650)
        onDaysPastChange(computedDays)
    }

    fun updateFutureNumber(newNumStr: String) {
        val clean = newNumStr.filter { it.isDigit() }.take(4)
        futureNumberString = clean
        val num = clean.toIntOrNull() ?: 1
        val computedDays = (num * futureUnit.multiplier).coerceIn(1, 3650)
        onDaysFutureChange(computedDays)
    }

    fun updateFutureUnit(newUnit: SyncTimeUnit) {
        val currentDays = (futureNumberString.toIntOrNull() ?: 1) * futureUnit.multiplier
        val convertedNum = when (newUnit) {
            SyncTimeUnit.MONTHS -> (currentDays / 30).coerceAtLeast(1)
            SyncTimeUnit.WEEKS -> (currentDays / 7).coerceAtLeast(1)
            SyncTimeUnit.DAYS -> currentDays.coerceAtLeast(1)
        }
        futureUnit = newUnit
        futureNumberString = convertedNum.toString()
        val computedDays = (convertedNum * newUnit.multiplier).coerceIn(1, 3650)
        onDaysFutureChange(computedDays)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Main Sync Range Container Card
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Column {
                    Text(
                        text = "Sync Range",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Define synchronization boundaries.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Side-by-Side: Past Sync & Future Sync Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        SyncBoundaryControl(
                            title = "Past Sync",
                            subtitle = "How far back",
                            numberString = pastNumberString,
                            unit = pastUnit,
                            isClockwise = false,
                            onNumberChange = { updatePastNumber(it) },
                            onUnitChange = { updatePastUnit(it) }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        SyncBoundaryControl(
                            title = "Future Sync",
                            subtitle = "How far forward",
                            numberString = futureNumberString,
                            unit = futureUnit,
                            isClockwise = true,
                            onNumberChange = { updateFutureNumber(it) },
                            onUnitChange = { updateFutureUnit(it) }
                        )
                    }
                }

                // Info Banner
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.background,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = TitaniumMint.Mint400,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Events outside this rolling window are automatically pruned from the target calendar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncBoundaryControl(
    title: String,
    subtitle: String,
    numberString: String,
    unit: SyncTimeUnit,
    isClockwise: Boolean,
    onNumberChange: (String) -> Unit,
    onUnitChange: (SyncTimeUnit) -> Unit
) {
    var showUnitMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Label Column: Title with subtitle underneath
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = TitaniumMint.Mint400,
                modifier = Modifier
                    .size(15.dp)
                    .rotate(if (isClockwise) 90f else -90f)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Single sleek input capsule (No nested cards!)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Big Bold Number Input
                BasicTextField(
                    value = numberString,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() }.take(4)
                        onNumberChange(filtered)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    cursorBrush = SolidColor(TitaniumMint.Mint400),
                    modifier = Modifier.weight(1f)
                )

                // Unit Dropdown
                Box {
                    Row(
                        modifier = Modifier
                            .clickable { showUnitMenu = true }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = unit.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = TitaniumMint.Mint400,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select time unit",
                            tint = TitaniumMint.Mint400,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showUnitMenu,
                        onDismissRequest = { showUnitMenu = false }
                    ) {
                        SyncTimeUnit.values().forEach { timeUnit ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = timeUnit.label,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (timeUnit == unit) FontWeight.Bold else FontWeight.Normal,
                                        color = if (timeUnit == unit) TitaniumMint.Mint400 else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    showUnitMenu = false
                                    onUnitChange(timeUnit)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CombinedSyncTelemetryCard(
    lastSyncTime: Long?,
    syncedEventsCount: Int,
    modifier: Modifier = Modifier
) {
    val formattedTime = remember(lastSyncTime) {
        if (lastSyncTime == null || lastSyncTime <= 0) {
            "Ready to sync"
        } else {
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val timeStr = timeFormat.format(Date(lastSyncTime))
            val now = Calendar.getInstance()
            val syncCal = Calendar.getInstance().apply { timeInMillis = lastSyncTime }
            when {
                DateUtils.isToday(lastSyncTime) -> "Today at $timeStr"
                now.get(Calendar.YEAR) == syncCal.get(Calendar.YEAR) &&
                    now.get(Calendar.DAY_OF_YEAR) - syncCal.get(Calendar.DAY_OF_YEAR) == 1 -> "Yesterday at $timeStr"
                else -> {
                    val dateFormat = SimpleDateFormat("MMM d 'at' h:mm a", Locale.getDefault())
                    dateFormat.format(Date(lastSyncTime))
                }
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Side: Last Sync Info (~60% split)
            Column(
                modifier = Modifier.weight(0.60f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = TitaniumMint.Mint400,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "LAST SYNC",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                }
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Subtle vertical separator
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            )

            // Right Side: Synced Events Count (~40% split)
            Column(
                modifier = Modifier.weight(0.40f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = syncedEventsCount.toString(),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TitaniumMint.Mint400
                )
                Text(
                    text = "Synced Events",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
    }
}
