package com.stripedlens.calcloner.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stripedlens.calcloner.BuildConfig
import com.stripedlens.calcloner.R
import com.stripedlens.calcloner.ThemeMode
import com.stripedlens.calcloner.ui.theme.TitaniumMint

/**
 * Top application branding and utility bar for CalCloner.
 * Houses the logo, version indicator, theme switcher, and overflow action menu.
 */
@Composable
fun CalClonerTopBar(
    themeMode: ThemeMode,
    isIgnoringBatteryOptimizations: Boolean,
    onCycleTheme: () -> Unit,
    onExportConfig: () -> Unit,
    onImportConfig: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Branding & Version
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (onNavigateBack != null) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = TitaniumMint.Mint500,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_calcloner),
                                    contentDescription = "CalCloner Logo",
                                    tint = Color(0xFF003824),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                    Column {
                        Text(
                            text = "CalCloner",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "v${com.stripedlens.calcloner.BuildConfig.VERSION_NAME}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = TitaniumMint.Mint400
                        )
                    }
                }

                // Theme Switcher & Overflow Menu
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onCycleTheme) {
                        val (icon, desc) = when (themeMode) {
                            ThemeMode.AUTO -> Icons.Default.BrightnessAuto to "Auto Theme"
                            ThemeMode.DARK -> Icons.Default.Brightness4 to "Dark Theme"
                            ThemeMode.LIGHT -> Icons.Default.BrightnessHigh to "Light Theme"
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = desc,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TopAppBarOverflowMenu(
                        isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                        onExportConfig = onExportConfig,
                        onImportConfig = onImportConfig,
                        onOpenBatterySettings = onOpenBatterySettings
                    )
                }
            }
        }
    }
}
