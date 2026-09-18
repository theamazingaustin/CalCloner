package com.stripedlens.calcloner.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stripedlens.calcloner.ui.theme.CalClonerTheme
import com.stripedlens.calcloner.ui.theme.Palette
import com.stripedlens.calcloner.viewmodel.AppTab

/**
 * Bottom navigation bar providing switching between the main Sync screen
 * and the dedicated Calendar Event Deletion screen.
 */
@Composable
fun AppBottomNavigationBar(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = CalClonerTheme.colors

    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        NavigationBarItem(
            selected = currentTab == AppTab.SYNC,
            onClick = { onTabSelected(AppTab.SYNC) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Sync Pairs"
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

        NavigationBarItem(
            selected = currentTab == AppTab.ONE_TIME,
            onClick = { onTabSelected(AppTab.ONE_TIME) },
            icon = {
                Icon(
                    imageVector = Icons.Default.FlashOn,
                    contentDescription = "One-Time Operations"
                )
            },
            label = {
                Text(
                    text = "One-Time",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (currentTab == AppTab.ONE_TIME) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 12.sp
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = if (colors.isDark) Palette.Zinc950 else Color.White,
                selectedTextColor = if (colors.isDark) Palette.Amber400 else Palette.Amber600,
                indicatorColor = if (colors.isDark) Palette.Amber500 else Palette.Amber600,
                unselectedIconColor = colors.textSecondary,
                unselectedTextColor = colors.textSecondary
            )
        )

        NavigationBarItem(
            selected = currentTab == AppTab.DELETE,
            onClick = { onTabSelected(AppTab.DELETE) },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Delete Calendar Events"
                )
            },
            label = {
                Text(
                    text = "Delete",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (currentTab == AppTab.DELETE) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 12.sp
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = if (colors.isDark) Palette.Zinc950 else Color.White,
                selectedTextColor = if (colors.isDark) Palette.Rose400 else Palette.Rose600,
                indicatorColor = if (colors.isDark) Palette.Rose500 else Palette.Rose600,
                unselectedIconColor = colors.textSecondary,
                unselectedTextColor = colors.textSecondary
            )
        )
    }
}
