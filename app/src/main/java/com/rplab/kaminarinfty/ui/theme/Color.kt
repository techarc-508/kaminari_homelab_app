package com.rplab.kaminarinfty.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// --- Kaminari-Fuji / RPLab Exact Palette (Extracted from Live Dashboard) ---

// Night Theme (Midnight PVE Observatory Console)
val NightBackground = Color(0xFF0B0F19)
val NightSurfaceMain = Color(0xFF121826)
val NightSurfaceSecondary = Color(0xFF182236)
val NightSurfaceElevated = Color(0xFF1E2C44)
val NightBorder = Color(0xFF23324C)
val NightTextPrimary = Color(0xFFE5ECF6)
val NightTextSecondary = Color(0xFF7C8FA8)
val NightAccentBlue = Color(0xFF7BB0FF)
val NightSuccess = Color(0xFF3DD68C) // Live Nominal Green
val NightWarning = Color(0xFFF0A742) // Storage/Temp Amber
val NightError = Color(0xFFFF5E6A)   // Memory Alloc Red

// Day Theme (Live Japanese Field-Manual Parchment & Sumi Ink)
val DayBackground = Color(0xFFEFE6D5)
val DaySurfaceMain = Color(0xFFF7F2E7)
val DaySurfaceSecondary = Color(0xFFE4DAAA)
val DaySurfaceElevated = Color(0xFFDCD1B8)
val DayBorder = Color(0xFF8F816B)
val DayTextPrimary = Color(0xFF1C1813)
val DayTextSecondary = Color(0xFF574F42)
val DayAccentVermilion = Color(0xFFC43235) // Live Graphs Red Button
val DaySuccess = Color(0xFF2E7D52)
val DayWarning = Color(0xFFA6691C)
val DayError = Color(0xFFB83232)

// Card category accent colors (from live telemetry cards)
val AccentCategoryPink = Color(0xFFFF5E6A)   // Alert / Load
val AccentCategoryAmber = Color(0xFFF0A742)  // Thermal / Sensor
val AccentCategoryCoral = Color(0xFFFF6B4A)  // Storage / DB
val AccentCategoryCyan = Color(0xFF5BB8F5)   // Compute / CPU
val AccentCategoryGreen = Color(0xFF3DD68C)  // Network / Gateway
val AccentCategoryPurple = Color(0xFF9E7CFF) // Security / SSL

// E-Ink profile: high-contrast, monochrome, low-visual-noise presentation.
val EinkBackground = Color(0xFFFFFFFF)
val EinkSurfaceMain = Color(0xFFFFFFFF)
val EinkSurfaceSecondary = Color(0xFFF2F2F2)
val EinkSurfaceElevated = Color(0xFFFFFFFF)
val EinkBorder = Color(0xFF000000)
val EinkTextPrimary = Color(0xFF000000)
val EinkTextSecondary = Color(0xFF333333)
val EinkAccent = Color(0xFF000000)
val EinkSuccess = Color(0xFF000000)
val EinkWarning = Color(0xFF000000)
val EinkError = Color(0xFF000000)

@Immutable
data class KaminariColors(
    val background: Color,
    val surfaceMain: Color,
    val surfaceSecondary: Color,
    val surfaceElevated: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val isDark: Boolean,
)

val LocalKaminariColors = staticCompositionLocalOf {
    KaminariColors(
        background = NightBackground,
        surfaceMain = NightSurfaceMain,
        surfaceSecondary = NightSurfaceSecondary,
        surfaceElevated = NightSurfaceElevated,
        border = NightBorder,
        textPrimary = NightTextPrimary,
        textSecondary = NightTextSecondary,
        accent = NightAccentBlue,
        success = NightSuccess,
        warning = NightWarning,
        error = NightError,
        isDark = true,
    )
}
