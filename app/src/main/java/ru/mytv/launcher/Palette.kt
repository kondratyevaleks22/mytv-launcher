package ru.mytv.launcher

import androidx.compose.ui.graphics.Color

/** Цвета одной темы, те же, что в макете. */
data class Palette(
    val isDark: Boolean,
    val bg: Color,
    val blob1: Color,
    val blob2: Color,
    val text: Color,
    val sub: Color,
    val sub2: Color,
    val card: Color,
    val hero: Color,
    val soft: Color,
    val small: Color,
    val shadow: Color,
)

val LightPalette = Palette(
    isDark = false,
    bg = Color(0xFFEEF0F5),
    blob1 = Color(0xFFE2E8F4),
    blob2 = Color(0xFFF3EAE4),
    text = Color(0xFF1C1C1E),
    sub = Color(0xFF6C6C70),
    sub2 = Color(0xFF3A3A3C),
    card = Color(0xFFFFFFFF),
    hero = Color(0xBFFFFFFF),
    soft = Color(0xFFF4F5F8),
    small = Color(0xCCFFFFFF),
    shadow = Color(0xFF1E2846),
)

val DarkPalette = Palette(
    isDark = true,
    bg = Color(0xFF0B0B0F),
    blob1 = Color(0xFF141A2C),
    blob2 = Color(0xFF1F1814),
    text = Color(0xFFF5F5F7),
    sub = Color(0xFFA1A1A6),
    sub2 = Color(0xFFE5E5EA),
    card = Color(0xFF1C1C1E),
    hero = Color(0xD91C1C1E),
    soft = Color(0xFF2C2C2E),
    small = Color(0xD91C1C1E),
    shadow = Color(0xFF000000),
)

/** Цвета подсветки: синий, оранжевый, фиолетовый, зелёный. */
val Accents: List<Long> = listOf(0xFF0A84FF, 0xFFFF9F0A, 0xFFBF5AF2, 0xFF30B158)
