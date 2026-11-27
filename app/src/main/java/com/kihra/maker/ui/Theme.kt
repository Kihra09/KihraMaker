package com.kihra.maker.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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

private fun lightSchemeFor(t: AppTheme, transparentBackground: Boolean) = lightColorScheme(
    primary = Color.hsv(t.hue, 0.75f * t.sat, 0.62f),
    onPrimary = Color.White,
    primaryContainer = Color.hsv(t.hue, 0.18f * t.sat, 1f),
    onPrimaryContainer = Color.hsv(t.hue, 0.8f * t.sat, 0.25f),
    secondary = Color.hsv((t.hue + 25f) % 360f, 0.5f * t.sat, 0.6f),
    secondaryContainer = Color.hsv(t.hue, 0.15f * t.sat, 0.97f),
    onSecondaryContainer = Color.hsv(t.hue, 0.7f * t.sat, 0.25f),
    background = if (transparentBackground) Color.Transparent else Color.hsv(t.hue, 0.05f * t.sat, 0.99f),
    onBackground = Color.hsv(t.hue, 0.3f * t.sat, 0.12f),
    surface = Color.hsv(t.hue, 0.03f * t.sat, 1f),
    onSurface = Color.hsv(t.hue, 0.3f * t.sat, 0.12f),
    surfaceVariant = Color.hsv(t.hue, 0.10f * t.sat, 0.94f),
    onSurfaceVariant = Color.hsv(t.hue, 0.25f * t.sat, 0.35f),
    outline = Color.hsv(t.hue, 0.15f * t.sat, 0.55f),
    error = Color(0xFFB3261E),
)

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
fun KihraTheme(themeId: String = "midnight", wallpaper: Boolean = false, light: Boolean = false, content: @Composable () -> Unit) {
    val t = themeById(themeId)
    MaterialTheme(colorScheme = if (light) lightSchemeFor(t, wallpaper) else schemeFor(t, wallpaper), typography = KihraTypography, content = content)
}

/** Plain page colour of a theme (used behind everything when there is no wallpaper). */
fun themeBackground(id: String, light: Boolean = false): Color {
    val t = themeById(id)
    if (light) return Color.hsv(t.hue, 0.05f * t.sat, 0.99f)
    return Color.hsv(t.hue, 0.30f * t.sat, 0.09f)
}
