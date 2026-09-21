package com.stripedlens.calcloner

import androidx.compose.ui.unit.dp

/**
 * Single consolidated source of truth for universal application constants.
 * Organized into clear namespaces: Ui, Sync, and Engine.
 */
object AppConstants {

    /**
     * Universal layout, sizing, and styling tokens for consistent UI presentation.
     */
    object Ui {
        /** Percentage of screen width used by dialogs and popups (0.92f = 92% screen width). */
        const val DialogWidthFraction = 0.92f

        // Glass & Blur Effects
        /** Radius in pixels for Android 12+ hardware window background blur */
        const val DialogBlurBehindRadiusPx = 32
        /** Opacity of frosted glass dialog background surface (0.88f = 88% opaque, subtle transparency) */
        const val DialogGlassAlpha = 0.88f
        /** Subtle highlight border opacity for glass elevation edge */
        const val DialogGlassBorderAlpha = 0.35f

        // Corner Radii
        val DialogCornerRadius = 16.dp
        val CardCornerRadius = 12.dp
        val SubCardCornerRadius = 8.dp
        val ButtonCornerRadius = 8.dp
        val PillCornerRadius = 28.dp

        // Common Layout Paddings
        val ScreenHorizontalPadding = 16.dp
        val CardContentPadding = 16.dp
        val ItemSpacing = 12.dp
    }

    /**
     * Default parameters for sync ranges and scheduling.
     */
    object Sync {
        const val DEFAULT_DAYS_PAST = 30
        const val DEFAULT_DAYS_FUTURE = 30
        const val DEFAULT_INTERVAL_MINUTES = 15
    }

    /**
     * Tag identifiers and parsing patterns used across calendar synchronization operations.
     */
    object Engine {
        val CALCLONER_TAG_REGEX = Regex("""\[CalClone(?:r)?-ID:\s*(?:([a-zA-Z0-9_-]+):)?(\d+)\]""")
        val CALCLONER_TAG_REPLACE_REGEX = Regex("""\[CalCloner-ID:\s*(?:[a-zA-Z0-9_-]+:)?\d+\]""")
        val LEGACY_CALCLONE_TAG_REPLACE_REGEX = Regex("""\[CalClone-ID:\s*(?:[a-zA-Z0-9_-]+:)?\d+\]""")
        val CALCLONER_UID_REGEX = Regex("""\[CalCloner-UID:\s*([^\]]+)\]""")
        val CALCLONER_UID_REPLACE_REGEX = Regex("""\[CalCloner-UID:\s*[^\]]+\]""")
    }
}
