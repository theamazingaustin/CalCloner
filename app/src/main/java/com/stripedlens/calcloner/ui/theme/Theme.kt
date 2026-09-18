package com.stripedlens.calcloner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stripedlens.calcloner.ThemeMode

// Titanium Mint Color Tokens (delegated to consolidated Palette)
object TitaniumMint {
    val Mint400 = Palette.Mint400
    val Mint500 = Palette.Mint500
    val Mint600 = Palette.Mint600
    val Amber400 = Palette.Amber400
    val Amber500 = Palette.Amber500
    val Orange400 = Palette.Orange400
    val Orange500 = Palette.Orange500
    val Rose400 = Palette.Rose400
    val Rose500 = Palette.Rose500
    val CarbonOnyx = Palette.Zinc950
    val SurfaceLow = Palette.Zinc900
    val SurfaceMid = Palette.Zinc800
    val SurfaceHigh = Palette.Zinc700
    val BorderDark = Palette.Zinc700
}

/**
 * Accessor for app-wide semantic color tokens: CalClonerTheme.colors.accent, etc.
 */
object CalClonerTheme {
    val colors: CalClonerColors
        @Composable
        get() = LocalCalClonerColors.current
}

/**
 * Universal layout and dimension tokens forwarded to centralized AppConstants.Ui.
 */
typealias UiDimensions = com.stripedlens.calcloner.AppConstants.Ui

private val DarkColorScheme = darkColorScheme(
    primary = TitaniumMint.Mint400,
    onPrimary = Color(0xFF003824),
    primaryContainer = TitaniumMint.Mint500,
    onPrimaryContainer = Color(0xFF022C1D),
    secondary = Color(0xFF4EDEA3),
    onSecondary = Color(0xFF003825),
    secondaryContainer = TitaniumMint.Mint600,
    onSecondaryContainer = Color(0xFFE6FDF3),
    tertiary = TitaniumMint.Amber500,
    onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFFCE9A00),
    onTertiaryContainer = Color(0xFF4A3500),
    background = TitaniumMint.CarbonOnyx,
    onBackground = Color(0xFFF4F4F5),
    surface = TitaniumMint.SurfaceLow,
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = TitaniumMint.SurfaceHigh,
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = Color(0xFF71717A),
    outlineVariant = TitaniumMint.BorderDark,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006948),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF00855D),
    onPrimaryContainer = Color(0xFFF5FFF7),
    secondary = Color(0xFF006C49),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF6CF8BB),
    onSecondaryContainer = Color(0xFF00714D),
    tertiary = Color(0xFF545C72),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF6C748B),
    onTertiaryContainer = Color(0xFFFEFCFF),
    background = Color(0xFFF7F9FB),
    onBackground = Color(0xFF191C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFECEEF0),
    onSurfaceVariant = Color(0xFF3D4A42),
    outline = Color(0xFF6D7A72),
    outlineVariant = Color(0xFFBCCAC0),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A)
)

val TitaniumMintShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
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
    val customColors = if (useDarkTheme) DarkCalClonerColors else LightCalClonerColors

    androidx.compose.runtime.CompositionLocalProvider(
        LocalCalClonerColors provides customColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = TitaniumMintShapes,
            content = content
        )
    }
}
