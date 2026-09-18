package com.stripedlens.calcloner.ui.components.sheets

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stripedlens.calcloner.ui.theme.TitaniumMint

/**
 * Expandable UI shell previewing upcoming condition-based event replication rules.
 * Displays disabled controls and an amber 'COMING SOON' badge to preview power features
 * without affecting current synchronization logic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncConditionsCard(
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Interactive preview state for Active Time Window dropdown
    var timeWindowExpanded by remember { mutableStateOf(false) }
    var selectedTimeWindow by remember { mutableStateOf("All Day / Anytime") }
    val timeWindowOptions = listOf(
        "All Day / Anytime",
        "Working Hours (8:00 AM – 5:00 PM)",
        "Morning (6:00 AM – 12:00 PM)",
        "Afternoon / Evening (12:00 PM – 9:00 PM)",
        "Custom Hours (Coming Soon)"
    )

    // RSVP preview state
    var selectedRsvp by remember { mutableStateOf("Accepted") }

    // Keyword & Regex preview state
    var titleFilterText by remember { mutableStateOf("") }
    var descriptionFilterText by remember { mutableStateOf("") }

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
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterAlt,
                        contentDescription = null,
                        tint = TitaniumMint.Amber400,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Sync Conditions & Filters",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = TitaniumMint.Amber500.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, TitaniumMint.Amber500.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = "COMING SOON",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    color = TitaniumMint.Amber400,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Filter events by availability, RSVP status, time windows, and regex.",
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

            // Expandable Inactive Controls Body
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Divider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 0.5.dp
                    )

                    // Notice Banner
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = TitaniumMint.Amber400,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Rule-based filtering is currently under preview. Interactive selections below are previewed for configuration design and will be wired into sync execution in an upcoming update.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    // 1. Availability Filter (Busy / Free)
                    Column(
                        modifier = Modifier.alpha(0.6f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "EVENT AVAILABILITY",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Checkbox(checked = true, onCheckedChange = null, enabled = false)
                                Text(
                                    text = "Busy Events",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Checkbox(checked = false, onCheckedChange = null, enabled = false)
                                Text(
                                    text = "Free / Transparent",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    // 2. RSVP Attendance Filter (Accepted / Tentative / Declined)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "ATTENDANCE / RSVP RESPONSE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.clickable { selectedRsvp = "Accepted" }
                            ) {
                                RadioButton(
                                    selected = selectedRsvp == "Accepted",
                                    onClick = { selectedRsvp = "Accepted" }
                                )
                                Text(
                                    text = "Accepted",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.clickable { selectedRsvp = "Tentative" }
                            ) {
                                RadioButton(
                                    selected = selectedRsvp == "Tentative",
                                    onClick = { selectedRsvp = "Tentative" }
                                )
                                Text(
                                    text = "Tentative",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.clickable { selectedRsvp = "Declined" }
                            ) {
                                RadioButton(
                                    selected = selectedRsvp == "Declined",
                                    onClick = { selectedRsvp = "Declined" }
                                )
                                Text(
                                    text = "Declined",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    // 3. Time Window Filter (Interactive Dropdown)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "ACTIVE TIME WINDOW",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )
                        ExposedDropdownMenuBox(
                            expanded = timeWindowExpanded,
                            onExpandedChange = { timeWindowExpanded = !timeWindowExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedTimeWindow,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = timeWindowExpanded)
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = timeWindowExpanded,
                                onDismissRequest = { timeWindowExpanded = false }
                            ) {
                                timeWindowOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option, style = MaterialTheme.typography.bodyMedium) },
                                        onClick = {
                                            selectedTimeWindow = option
                                            timeWindowExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 4. Title Keyword & Regex Filter
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "TITLE KEYWORD & REGEX FILTER",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )
                        OutlinedTextField(
                            value = titleFilterText,
                            onValueChange = { titleFilterText = it },
                            placeholder = { Text("e.g. #work, /sync|standup/i, or !Personal") },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Accepts literal keywords (e.g. '#work'), negation ('!Private'), or regex patterns (/pattern/flags).",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            fontSize = 10.sp
                        )
                    }

                    // 5. Description Keyword & Regex Filter
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "DESCRIPTION KEYWORD & REGEX FILTER",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )
                        OutlinedTextField(
                            value = descriptionFilterText,
                            onValueChange = { descriptionFilterText = it },
                            placeholder = { Text("e.g. zoom.us, /(?i)confidential/, or !skip-sync") },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Filter based on event description content. Matches plain substrings or standard Java/Kotlin regex patterns.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
