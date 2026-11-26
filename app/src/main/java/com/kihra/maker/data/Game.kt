package com.kihra.maker.data

import android.net.Uri
import org.json.JSONObject

enum class Rgss(val label: String) {
    XP("RPG Maker XP"),
    VX("RPG Maker VX"),
    VX_ACE("RPG Maker VX Ace"),
    UNKNOWN("Unknown engine");

    /** The engine handles all three, but VX Ace is the one we test and tune for. */
    val supported: Boolean get() = this != UNKNOWN
}

enum class ScaleMode(val label: String) {
    FIT("Fit"),
    INTEGER("Pixel-perfect"),
    STRETCH("Stretch"),
}

data class GameSettings(
    val scaleMode: ScaleMode = ScaleMode.FIT,
    /** 0 = use the game's own speed. */
    val fpsCap: Int = 0,
    val frameSkip: Boolean = true,
    val smoothScaling: Boolean = false,
    val showFps: Boolean = false,
    val controlsOpacity: Float = 0.6f,
)

data class Game(
    val id: String,
    /** Real folder path of the game (the folder that contains Game.ini). */
    val path: String,
    val title: String,
    val rgss: Rgss,
    val rtp: String,
    val addedAt: Long,
    /** True when the game was unpacked from a .zip into app storage (we own those files). */
    val extracted: Boolean = false,
    /** Root of the extracted copy, deleted together with the game. */
    val extractRoot: String = "",
    val lastPlayed: Long = 0L,
    val settings: GameSettings = GameSettings(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("path", path)
        put("title", title)
        put("rgss", rgss.name)
        put("rtp", rtp)
        put("addedAt", addedAt)
        put("extracted", extracted)
        put("extractRoot", extractRoot)
        put("lastPlayed", lastPlayed)
        put("scaleMode", settings.scaleMode.name)
        put("fpsCap", settings.fpsCap)
        put("frameSkip", settings.frameSkip)
        put("smoothScaling", settings.smoothScaling)
        put("showFps", settings.showFps)
        put("controlsOpacity", settings.controlsOpacity.toDouble())
    }

    companion object {
        fun fromJson(o: JSONObject): Game {
            // Older saves stored a folder URI instead of a path.
            val path = o.optString("path", "").ifBlank {
                o.optString("treeUri", "").takeIf { it.isNotBlank() }
                    ?.let { PathUtils.treeUriToPath(Uri.parse(it)) }
                    .orEmpty()
            }
            return Game(
                id = o.optString("id", path),
                path = path,
                title = o.getString("title"),
                rgss = runCatching { Rgss.valueOf(o.getString("rgss")) }.getOrDefault(Rgss.UNKNOWN),
                rtp = o.optString("rtp", ""),
                addedAt = o.optLong("addedAt", 0L),
                extracted = o.optBoolean("extracted", false),
                extractRoot = o.optString("extractRoot", ""),
                lastPlayed = o.optLong("lastPlayed", 0L),
                settings = GameSettings(
                    scaleMode = runCatching { ScaleMode.valueOf(o.getString("scaleMode")) }
                        .getOrDefault(ScaleMode.FIT),
                    fpsCap = o.optInt("fpsCap", 0).takeIf { it == 0 || it == 30 || it == 60 } ?: 0,
                    frameSkip = o.optBoolean("frameSkip", true),
                    smoothScaling = o.optBoolean("smoothScaling", false),
                    showFps = o.optBoolean("showFps", false),
                    controlsOpacity = o.optDouble("controlsOpacity", 0.6).toFloat(),
                ),
            )
        }
    }
}
