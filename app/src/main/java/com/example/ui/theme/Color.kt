package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// --- Architectural Paper & Ink Light Palette ---
val PaperBackground = Color(0xFFF7F8FA)
val PaperSurface = Color(0xFFFFFFFF)
val PaperSurfaceVariant = Color(0xFFEEF2F6)
val PaperOutline = Color(0xFFDCE3EC)
val PaperGridDot = Color(0xFFD4DCE6)

val InkPrimary = Color(0xFF0F172A)
val InkSecondary = Color(0xFF475569)
val InkTertiary = Color(0xFF94A3B8)

// --- Obsidian Dark Palette ---
val ObsidianBackground = Color(0xFF0B1120)
val ObsidianSurface = Color(0xFF131D31)
val ObsidianSurfaceVariant = Color(0xFF1C2942)
val ObsidianOutline = Color(0xFF283753)
val ObsidianGridDot = Color(0xFF1E2D47)

val SnowPrimary = Color(0xFFF8FAFC)
val SnowSecondary = Color(0xFF94A3B8)
val SnowTertiary = Color(0xFF64748B)

// --- Brand & Semantic Accents ---
val BranchIndigo = Color(0xFF2563EB)
val BranchIndigoLight = Color(0xFF60A5FA)
val BranchIndigoContainerLight = Color(0xFFEFF6FF)
val BranchIndigoContainerDark = Color(0xFF172554)

val StatusPlanColor = Color(0xFF94A3B8)
val StatusDoColor = Color(0xFFF59E0B)
val StatusDoGlowColor = Color(0xFFFBBF24)
val StatusDoneColor = Color(0xFF10B981)
val DropGlowColor = Color(0xFF38BDF8)
val DangerColor = Color(0xFFEF4444)

// --- Curated Node Accent Palette (Standard Hex for Android + iPad PWA Sync) ---
@Immutable
data class NodeColorOption(
    val hex: String,
    val label: String,
    val color: Color,
)

val BranchNodePalette = listOf(
    NodeColorOption("#334155", "石板灰", Color(0xFF334155)),
    NodeColorOption("#2563EB", "鈷藍", Color(0xFF2563EB)),
    NodeColorOption("#10B981", "翡翠綠", Color(0xFF10B981)),
    NodeColorOption("#F59E0B", "琥珀黃", Color(0xFFF59E0B)),
    NodeColorOption("#F43F5E", "珊瑚紅", Color(0xFFF43F5E)),
    NodeColorOption("#8B5CF6", "紫羅蘭", Color(0xFF8B5CF6)),
    NodeColorOption("#14B8A6", "青瓷藍", Color(0xFF14B8A6)),
    NodeColorOption("#EA580C", "赤陶橘", Color(0xFFEA580C)),
)

// --- Exclusive Gradient Themes for Node States (狀態專屬漸層外觀) ---
@Immutable
data class StateGradientTheme(
    val name: String,
    val accent: Color,
    val accentHex: String,
    val lightStart: Color,
    val lightEnd: Color,
    val darkStart: Color,
    val darkEnd: Color,
)

val StateGradientPalette = listOf(
    StateGradientTheme(
        name = "晴空藍",
        accent = Color(0xFF2563EB),
        accentHex = "#2563EB",
        lightStart = Color(0xFFEFF6FF),
        lightEnd = Color(0xFFDBEAFE),
        darkStart = Color(0xFF172554),
        darkEnd = Color(0xFF1E3A8A),
    ),
    StateGradientTheme(
        name = "晨露綠",
        accent = Color(0xFF059669),
        accentHex = "#059669",
        lightStart = Color(0xFFECFDF5),
        lightEnd = Color(0xFFD1FAE5),
        darkStart = Color(0xFF064E3B),
        darkEnd = Color(0xFF065F46),
    ),
    StateGradientTheme(
        name = "暖陽橘",
        accent = Color(0xFFD97706),
        accentHex = "#D97706",
        lightStart = Color(0xFFFFFBEB),
        lightEnd = Color(0xFFFEF3C7),
        darkStart = Color(0xFF451A03),
        darkEnd = Color(0xFF78350F),
    ),
    StateGradientTheme(
        name = "晚霞玫",
        accent = Color(0xFFE11D48),
        accentHex = "#E11D48",
        lightStart = Color(0xFFFFF1F2),
        lightEnd = Color(0xFFFFE4E6),
        darkStart = Color(0xFF4C0519),
        darkEnd = Color(0xFF881337),
    ),
    StateGradientTheme(
        name = "暮霧紫",
        accent = Color(0xFF7C3AED),
        accentHex = "#7C3AED",
        lightStart = Color(0xFFF5F3FF),
        lightEnd = Color(0xFFEDE9FE),
        darkStart = Color(0xFF2E1065),
        darkEnd = Color(0xFF4C1D95),
    ),
    StateGradientTheme(
        name = "湖光青",
        accent = Color(0xFF0D9488),
        accentHex = "#0D9488",
        lightStart = Color(0xFFF0FDFA),
        lightEnd = Color(0xFFCCFBF1),
        darkStart = Color(0xFF042F2E),
        darkEnd = Color(0xFF115E59),
    ),
)

fun stateGradientForIndex(index: Int): StateGradientTheme {
    val safe = ((index % StateGradientPalette.size) + StateGradientPalette.size) % StateGradientPalette.size
    return StateGradientPalette[safe]
}

fun parseNodeHexColor(hex: String, fallback: Color = BranchIndigo): Color {
    val clean = hex.removePrefix("#")
    return try {
        when (clean.length) {
            6 -> Color(0xFF000000L or clean.toLong(16))
            8 -> Color(clean.toLong(16))
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    }
}
