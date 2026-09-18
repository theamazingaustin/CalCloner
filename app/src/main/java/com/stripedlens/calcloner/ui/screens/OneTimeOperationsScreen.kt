package com.stripedlens.calcloner.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stripedlens.calcloner.ui.theme.Palette
import com.stripedlens.calcloner.ui.theme.TitaniumMint
import com.stripedlens.calcloner.viewmodel.MainUiState

/**
 * Dedicated screen for ad-hoc, one-time calendar operations.
 *
 * Hosts tools for one-off actions that do not require ongoing background sync:
 * - One-time event cloning / copying between calendars
 * - Filtered batch deletion (by regex, keyword, date range, or status)
 * - ICS file import & export
 * - Calendar deduplication & cleanup
 * - Account-to-account calendar migration
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun OneTimeOperationsScreen(
    uiState: MainUiState,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Header Card ──────────────────────────────────────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = TitaniumMint.Amber500.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, TitaniumMint.Amber500.copy(alpha = 0.3f)),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = null,
                                tint = TitaniumMint.Amber400,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "One-Time Operations",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "AD-HOC TOOLS & UTILITIES",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TitaniumMint.Amber400,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
                Text(
                    text = "Perform single, immediate actions without configuring recurring background synchronization pairs. Copy events, run targeted batch deletions, export/import .ics files, or clean duplicates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }

        // ── Operation Cards ──────────────────────────────────────────────────────

        OneTimeFeatureCard(
            title = "One-Time Event Copy & Move",
            badge = "COMING SOON",
            description = "Select a source and target calendar, choose a date window or pick specific events, and copy or move them immediately.",
            icon = Icons.Default.ContentCopy,
            accentColor = TitaniumMint.Mint400,
            features = listOf(
                "Copy single event or entire date range across any calendars",
                "Move (cut & paste) with automatic source event deletion",
                "Side-by-side match preview before executing"
            )
        )

        OneTimeFeatureCard(
            title = "Filtered Event Deletion",
            badge = "COMING SOON",
            description = "Delete specific events from a calendar matching title regex, keywords, RSVP response, or time frames without wiping the entire calendar.",
            icon = Icons.Default.FilterListOff,
            accentColor = TitaniumMint.Orange400,
            features = listOf(
                "Regex & substring keyword matching across title and description",
                "Filter by RSVP status (e.g. purge only declined invitations)",
                "Safe dry-run preview showing exactly how many events match"
            )
        )

        OneTimeFeatureCard(
            title = "ICS File Import & Export",
            badge = "COMING SOON",
            description = "Export any calendar to a standard .ics iCalendar file for backup or cross-app sharing, or import external .ics calendar files directly.",
            icon = Icons.Default.ImportExport,
            accentColor = TitaniumMint.Mint400,
            features = listOf(
                "Export full calendar or filtered date ranges to .ics",
                "Import .ics event files into any writable Android calendar",
                "Conflict detection and duplicate prevention on import"
            )
        )

        OneTimeFeatureCard(
            title = "Calendar Event Deduplication",
            badge = "COMING SOON",
            description = "Scan any calendar for duplicate entries created by multi-device sync collisions or third-party calendar sync engines.",
            icon = Icons.Default.FindReplace,
            accentColor = TitaniumMint.Amber400,
            features = listOf(
                "Identifies identical title, start/end time, and recurrence clones",
                "Interactive review before pruning redundant copies",
                "Preserves primary event and original provider IDs"
            )
        )

        OneTimeFeatureCard(
            title = "Calendar Migration Wizard",
            badge = "COMING SOON",
            description = "Full calendar transfer wizard to migrate all past and future events when switching to a new Google, Outlook, or CalDAV account.",
            icon = Icons.Default.MoveDown,
            accentColor = Palette.Zinc400,
            features = listOf(
                "Batch event transfer with progress bar and cancel safeguard",
                "Remaps reminders, recurrences, and exceptions automatically",
                "Verification report comparing source vs destination counts"
            )
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun OneTimeFeatureCard(
    title: String,
    badge: String,
    description: String,
    icon: ImageVector,
    accentColor: Color,
    features: List<String>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = TitaniumMint.Amber500.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, TitaniumMint.Amber500.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = badge,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = TitaniumMint.Amber400,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.5.dp)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                features.forEach { feature ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "•",
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = feature,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
