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
import com.stripedlens.calcloner.ui.components.ScreenHeaderBanner
import com.stripedlens.calcloner.ui.theme.Palette
import com.stripedlens.calcloner.ui.theme.TitaniumMint
import com.stripedlens.calcloner.viewmodel.MainUiState

/**
 * Dedicated screen for ad-hoc, one-time calendar operations.
 *
 * Hosts tools for one-off actions that do not require ongoing background sync:
 * - Delete Calendar Events (purge cloned, clear, or wipe)
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
    onOpenDeleteScreen: () -> Unit = {},
    onOpenCopyScreen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Full-Width Screen Header ──────────────────────────────────────────────
        ScreenHeaderBanner(
            icon = Icons.Default.FlashOn,
            title = "One-Time Operations",
            subtitle = "AD-HOC TOOLS & UTILITIES",
            description = "Perform single, immediate actions without configuring recurring background synchronization pairs. Copy events, run targeted batch deletions, export/import .ics files, or clean duplicates.",
            iconTint = TitaniumMint.Mint400,
            subtitleColor = TitaniumMint.Mint400
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Functioning Features (Listed First) ───────────────────────────────

            OneTimeFeatureCard(
                title = "Delete Calendar Events",
                badge = "AVAILABLE",
                badgeColor = TitaniumMint.Mint400,
                badgeBg = TitaniumMint.Mint500.copy(alpha = 0.15f),
                badgeBorder = TitaniumMint.Mint500.copy(alpha = 0.35f),
                description = "Purge cloned sync events, clear calendar contents, or wipe all events with double-confirmation safety phrases.",
                icon = Icons.Default.DeleteSweep,
                iconColor = TitaniumMint.Mint400,
                features = listOf(
                    "Purge cloned events created by CalCloner without affecting originals",
                    "Clear all events or wipe calendar with typed phrase verification",
                    "Purge orphaned tombstone records to optimize device calendar storage"
                ),
                actionButtonText = "Open Deletion Tool",
                onActionClick = onOpenDeleteScreen
            )

            OneTimeFeatureCard(
                title = "One-Time Event Copy & Move",
                badge = "AVAILABLE",
                badgeColor = TitaniumMint.Mint400,
                badgeBg = TitaniumMint.Mint500.copy(alpha = 0.15f),
                badgeBorder = TitaniumMint.Mint500.copy(alpha = 0.35f),
                description = "Transfer events on demand between any two calendars within a custom date window with strict source calendar protection.",
                icon = Icons.Default.ContentCopy,
                iconColor = TitaniumMint.Mint400,
                features = listOf(
                    "Safe additive copy: source calendar is strictly read-only and never modified",
                    "Cloned events are stamped with CalCloner tags for easy identification and deletion",
                    "Move (cut & paste) is staged in the UI and will unlock once backups are available"
                ),
                actionButtonText = "Open Copy & Move Tool",
                onActionClick = onOpenCopyScreen
            )

            // ── Coming Soon Features ──────────────────────────────────────────────

            OneTimeFeatureCard(
                title = "Filtered Event Deletion",
                badge = "COMING SOON",
                badgeColor = TitaniumMint.Amber400,
                badgeBg = TitaniumMint.Amber500.copy(alpha = 0.15f),
                badgeBorder = TitaniumMint.Amber500.copy(alpha = 0.35f),
                description = "Delete specific events from a calendar matching title regex, keywords, RSVP response, or time frames without wiping the entire calendar.",
                icon = Icons.Default.FilterListOff,
                iconColor = TitaniumMint.Mint400,
                features = listOf(
                    "Regex & substring keyword matching across title and description",
                    "Filter by RSVP status (e.g. purge only declined invitations)",
                    "Safe dry-run preview showing exactly how many events match"
                )
            )

            OneTimeFeatureCard(
                title = "ICS File Import & Export",
                badge = "COMING SOON",
                badgeColor = TitaniumMint.Amber400,
                badgeBg = TitaniumMint.Amber500.copy(alpha = 0.15f),
                badgeBorder = TitaniumMint.Amber500.copy(alpha = 0.35f),
                description = "Export any calendar to a standard .ics iCalendar file for backup or cross-app sharing, or import external .ics calendar files directly.",
                icon = Icons.Default.ImportExport,
                iconColor = TitaniumMint.Mint400,
                features = listOf(
                    "Export full calendar or filtered date ranges to .ics",
                    "Import .ics event files into any writable Android calendar",
                    "Conflict detection and duplicate prevention on import"
                )
            )

            OneTimeFeatureCard(
                title = "Calendar Event Deduplication",
                badge = "COMING SOON",
                badgeColor = TitaniumMint.Amber400,
                badgeBg = TitaniumMint.Amber500.copy(alpha = 0.15f),
                badgeBorder = TitaniumMint.Amber500.copy(alpha = 0.35f),
                description = "Scan any calendar for duplicate entries created by multi-device sync collisions or third-party calendar sync engines.",
                icon = Icons.Default.FindReplace,
                iconColor = TitaniumMint.Mint400,
                features = listOf(
                    "Identifies identical title, start/end time, and recurrence clones",
                    "Interactive review before pruning redundant copies",
                    "Preserves primary event and original provider IDs"
                )
            )

            OneTimeFeatureCard(
                title = "Calendar Migration Wizard",
                badge = "COMING SOON",
                badgeColor = TitaniumMint.Amber400,
                badgeBg = TitaniumMint.Amber500.copy(alpha = 0.15f),
                badgeBorder = TitaniumMint.Amber500.copy(alpha = 0.35f),
                description = "Full calendar transfer wizard to migrate all past and future events when switching to a new Google, Outlook, or CalDAV account.",
                icon = Icons.Default.MoveDown,
                iconColor = TitaniumMint.Mint400,
                features = listOf(
                    "Batch event transfer with progress bar and cancel safeguard",
                    "Remaps reminders, recurrences, and exceptions automatically",
                    "Verification report comparing source vs destination counts"
                )
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun OneTimeFeatureCard(
    title: String,
    badge: String,
    badgeColor: Color,
    badgeBg: Color,
    badgeBorder: Color,
    description: String,
    icon: ImageVector,
    iconColor: Color = TitaniumMint.Mint400,
    features: List<String>,
    actionButtonText: String? = null,
    onActionClick: (() -> Unit)? = null,
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
                        tint = iconColor,
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
                    color = badgeBg,
                    border = BorderStroke(1.dp, badgeBorder)
                ) {
                    Text(
                        text = badge,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = badgeColor,
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
                            color = iconColor,
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

            if (actionButtonText != null && onActionClick != null) {
                Spacer(modifier = Modifier.height(2.dp))
                FilledTonalButton(
                    onClick = onActionClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = TitaniumMint.Mint500.copy(alpha = 0.15f),
                        contentColor = TitaniumMint.Mint400
                    ),
                    border = BorderStroke(1.dp, TitaniumMint.Mint500.copy(alpha = 0.35f))
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = actionButtonText,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
