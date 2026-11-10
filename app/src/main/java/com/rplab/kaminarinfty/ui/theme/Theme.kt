package com.rplab.kaminarinfty.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

private val DarkColorScheme = darkColorScheme(
    primary = NightAccentBlue,
    onPrimary = NightBackground,
    primaryContainer = NightSurfaceSecondary,
    onPrimaryContainer = NightTextPrimary,
    secondary = NightAccentBlue,
    onSecondary = NightBackground,
    background = NightBackground,
    onBackground = NightTextPrimary,
    surface = NightSurfaceMain,
    onSurface = NightTextPrimary,
    surfaceVariant = NightSurfaceSecondary,
    onSurfaceVariant = NightTextSecondary,
    outline = NightBorder,
    error = NightError,
    onError = NightBackground,
)

private val LightColorScheme = lightColorScheme(
    primary = DayAccentVermilion,
    onPrimary = DaySurfaceMain,
    primaryContainer = DaySurfaceSecondary,
    onPrimaryContainer = DayTextPrimary,
    secondary = DayAccentVermilion,
    onSecondary = DaySurfaceMain,
    background = DayBackground,
    onBackground = DayTextPrimary,
    surface = DaySurfaceMain,
    onSurface = DayTextPrimary,
    surfaceVariant = DaySurfaceSecondary,
    onSurfaceVariant = DayTextSecondary,
    outline = DayBorder,
    error = DayError,
    onError = DaySurfaceMain,
)

private val EinkColorScheme = lightColorScheme(
    primary = Color(0xFF000000),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFFFFF),
    onPrimaryContainer = Color(0xFF000000),
    secondary = Color(0xFF000000),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF000000),
    outline = Color(0xFF000000),
    error = Color(0xFF000000),
    onError = Color(0xFFFFFFFF),
)

private val KaminariNightTokens = KaminariColors(
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

private val KaminariDayTokens = KaminariColors(
    background = DayBackground,
    surfaceMain = DaySurfaceMain,
    surfaceSecondary = DaySurfaceSecondary,
    surfaceElevated = DaySurfaceElevated,
    border = DayBorder,
    textPrimary = DayTextPrimary,
    textSecondary = DayTextSecondary,
    accent = DayAccentVermilion,
    success = DaySuccess,
    warning = DayWarning,
    error = DayError,
    isDark = false,
)

private val KaminariEinkTokens = KaminariColors(
    background = EinkBackground,
    surfaceMain = EinkSurfaceMain,
    surfaceSecondary = EinkSurfaceSecondary,
    surfaceElevated = EinkSurfaceElevated,
    border = EinkBorder,
    textPrimary = EinkTextPrimary,
    textSecondary = EinkTextSecondary,
    accent = EinkAccent,
    success = EinkSuccess,
    warning = EinkWarning,
    error = EinkError,
    isDark = false,
)

object KaminariTheme {
    val colors: KaminariColors
        @Composable
        @ReadOnlyComposable
        get() = LocalKaminariColors.current

    val techTypography: TechnicalTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalTechnicalTypography.current
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Strictly adhere to Kaminari-Fuji field manual palette
    eink: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        eink -> EinkColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val kaminariTokens = when {
        eink -> KaminariEinkTokens
        darkTheme -> KaminariNightTokens
        else -> KaminariDayTokens
    }
    val techType = TechnicalTypography()

    CompositionLocalProvider(
        LocalKaminariColors provides kaminariTokens,
        LocalTechnicalTypography provides techType,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
