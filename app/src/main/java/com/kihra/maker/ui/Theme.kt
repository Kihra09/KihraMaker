package com.kihra.maker.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** A colour theme: the whole app is tinted from one hue. */
data class AppTheme(val id: String, val label: String, val hue: Float, val sat: Float = 1f) {
    val preview: Color get() = Color.hsv(hue, 0.55f * sat, 1f)
}

val AppThemes = listOf(
    AppTheme("midnight", "Midnight", 218f, 0.8f),
    AppTheme("strawberry", "Strawberry", 350f),
    AppTheme("lime", "Lime", 95f),
    AppTheme("blueberry", "Blueberry", 245f),
    AppTheme("grape", "Grape", 285f),
    AppTheme("mango", "Mango", 30f),
    AppTheme("mint", "Mint", 160f),
    AppTheme("sakura", "Sakura", 330f, 0.7f),
)

fun themeById(id: String): AppTheme = AppThemes.firstOrNull { it.id == id } ?: AppThemes.first()

private fun schemeFor(t: AppTheme, transparentBackground: Boolean) = darkColorScheme(
    primary = Color.hsv(t.hue, 0.45f * t.sat, 1f),
    onPrimary = Color.hsv(t.hue, 0.9f * t.sat, 0.2f),
    primaryContainer = Color.hsv(t.hue, 0.6f * t.sat, 0.38f),
    onPrimaryContainer = Color.hsv(t.hue, 0.2f * t.sat, 1f),
    secondary = Color.hsv((t.hue + 25f) % 360f, 0.4f * t.sat, 0.95f),
    secondaryContainer = Color.hsv(t.hue, 0.45f * t.sat, 0.32f),
    onSecondaryContainer = Color.hsv(t.hue, 0.15f * t.sat, 0.97f),
    background = if (transparentBackground) Color.Transparent else Color.hsv(t.hue, 0.30f * t.sat, 0.09f),
    onBackground = Color.hsv(t.hue, 0.05f, 0.93f),
    surface = Color.hsv(t.hue, 0.28f * t.sat, 0.14f),
    onSurface = Color.hsv(t.hue, 0.05f, 0.93f),
    surfaceVariant = Color.hsv(t.hue, 0.25f * t.sat, 0.20f),
    onSurfaceVariant = Color.hsv(t.hue, 0.12f * t.sat, 0.78f),
    outline = Color.hsv(t.hue, 0.15f * t.sat, 0.55f),
    error = Color(0xFFFF8A80),
)

/** With a wallpaper the page background becomes see-through so the wallpaper shows behind the screens. */
@Composable
fun KihraTheme(themeId: String = "midnight", wallpaper: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = schemeFor(themeById(themeId), wallpaper), content = content)
}

/** Plain page colour of a theme (used behind everything when there is no wallpaper). */
fun themeBackground(id: String): Color {
    val t = themeById(id)
    return Color.hsv(t.hue, 0.30f * t.sat, 0.09f)
}
