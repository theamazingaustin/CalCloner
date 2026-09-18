package com.stripedlens.calcloner.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stripedlens.calcloner.ui.theme.TitaniumMint
import com.stripedlens.calcloner.util.AppUpdateInfo

/**
 * Top App Bar Overflow Options Menu providing quick access to:
 * - In-app APK updates from GitHub
 * - JSON configuration export (share sheet)
 * - JSON configuration import
 * - Device battery deep-sleep bypass settings
 */
@Composable
fun TopAppBarOverflowMenu(
    isIgnoringBatteryOptimizations: Boolean,
    availableUpdate: AppUpdateInfo? = null,
    isDownloadingUpdate: Boolean = false,
    updateDownloadProgress: Float = 0f,
    onTriggerUpdate: () -> Unit = {},
    onExportConfig: () -> Unit,
    onImportConfig: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showOptionsMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(
            onClick = { showOptionsMenu = true }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (availableUpdate != null) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .align(Alignment.TopEnd)
                            .background(TitaniumMint.Mint400, CircleShape)
                    )
                }
            }
        }

        DropdownMenu(
            expanded = showOptionsMenu,
            onDismissRequest = { showOptionsMenu = false }
        ) {
            if (availableUpdate != null) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = if (isDownloadingUpdate) {
                                    "Downloading ${availableUpdate.tagName} (${(updateDownloadProgress * 100).toInt()}%)"
                                } else {
                                    "Update to ${availableUpdate.tagName}"
                                },
                                fontWeight = FontWeight.SemiBold,
                                color = TitaniumMint.Mint400
                            )
                            if (isDownloadingUpdate) {
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = updateDownloadProgress,
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                    color = TitaniumMint.Mint400,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = TitaniumMint.Mint400,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = {
                        if (!isDownloadingUpdate) {
                            showOptionsMenu = false
                            onTriggerUpdate()
                        }
                    }
                )
                Divider()
            }

            DropdownMenuItem(
                text = { Text("Export Configuration") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    showOptionsMenu = false
                    onExportConfig()
                }
            )

            DropdownMenuItem(
                text = { Text("Import Configuration") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    showOptionsMenu = false
                    onImportConfig()
                }
            )

            val isSamsung = Build.MANUFACTURER.contains("samsung", ignoreCase = true)
            if (isSamsung && !isIgnoringBatteryOptimizations) {
                Divider()
                DropdownMenuItem(
                    text = { Text("Disable Samsung Deep Sleep") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = TitaniumMint.Amber400,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = {
                        showOptionsMenu = false
                        onOpenBatterySettings()
                    }
                )
            }
        }
    }
}
