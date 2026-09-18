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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stripedlens.calcloner.ui.theme.TitaniumMint
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
                selectedIconColor = TitaniumMint.Mint400,
                selectedTextColor = TitaniumMint.Mint400,
                indicatorColor = TitaniumMint.Mint500.copy(alpha = 0.2f),
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                selectedIconColor = TitaniumMint.Amber400,
                selectedTextColor = TitaniumMint.Amber400,
                indicatorColor = TitaniumMint.Amber500.copy(alpha = 0.2f),
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                selectedIconColor = TitaniumMint.Rose400,
                selectedTextColor = TitaniumMint.Rose400,
                indicatorColor = TitaniumMint.Rose500.copy(alpha = 0.2f),
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}
