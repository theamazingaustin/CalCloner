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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
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
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
    val isEnabled: Boolean = false,
    val isExpanded: Boolean = false,
    // Availability
    val allowBusy: Boolean = true,
    val allowFree: Boolean = true,
    val allowTentative: Boolean = false,
    val allowEmpty: Boolean = true,
    // RSVP
    val rsvpAccepted: Boolean = true,
    val rsvpTentative: Boolean = false,
    val rsvpDeclined: Boolean = false,
    // Schedule
    val includeAllDayEvents: Boolean = true,
    val enableTimeFilter: Boolean = false,
    val fromHour: Int = 9,
    val fromMinute: Int = 0,
    val toHour: Int = 17,
    val toMinute: Int = 0,
    val activeDays: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7), // 1=Mon .. 7=Sun
    // Text Filtering (Words/Phrases separated by comma)
    val titleContains: String = "",
    val titleContainsMatchAll: Boolean = false,
    val titleDoesNotContain: String = "",
    val titleDoesNotContainMatchAll: Boolean = false,
    val descriptionContains: String = "",
    val descriptionContainsMatchAll: Boolean = false,
    val descriptionDoesNotContain: String = "",
    val descriptionDoesNotContainMatchAll: Boolean = false
) {
    /**
     * Checks for invalid or incomplete logic and returns a human-readable error,
     * or null if the rule logic is sound.
     */
    val validationError: String?
        get() {
            if (!allowBusy && !allowFree && !allowTentative && !allowEmpty) {
                return "At least one availability option (Busy, Free, Tentative, or Empty) must be selected."
            }
            if (!rsvpAccepted && !rsvpTentative && !rsvpDeclined) {
                return "At least one RSVP status (Accepted, Tentative, or Declined) must be selected."
            }
            if (activeDays.isEmpty()) {
                return "At least one active day of the week must be selected."
            }
            if (enableTimeFilter) {
                val fromTotalMins = fromHour * 60 + fromMinute
                val toTotalMins = toHour * 60 + toMinute
                if (fromTotalMins >= toTotalMins) {
                    return "From time must be earlier than To time."
                }
            }
            // Check for direct keyword conflicts
            val titleInclusions = titleContains.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            val titleExclusions = titleDoesNotContain.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            val conflictingTitle = titleInclusions.firstOrNull { it in titleExclusions }
            if (conflictingTitle != null) {
                return "Title cannot simultaneously contain and exclude '$conflictingTitle'."
            }

            val descInclusions = descriptionContains.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            val descExclusions = descriptionDoesNotContain.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            val conflictingDesc = descInclusions.firstOrNull { it in descExclusions }
            if (conflictingDesc != null) {
                return "Description cannot simultaneously contain and exclude '$conflictingDesc'."
            }

            return null
        }

    /**
     * Dynamically calculates how many active filter rules are currently configured.
     */
    val activeRuleCount: Int
        get() {
            var count = 0
            if (!allowFree || !allowBusy || allowTentative || !allowEmpty) count++
            if (!rsvpAccepted || rsvpTentative || rsvpDeclined) count++
            if (!includeAllDayEvents) count++
            if (enableTimeFilter) count++
            if (activeDays.size < 7) count++
            if (titleContains.isNotBlank()) count++
            if (titleDoesNotContain.isNotBlank()) count++
            if (descriptionContains.isNotBlank()) count++
            if (descriptionDoesNotContain.isNotBlank()) count++
            return count
        }
}

/**
 * Sample event for the interactive live mock preview inspector.
 */
private data class MockSampleEvent(
    val title: String,
    val description: String,
    val startHour: Int,
    val startMinute: Int,
    val timeSlot: String,
    val isBusy: Boolean,
    val rsvpStatus: String, // "Accepted", "Tentative", "Declined"
    val dayOfWeek: Int, // 1=Mon .. 7=Sun
    val isAllDay: Boolean = false
)

/**
 * Reusable, industry-leading condition and filter rule builder component.
 * Features:
 * - Master Enable/Disable Switch with strict validation barrier (blocks enabling if errored)
 * - User-selectable From/To times with 'From cannot be after To' validation
 * - 4 explicit text filters (Title/Description Contains & Doesn't Contain, comma-separated)
 * - Contrast-optimized day-of-week bubble toggles
 * - Live mock preview counter & interactive sample inspector dialog
 */
@Composable
fun EventConditionFilterCard(
    modifier: Modifier = Modifier,
    title: String = "Sync Conditions & Filters",
    subtitle: String = "Events that match all the following criteria will be copied to the target calendar.",
    initialState: EventFilterUiState = remember { EventFilterUiState() },
    onStateChange: (EventFilterUiState) -> Unit = {}
) {
    var state by remember { mutableStateOf(initialState) }
    var showPreviewModal by remember { mutableStateOf(false) }
    var validationErrorMessage by remember { mutableStateOf<String?>(null) }

    // Dialog state for custom time selection
    var showTimePickerDialog by remember { mutableStateOf(false) }
    var isPickingFromTime by remember { mutableStateOf(true) }

    fun updateState(reducer: (EventFilterUiState) -> EventFilterUiState) {
        state = reducer(state)
        // Clear error message if user fixes the condition
        if (validationErrorMessage != null && state.validationError == null) {
            validationErrorMessage = null
        }
        onStateChange(state)
    }

    // Helper to format hour & minute to 12h AM/PM
    fun formatTime(hour: Int, minute: Int): String {
        val h = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        val amPm = if (hour < 12) "AM" else "PM"
        val m = minute.toString().padStart(2, '0')
        return "$h:$m $amPm"
    }

    // Realistic mock sample events for interactive UI testing
    val mockEvents = remember {
        listOf(
            MockSampleEvent("Weekly Team Standup", "Discuss team priorities and blockers via Meet", 9, 30, "Mon 9:30 AM", isBusy = true, rsvpStatus = "Accepted", dayOfWeek = 1),
            MockSampleEvent("Dentist Appointment", "Routine dental checkup [Personal]", 14, 0, "Tue 2:00 PM", isBusy = true, rsvpStatus = "Declined", dayOfWeek = 2),
            MockSampleEvent("Family BBQ & Weekend Gathering", "Lunch at the park with friends", 13, 0, "Sat 1:00 PM", isBusy = false, rsvpStatus = "Accepted", dayOfWeek = 6),
            MockSampleEvent("Product Roadmap & Strategy", "Review quarterly deliverables and metrics", 11, 0, "Wed 11:00 AM", isBusy = true, rsvpStatus = "Accepted", dayOfWeek = 3),
            MockSampleEvent("Focus Block / Free Reading", "Catching up on engineering docs", 15, 0, "Thu 3:00 PM", isBusy = false, rsvpStatus = "Accepted", dayOfWeek = 4),
            MockSampleEvent("Late Evening APAC Sync", "Coordination call with Tokyo team", 21, 30, "Tue 9:30 PM", isBusy = true, rsvpStatus = "Accepted", dayOfWeek = 2),
            MockSampleEvent("Coffee Chat with Alex", "Informal 1-on-1 sync", 16, 30, "Fri 4:30 PM", isBusy = false, rsvpStatus = "Tentative", dayOfWeek = 5),
            MockSampleEvent("Confidential Client Pitch", "NDA protected business review", 13, 30, "Mon 1:30 PM", isBusy = true, rsvpStatus = "Accepted", dayOfWeek = 1)
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

            // Check Days
            if (!state.activeDays.contains(event.dayOfWeek)) reasons.add("Day of week filtered out")

            // Check All-Day Events
            if (event.isAllDay && !state.includeAllDayEvents) {
                reasons.add("All-day events excluded")
            }

            // Check Time Window (Timed events only; all-day events bypass hour window if included)
            if (state.enableTimeFilter && !event.isAllDay) {
                val eventMins = event.startHour * 60 + event.startMinute
                val fromMins = state.fromHour * 60 + state.fromMinute
                val toMins = state.toHour * 60 + state.toMinute
                if (eventMins < fromMins || eventMins > toMins) {
                    reasons.add("Outside time window (${formatTime(state.fromHour, state.fromMinute)} – ${formatTime(state.toHour, state.toMinute)})")
                }
            }

            // Check Title Contains
            val titleInc = state.titleContains.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (titleInc.isNotEmpty()) {
                val matches = if (state.titleContainsMatchAll) {
                    titleInc.all { event.title.lowercase().contains(it) }
                } else {
                    titleInc.any { event.title.lowercase().contains(it) }
                }
                if (!matches) {
                    reasons.add(if (state.titleContainsMatchAll) "Title doesn't contain all required keywords" else "Title doesn't contain any required keywords")
                }
            }

            // Check Title Doesn't Contain
            val titleExc = state.titleDoesNotContain.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (titleExc.isNotEmpty()) {
                val matchesExcluded = if (state.titleDoesNotContainMatchAll) {
                    titleExc.all { event.title.lowercase().contains(it) }
                } else {
                    titleExc.any { event.title.lowercase().contains(it) }
                }
                if (matchesExcluded) {
                    reasons.add(if (state.titleDoesNotContainMatchAll) "Title contains all excluded keywords" else "Title contains excluded keyword")
                }
            }

            // Check Description Contains
            val descInc = state.descriptionContains.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (descInc.isNotEmpty()) {
                val matches = if (state.descriptionContainsMatchAll) {
                    descInc.all { event.description.lowercase().contains(it) }
                } else {
                    descInc.any { event.description.lowercase().contains(it) }
                }
                if (!matches) {
                    reasons.add(if (state.descriptionContainsMatchAll) "Description doesn't contain all required keywords" else "Description doesn't contain any required keywords")
                }
            }

            // Check Description Doesn't Contain
            val descExc = state.descriptionDoesNotContain.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (descExc.isNotEmpty()) {
                val matchesExcluded = if (state.descriptionDoesNotContainMatchAll) {
                    descExc.all { event.description.lowercase().contains(it) }
                } else {
                    descExc.any { event.description.lowercase().contains(it) }
                }
                if (matchesExcluded) {
                    reasons.add(if (state.descriptionDoesNotContainMatchAll) "Description contains all excluded keywords" else "Description contains excluded keyword")
                }
            }

            val isMatched = if (!state.isEnabled && state.activeRuleCount == 0) true else reasons.isEmpty()
            event to (isMatched to reasons.joinToString(", "))
        }
    }

    val matchedCount = evaluatedEvents.count { it.second.first }
    val totalCount = evaluatedEvents.size

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            when {
                validationErrorMessage != null -> MaterialTheme.colorScheme.error
                state.isEnabled -> TitaniumMint.Mint500.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── 1. Header Bar with Master Enable Switch ─────────────────────
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
                        color = when {
                            state.isEnabled -> TitaniumMint.Mint500.copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = if (state.isEnabled) TitaniumMint.Mint400 else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Master Enable Switch with Error Guardrail
                    Switch(
                        checked = state.isEnabled,
                        onCheckedChange = { targetState ->
                            if (targetState) {
                                // User attempting to enable: check validation
                                val error = state.validationError
                                if (error != null) {
                                    validationErrorMessage = error
                                    // Expand card so user sees what needs fixing
                                    updateState { it.copy(isExpanded = true) }
                                } else {
                                    validationErrorMessage = null
                                    updateState { it.copy(isEnabled = true) }
                                }
                            } else {
                                validationErrorMessage = null
                                updateState { it.copy(isEnabled = false) }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TitaniumMint.CarbonOnyx,
                            checkedTrackColor = TitaniumMint.Mint500,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    IconButton(onClick = { updateState { it.copy(isExpanded = !it.isExpanded) } }) {
                        Icon(
                            imageVector = if (state.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (state.isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Error Alert Banner (When Enable Attempt Fails) ────────────
            if (validationErrorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cannot Enable Filter",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = validationErrorMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // ── 2. Expandable Criteria Sections ───────────────────────────
            AnimatedVisibility(
                visible = state.isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (state.isEnabled) 1.0f else 0.75f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Divider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        thickness = 0.5.dp
                    )

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
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                FilterCheckboxRow(
                                    label = "Busy",
                                    checked = state.allowBusy,
                                    onCheckedChange = { updateState { s -> s.copy(allowBusy = it) } }
                                )
                                FilterCheckboxRow(
                                    label = "Free",
                                    checked = state.allowFree,
                                    onCheckedChange = { updateState { s -> s.copy(allowFree = it) } }
                                )
                                FilterCheckboxRow(
                                    label = "Tentative",
                                    checked = state.allowTentative,
                                    onCheckedChange = { updateState { s -> s.copy(allowTentative = it) } }
                                )
                                FilterCheckboxRow(
                                    label = "Empty",
                                    checked = state.allowEmpty,
                                    onCheckedChange = { updateState { s -> s.copy(allowEmpty = it) } }
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

                    // Section B: Time Window & Days of Week
                    RuleSectionCard(
                        icon = Icons.Default.Schedule,
                        title = "Active Schedule Window & Days"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Include All-Day Events Checkbox (selected by default)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { updateState { it.copy(includeAllDayEvents = !it.includeAllDayEvents) } },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Include All-Day Events", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = if (state.includeAllDayEvents) "All-day and multi-day events will be synced" else "Skip all-day and multi-day events",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Checkbox(
                                    checked = state.includeAllDayEvents,
                                    onCheckedChange = { updateState { s -> s.copy(includeAllDayEvents = it) } }
                                )
                            }

                            Divider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                thickness = 0.5.dp
                            )

                            // Enable Time Window Toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { updateState { it.copy(enableTimeFilter = !it.enableTimeFilter) } },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Filter by Hours of the Day", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = if (state.enableTimeFilter) "Only sync events starting within this time range" else "Any time of day (All day)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Checkbox(
                                    checked = state.enableTimeFilter,
                                    onCheckedChange = { updateState { s -> s.copy(enableTimeFilter = it) } }
                                )
                            }

                            // User-Selectable From & To Times
                            if (state.enableTimeFilter) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // From Time Chip
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                isPickingFromTime = true
                                                showTimePickerDialog = true
                                            }
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("FROM TIME", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(Icons.Default.AccessTime, contentDescription = null, tint = TitaniumMint.Mint400, modifier = Modifier.size(16.dp))
                                                Text(formatTime(state.fromHour, state.fromMinute), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    // To Time Chip
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                isPickingFromTime = false
                                                showTimePickerDialog = true
                                            }
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("TO TIME", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(Icons.Default.AccessTime, contentDescription = null, tint = TitaniumMint.Mint400, modifier = Modifier.size(16.dp))
                                                Text(formatTime(state.toHour, state.toMinute), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                // In-flight warning if From >= To
                                val fromMins = state.fromHour * 60 + state.fromMinute
                                val toMins = state.toHour * 60 + state.toMinute
                                if (fromMins >= toMins) {
                                    Text(
                                        text = "⚠️ From time must be earlier than To time.",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            // Day of week bubbles (M T W T F S S) with enhanced contrast
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
                                        color = if (isSelected) TitaniumMint.Mint500 else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) TitaniumMint.Mint400 else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier
                                            .size(36.dp)
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
                                                color = if (isSelected) Color(0xFF003824) else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section C: Text & Keyword Filtering (4 Explicit Text Boxes)
                    RuleSectionCard(
                        icon = Icons.Default.TextFields,
                        title = "Text & Keyword Filtering"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Instructional Note
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "💡 Separate multiple words or phrases with a comma (e.g. 'standup, team meeting, sync')\nClick to toggle between \"ANY\" and \"ALL\".",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }

                            // 1. Title Contains
                            KeywordInputField(
                                label = "Title Contains",
                                value = state.titleContains,
                                placeholder = "e.g. Meeting, Standup, Project",
                                isMatchAll = state.titleContainsMatchAll,
                                isExclude = false,
                                onMatchAllChange = { mode -> updateState { s -> s.copy(titleContainsMatchAll = mode) } },
                                onValueChange = { updateState { s -> s.copy(titleContains = it) } }
                            )

                            // 2. Title Doesn't Contain
                            KeywordInputField(
                                label = "Title Doesn't Contain",
                                value = state.titleDoesNotContain,
                                placeholder = "e.g. Private, Personal, Doctor",
                                isMatchAll = state.titleDoesNotContainMatchAll,
                                isExclude = true,
                                onMatchAllChange = { mode -> updateState { s -> s.copy(titleDoesNotContainMatchAll = mode) } },
                                onValueChange = { updateState { s -> s.copy(titleDoesNotContain = it) } }
                            )

                            // Subtle Divider between Title and Description Filter Fields
                            Divider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            // 3. Description Contains
                            KeywordInputField(
                                label = "Description Contains",
                                value = state.descriptionContains,
                                placeholder = "e.g. Zoom, Google Meet, Urgent",
                                isMatchAll = state.descriptionContainsMatchAll,
                                isExclude = false,
                                onMatchAllChange = { mode -> updateState { s -> s.copy(descriptionContainsMatchAll = mode) } },
                                onValueChange = { updateState { s -> s.copy(descriptionContains = it) } }
                            )

                            // 4. Description Doesn't Contain
                            KeywordInputField(
                                label = "Description Doesn't Contain",
                                value = state.descriptionDoesNotContain,
                                placeholder = "e.g. Confidential, Draft, Ignore",
                                isMatchAll = state.descriptionDoesNotContainMatchAll,
                                isExclude = true,
                                onMatchAllChange = { mode -> updateState { s -> s.copy(descriptionDoesNotContainMatchAll = mode) } },
                                onValueChange = { updateState { s -> s.copy(descriptionDoesNotContain = it) } }
                            )
                        }
                    }

                    // ── 3. Live Mock Preview Banner (Only Shown When Section is Expanded) ──
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
                                        text = if (!state.isEnabled) "Filter is OFF (All events sync). Tap to test rules." else "${totalCount - matchedCount} events would be filtered out. Tap to inspect.",
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
        }
    }

    // ── 4. Time Picker Dialog (User Selectable to Any Time) ───────────────
    if (showTimePickerDialog) {
        val currentHour = if (isPickingFromTime) state.fromHour else state.toHour
        val currentMinute = if (isPickingFromTime) state.fromMinute else state.toMinute

        var selectedHour12 by remember { mutableStateOf(if (currentHour == 0) 12 else if (currentHour > 12) currentHour - 12 else currentHour) }
        var selectedMinute by remember { mutableStateOf(currentMinute) }
        var isAm by remember { mutableStateOf(currentHour < 12) }

        var hourMenuExpanded by remember { mutableStateOf(false) }
        var minuteMenuExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showTimePickerDialog = false },
            title = {
                Text(
                    text = if (isPickingFromTime) "Set 'From' Time" else "Set 'To' Time",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Select any time of day:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Hour Selector Box
                        Box {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.clickable { hourMenuExpanded = true }
                            ) {
                                Text(
                                    text = selectedHour12.toString().padStart(2, '0'),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = hourMenuExpanded,
                                onDismissRequest = { hourMenuExpanded = false }
                            ) {
                                (1..12).forEach { h ->
                                    DropdownMenuItem(
                                        text = { Text(h.toString().padStart(2, '0')) },
                                        onClick = {
                                            selectedHour12 = h
                                            hourMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Text(":", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

                        // Minute Selector Box
                        Box {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.clickable { minuteMenuExpanded = true }
                            ) {
                                Text(
                                    text = selectedMinute.toString().padStart(2, '0'),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = minuteMenuExpanded,
                                onDismissRequest = { minuteMenuExpanded = false }
                            ) {
                                listOf(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55).forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(m.toString().padStart(2, '0')) },
                                        onClick = {
                                            selectedMinute = m
                                            minuteMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // AM / PM Toggle
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isAm) TitaniumMint.Mint500 else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { isAm = true }
                            ) {
                                Text(
                                    text = "AM",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (isAm) Color(0xFF003824) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (!isAm) TitaniumMint.Mint500 else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { isAm = false }
                            ) {
                                Text(
                                    text = "PM",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (!isAm) Color(0xFF003824) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val computed24Hour = when {
                            isAm && selectedHour12 == 12 -> 0
                            !isAm && selectedHour12 < 12 -> selectedHour12 + 12
                            else -> selectedHour12
                        }

                        if (isPickingFromTime) {
                            updateState { it.copy(fromHour = computed24Hour, fromMinute = selectedMinute) }
                        } else {
                            updateState { it.copy(toHour = computed24Hour, toMinute = selectedMinute) }
                        }
                        showTimePickerDialog = false
                    }
                ) {
                    Text("Done", color = TitaniumMint.Mint400, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
                                text = "Testing against sample calendar events. Results recalculate live as you edit criteria.",
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
                                    if (event.description.isNotEmpty()) {
                                        Text(
                                            text = "Note: ${event.description}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            maxLines = 1
                                        )
                                    }
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
private fun KeywordInputField(
    label: String,
    value: String,
    placeholder: String,
    isMatchAll: Boolean = false,
    isExclude: Boolean = false,
    onMatchAllChange: (Boolean) -> Unit = {},
    onValueChange: (String) -> Unit
) {
    val words = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val explainer = when {
        words.isEmpty() -> {
            if (isExclude) {
                if (isMatchAll) "Skips only if text contains ALL entered words"
                else "Skips if text contains ANY entered word"
            } else {
                if (isMatchAll) "Matches only if text contains ALL entered words"
                else "Matches if text contains ANY entered word"
            }
        }
        words.size == 1 -> {
            if (isExclude) "Skips if text contains '${words.first()}'"
            else "Matches if text contains '${words.first()}'"
        }
        else -> {
            val formattedList = words.joinToString(if (isMatchAll) " AND " else " OR ") { "'$it'" }
            if (isExclude) {
                if (isMatchAll) "Skips only if text contains ALL: $formattedList"
                else "Skips if text contains ANY: $formattedList"
            } else {
                if (isMatchAll) "Matches only if text contains ALL: $formattedList"
                else "Matches if text contains ANY: $formattedList"
            }
        }
    }

    var showModeTooltip by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label.uppercase(),
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Split Input Row: Solid green toggle button on left + OutlinedTextField on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Left-Side Solid Green Toggle Button (Split button style)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = TitaniumMint.Mint500,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        onMatchAllChange(!isMatchAll)
                        showModeTooltip = false
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (isMatchAll) "ALL" else "ANY",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TitaniumMint.CarbonOnyx
                    )
                }
            }

            // Text Input Field (Takes remaining width)
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder, fontSize = 13.sp) },
                trailingIcon = {
                    if (value.isNotEmpty()) {
                        IconButton(onClick = { onValueChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TitaniumMint.Mint400,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.weight(1f)
            )
        }

        // Dismissible Tooltip Helper
        if (showModeTooltip) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(0.5.dp, TitaniumMint.Mint400.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "💡 Tap the green ANY/ALL button to toggle match logic.",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.5.sp,
                        color = TitaniumMint.Mint400,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { showModeTooltip = false },
                        modifier = Modifier.size(18.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(12.dp))
                    }
                }
            }
        }

        // Dynamic micro-explainer text
        Text(
            text = explainer,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = if (words.isNotEmpty()) TitaniumMint.Mint400.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 2.dp)
        )
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
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
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
