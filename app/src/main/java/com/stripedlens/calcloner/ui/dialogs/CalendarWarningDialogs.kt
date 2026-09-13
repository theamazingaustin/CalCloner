package com.stripedlens.calcloner.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.stripedlens.calcloner.CalendarInfo
import com.stripedlens.calcloner.ui.components.SwipeToConfirmSlider
import kotlinx.coroutines.delay

/**
 * Target Calendar Warning Dialog
 * Shows event count (green if empty, warning red if populated)
 * 5-second lock + swipe slider to confirm.
 */
@Composable
fun TargetCalendarWarningDialog(
    calendar: CalendarInfo,
    eventCount: Int,
    onDismiss: () -> Unit,
    onConfirmSelection: () -> Unit
) {
    var countdown by remember { mutableIntStateOf(5) }

    LaunchedEffect(Unit) {
        countdown = 5
        while (countdown > 0) {
            delay(1000L)
            countdown--
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Target Calendar Warning",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "Selected Clone Calendar:\n\"${calendar.displayName}\" (${calendar.accountName})",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                // Event count status card
                if (eventCount == 0) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)) // Light green
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Empty",
                                tint = Color(0xFF166534),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "This calendar appears to be empty.",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF166534),
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)) // Light red
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Warning",
                                    tint = Color(0xFF991B1B),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "This calendar currently has $eventCount event(s)!",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF991B1B),
                                    fontSize = 13.sp
                                )
                            }
                            Text(
                                text = "It is strongly recommended to use a BLANK calendar so if anything goes wrong the calendar can simply be deleted without touching other events.",
                                color = Color(0xFF7F1D1D),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Text(
                    text = "What CalCloner will do:\n• Replicates events from your source calendar into this clone calendar.\n• Cloned events will be inserted and kept up-to-date.\n• CRITICAL: The delete button wipes ALL events in this calendar (not just cloned events).",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "SAFEGUARD: The SOURCE calendar is strictly read-only and will NEVER be altered.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Swipe slider with 5s countdown
                SwipeToConfirmSlider(
                    text = "Slide to Select Target ->",
                    lockSeconds = countdown,
                    accentColor = MaterialTheme.colorScheme.primary,
                    onConfirmed = onConfirmSelection
                )

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDismiss
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

/**
 * Delete (Clear) Clone Events Dialog
 * Shows event count to be deleted
 * 5-second lock + swipe slider to confirm deletion.
 */
@Composable
fun ClearCloneEventsDialog(
    calendar: CalendarInfo,
    eventCount: Int,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    var countdown by remember { mutableIntStateOf(5) }

    LaunchedEffect(Unit) {
        countdown = 5
        while (countdown > 0) {
            delay(1000L)
            countdown--
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Delete ALL Events from Calendar?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Text(
                    text = "Target Calendar: \"${calendar.displayName}\"",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "⚠️ WARNING: PERMANENTLY ERASES ALL EVENTS",
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (eventCount > 0)
                                "This will delete ALL $eventCount event(s) currently in \"${calendar.displayName}\".\n\nThis wipes EVERY event in this calendar — including any personal, preexisting, or manually created events, NOT just events cloned by this app."
                            else
                                "This will delete ALL events inside \"${calendar.displayName}\" (currently appears to have 0 events).",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "SAFEGUARD GUARANTEE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "The SOURCE calendar will NEVER be edited, modified, or deleted.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // Swipe slider with 5s countdown
                SwipeToConfirmSlider(
                    text = "Slide to Delete ALL Events ->",
                    lockSeconds = countdown,
                    accentColor = MaterialTheme.colorScheme.error,
                    onConfirmed = onConfirmDelete
                )

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDismiss
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

/**
 * Nuke Confirmation Dialog:
 * Forces Google Cloud downstream sync before wiping 100% of events from selected calendar.
 * 5-second lock + swipe slider to confirm.
 */
@Composable
fun NukeCalendarEventsDialog(
    calendar: CalendarInfo,
    onDismiss: () -> Unit,
    onConfirmNuke: () -> Unit
) {
    var countdown by remember { mutableIntStateOf(5) }

    LaunchedEffect(Unit) {
        countdown = 5
        while (countdown > 0) {
            delay(1000L)
            countdown--
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Nuke",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NUKE & Deep Clean Calendar?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Text(
                    text = "Target Calendar: \"${calendar.displayName}\"",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "☢️ COMPLETE 3-STEP CLOUD & LOCAL PURGE",
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "1. Forces Google Cloud to download all orphaned/cloud events to this phone.\n" +
                                   "2. Deletes 100% of ALL events in \"${calendar.displayName}\" (both local & newly downloaded).\n" +
                                   "3. Pushes deletion tombstones to Google Cloud to permanently wipe them.\n\n" +
                                   "⚠️ This wipes EVERY event in this calendar, including any personal or manually created events. Source calendar will NOT be touched.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                SwipeToConfirmSlider(
                    text = "Slide to NUKE ALL Events ->",
                    lockSeconds = countdown,
                    accentColor = MaterialTheme.colorScheme.error,
                    onConfirmed = onConfirmNuke
                )

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDismiss
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}
