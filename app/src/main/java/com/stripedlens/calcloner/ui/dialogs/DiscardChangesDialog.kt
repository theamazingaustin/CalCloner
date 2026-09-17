package com.stripedlens.calcloner.ui.dialogs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.stripedlens.calcloner.ui.theme.TitaniumMint
import com.stripedlens.calcloner.ui.theme.UiDimensions

/**
 * Confirmation dialog shown when the user attempts to dismiss a dirty edit sheet.
 */
@Composable
fun DiscardChangesDialog(
    onConfirmDiscard: () -> Unit,
    onDismiss: () -> Unit
) {
    ApplyDialogBlurEffect()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(UiDimensions.DialogWidthFraction),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        shape = RoundedCornerShape(UiDimensions.DialogCornerRadius),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = UiDimensions.DialogGlassAlpha),
        tonalElevation = 6.dp,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = TitaniumMint.Amber400
            )
        },
        title = {
            Text(
                text = "Discard Changes?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "You have unsaved changes. Discarding will revert to previous settings and return to the main screen.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmDiscard,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Discard", color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep Editing")
            }
        }
    )
}
