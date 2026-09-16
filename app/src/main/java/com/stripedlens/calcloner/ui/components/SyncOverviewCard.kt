package com.stripedlens.calcloner.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stripedlens.calcloner.SyncPair
import com.stripedlens.calcloner.ui.theme.TitaniumMint
import kotlinx.coroutines.launch

/**
 * Master Sync Card displaying the global active/paused toggle for all sync pairs
 * and the primary "Sync All" action button with reactive glow and pulse feedback.
 */
@Composable
fun MasterSyncCard(
    syncPairs: List<SyncPair>,
    isSyncingAll: Boolean,
    isOperating: Boolean,
    spinAngle: Float,
    onToggleAll: (Boolean) -> Unit,
    onSyncAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val anyEnabled = syncPairs.any { it.isEnabled }

    // Button feedback animations for Sync All
    val syncAllPulseAnim = remember { Animatable(1f) }
    val syncAllGlowAnim = remember { Animatable(0f) }
    var prevSyncingAll by remember { mutableStateOf(false) }

    LaunchedEffect(isSyncingAll) {
        if (prevSyncingAll && !isSyncingAll) {
            launch {
                syncAllPulseAnim.animateTo(1.05f, tween(180, easing = FastOutSlowInEasing))
                syncAllPulseAnim.animateTo(1.0f, tween(250, easing = LinearOutSlowInEasing))
            }
            launch {
                syncAllGlowAnim.snapTo(1f)
                syncAllGlowAnim.animateTo(0f, tween(1500, easing = FastOutSlowInEasing))
            }
        }
        prevSyncingAll = isSyncingAll
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = TitaniumMint.Mint500.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, TitaniumMint.Mint500.copy(alpha = 0.25f)),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = TitaniumMint.Mint400,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "Master Sync",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (anyEnabled) "${syncPairs.count { it.isEnabled }} of ${syncPairs.size} pairs active" else "All pairs paused",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
            Switch(
                checked = anyEnabled,
                enabled = syncPairs.isNotEmpty(),
                onCheckedChange = onToggleAll,
                modifier = Modifier
                    .scale(0.85f)
                    .height(24.dp)
            )
        }

        val syncAllBorderColor = androidx.compose.ui.graphics.lerp(
            TitaniumMint.Mint500.copy(alpha = 0.3f),
            TitaniumMint.Mint400,
            syncAllGlowAnim.value
        )
        val syncAllBgColor = androidx.compose.ui.graphics.lerp(
            TitaniumMint.Mint500.copy(alpha = 0.12f),
            TitaniumMint.Mint500.copy(alpha = 0.28f),
            syncAllGlowAnim.value
        )

        FilledTonalButton(
            onClick = onSyncAll,
            enabled = anyEnabled && !isOperating,
            modifier = Modifier
                .fillMaxWidth()
                .scale(syncAllPulseAnim.value),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = syncAllBgColor,
                contentColor = TitaniumMint.Mint400
            ),
            border = BorderStroke((1f + syncAllGlowAnim.value).dp, syncAllBorderColor)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Sync All",
                modifier = Modifier
                    .size(16.dp)
                    .rotate(if (isSyncingAll) spinAngle else 0f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            AnimatedContent(
                targetState = isSyncingAll,
                transitionSpec = {
                    fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
                },
                label = "syncAllButtonFade"
            ) { syncing ->
                Text(
                    text = if (syncing) "Syncing..." else "Sync All Now",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}
