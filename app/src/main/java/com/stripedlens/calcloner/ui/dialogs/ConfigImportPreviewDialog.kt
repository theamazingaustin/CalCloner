package com.stripedlens.calcloner.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.stripedlens.calcloner.SyncPair
import com.stripedlens.calcloner.ui.theme.TitaniumMint
import com.stripedlens.calcloner.ui.theme.UiDimensions
import com.stripedlens.calcloner.viewmodel.ImportPairStatus
import com.stripedlens.calcloner.viewmodel.ImportPreviewState
import com.stripedlens.calcloner.viewmodel.ValidatedImportPair

/**
 * Preview dialog displayed before committing imported sync pairs.
 * Inspects incoming configurations against local calendars, displays remapping status,
 * and alerts users to any unresolved calendars before applying.
 */
@Composable
fun ConfigImportPreviewDialog(
    previewState: ImportPreviewState,
    onConfirmApply: () -> Unit,
    onDismiss: () -> Unit
) {
    ApplyDialogBlurEffect()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(UiDimensions.DialogWidthFraction)
            .heightIn(max = 580.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        shape = RoundedCornerShape(UiDimensions.DialogCornerRadius),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = UiDimensions.DialogGlassAlpha),
        tonalElevation = 6.dp,
        icon = {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = TitaniumMint.Mint500.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, TitaniumMint.Mint500.copy(alpha = 0.3f)),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = null,
                        tint = TitaniumMint.Mint400,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Import Configuration Preview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "CONFIG VALIDATION & REMAPPING",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TitaniumMint.Mint400,
                    letterSpacing = 0.6.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Summary Stat Badges Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatBadge(
                        label = "Total",
                        count = previewState.totalCount,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f)
                    )
                    StatBadge(
                        label = "Ready",
                        count = previewState.readyCount,
                        color = TitaniumMint.Mint400,
                        modifier = Modifier.weight(1f)
                    )
                    if (previewState.remappedCount > 0) {
                        StatBadge(
                            label = "Remapped",
                            count = previewState.remappedCount,
                            color = TitaniumMint.Amber400,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (previewState.unresolvedCount > 0) {
                        StatBadge(
                            label = "Missing",
                            count = previewState.unresolvedCount,
                            color = TitaniumMint.Rose400,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text(
                    text = if (previewState.unresolvedCount > 0) {
                        "Some calendars were not found on this device. Valid pairs can still be imported."
                    } else if (previewState.remappedCount > 0) {
                        "Calendar IDs were automatically remapped to matching calendars on this device."
                    } else {
                        "All sync pairs match local calendars exactly and are ready to import."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Scrollable List of Parsed Pairs
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(previewState.pairs) { item ->
                        ImportPairCard(item)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmApply,
                enabled = previewState.canApply,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TitaniumMint.Mint500,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                val validCount = previewState.readyCount + previewState.remappedCount
                val suffix = if (validCount != 1) "s" else ""
                Text(
                    text = "Apply $validCount Pair$suffix",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Cancel",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun StatBadge(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = color
            )
            Text(
                text = label,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = color.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun ImportPairCard(item: ValidatedImportPair) {
    val statusColor = when (item.status) {
        ImportPairStatus.READY -> TitaniumMint.Mint400
        ImportPairStatus.REMAPPED -> TitaniumMint.Amber400
        ImportPairStatus.UNRESOLVED -> TitaniumMint.Rose400
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.originalPair.nickname ?: "${item.originalPair.fromCalendarName} -> ${item.originalPair.toCalendarName}",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = when (item.status) {
                            ImportPairStatus.READY -> "READY"
                            ImportPairStatus.REMAPPED -> "REMAPPED"
                            ImportPairStatus.UNRESOLVED -> "MISSING"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.originalPair.fromCalendarName,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "->",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TitaniumMint.Mint400
                )
                Text(
                    text = item.originalPair.toCalendarName,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (item.issueDescription != null) {
                Text(
                    text = item.issueDescription,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = statusColor,
                    lineHeight = 13.sp
                )
            }
        }
    }
}
