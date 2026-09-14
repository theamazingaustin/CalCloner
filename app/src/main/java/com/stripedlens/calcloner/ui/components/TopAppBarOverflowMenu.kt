package com.stripedlens.calcloner.ui.components

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stripedlens.calcloner.ui.theme.TitaniumMint

/**
 * Top App Bar Overflow Options Menu providing quick access to:
 * - JSON configuration export (share sheet)
 * - JSON configuration import
 * - Device battery deep-sleep bypass settings
 */
@Composable
fun TopAppBarOverflowMenu(
    isIgnoringBatteryOptimizations: Boolean,
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
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DropdownMenu(
            expanded = showOptionsMenu,
            onDismissRequest = { showOptionsMenu = false }
        ) {
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
