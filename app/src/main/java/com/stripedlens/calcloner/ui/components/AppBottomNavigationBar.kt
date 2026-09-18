package com.stripedlens.calcloner.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stripedlens.calcloner.ui.theme.CalClonerTheme
import com.stripedlens.calcloner.ui.theme.Palette
import com.stripedlens.calcloner.viewmodel.AppTab
import kotlinx.coroutines.launch

/**
 * Bottom navigation bar providing switching between the main Sync screen
 * and the One-Time Operations screen.
 */
@Composable
fun AppBottomNavigationBar(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = CalClonerTheme.colors
    val scope = rememberCoroutineScope()

    // Micro-animation states for tab clicks
    val syncSpinAnim = remember { Animatable(0f) }
    val oneTimeScaleAnim = remember { Animatable(1f) }

    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        // Sync Navigation Item
        NavigationBarItem(
            selected = currentTab == AppTab.SYNC,
            onClick = {
                scope.launch {
                    syncSpinAnim.snapTo(0f)
                    syncSpinAnim.animateTo(
                        targetValue = 360f,
                        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
                    )
                    syncSpinAnim.snapTo(0f)
                }
                onTabSelected(AppTab.SYNC)
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Sync Pairs",
                    modifier = Modifier.rotate(syncSpinAnim.value)
                )
            },
            label = {
                Text(
                    text = "Sync",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (currentTab == AppTab.SYNC) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 12.sp
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = if (colors.isDark) Palette.Zinc950 else Color.White,
                selectedTextColor = if (colors.isDark) Palette.Mint400 else Palette.Mint700,
                indicatorColor = if (colors.isDark) Palette.Mint500 else Palette.Mint600,
                unselectedIconColor = colors.textSecondary,
                unselectedTextColor = colors.textSecondary
            )
        )

        // One-Time Operations Navigation Item (uniform green palette)
        NavigationBarItem(
            selected = currentTab == AppTab.ONE_TIME || currentTab == AppTab.DELETE,
            onClick = {
                scope.launch {
                    oneTimeScaleAnim.animateTo(
                        targetValue = 1.3f,
                        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
                    )
                    oneTimeScaleAnim.animateTo(
                        targetValue = 1.0f,
                        animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing)
                    )
                }
                onTabSelected(AppTab.ONE_TIME)
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.FlashOn,
                    contentDescription = "One-Time Operations",
                    modifier = Modifier.scale(oneTimeScaleAnim.value)
                )
            },
            label = {
                Text(
                    text = "One-Time",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (currentTab == AppTab.ONE_TIME || currentTab == AppTab.DELETE) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 12.sp
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = if (colors.isDark) Palette.Zinc950 else Color.White,
                selectedTextColor = if (colors.isDark) Palette.Mint400 else Palette.Mint700,
                indicatorColor = if (colors.isDark) Palette.Mint500 else Palette.Mint600,
                unselectedIconColor = colors.textSecondary,
                unselectedTextColor = colors.textSecondary
            )
        )
    }
}
