package com.stripedlens.calcloner.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.stripedlens.calcloner.CalendarInfo
import com.stripedlens.calcloner.ui.components.CalendarDropdown
import com.stripedlens.calcloner.ui.theme.TitaniumMint
import com.stripedlens.calcloner.viewmodel.DeleteOperationType
import com.stripedlens.calcloner.viewmodel.MainUiState

/**
 * Dedicated calendar maintenance and event deletion screen.
 *
 * Provides safe, intentional event deletion:
 * 1. Target calendar selection (filtered to writable calendars).
 * 2. Operation mode selection: Purge Cloned Events Only (safe) vs. Wipe All Events (complete wipe).
 * 3. Exact confirmation typing lock: requires the user to type "delete <calendar name>"
 *    before the deletion action can be triggered.
 */
@Composable
fun DeleteScreen(
    uiState: MainUiState,
    onCalendarSelected: (CalendarInfo?) -> Unit,
    onOperationTypeSelected: (DeleteOperationType) -> Unit,
    onConfirmationTextChanged: (String) -> Unit,
    onDeleteConfirmed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val selectedCalendar = uiState.selectedDeleteCalendar
    val requiredPhrase = selectedCalendar?.let { "delete ${it.displayName.trim()}" } ?: ""
    val isConfirmed = selectedCalendar != null &&
            requiredPhrase.isNotEmpty() &&
            uiState.deleteConfirmationText.trim().equals(requiredPhrase, ignoreCase = true)

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
                        color = TitaniumMint.Rose500.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, TitaniumMint.Rose500.copy(alpha = 0.3f)),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                tint = TitaniumMint.Rose400,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Delete Calendar Events",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "CALENDAR MAINTENANCE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TitaniumMint.Rose400,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
                Text(
                    text = "Select a calendar and an operation type. To prevent accidental data loss, deletions require typing the confirmation phrase before the action is unlocked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }

        // ── Step 1: Select Calendar ──────────────────────────────────────────────
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "STEP 1: SELECT TARGET CALENDAR",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TitaniumMint.Mint400,
                    letterSpacing = 0.5.sp
                )

                CalendarDropdown(
                    label = "Target Calendar",
                    calendars = uiState.availableCalendars,
                    selectedCalendar = selectedCalendar,
                    filterWritable = true,
                    onCalendarSelected = onCalendarSelected
                )

                if (selectedCalendar != null) {
                    Text(
                        text = "Account: ${selectedCalendar.accountName}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── Step 2: Select Operation Mode ────────────────────────────────────────
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "STEP 2: SELECT OPERATION TYPE",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TitaniumMint.Mint400,
                    letterSpacing = 0.5.sp
                )

                // Option A: Purge Cloned Events Only
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (uiState.deleteOperationType == DeleteOperationType.PURGE_CLONED) {
                        TitaniumMint.Mint500.copy(alpha = 0.08f)
                    } else {
                        MaterialTheme.colorScheme.background
                    },
                    border = BorderStroke(
                        1.dp,
                        if (uiState.deleteOperationType == DeleteOperationType.PURGE_CLONED) {
                            TitaniumMint.Mint500.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOperationTypeSelected(DeleteOperationType.PURGE_CLONED) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RadioButton(
                            selected = uiState.deleteOperationType == DeleteOperationType.PURGE_CLONED,
                            onClick = { onOperationTypeSelected(DeleteOperationType.PURGE_CLONED) },
                            colors = RadioButtonDefaults.colors(selectedColor = TitaniumMint.Mint400)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Purge Cloned Events Only",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Removes only events created and tagged by CalCloner. Organic and manually created events in this calendar are kept safe.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Option B: Wipe All Events
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (uiState.deleteOperationType == DeleteOperationType.WIPE_ALL) {
                        TitaniumMint.Rose500.copy(alpha = 0.08f)
                    } else {
                        MaterialTheme.colorScheme.background
                    },
                    border = BorderStroke(
                        1.dp,
                        if (uiState.deleteOperationType == DeleteOperationType.WIPE_ALL) {
                            TitaniumMint.Rose500.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOperationTypeSelected(DeleteOperationType.WIPE_ALL) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RadioButton(
                            selected = uiState.deleteOperationType == DeleteOperationType.WIPE_ALL,
                            onClick = { onOperationTypeSelected(DeleteOperationType.WIPE_ALL) },
                            colors = RadioButtonDefaults.colors(selectedColor = TitaniumMint.Rose400)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Wipe All Events",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TitaniumMint.Rose400
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = TitaniumMint.Rose500.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "DESTRUCTIVE",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TitaniumMint.Rose400,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Deletes 100% of all events from this calendar, including manually created events. Preserves cloud tombstones so deletions sync to remote servers.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // ── Step 3: Safety Confirmation Lock ─────────────────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                if (isConfirmed) TitaniumMint.Rose500.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isConfirmed) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (isConfirmed) TitaniumMint.Rose400 else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "STEP 3: CONFIRMATION LOCK",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isConfirmed) TitaniumMint.Rose400 else MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                }

                if (selectedCalendar == null) {
                    Text(
                        text = "Please select a calendar in Step 1 to unlock the confirmation field.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "To proceed, type the following phrase exactly as shown:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.background,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = requiredPhrase,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TitaniumMint.Rose400,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }

                        OutlinedTextField(
                            value = uiState.deleteConfirmationText,
                            onValueChange = onConfirmationTextChanged,
                            label = { Text("Type confirmation phrase") },
                            placeholder = { Text(requiredPhrase) },
                            singleLine = true,
                            enabled = !uiState.isOperating,
                            trailingIcon = {
                                if (isConfirmed) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Confirmed",
                                        tint = TitaniumMint.Mint400
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (isConfirmed) TitaniumMint.Mint400 else MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = if (isConfirmed) TitaniumMint.Mint400 else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (uiState.deleteConfirmationText.isNotEmpty() && !isConfirmed) {
                            Text(
                                text = "Phrase does not match yet.",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = TitaniumMint.Amber400,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = onDeleteConfirmed,
                        enabled = isConfirmed && !uiState.isOperating,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TitaniumMint.Rose500,
                            disabledContainerColor = TitaniumMint.Rose500.copy(alpha = 0.25f),
                            contentColor = Color.White,
                            disabledContentColor = Color.White.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = when (uiState.deleteOperationType) {
                                    DeleteOperationType.PURGE_CLONED -> "Purge Cloned Events"
                                    DeleteOperationType.WIPE_ALL -> "Permanently Delete All Events"
                                },
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // ── Step 4: Progress / Result Status Card ────────────────────────────────
        AnimatedVisibility(
            visible = uiState.isOperating || uiState.operationDone || uiState.progressStatusText.isNotBlank(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (uiState.progressStatusText.contains("failed", ignoreCase = true)) {
                        MaterialTheme.colorScheme.error
                    } else {
                        TitaniumMint.Mint500.copy(alpha = 0.5f)
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.isOperating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = TitaniumMint.Mint400
                            )
                        } else {
                            Icon(
                                imageVector = if (uiState.progressStatusText.contains("failed", ignoreCase = true)) {
                                    Icons.Default.ErrorOutline
                                } else {
                                    Icons.Default.CheckCircle
                                },
                                contentDescription = null,
                                tint = if (uiState.progressStatusText.contains("failed", ignoreCase = true)) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    TitaniumMint.Mint400
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Text(
                            text = if (uiState.isOperating) "OPERATION IN PROGRESS" else "OPERATION STATUS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Text(
                        text = uiState.progressStatusText,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
