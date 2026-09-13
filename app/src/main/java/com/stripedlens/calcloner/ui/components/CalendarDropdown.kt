package com.stripedlens.calcloner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stripedlens.calcloner.CalendarInfo

@Composable
fun CustomProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .background(Color(0xFFE0E0E0), RoundedCornerShape(5.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(5.dp))
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarDropdown(
    label: String,
    calendars: List<CalendarInfo>,
    selectedCalendar: CalendarInfo?,
    filterWritable: Boolean = false,
    onCalendarSelected: (CalendarInfo?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val displayCalendars = remember(calendars, filterWritable) {
        if (filterWritable) calendars.filter { it.canWrite } else calendars
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedCalendar?.let { "${it.displayName} (${it.accountName})" } ?: "None (Unselected)",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("None (Unselected)", color = Color.Gray) },
                onClick = {
                    expanded = false
                    onCalendarSelected(null)
                }
            )
            if (displayCalendars.isNotEmpty()) {
                val grouped = displayCalendars.groupBy { it.accountName }
                grouped.forEach { (accountName, cals) ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = accountName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                        )
                    }
                    cals.forEach { cal ->
                        DropdownMenuItem(
                            text = {
                                Column(modifier = Modifier.padding(start = 6.dp)) {
                                    Text(cal.displayName, fontWeight = FontWeight.Medium)
                                    if (cal.isPrimary) {
                                        Text("Primary", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                                    }
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
