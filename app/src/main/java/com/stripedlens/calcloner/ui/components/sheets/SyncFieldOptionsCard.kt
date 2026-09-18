package com.stripedlens.calcloner.ui.components.sheets

import android.provider.CalendarContract
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stripedlens.calcloner.ui.theme.TitaniumMint

/**
 * Expandable accordion section for an individual calendar event field.
 */
@Composable
private fun FieldAccordionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    hasSubOptions: Boolean,
    isError: Boolean = false,
    accentColor: Color = TitaniumMint.Mint400,
    onCheckedChange: (Boolean) -> Unit,
    expandedContent: @Composable () -> Unit
) {
    val titleColor = when {
        isError -> MaterialTheme.colorScheme.error
        checked -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    }
    val subtitleColor = when {
        isError -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
        checked -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }
    val iconTint = when {
        isError -> MaterialTheme.colorScheme.error
        checked -> accentColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (hasSubOptions) {
                        onExpandToggle()
                    } else {
                        onCheckedChange(!checked)
                    }
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                        color = titleColor
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = subtitleColor,
                        fontSize = 11.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier.height(24.dp)
                )
                if (hasSubOptions) {
                    IconButton(
                        onClick = onExpandToggle,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse options" else "Expand options",
                            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        if (hasSubOptions) {
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 40.dp, end = 12.dp, top = 4.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    expandedContent()
                }
            }
        }
    }
}

/**
 * Selective event field synchronization card with per-field progressive disclosure accordions,
 * prefix/suffix formatting, fallback values, and real-time field validation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncFieldOptionsCard(
    syncTitle: Boolean,
    onSyncTitleChange: (Boolean) -> Unit,
    customTitle: String,
    onCustomTitleChange: (String) -> Unit,
    titlePrefix: String,
    onTitlePrefixChange: (String) -> Unit,
    titleSuffix: String,
    onTitleSuffixChange: (String) -> Unit,
    syncDescription: Boolean,
    onSyncDescriptionChange: (Boolean) -> Unit,
    customDescription: String,
    onCustomDescriptionChange: (String) -> Unit,
    descriptionPrefix: String,
    onDescriptionPrefixChange: (String) -> Unit,
    descriptionSuffix: String,
    onDescriptionSuffixChange: (String) -> Unit,
    syncLocation: Boolean,
    onSyncLocationChange: (Boolean) -> Unit,
    customLocation: String,
    onCustomLocationChange: (String) -> Unit,
    syncReminders: Boolean,
    onSyncRemindersChange: (Boolean) -> Unit,
    syncAvailability: Boolean,
    onSyncAvailabilityChange: (Boolean) -> Unit,
    customAvailability: Int?,
    onCustomAvailabilityChange: (Int?) -> Unit,
    syncStatus: Boolean,
    onSyncStatusChange: (Boolean) -> Unit,
    customStatus: Int? = null,
    onCustomStatusChange: (Int?) -> Unit = {},
    accentColor: Color = TitaniumMint.Mint400,
    modifier: Modifier = Modifier
) {
    var isCardExpanded by remember { mutableStateOf(false) }

    // Per-field accordion states
    var titleExpanded by remember { mutableStateOf(false) }
    var descriptionExpanded by remember { mutableStateOf(false) }
    var locationExpanded by remember { mutableStateOf(false) }
    var availabilityExpanded by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }

    // Real-time validation: Title is required if mirroring is turned off
    val isTitleInvalid = !syncTitle && customTitle.isBlank()

    val cardBorderColor = if (isTitleInvalid) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    val cardBgColor = if (isTitleInvalid) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardBgColor,
        border = BorderStroke(if (isTitleInvalid) 1.5.dp else 1.dp, cardBorderColor),
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
                    .clickable { isCardExpanded = !isCardExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isTitleInvalid) Icons.Default.ErrorOutline else Icons.Default.Tune,
                        contentDescription = null,
                        tint = if (isTitleInvalid) MaterialTheme.colorScheme.error else accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Selective Field Options",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isTitleInvalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                            if (isTitleInvalid) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error)
                                ) {
                                    Text(
                                        text = "INVALID",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (isTitleInvalid) {
                                "Missing required field. Sync is disabled until corrected."
                            } else {
                                "Control prefixes, notes, and what details mirror."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isTitleInvalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
                IconButton(onClick = { isCardExpanded = !isCardExpanded }) {
                    Icon(
                        imageVector = if (isCardExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isCardExpanded) "Collapse" else "Expand",
                        tint = if (isTitleInvalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expandable Content
            AnimatedVisibility(
                visible = isCardExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isTitleInvalid) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
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
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "A replacement title is required when title mirroring is disabled.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Divider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 0.5.dp
                    )

                    // 1. EVENT TITLE ACCORDION
                    FieldAccordionRow(
                        title = "Event Title",
                        subtitle = if (syncTitle) "Mirror name (optional prefix / suffix)" else "Custom replacement title",
                        icon = Icons.Default.Title,
                        checked = syncTitle,
                        isExpanded = titleExpanded || isTitleInvalid,
                        onExpandToggle = { titleExpanded = !titleExpanded },
                        hasSubOptions = true,
                        isError = isTitleInvalid,
                        accentColor = accentColor,
                        onCheckedChange = {
                            onSyncTitleChange(it)
                            if (!it && customTitle.isBlank()) {
                                onCustomTitleChange("Busy")
                            }
                        }
                    ) {
                        if (syncTitle) {
                            OutlinedTextField(
                                value = titlePrefix,
                                onValueChange = onTitlePrefixChange,
                                label = { Text("Prepend Prefix", fontSize = 12.sp) },
                                placeholder = { Text("e.g. [Personal] ", fontSize = 12.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = accentColor,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = titleSuffix,
                                onValueChange = onTitleSuffixChange,
                                label = { Text("Append Suffix", fontSize = 12.sp) },
                                placeholder = { Text("e.g. (Synced)", fontSize = 12.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = accentColor,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (titlePrefix.isNotEmpty() || titleSuffix.isNotEmpty()) {
                                Text(
                                    text = "Preview: ${titlePrefix}Doctor Appointment${titleSuffix}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = accentColor,
                                    fontSize = 11.sp
                                )
                            }
                        } else {
                            OutlinedTextField(
                                value = customTitle,
                                onValueChange = onCustomTitleChange,
                                isError = isTitleInvalid,
                                label = { Text("Replacement Title (Required)", fontSize = 12.sp) },
                                placeholder = { Text("e.g. Busy, Reserved, Unavailable", fontSize = 12.sp) },
                                supportingText = {
                                    if (isTitleInvalid) {
                                        Text(
                                            text = "A title is required to sync safely with Android calendar providers.",
                                            color = MaterialTheme.colorScheme.error,
                                            fontSize = 11.sp
                                        )
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = accentColor,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    errorBorderColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), thickness = 0.5.dp)

                    // 2. DESCRIPTION & NOTES ACCORDION
                    FieldAccordionRow(
                        title = "Description & Notes",
                        subtitle = if (syncDescription) "Mirror body (optional prepended / appended notes)" else "Custom description or none",
                        icon = Icons.Default.Description,
                        checked = syncDescription,
                        isExpanded = descriptionExpanded,
                        onExpandToggle = { descriptionExpanded = !descriptionExpanded },
                        hasSubOptions = true,
                        accentColor = accentColor,
                        onCheckedChange = onSyncDescriptionChange
                    ) {
                        if (syncDescription) {
                            OutlinedTextField(
                                value = descriptionPrefix,
                                onValueChange = onDescriptionPrefixChange,
                                label = { Text("Prepend Note", fontSize = 12.sp) },
                                placeholder = { Text("e.g. Synced from personal calendar", fontSize = 12.sp) },
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = accentColor,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = descriptionSuffix,
                                onValueChange = onDescriptionSuffixChange,
                                label = { Text("Append Note", fontSize = 12.sp) },
                                placeholder = { Text("e.g. Confidential schedule item", fontSize = 12.sp) },
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = accentColor,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            OutlinedTextField(
                                value = customDescription,
                                onValueChange = onCustomDescriptionChange,
                                label = { Text("Fixed Note (Optional)", fontSize = 12.sp) },
                                placeholder = { Text("e.g. Private calendar reservation", fontSize = 12.sp) },
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = accentColor,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Text(
                            text = "CalCloner internal sync tracking tags are automatically appended for deduplication.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), thickness = 0.5.dp)

                    // 3. LOCATION ACCORDION
                    FieldAccordionRow(
                        title = "Location",
                        subtitle = if (syncLocation) "Mirror event address or video links" else "Fixed location or omit",
                        icon = Icons.Default.LocationOn,
                        checked = syncLocation,
                        isExpanded = locationExpanded,
                        onExpandToggle = { locationExpanded = !locationExpanded },
                        hasSubOptions = !syncLocation,
                        accentColor = accentColor,
                        onCheckedChange = onSyncLocationChange
                    ) {
                        OutlinedTextField(
                            value = customLocation,
                            onValueChange = onCustomLocationChange,
                            label = { Text("Fixed Location (Optional)", fontSize = 12.sp) },
                            placeholder = { Text("e.g. Remote / Teleconference", fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), thickness = 0.5.dp)

                    // 4. AVAILABILITY ACCORDION
                    FieldAccordionRow(
                        title = "Availability (Free / Busy)",
                        subtitle = if (syncAvailability) "Mirror source availability status" else "Fixed availability override",
                        icon = Icons.Default.EventAvailable,
                        checked = syncAvailability,
                        isExpanded = availabilityExpanded,
                        onExpandToggle = { availabilityExpanded = !availabilityExpanded },
                        hasSubOptions = !syncAvailability,
                        accentColor = accentColor,
                        onCheckedChange = onSyncAvailabilityChange
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Choose default availability on target calendar:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = customAvailability == CalendarContract.Events.AVAILABILITY_BUSY,
                                    onClick = { onCustomAvailabilityChange(CalendarContract.Events.AVAILABILITY_BUSY) },
                                    label = { Text("Busy") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accentColor.copy(alpha = 0.2f),
                                        selectedLabelColor = accentColor
                                    )
                                )
                                FilterChip(
                                    selected = customAvailability == CalendarContract.Events.AVAILABILITY_FREE,
                                    onClick = { onCustomAvailabilityChange(CalendarContract.Events.AVAILABILITY_FREE) },
                                    label = { Text("Free") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accentColor.copy(alpha = 0.2f),
                                        selectedLabelColor = accentColor
                                    )
                                )
                                FilterChip(
                                    selected = customAvailability == null,
                                    onClick = { onCustomAvailabilityChange(null) },
                                    label = { Text("Empty / None") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accentColor.copy(alpha = 0.2f),
                                        selectedLabelColor = accentColor
                                    )
                                )
                            }
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), thickness = 0.5.dp)

                    // 5. REMINDERS (Simple Toggle)
                    FieldAccordionRow(
                        title = "Reminders & Notifications",
                        subtitle = "Mirror alarms, alert popups, and advance notifications",
                        icon = Icons.Default.Alarm,
                        checked = syncReminders,
                        isExpanded = false,
                        onExpandToggle = {},
                        hasSubOptions = false,
                        accentColor = accentColor,
                        onCheckedChange = onSyncRemindersChange
                    ) {}

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), thickness = 0.5.dp)

                    // 6. STATUS ACCORDION
                    FieldAccordionRow(
                        title = "Event Status",
                        subtitle = if (syncStatus) "Mirror Confirmed, Tentative, or Canceled status" else "Fixed status override",
                        icon = Icons.Default.CheckCircleOutline,
                        checked = syncStatus,
                        isExpanded = statusExpanded,
                        onExpandToggle = { statusExpanded = !statusExpanded },
                        hasSubOptions = !syncStatus,
                        accentColor = accentColor,
                        onCheckedChange = onSyncStatusChange
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Choose default status on target calendar:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val effectiveStatus = customStatus ?: CalendarContract.Events.STATUS_CONFIRMED
                                FilterChip(
                                    selected = effectiveStatus == CalendarContract.Events.STATUS_CONFIRMED,
                                    onClick = { onCustomStatusChange(CalendarContract.Events.STATUS_CONFIRMED) },
                                    label = { Text("Confirmed") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accentColor.copy(alpha = 0.2f),
                                        selectedLabelColor = accentColor
                                    )
                                )
                                FilterChip(
                                    selected = effectiveStatus == CalendarContract.Events.STATUS_TENTATIVE,
                                    onClick = { onCustomStatusChange(CalendarContract.Events.STATUS_TENTATIVE) },
                                    label = { Text("Tentative") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accentColor.copy(alpha = 0.2f),
                                        selectedLabelColor = accentColor
                                    )
                                )
                                FilterChip(
                                    selected = effectiveStatus == CalendarContract.Events.STATUS_CANCELED,
                                    onClick = { onCustomStatusChange(CalendarContract.Events.STATUS_CANCELED) },
                                    label = { Text("Canceled") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accentColor.copy(alpha = 0.2f),
                                        selectedLabelColor = accentColor
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

