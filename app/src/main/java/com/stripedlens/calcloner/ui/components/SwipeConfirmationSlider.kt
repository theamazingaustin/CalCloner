package com.stripedlens.calcloner.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SwipeToConfirmSlider(
    text: String,
    lockSeconds: Int,
    accentColor: Color,
    onConfirmed: () -> Unit
) {
    val isLocked = lockSeconds > 0
    val density = LocalDensity.current

    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val thumbWidth = 52.dp
    val thumbWidthPx = with(density) { thumbWidth.toPx() }
    val maxDragPx = (trackWidthPx - thumbWidthPx).coerceAtLeast(0f)

    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val draggableState = rememberDraggableState { delta ->
        if (!isLocked && maxDragPx > 0f) {
            val newOffset = (offsetX.value + delta).coerceIn(0f, maxDragPx)
            scope.launch {
                offsetX.snapTo(newOffset)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                if (isLocked) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                else accentColor.copy(alpha = 0.15f)
            )
            .onSizeChanged { size ->
                trackWidthPx = size.width.toFloat()
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Label text in track center
        Text(
            text = if (isLocked) "Locked: Please read ($lockSeconds s)" else text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            textAlign = TextAlign.Center,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isLocked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else accentColor
        )

        // Draggable Thumb
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .size(56.dp)
                .padding(4.dp)
                .clip(CircleShape)
                .background(if (isLocked) Color.Gray else accentColor)
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    enabled = !isLocked,
                    onDragStopped = {
                        if (maxDragPx > 0f && offsetX.value >= maxDragPx * 0.85f) {
                            scope.launch {
                                offsetX.animateTo(maxDragPx, tween(100))
                                onConfirmed()
                            }
                        } else {
                            scope.launch {
                                offsetX.animateTo(0f, tween(200))
                            }
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isLocked) Icons.Default.Lock else Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Swipe Handle",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
