package com.stripedlens.calcloner.ui.components.filters

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stripedlens.calcloner.ui.theme.TitaniumMint

/**
 * Interactive filter state model holding all condition settings.
 * Designed for reusability across Sync Pair Settings and One-Time Operations screens.
 */
data class EventFilterUiState(
    val isExpanded: Boolean = false,
    val matchAllLogic: Boolean = true, // true = ALL (AND), false = ANY (OR)
    // Presets
    val presetWorkHours: Boolean = false,
    val presetAcceptedOnly: Boolean = false,
    val presetBusyOnly: Boolean = false,
    // Availability
    val allowBusy: Boolean = true,
    val allowFree: Boolean = true,
    val allowTentative: Boolean = false,
    // RSVP
    val rsvpAccepted: Boolean = true,
    val rsvpTentative: Boolean = false,
    val rsvpDeclined: Boolean = false,
    // Schedule
    val timeWindow: String = "All Day / Anytime",
    val activeDays: Set<Int> = setOf(1, 2, 3, 4, 5), // 1=Mon .. 7=Sun
    // Keywords
    val titleQuery: String = "",
    val descriptionQuery: String = "",
    val isCaseSensitive: Boolean = false
) {
    /**
     * Dynamically calculates how many active filter rules are currently configured.
     */
    val activeRuleCount: Int
        get() {
            var count = 0
            if (!allowFree || !allowBusy || allowTentative) count++
            if (!rsvpAccepted || rsvpTentative || rsvpDeclined) count++
            if (timeWindow != "All Day / Anytime" || activeDays.size < 7) count++
            if (titleQuery.isNotBlank()) count++
            if (descriptionQuery.isNotBlank()) count++
            return count
        }
}

/**
 * Sample event for the interactive live mock preview inspector.
 */
private data class MockSampleEvent(
    val title: String,
    val timeSlot: String,
    val isBusy: Boolean,
    val rsvpStatus: String, // "Accepted", "Tentative", "Declined"
    val isWeekend: Boolean,
    val isWorkHours: Boolean,
    val hasPrivateTag: Boolean
)

/**
 * Reusable, industry-leading condition and filter rule builder component.
 * Features:
 * - 1-tap Quick Preset Chips (Work Hours, Accepted Only, Busy Only)
 * - Plain-language Match Logic switcher (Match ALL vs Match ANY)
 * - Modular criteria cards (Availability/RSVP, Time/Days, Keywords/Regex)
 * - Dynamic Live Mock Preview counter & interactive sample inspector dialog
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventConditionFilterCard(
    modifier: Modifier = Modifier,
    title: String = "Sync Conditions & Filters",
    subtitle: String = "Filter events by availability, RSVP, schedule windows, and keywords.",
    initialState: EventFilterUiState = remember { EventFilterUiState() },
    onStateChange: (EventFilterUiState) -> Unit = {}
) {
    var state by remember { mutableStateOf(initialState) }
    var showPreviewModal by remember { mutableStateOf(false) }
    var timeWindowMenuExpanded by remember { mutableStateOf(false) }

    val timeWindowOptions = listOf(
        "All Day / Anytime",
        "Working Hours (9:00 AM – 5:00 PM)",
        "Morning (6:00 AM – 12:00 PM)",
        "Afternoon / Evening (12:00 PM – 9:00 PM)",
        "Custom Hours (9:00 AM – 5:00 PM)"
    )

    // Helper to update state and invoke callback
    fun updateState(reducer: (EventFilterUiState) -> EventFilterUiState) {
        state = reducer(state)
        onStateChange(state)
    }

    // Realistic mock sample events for interactive UI testing
    val mockEvents = remember {
        listOf(
            MockSampleEvent("Weekly Team Standup", "Mon 9:30 AM", isBusy = true, rsvpStatus = "Accepted", isWeekend = false, isWorkHours = true, hasPrivateTag = false),
            MockSampleEvent("Dentist Appointment [Private]", "Tue 2:00 PM", isBusy = true, rsvpStatus = "Declined", isWeekend = false, isWorkHours = true, hasPrivateTag = true),
            MockSampleEvent("Family BBQ & Weekend Fun", "Sat 1:00 PM", isBusy = false, rsvpStatus = "Accepted", isWeekend = true, isWorkHours = false, hasPrivateTag = false),
            MockSampleEvent("Product Roadmap Review", "Wed 11:00 AM", isBusy = true, rsvpStatus = "Accepted", isWeekend = false, isWorkHours = true, hasPrivateTag = false),
            MockSampleEvent("Focus Block / Free Reading", "Thu 3:00 PM", isBusy = false, rsvpStatus = "Accepted", isWeekend = false, isWorkHours = true, hasPrivateTag = false),
            MockSampleEvent("Late Evening APAC Sync", "Tue 9:30 PM", isBusy = true, rsvpStatus = "Accepted", isWeekend = false, isWorkHours = false, hasPrivateTag = false),
            MockSampleEvent("Optional Coffee Chat", "Fri 4:30 PM", isBusy = false, rsvpStatus = "Tentative", isWeekend = false, isWorkHours = true, hasPrivateTag = false),
            MockSampleEvent("Strategic Client Pitch", "Mon 1:30 PM", isBusy = true, rsvpStatus = "Accepted", isWeekend = false, isWorkHours = true, hasPrivateTag = false)
        )
    }

    // Mock evaluation for live preview counter
    val evaluatedEvents = remember(state) {
        mockEvents.map { event ->
            val reasons = mutableListOf<String>()

            // Check availability
            if (event.isBusy && !state.allowBusy) reasons.add("Availability (Busy unselected)")
            if (!event.isBusy && !state.allowFree) reasons.add("Availability (Free unselected)")

            // Check RSVP
            if (event.rsvpStatus == "Accepted" && !state.rsvpAccepted) reasons.add("RSVP (Accepted unselected)")
            if (event.rsvpStatus == "Tentative" && !state.rsvpTentative) reasons.add("RSVP (Tentative unselected)")
            if (event.rsvpStatus == "Declined" && !state.rsvpDeclined) reasons.add("RSVP (Declined)")

            // Check Schedule & Days
            if (state.timeWindow.contains("Working Hours") && !event.isWorkHours) reasons.add("Outside Working Hours")
            if (state.presetWorkHours && (event.isWeekend || !event.isWorkHours)) reasons.add("Outside Work Hours Preset")
            if (event.isWeekend && !state.activeDays.contains(6) && !state.activeDays.contains(7)) reasons.add("Weekend Day")

            // Check Keyword exclusions
            if (state.titleQuery.contains("!private", ignoreCase = true) && event.hasPrivateTag) reasons.add("Matches '!private' exclusion")

            val isMatched = if (state.activeRuleCount == 0) true else reasons.isEmpty()
            event to (isMatched to reasons.joinToString(", "))
        }
    }

    val matchedCount = evaluatedEvents.count { it.second.first }
    val totalCount = evaluatedEvents.size

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (state.activeRuleCount > 0) TitaniumMint.Mint500.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── 1. Header Bar ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { updateState { it.copy(isExpanded = !it.isExpanded) } }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (state.activeRuleCount > 0) TitaniumMint.Mint500.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = if (state.activeRuleCount > 0) TitaniumMint.Mint400 else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (state.activeRuleCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = TitaniumMint.Mint500.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, TitaniumMint.Mint500.copy(alpha = 0.35f))
                                ) {
                                    Text(
                                        text = "${state.activeRuleCount} ACTIVE",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        color = TitaniumMint.Mint400,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
                IconButton(onClick = { updateState { it.copy(isExpanded = !it.isExpanded) } }) {
                    Icon(
                        imageVector = if (state.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (state.isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── 2. Quick Preset Chips (Always Visible for 1-Tap Use) ───────
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "QUICK PRESETS",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterPresetChip(
                        label = "Work Hours Only",
                        icon = Icons.Default.Schedule,
                        isSelected = state.presetWorkHours,
                        onClick = {
                            updateState {
                                val next = !it.presetWorkHours
                                it.copy(
                                    presetWorkHours = next,
                                    timeWindow = if (next) "Working Hours (9:00 AM – 5:00 PM)" else "All Day / Anytime",
                                    activeDays = if (next) setOf(1, 2, 3, 4, 5) else setOf(1, 2, 3, 4, 5, 6, 7)
                                )
                            }
                        }
                    )
                    FilterPresetChip(
                        label = "Accepted Only",
                        icon = Icons.Default.Check,
                        isSelected = state.presetAcceptedOnly,
                        onClick = {
                            updateState {
                                val next = !it.presetAcceptedOnly
                                it.copy(
                                    presetAcceptedOnly = next,
                                    rsvpAccepted = true,
                                    rsvpTentative = !next,
                                    rsvpDeclined = false
                                )
                            }
                        }
                    )
                    FilterPresetChip(
                        label = "Busy Only",
                        icon = Icons.Default.FilterAlt,
                        isSelected = state.presetBusyOnly,
                        onClick = {
                            updateState {
                                val next = !it.presetBusyOnly
                                it.copy(
                                    presetBusyOnly = next,
                                    allowBusy = true,
                                    allowFree = !next
                                )
                            }
                        }
                    )
                }
            }

            // ── 3. Expandable Detailed Criteria Sections ──────────────────
            AnimatedVisibility(
                visible = state.isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Divider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 0.5.dp
                    )

                    // Match Logic Switcher (Match ALL vs Match ANY)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "MATCH LOGIC",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { updateState { it.copy(matchAllLogic = true) } }
                                ) {
                                    RadioButton(
                                        selected = state.matchAllLogic,
                                        onClick = { updateState { it.copy(matchAllLogic = true) } },
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Match ALL (AND)", style = MaterialTheme.typography.bodySmall)
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { updateState { it.copy(matchAllLogic = false) } }
                                ) {
                                    RadioButton(
                                        selected = !state.matchAllLogic,
                                        onClick = { updateState { it.copy(matchAllLogic = false) } },
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Match ANY (OR)", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    // Section A: Availability & RSVP Status
                    RuleSectionCard(
                        icon = Icons.Default.FilterAlt,
                        title = "Availability & RSVP Response"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Availability Checkboxes
                            Text(
                                text = "EVENT AVAILABILITY",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                FilterCheckboxRow(
                                    label = "Busy",
                                    checked = state.allowBusy,
                                    onCheckedChange = { updateState { s -> s.copy(allowBusy = it) } }
                                )
                                FilterCheckboxRow(
                                    label = "Free / Transparent",
                                    checked = state.allowFree,
                                    onCheckedChange = { updateState { s -> s.copy(allowFree = it) } }
                                )
                                FilterCheckboxRow(
                                    label = "Tentative",
                                    checked = state.allowTentative,
                                    onCheckedChange = { updateState { s -> s.copy(allowTentative = it) } }
                                )
                            }

                            Spacer(Modifier.height(2.dp))

                            // RSVP Status Checkboxes
                            Text(
                                text = "RSVP RESPONSE STATUS",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                FilterCheckboxRow(
                                    label = "Accepted",
                                    checked = state.rsvpAccepted,
                                    onCheckedChange = { updateState { s -> s.copy(rsvpAccepted = it) } }
                                )
                                FilterCheckboxRow(
                                    label = "Tentative",
                                    checked = state.rsvpTentative,
                                    onCheckedChange = { updateState { s -> s.copy(rsvpTentative = it) } }
                                )
                                FilterCheckboxRow(
                                    label = "Declined",
                                    checked = state.rsvpDeclined,
                                    onCheckedChange = { updateState { s -> s.copy(rsvpDeclined = it) } }
                                )
                            }
                        }
                    }

                    // Section B: Active Time Window & Days of Week
                    RuleSectionCard(
                        icon = Icons.Default.Schedule,
                        title = "Active Schedule Window & Days"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Dropdown Window
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { timeWindowMenuExpanded = true }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(state.timeWindow, style = MaterialTheme.typography.bodyMedium)
                                        Icon(
                                            imageVector = Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = timeWindowMenuExpanded,
                                    onDismissRequest = { timeWindowMenuExpanded = false }
                                ) {
                                    timeWindowOptions.forEach { opt ->
                                        DropdownMenuItem(
                                            text = { Text(opt, style = MaterialTheme.typography.bodyMedium) },
                                            onClick = {
                                                updateState { it.copy(timeWindow = opt) }
                                                timeWindowMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Day of week bubbles (M T W T F S S)
                            Text(
                                text = "ACTIVE DAYS OF WEEK",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val dayLabels = listOf("M" to 1, "T" to 2, "W" to 3, "T" to 4, "F" to 5, "S" to 6, "S" to 7)
                                dayLabels.forEach { (label, dayNum) ->
                                    val isSelected = state.activeDays.contains(dayNum)
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) TitaniumMint.Mint500 else MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) TitaniumMint.Mint400 else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clickable {
                                                updateState { s ->
                                                    val newDays = s.activeDays.toMutableSet()
                                                    if (isSelected) newDays.remove(dayNum) else newDays.add(dayNum)
                                                    s.copy(activeDays = newDays)
                                                }
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color(0xFF003824) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section C: Keywords & Regex Filter
                    RuleSectionCard(
                        icon = Icons.Default.TextFields,
                        title = "Keyword & Regex Matching"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = state.titleQuery,
                                onValueChange = { updateState { s -> s.copy(titleQuery = it) } },
                                placeholder = { Text("e.g. #work, !private, /sync|standup/i") },
                                trailingIcon = {
                                    if (state.titleQuery.isNotEmpty()) {
                                        IconButton(onClick = { updateState { it.copy(titleQuery = "") } }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = TitaniumMint.Mint400,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                QuerySyntaxTag(text = "#tag: include")
                                QuerySyntaxTag(text = "!word: exclude")
                                QuerySyntaxTag(text = "/pattern/: regex")
                            }
                        }
                    }

                    // Reset Filters Button
                    if (state.activeRuleCount > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    updateState {
                                        EventFilterUiState(isExpanded = true)
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Reset All Conditions", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // ── 4. Live Mock Preview Banner ────────────────────────────────
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = TitaniumMint.Mint500.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, TitaniumMint.Mint500.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { showPreviewModal = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            tint = TitaniumMint.Mint400,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Preview: $matchedCount of $totalCount sample events match",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (state.activeRuleCount == 0) "No active filters (All events will sync)" else "${totalCount - matchedCount} events would be filtered out. Tap to inspect.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = "Inspect",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TitaniumMint.Mint400
                    )
                }
            }
        }
    }

    // ── 5. Sample Event Inspector Modal Dialog ─────────────────────────────
    if (showPreviewModal) {
        AlertDialog(
            onDismissRequest = { showPreviewModal = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        tint = TitaniumMint.Mint400,
                        modifier = Modifier.size(22.dp)
                    )
                    Text("Filter Preview Inspector", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = TitaniumMint.Mint400, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Testing against representative calendar events. Matches recalculate live as you toggle conditions.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    evaluatedEvents.forEach { (event, eval) ->
                        val (isMatched, reason) = eval
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isMatched) TitaniumMint.Mint500.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            border = BorderStroke(
                                1.dp,
                                if (isMatched) TitaniumMint.Mint500.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = event.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isMatched) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                    )
                                    Text(
                                        text = "${event.timeSlot} • ${if (event.isBusy) "Busy" else "Free"} • RSVP: ${event.rsvpStatus}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!isMatched && reason.isNotEmpty()) {
                                        Text(
                                            text = "Excluded: $reason",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isMatched) TitaniumMint.Mint500.copy(alpha = 0.2f) else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = if (isMatched) "MATCH" else "SKIP",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isMatched) TitaniumMint.Mint400 else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPreviewModal = false }) {
                    Text("Close", color = TitaniumMint.Mint400)
                }
            }
        )
    }
}

// ── Sub-Components ─────────────────────────────────────────────────────────

@Composable
private fun FilterPresetChip(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) TitaniumMint.Mint500.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            1.dp,
            if (isSelected) TitaniumMint.Mint400 else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) TitaniumMint.Mint400 else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) TitaniumMint.Mint400 else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun RuleSectionCard(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = TitaniumMint.Mint400, modifier = Modifier.size(16.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun FilterCheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(20.dp)
        )
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun QuerySyntaxTag(text: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
