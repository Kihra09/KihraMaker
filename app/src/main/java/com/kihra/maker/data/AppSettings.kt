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
    /** Light colours instead of dark. */
    val light: Boolean = false,
    /** Daily play goal in minutes, 0 = none. */
    val goalMinutes: Int = 0,
    /** RECENT, MOST_PLAYED, NAME or ADDED. */
    val sort: String = "RECENT",
    /** "", "favorite", "playing" or "finished". */
    val filter: String = "",
    /** Settings copied to every newly added game (display, controls, advanced). */
    val defaults: GameSettings = GameSettings(),
    /** One button layout (position, size, keys) for every game of every RPG Maker version. */
    val sharedLayout: Boolean = false,
    /** Display, controls and advanced settings come from the defaults for every game. */
    val sharedSettings: Boolean = false,
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
                light = o.optBoolean("light", false),
                goalMinutes = o.optInt("goalMinutes", 0),
                sort = o.optString("sort", "RECENT"),
                filter = o.optString("filter", ""),
                defaults = o.optJSONObject("defaults")?.let { GameSettings.fromJson(it) } ?: GameSettings(),
                sharedLayout = o.optBoolean("sharedLayout", false),
                sharedSettings = o.optBoolean("sharedSettings", false),
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
                        .put("light", s.light)
                        .put("goalMinutes", s.goalMinutes)
                        .put("sort", s.sort)
                        .put("filter", s.filter)
                        .put("defaults", s.defaults.toJson())
                        .put("sharedLayout", s.sharedLayout)
                        .put("sharedSettings", s.sharedSettings)
                        .toString(2)
                )
            }
        }
    }
}
