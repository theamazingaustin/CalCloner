package com.stripedlens.calcloner.ui.components.sheets

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Top header banner for [AddEditSyncPairSheet] showing sheet title, subtitle,
 * dismiss trigger, and active/paused state switch.
 */
@Composable
fun SyncPairSheetHeader(
    isEditing: Boolean,
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = Color.White
    val subheadColor = Color.White.copy(alpha = 0.85f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            IconButton(
                onClick = onCloseClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = contentColor
                )
            }
            Column {
                Text(
                    text = if (isEditing) "Edit Sync Configuration" else "New Sync Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = if (isEditing) "Configure Route, Window & Filters" else "Map source calendar to target clone",
                    style = MaterialTheme.typography.labelSmall,
                    color = subheadColor,
                    fontSize = 11.sp
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (isEnabled) "Active" else "Paused",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Switch(
                checked = isEnabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier
                    .scale(0.75f)
                    .height(20.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color.White.copy(alpha = 0.45f),
                    uncheckedThumbColor = Color.White.copy(alpha = 0.8f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.25f)
                )
            )
        }
    }
}
