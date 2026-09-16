package com.stripedlens.calcloner.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stripedlens.calcloner.ui.theme.TitaniumMint

/**
 * Universal background battery guidance banner warning users when the app is restricted/optimized,
 * preventing reliable automated background calendar synchronization.
 */
@Composable
fun BackgroundBatteryProtectionBanner(
    isIgnoringBatteryOptimizations: Boolean,
    onOpenBatterySettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSamsungDevice = remember { Build.MANUFACTURER.contains("samsung", ignoreCase = true) }
    var bannerDismissed by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = !isIgnoringBatteryOptimizations && !bannerDismissed,
        enter = fadeIn(tween(200)) + expandVertically(tween(250)),
        exit = fadeOut(tween(150)) + shrinkVertically(tween(200)),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
            ),
            border = BorderStroke(1.dp, TitaniumMint.Amber400.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = TitaniumMint.Amber400,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isSamsungDevice) "Samsung Background Protection" else "Background Sync Protection",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Top-right close button
                    IconButton(
                        onClick = { bannerDismissed = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Text(
                    text = if (isSamsungDevice) {
                        "Samsung One UI may put CalCloner into 'Deep Sleep' when unused, stopping automated background syncs. Set battery to 'Unrestricted' to prevent sleep."
                    } else {
                        "Android battery optimization may defer or pause CalCloner background syncs during sleep. Allow 'Unrestricted' battery to ensure continuous sync."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                Button(
                    onClick = onOpenBatterySettings,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TitaniumMint.Amber400,
                        contentColor = Color(0xFF09090B)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "Unrestrict Battery",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Backward compatibility alias for [BackgroundBatteryProtectionBanner].
 */
@Composable
fun SamsungBatteryBanner(
    isIgnoringBatteryOptimizations: Boolean,
    onOpenBatterySettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackgroundBatteryProtectionBanner(
        isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
        onOpenBatterySettings = onOpenBatterySettings,
        modifier = modifier
    )
}

/**
 * Status indicator row showing whether battery optimization is unrestricted or restricted/throttled.
 */
@Composable
fun BatteryOptimizationStatusCard(
    isIgnoringBatteryOptimizations: Boolean,
    syncIntervalMinutes: Int,
    onOpenBatterySettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !isIgnoringBatteryOptimizations) {
                onOpenBatterySettings()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isIgnoringBatteryOptimizations) {
                        TitaniumMint.Mint500.copy(alpha = 0.1f)
                    } else {
                        TitaniumMint.Amber500.copy(alpha = 0.12f)
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isIgnoringBatteryOptimizations) {
                                Icons.Default.CheckCircle
                            } else {
                                Icons.Default.BatteryAlert
                            },
                            contentDescription = null,
                            tint = if (isIgnoringBatteryOptimizations) {
                                TitaniumMint.Mint400
                            } else {
                                TitaniumMint.Amber400
                            },
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text(
                    text = "Battery Optimization",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (isIgnoringBatteryOptimizations) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = TitaniumMint.Mint500.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, TitaniumMint.Mint500.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = TitaniumMint.Mint400,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Unrestricted",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TitaniumMint.Mint400
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = TitaniumMint.Amber500.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, TitaniumMint.Amber500.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = TitaniumMint.Amber400,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Restricted (Tap to fix)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TitaniumMint.Amber400
                        )
                    }
                }
            }
        }
    }
}

