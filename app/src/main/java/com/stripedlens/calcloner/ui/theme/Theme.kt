package com.stripedlens.calcloner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stripedlens.calcloner.ThemeMode

// Titanium Mint Color Tokens (delegated to consolidated Palette)
object TitaniumMint {
    val Mint400 = Palette.Mint400
    val Mint500 = Palette.Mint500
    val Mint600 = Palette.Mint600
    val Mint700 = Palette.Mint700
    val Amber400 = Palette.Amber400
    val Amber500 = Palette.Amber500
    val Amber600 = Palette.Amber600
    val Orange400 = Palette.Orange400
    val Orange500 = Palette.Orange500
    val Orange600 = Palette.Orange600
    val Rose400 = Palette.Rose400
    val Rose500 = Palette.Rose500
    val Rose600 = Palette.Rose600
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
    primary = Color(0xFF0F766E),           // Deep Teal / Pine Green - calm, elegant, zero eye fatigue
    onPrimary = Color.White,
    primaryContainer = Color(0xFF047857),  // Emerald 700
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF115E59),        // Teal 800
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2ECE6), // Soft muted sage container
    onSecondaryContainer = Color(0xFF0F3E35),
    tertiary = Color(0xFFD97706),         // Amber 600
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    background = Color(0xFFF1F4F2),       // Soft muted warm-sage gray - eliminates high glare
    onBackground = Color(0xFF1E2923),     // Deep slate forest - comfortable reading contrast
    surface = Color(0xFFF9FAF9),          // Off-white surface - gentle card depth without harsh starkness
    onSurface = Color(0xFF1E2923),        // Deep slate forest
    surfaceVariant = Color(0xFFDFE5E1),   // Calm sage container boundary
    onSurfaceVariant = Color(0xFF4A5550), // Balanced secondary slate
    outline = Color(0xFF6B7280),          // Neutral gray
    outlineVariant = Color(0xFFCBD3CD),   // Soft, clean card borders
    error = Color(0xFFDC2626),            // Red 600
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),   // Soft red container
    onErrorContainer = Color(0xFF991B1B)
)

val TitaniumMintShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

val CalClonerTypography = Typography(
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
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
            typography = CalClonerTypography,
            shapes = TitaniumMintShapes,
            content = content
        )
    }
}
