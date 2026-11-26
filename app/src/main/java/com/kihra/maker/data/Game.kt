package com.kihra.maker.data

import org.json.JSONObject

enum class Rgss(val label: String) {
    XP("RPG Maker XP"),
    VX("RPG Maker VX"),
    VX_ACE("RPG Maker VX Ace"),
    UNKNOWN("Unknown engine");

    /** Only VX Ace (RGSS3) is supported for now. */
    val supported: Boolean get() = this == VX_ACE
}

enum class ScaleMode(val label: String) {
    FIT("Fit"),
    INTEGER("Pixel-perfect"),
    STRETCH("Stretch"),
}

data class GameSettings(
    val scaleMode: ScaleMode = ScaleMode.FIT,
    val fpsCap: Int = 60,
    val showFps: Boolean = false,
    val controlsOpacity: Float = 0.6f,
)

data class Game(
    val id: String,
    val treeUri: String,
    val title: String,
    val rgss: Rgss,
    val rtp: String,
    val addedAt: Long,
    val lastPlayed: Long = 0L,
    val settings: GameSettings = GameSettings(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("treeUri", treeUri)
        put("title", title)
        put("rgss", rgss.name)
        put("rtp", rtp)
        put("addedAt", addedAt)
        put("lastPlayed", lastPlayed)
        put("scaleMode", settings.scaleMode.name)
        put("fpsCap", settings.fpsCap)
        put("showFps", settings.showFps)
        put("controlsOpacity", settings.controlsOpacity.toDouble())
    }

    companion object {
        fun fromJson(o: JSONObject): Game = Game(
            id = o.getString("id"),
            treeUri = o.getString("treeUri"),
            title = o.getString("title"),
            rgss = runCatching { Rgss.valueOf(o.getString("rgss")) }.getOrDefault(Rgss.UNKNOWN),
            rtp = o.optString("rtp", ""),
            addedAt = o.optLong("addedAt", 0L),
            lastPlayed = o.optLong("lastPlayed", 0L),
            settings = GameSettings(
                scaleMode = runCatching { ScaleMode.valueOf(o.getString("scaleMode")) }
                    .getOrDefault(ScaleMode.FIT),
                fpsCap = o.optInt("fpsCap", 60),
                showFps = o.optBoolean("showFps", false),
                controlsOpacity = o.optDouble("controlsOpacity", 0.6).toFloat(),
            ),
        )
    }
}
