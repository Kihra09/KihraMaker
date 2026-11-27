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
    val controlsOpacity: Float = 0.85f,
    /** Analog stick instead of the D-pad. */
    val joystick: Boolean = false,
    /** On-screen controls size in percent (50..150). */
    val controlsScale: Int = 100,
    /** 8-way movement on the on-screen D-pad. */
    val diagonalPad: Boolean = false,
    /** Short vibration when an on-screen button is pressed. */
    val haptics: Boolean = true,
    /** Camera button over the game for taking screenshots. */
    val screenshotButton: Boolean = true,
    /** Extra Ruby run before the game starts (advanced fixes for a single game). */
    val customRuby: String = "",
    /** Physical controller remap: "<controller key code>:<keyboard key code>,..." */
    val padMap: String = "",
    /** Swipe/tap gestures: "up:<key code>,down:...,left:...,right:...,tap:..." */
    val gestures: String = "",
    /** Font used inside the game: "" (game's own), "fira" or "mplus". */
    val gameFont: String = "",
)

data class Game(
    val id: String,
    /**
     * Working folder of the engine. For normal and unpacked games this is the game folder
     * (the one with Game.ini). For games played straight from a zip it is a save folder in app storage.
     */
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
    /** Non-empty when the game is played straight from this .zip without unpacking. */
    val zipPath: String = "",
    /** The zip uses compression, so loading can be slower than from an unpacked game. */
    val zipCompressed: Boolean = false,
    val favorite: Boolean = false,
    /** "", "playing", "finished" or "hold". */
    val status: String = "",
) {
    val fromZip: Boolean get() = zipPath.isNotEmpty()

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
        put("zipPath", zipPath)
        put("zipCompressed", zipCompressed)
        put("favorite", favorite)
        put("status", status)
        put("customRuby", settings.customRuby)
        put("padMap", settings.padMap)
        put("gestures", settings.gestures)
        put("gameFont", settings.gameFont)
        put("scaleMode", settings.scaleMode.name)
        put("fpsCap", settings.fpsCap)
        put("frameSkip", settings.frameSkip)
        put("smoothScaling", settings.smoothScaling)
        put("showFps", settings.showFps)
        put("controlsOpacity", settings.controlsOpacity.toDouble())
        put("controlsScale", settings.controlsScale)
        put("joystick", settings.joystick)
        put("diagonalPad", settings.diagonalPad)
        put("haptics", settings.haptics)
        put("screenshotButton", settings.screenshotButton)
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
                zipPath = o.optString("zipPath", ""),
                zipCompressed = o.optBoolean("zipCompressed", false),
                favorite = o.optBoolean("favorite", false),
                status = o.optString("status", ""),
                settings = GameSettings(
                    scaleMode = runCatching { ScaleMode.valueOf(o.getString("scaleMode")) }
                        .getOrDefault(ScaleMode.FIT),
                    fpsCap = o.optInt("fpsCap", 0).takeIf { it == 0 || it == 30 || it == 60 } ?: 0,
                    frameSkip = o.optBoolean("frameSkip", true),
                    smoothScaling = o.optBoolean("smoothScaling", false),
                    showFps = o.optBoolean("showFps", false),
                    controlsOpacity = o.optDouble("controlsOpacity", 0.85).toFloat(),
                    controlsScale = o.optInt("controlsScale", 100).coerceIn(50, 150),
                    joystick = o.optBoolean("joystick", false),
                    diagonalPad = o.optBoolean("diagonalPad", false),
                    haptics = o.optBoolean("haptics", true),
                    screenshotButton = o.optBoolean("screenshotButton", true),
                    customRuby = o.optString("customRuby", ""),
                    padMap = o.optString("padMap", ""),
                    gestures = o.optString("gestures", ""),
                    gameFont = o.optString("gameFont", ""),
                ),
            )
        }
    }
}
