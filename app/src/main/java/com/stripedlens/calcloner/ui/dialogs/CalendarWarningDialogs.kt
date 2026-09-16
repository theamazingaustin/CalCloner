package com.stripedlens.calcloner.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.stripedlens.calcloner.ui.theme.TitaniumMint

/**
 * Educational Dialog for Source ("Read Only") and Target ("Writable Replica") calendar roles.
 */
@Composable
fun CalendarRoleInfoDialog(
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
