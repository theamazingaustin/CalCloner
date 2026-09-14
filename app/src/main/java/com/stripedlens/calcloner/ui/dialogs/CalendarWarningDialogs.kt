package com.stripedlens.calcloner.ui.dialogs

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.stripedlens.calcloner.CalendarInfo
import com.stripedlens.calcloner.SyncPair
import com.stripedlens.calcloner.ui.components.SwipeToConfirmSlider
import com.stripedlens.calcloner.ui.theme.TitaniumMint
import kotlinx.coroutines.delay

/**
 * Educational Popover Dialog for Source ("Read Only") and Target ("Writable Replica")
 */
@Composable
fun CalendarInfoPopoverDialog(
    type: String, // "source" or "target"
    onDismiss: () -> Unit
) {
    val isSource = type == "source"
    val title = if (isSource) "Primary Account" else "Writable Replica"
    val subtitle = if (isSource) "SOURCE CALENDAR" else "TARGET CALENDAR"
    val icon = if (isSource) Icons.Default.DateRange else Icons.Default.Refresh
    val description = if (isSource) {
        "Events will be copied from this calendar. The source calendar and its events will never be altered, edited, or written to by CalCloner."
    } else {
        "This calendar will be populated with all events found in the source calendar. In case there is a bug or glitch, it is highly recommended to sync to a blank calendar, not a calendar mixed with other / manually created events."
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, TitaniumMint.Mint500.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = TitaniumMint.Mint500.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, TitaniumMint.Mint500.copy(alpha = 0.3f)),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = TitaniumMint.Mint400,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = subtitle,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TitaniumMint.Mint400,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Description Box
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.background,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                        lineHeight = 20.sp
                    )
                }

                // Got it Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TitaniumMint.Mint500)
                    ) {
                        Text(
                            text = "Got it",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF003824)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dedicated Delete Cloned Events Confirmation Modal (with 5-second countdown lock)
 */
@Composable
fun ClearPairClonedEventsDialog(
    targetCalendarName: String,
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
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, TitaniumMint.Rose500.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = TitaniumMint.Rose500.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, TitaniumMint.Rose500.copy(alpha = 0.4f)),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.EventBusy,
                                    contentDescription = null,
                                    tint = TitaniumMint.Rose400,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Delete Cloned Events?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "REPLICA MAINTENANCE",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TitaniumMint.Rose400,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Informative Target Notice
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.background,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Events will ONLY be deleted from target calendar:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = targetCalendarName,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }

                        // Re-clone warning
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = TitaniumMint.Amber500.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, TitaniumMint.Amber500.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = TitaniumMint.Amber400,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Note: These events will be cloned again on the next sync run if this pair is not disabled or paused.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TitaniumMint.Amber400,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Safety Countdown Lock Banner
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.background,
                    border = BorderStroke(1.dp, if (countdown > 0) MaterialTheme.colorScheme.outlineVariant else TitaniumMint.Mint500.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (countdown > 0) Icons.Default.HourglassTop else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (countdown > 0) MaterialTheme.colorScheme.onSurfaceVariant else TitaniumMint.Mint400,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (countdown > 0) "Please wait ${countdown}s to confirm..." else "Safety delay passed. Actions unlocked.",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = if (countdown > 0) "${countdown}s" else "Unlocked",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (countdown > 0) MaterialTheme.colorScheme.onSurfaceVariant else TitaniumMint.Mint400
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirmDelete,
                        enabled = countdown == 0,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TitaniumMint.Rose500,
                            disabledContainerColor = TitaniumMint.Rose500.copy(alpha = 0.25f)
                        )
                    ) {
                        Text(
                            text = "Delete Cloned Events",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (countdown == 0) Color.White else Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Destructive "Delete ALL Calendar Events" Modal (with Dual Swipe Sliders)
 */
@Composable
fun NukeAllTargetEventsDialog(
    calendar: CalendarInfo,
    onDismiss: () -> Unit,
    onConfirmNormalDelete: () -> Unit,
    onConfirmForceNuke: () -> Unit
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
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, TitaniumMint.Rose500.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = TitaniumMint.Rose500.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, TitaniumMint.Rose500.copy(alpha = 0.4f)),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = TitaniumMint.Rose400,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Delete All Calendar Events",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "TARGET CALENDAR WIPE",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TitaniumMint.Rose400,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Permanent Warning Notice
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = TitaniumMint.Rose500.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, TitaniumMint.Rose500.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "This action will permanently wipe ALL events directly from target calendar:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.background,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${calendar.displayName} (${calendar.accountName})",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                        Text(
                            text = "Source calendar will remain completely untouched.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TitaniumMint.Mint400,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // 5-Second Countdown Lock Banner
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.background,
                    border = BorderStroke(1.dp, if (countdown > 0) MaterialTheme.colorScheme.outlineVariant else TitaniumMint.Mint500.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (countdown > 0) Icons.Default.HourglassTop else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (countdown > 0) TitaniumMint.Amber400 else TitaniumMint.Mint400,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (countdown > 0) "Please review carefully (${countdown}s remaining)..." else "Safety delay passed. Actions unlocked.",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = if (countdown > 0) "${countdown}s" else "Unlocked",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (countdown > 0) TitaniumMint.Amber400 else TitaniumMint.Mint400
                        )
                    }
                }

                // Option A: Delete All Events (Normal Wipe)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.background,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Option A: Delete All Events",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Normal",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Cleanly delete ALL calendar events from the target calendar, not just the events created with this app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.5.sp
                        )
                        SwipeToConfirmSlider(
                            text = "Swipe to Delete ->",
                            lockSeconds = countdown,
                            accentColor = TitaniumMint.Rose500,
                            onConfirmed = onConfirmNormalDelete
                        )
                    }
                }

                // Option B: Force Delete All Events (Cloud Resync Wipe)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = TitaniumMint.Rose500.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, TitaniumMint.Rose500.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Option B: Force Delete All Events",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TitaniumMint.Rose400
                            )
                            Text(
                                text = "Deep Wipe",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TitaniumMint.Rose400
                            )
                        }
                        Text(
                            text = "A more involved 3-step process to download orphaned cloud events and permanently wipe ALL events from Google Cloud.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.5.sp
                        )
                        SwipeToConfirmSlider(
                            text = "Swipe to Force Delete ->",
                            lockSeconds = countdown,
                            accentColor = TitaniumMint.Rose400,
                            onConfirmed = onConfirmForceNuke
                        )
                    }
                }

                // Cancel Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

/**
 * Delete Sync Pair Dialog
 * Prompts user whether to keep cloned events on the target calendar or wipe them.
 */
@Composable
fun DeletePairDialog(
    pair: SyncPair,
    onDismiss: () -> Unit,
    onDeleteKeepEvents: () -> Unit,
    onDeleteAndClearEvents: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, TitaniumMint.Rose500.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Pair",
                        tint = TitaniumMint.Rose400,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Delete Sync Pair?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "You are deleting the sync pair between \"${pair.fromCalendarName}\" and \"${pair.toCalendarName}\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.background,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "• Keep Cloned Events: Sync stops, but cloned events remain on \"${pair.toCalendarName}\".",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "• Delete & Clear: Automatically deletes all events created by this pair from \"${pair.toCalendarName}\".",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    FilledTonalButton(
                        onClick = onDeleteKeepEvents,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Keep Events")
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = onDeleteAndClearEvents,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TitaniumMint.Rose500)
                    ) {
                        Text("Delete & Clear", color = Color.White)
                    }
                }
            }
        }
    }
}
