package com.kihra.maker.data

import android.content.Context
import org.json.JSONObject
import java.io.File

enum class ViewMode(val label: String) { LIST("List"), GRID("Grid"), CARDS("Cards") }

/** App-wide settings, stored in files/settings.json next to the library. */
data class AppSettings(
    val theme: String = "midnight",
    val view: ViewMode = ViewMode.LIST,
    /** Wallpaper behind the whole app. */
    val wallpaperInApp: Boolean = true,
    /** Wallpaper behind the game picture (fills the space beside the screen). */
    val wallpaperInGame: Boolean = true,
    val hintSeen: Boolean = false,
) {
    companion object {
        private fun file(context: Context) = File(AppPaths.base(context), "settings.json")

        fun wallpaperFile(context: Context) = File(AppPaths.base(context), "wallpaper.jpg")

        fun load(context: Context): AppSettings = runCatching {
            val o = JSONObject(file(context).readText())
            AppSettings(
                theme = o.optString("theme", "midnight"),
                view = runCatching { ViewMode.valueOf(o.getString("view")) }.getOrDefault(ViewMode.LIST),
                wallpaperInApp = o.optBoolean("wallpaperInApp", true),
                wallpaperInGame = o.optBoolean("wallpaperInGame", true),
                hintSeen = o.optBoolean("hintSeen", false),
            )
        }.getOrDefault(AppSettings())

        fun save(context: Context, s: AppSettings) {
            runCatching {
                file(context).writeText(
                    JSONObject()
                        .put("theme", s.theme)
                        .put("view", s.view.name)
                        .put("wallpaperInApp", s.wallpaperInApp)
                        .put("wallpaperInGame", s.wallpaperInGame)
                        .put("hintSeen", s.hintSeen)
                        .toString(2)
                )
            }
        }
    }
}
