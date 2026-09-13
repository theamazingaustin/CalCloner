package com.stripedlens.calcloner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.stripedlens.calcloner.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),          // Sky 400
    onPrimary = Color(0xFF082F49),        // Sky 950
    primaryContainer = Color(0xFF0369A1), // Sky 700
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF2DD4BF),        // Teal 400
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = Color(0xFF115E59),
    onSecondaryContainer = Color(0xFFCCFBF1),
    background = Color(0xFF0F172A),       // Slate 900
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF1E293B),          // Slate 800
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF334155),   // Slate 700
    onSurfaceVariant = Color(0xFFCBD5E1),
    error = Color(0xFFF87171),            // Red 400
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7),          // Sky 600
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE), // Sky 100
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = Color(0xFF0D9488),        // Teal 600
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1), // Teal 100
    onSecondaryContainer = Color(0xFF115E59),
    background = Color(0xFFF8FAFC),       // Slate 50
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),   // Slate 100
    onSurfaceVariant = Color(0xFF475569),
    error = Color(0xFFDC2626),            // Red 600
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B)
)

@Composable
fun CalClonerTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
