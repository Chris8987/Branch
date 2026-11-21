package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class BranchExtendedColors(
    val canvasBackground: Color,
    val gridDot: Color,
    val nodeSurface: Color,
    val nodeBorder: Color,
    val edgeDefault: Color,
    val trunkSpine: Color,
    val statusPlan: Color,
    val statusDo: Color,
    val statusDone: Color,
    val dropGlow: Color,
    val guideLine: Color,
    val isDark: Boolean,
)

val LocalBranchColors = staticCompositionLocalOf {
    BranchExtendedColors(
        canvasBackground = PaperBackground,
        gridDot = PaperGridDot,
        nodeSurface = PaperSurface,
        nodeBorder = PaperOutline,
        edgeDefault = Color(0xFFCBD5E1),
        trunkSpine = Color(0xFF334155),
        statusPlan = StatusPlanColor,
        statusDo = StatusDoColor,
        statusDone = StatusDoneColor,
        dropGlow = DropGlowColor,
        guideLine = BranchIndigo,
        isDark = false,
    )
}

private val LightColorScheme = lightColorScheme(
    primary = BranchIndigo,
    onPrimary = Color.White,
    primaryContainer = BranchIndigoContainerLight,
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFF0F766E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF134E4A),
    tertiary = StatusDoneColor,
    background = PaperBackground,
    onBackground = InkPrimary,
    surface = PaperSurface,
    onSurface = InkPrimary,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = InkSecondary,
    outline = PaperOutline,
    outlineVariant = Color(0xFFE2E8F0),
    error = DangerColor,
    onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary = BranchIndigoLight,
    onPrimary = Color(0xFF0F172A),
    primaryContainer = BranchIndigoContainerDark,
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF2DD4BF),
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = Color(0xFF115E59),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = StatusDoneColor,
    background = ObsidianBackground,
    onBackground = SnowPrimary,
    surface = ObsidianSurface,
    onSurface = SnowPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = SnowSecondary,
    outline = ObsidianOutline,
    outlineVariant = Color(0xFF1E293B),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
)

@Composable
fun BranchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) {
        BranchExtendedColors(
            canvasBackground = ObsidianBackground,
            gridDot = ObsidianGridDot,
            nodeSurface = ObsidianSurface,
            nodeBorder = ObsidianOutline,
            edgeDefault = Color(0xFF334155),
            trunkSpine = Color(0xFF94A3B8),
            statusPlan = Color(0xFF64748B),
            statusDo = StatusDoColor,
            statusDone = StatusDoneColor,
            dropGlow = DropGlowColor,
            guideLine = BranchIndigoLight,
            isDark = true,
        )
    } else {
        BranchExtendedColors(
            canvasBackground = PaperBackground,
            gridDot = PaperGridDot,
            nodeSurface = PaperSurface,
            nodeBorder = PaperOutline,
            edgeDefault = Color(0xFFCBD5E1),
            trunkSpine = Color(0xFF334155),
            statusPlan = StatusPlanColor,
            statusDo = StatusDoColor,
            statusDone = StatusDoneColor,
            dropGlow = DropGlowColor,
            guideLine = BranchIndigo,
            isDark = false,
        )
    }

    CompositionLocalProvider(LocalBranchColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BranchTypography,
            content = content,
        )
    }
}
