package com.stripedlens.calcloner.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stripedlens.calcloner.ui.theme.TitaniumMint

/**
 * Reusable, sleek date range picker featuring quick-select preset chips
 * and fine-grained past/future boundary capsules (number + days/weeks/months dropdown).
 *
 * Reusable across One-Time Copy, ICS Import/Export, and other ad-hoc date-scoped operations.
 */
@Composable
fun UnifiedDateRangeCard(
    daysPast: Int,
    daysFuture: Int,
    onRangeChanged: (daysPast: Int, daysFuture: Int) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "DATE RANGE WINDOW",
    description: String? = "Events within this window will be included."
) {
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
            SyncTimeUnit.DAYS -> daysPast.coerceAtLeast(0)
        }
        mutableStateOf(count.toString())
    }

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
        val num = clean.toIntOrNull() ?: 0
        val computedDays = (num * pastUnit.multiplier).coerceIn(0, 3650)
        onRangeChanged(computedDays, daysFuture)
    }

    fun updatePastUnit(newUnit: SyncTimeUnit) {
        val currentDays = (pastNumberString.toIntOrNull() ?: 0) * pastUnit.multiplier
        val convertedNum = when (newUnit) {
            SyncTimeUnit.MONTHS -> (currentDays / 30).coerceAtLeast(1)
            SyncTimeUnit.WEEKS -> (currentDays / 7).coerceAtLeast(1)
            SyncTimeUnit.DAYS -> currentDays.coerceAtLeast(0)
        }
        pastUnit = newUnit
        pastNumberString = convertedNum.toString()
        val computedDays = (convertedNum * newUnit.multiplier).coerceIn(0, 3650)
        onRangeChanged(computedDays, daysFuture)
    }

    fun updateFutureNumber(newNumStr: String) {
        val clean = newNumStr.filter { it.isDigit() }.take(4)
        futureNumberString = clean
        val num = clean.toIntOrNull() ?: 1
        val computedDays = (num * futureUnit.multiplier).coerceIn(1, 3650)
        onRangeChanged(daysPast, computedDays)
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
        onRangeChanged(daysPast, computedDays)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Title and current window badge
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
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = TitaniumMint.Mint400,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = title,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TitaniumMint.Mint400
                    )
                }

                Text(
                    text = "-${daysPast}d / +${daysFuture}d",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TitaniumMint.Mint400,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!description.isNullOrEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Quick Preset Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val is30d = daysPast == 7 && daysFuture == 30
                val is90d = daysPast == 14 && daysFuture == 90
                val is1y = daysPast == 30 && daysFuture == 365
                val isAll = daysPast == 3650 && daysFuture == 3650

                DatePresetPill(
                    label = "30 Days",
                    selected = is30d,
                    onClick = { onRangeChanged(7, 30) },
                    modifier = Modifier.weight(1f)
                )
                DatePresetPill(
                    label = "90 Days",
                    selected = is90d,
                    onClick = { onRangeChanged(14, 90) },
                    modifier = Modifier.weight(1f)
                )
                DatePresetPill(
                    label = "1 Year",
                    selected = is1y,
                    onClick = { onRangeChanged(30, 365) },
                    modifier = Modifier.weight(1f)
                )
                DatePresetPill(
                    label = "All Events",
                    selected = isAll,
                    onClick = { onRangeChanged(3650, 3650) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Side-by-Side: Past Boundary & Future Boundary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    UnifiedBoundaryControl(
                        title = "Past Window",
                        subtitle = "How far back",
                        numberString = pastNumberString,
                        unit = pastUnit,
                        isClockwise = false,
                        onNumberChange = { updatePastNumber(it) },
                        onUnitChange = { updatePastUnit(it) }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    UnifiedBoundaryControl(
                        title = "Future Window",
                        subtitle = "How far forward",
                        numberString = futureNumberString,
                        unit = futureUnit,
                        isClockwise = true,
                        onNumberChange = { updateFutureNumber(it) },
                        onUnitChange = { updateFutureUnit(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DatePresetPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) TitaniumMint.Mint500.copy(alpha = 0.18f) else MaterialTheme.colorScheme.background,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) TitaniumMint.Mint400 else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier.height(34.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                color = if (selected) TitaniumMint.Mint400 else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UnifiedBoundaryControl(
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
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = TitaniumMint.Mint400,
                modifier = Modifier
                    .size(13.dp)
                    .rotate(if (isClockwise) 90f else -90f)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Input Capsule
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = numberString,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() }.take(4)
                        onNumberChange(filtered)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                // Unit Selector Dropdown
                Box {
                    Row(
                        modifier = Modifier
                            .clickable { showUnitMenu = true }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = unit.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TitaniumMint.Mint400
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select unit",
                            tint = TitaniumMint.Mint400,
                            modifier = Modifier.size(18.dp)
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
                                        fontWeight = if (timeUnit == unit) FontWeight.Bold else FontWeight.Normal,
                                        color = if (timeUnit == unit) TitaniumMint.Mint400 else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    onUnitChange(timeUnit)
                                    showUnitMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
