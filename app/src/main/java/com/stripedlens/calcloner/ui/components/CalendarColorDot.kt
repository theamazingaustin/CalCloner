package com.stripedlens.calcloner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun CalendarColorDot(
    color: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp
) {
    val dotColor = if (color != null && color != 0) {
        Color(color)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(dotColor)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                shape = CircleShape
            )
    )
}
