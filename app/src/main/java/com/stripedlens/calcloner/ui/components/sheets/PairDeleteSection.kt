package com.stripedlens.calcloner.ui.components.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stripedlens.calcloner.SyncPair
import com.stripedlens.calcloner.ui.theme.TitaniumMint

/**
 * Section in [AddEditSyncPairSheet] providing sync pair configuration removal controls.
 *
 * Dedicated strictly to sync pair configuration deletion.
 * Standalone calendar event wiping is managed in the dedicated Delete/Maintenance screen.
 */
@Composable
fun PairDeleteSection(
    pairToEdit: SyncPair?,
    onDeleteConfigClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (pairToEdit == null) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section Header
        Text(
            text = "DANGER ZONE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = TitaniumMint.Rose400,
            letterSpacing = 1.sp
        )

        // Delete Configuration Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, TitaniumMint.Rose500.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDeleteConfigClick() }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = TitaniumMint.Rose400,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Delete Sync Configuration",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TitaniumMint.Rose400
                        )
                        Text(
                            text = "Permanently remove this sync pair.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TitaniumMint.Rose400,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
