package com.stripedlens.calcloner.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Consolidated raw color palette for CalCloner.
 * Standardizes identical and near-identical color definitions into a single, minimal scale.
 */
object Palette {
    // Mint / Emerald
    val Mint300 = Color(0xFF6EE7B7)
    val Mint400 = Color(0xFF34D399)
    val Mint500 = Color(0xFF10B981)
    val Mint600 = Color(0xFF059669)
    val Mint700 = Color(0xFF047857)
    val Mint900 = Color(0xFF064E3B)

    // Amber
    val Amber400 = Color(0xFFFBBF24)
    val Amber500 = Color(0xFFF59E0B)
    val Amber600 = Color(0xFFD97706)

    // Orange / Warm Tangerine
    val Orange400 = Color(0xFFFB923C)
    val Orange500 = Color(0xFFF97316)
    val Orange600 = Color(0xFFEA580C)

    // Rose / Crimson
    val Rose400 = Color(0xFFFB7185)
    val Rose500 = Color(0xFFF43F5E)
    val Rose600 = Color(0xFFE11D48)

    // Zinc / Monochromes
    val White = Color(0xFFFFFFFF)
    val Zinc50 = Color(0xFFFAFAFA)
    val Zinc100 = Color(0xFFF4F4F5)
    val Zinc200 = Color(0xFFE4E4E7)
    val Zinc400 = Color(0xFFA1A1AA)
    val Zinc500 = Color(0xFF71717A)
    val Zinc700 = Color(0xFF3F3F46)
    val Zinc800 = Color(0xFF27272A)
    val Zinc900 = Color(0xFF18181B)
    val Zinc950 = Color(0xFF121214)
}

/**
 * App-wide semantic color token specification.
 * Eliminates scattered raw color values and enables synchronized light/dark mode adaptation.
 */
@Immutable
data class CalClonerColors(
    val isDark: Boolean,

    // Primary Accents
    val accent: Color,
    val onAccent: Color,
    val accentSubtle: Color,
    val accentBorder: Color,

    // Success
    val success: Color,
    val onSuccess: Color,
    val successSubtle: Color,
    val successBorder: Color,

    // Warning / Caution
    val warning: Color,
    val onWarning: Color,
    val warningSubtle: Color,
    val warningBorder: Color,

    // Danger / Destructive
    val danger: Color,
    val onDanger: Color,
    val dangerSubtle: Color,
    val dangerBorder: Color,

    // Surfaces & Chrome
    val background: Color,
    val surfaceCard: Color,
    val surfaceElevated: Color,
    val borderSubtle: Color,
    val borderProminent: Color,

    // Typography
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,

    // Interactive Controls
    val buttonPrimaryBg: Color,
    val buttonPrimaryContent: Color,
    val buttonDangerBg: Color,
    val buttonDangerContent: Color
)

val DarkCalClonerColors = CalClonerColors(
    isDark = true,
    accent = Palette.Mint400,
    onAccent = Palette.Zinc950,
    accentSubtle = Palette.Mint500.copy(alpha = 0.15f),
    accentBorder = Palette.Mint500.copy(alpha = 0.35f),

    success = Palette.Mint400,
    onSuccess = Palette.Zinc950,
    successSubtle = Palette.Mint500.copy(alpha = 0.12f),
    successBorder = Palette.Mint500.copy(alpha = 0.30f),

    warning = Palette.Amber400,
    onWarning = Palette.Zinc950,
    warningSubtle = Palette.Amber500.copy(alpha = 0.12f),
    warningBorder = Palette.Amber500.copy(alpha = 0.35f),

    danger = Palette.Rose400,
    onDanger = Palette.White,
    dangerSubtle = Palette.Rose500.copy(alpha = 0.15f),
    dangerBorder = Palette.Rose500.copy(alpha = 0.35f),

    background = Palette.Zinc950,
    surfaceCard = Palette.Zinc900,
    surfaceElevated = Palette.Zinc800,
    borderSubtle = Palette.Zinc700.copy(alpha = 0.5f),
    borderProminent = Palette.Zinc500,

    textPrimary = Palette.Zinc100,
    textSecondary = Palette.Zinc400,
    textMuted = Palette.Zinc500,

    buttonPrimaryBg = Palette.Mint500,
    buttonPrimaryContent = Palette.Zinc950,
    buttonDangerBg = Palette.Rose500,
    buttonDangerContent = Palette.White
)

val LightCalClonerColors = CalClonerColors(
    isDark = false,
    accent = Palette.Mint700,
    onAccent = Palette.White,
    accentSubtle = Palette.Mint500.copy(alpha = 0.14f),
    accentBorder = Palette.Mint600.copy(alpha = 0.45f),

    success = Palette.Mint700,
    onSuccess = Palette.White,
    successSubtle = Palette.Mint500.copy(alpha = 0.12f),
    successBorder = Palette.Mint600.copy(alpha = 0.40f),

    warning = Palette.Amber600,
    onWarning = Palette.White,
    warningSubtle = Palette.Amber500.copy(alpha = 0.16f),
    warningBorder = Palette.Amber600.copy(alpha = 0.50f),

    danger = Palette.Rose600,
    onDanger = Palette.White,
    dangerSubtle = Palette.Rose500.copy(alpha = 0.14f),
    dangerBorder = Palette.Rose600.copy(alpha = 0.45f),

    background = Color(0xFFF1F5F9),
    surfaceCard = Palette.White,
    surfaceElevated = Color(0xFFF8FAFC),
    borderSubtle = Color(0xFFCBD5E1),
    borderProminent = Color(0xFF94A3B8),

    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF334155),
    textMuted = Color(0xFF64748B),

    buttonPrimaryBg = Palette.Mint600,
    buttonPrimaryContent = Palette.White,
    buttonDangerBg = Palette.Rose600,
    buttonDangerContent = Palette.White
)

val LocalCalClonerColors = staticCompositionLocalOf { DarkCalClonerColors }

/**
 * Accessor for app-wide semantic colors: CalClonerTheme.colors.accent, etc.
 */
object CalClonerThemeTokens {
    val colors: CalClonerColors
        @Composable
        @ReadOnlyComposable
        get() = LocalCalClonerColors.current
}
