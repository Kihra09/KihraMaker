package com.kihra.maker.engine

import android.content.Context
import android.content.Intent
import com.kihra.maker.data.Game
import com.kihra.maker.data.Rgss
import com.kihra.maker.data.ScaleMode
import com.kihra.maker.data.ZipImporter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object EngineLauncher {
    const val EXTRA_PATH = "path"
    const val EXTRA_TITLE = "title"
    const val EXTRA_CONTROLS_OPACITY = "controlsOpacity"
    const val EXTRA_GAME_ID = "gameId"
    const val EXTRA_CONTROLS_SCALE = "controlsScale"
    const val EXTRA_DIAGONAL = "diagonalPad"
    const val EXTRA_HAPTICS = "haptics"
    const val EXTRA_SHOT_BUTTON = "screenshotButton"
    const val EXTRA_WALLPAPER = "wallpaper"
    const val EXTRA_ASPECT = "aspect"
    const val EXTRA_FILL = "fill"

    private const val MARKER = "_generatedBy"
    private const val MARKER_VALUE = "KihraMaker"

    /** Returns an error message, or null when the game was started. */
    fun launch(context: Context, game: Game, rtpPath: String, wallpaperPath: String = ""): String? {
        val dir = File(game.path)
        val rtps = mutableListOf<String>()

        if (game.fromZip) {
            // Played straight from the zip: the engine works inside a save folder and sees the
            // zip as an extra archive (mounted before the RTP, so the game's own files win).
            val zip = File(game.zipPath)
            if (!zip.isFile) return "The zip file is missing or was moved: ${game.zipPath}"
            if (!zip.canRead()) return "Can't read the zip. Allow file access for the app and try again."
            dir.mkdirs()
            // The engine reads Game.ini before it mounts anything, so it needs a real copy here.
            if (!ZipImporter.extractIni(zip, File(dir, "Game.ini"))) return "Couldn't read Game.ini from the zip."
            rtps += game.zipPath
        } else if (!dir.isDirectory) {
            return "The game folder is missing: ${game.path}"
        }

        if (rtpPath.isNotBlank()) rtps += rtpPath
        writeConfig(context, dir, game, rtps, mustOwn = game.fromZip)?.let { return it }

        context.startActivity(
            Intent(context, GameActivity::class.java)
                .putExtra(EXTRA_PATH, game.path)
                .putExtra(EXTRA_TITLE, game.title)
                .putExtra(EXTRA_CONTROLS_OPACITY, game.settings.controlsOpacity)
                .putExtra(EXTRA_GAME_ID, game.id)
                .putExtra(EXTRA_CONTROLS_SCALE, game.settings.controlsScale)
                .putExtra(EXTRA_DIAGONAL, game.settings.diagonalPad)
                .putExtra(EXTRA_HAPTICS, game.settings.haptics)
                .putExtra(EXTRA_SHOT_BUTTON, game.settings.screenshotButton)
                .putExtra(EXTRA_WALLPAPER, wallpaperPath)
                // Native game screen size: XP is 640x480, VX and VX Ace are 544x416.
                .putExtra(EXTRA_ASPECT, if (game.rgss == Rgss.XP) 640f / 480f else 544f / 416f)
                .putExtra(EXTRA_FILL, game.settings.scaleMode == ScaleMode.STRETCH)
        )
        return null
    }

    /**
     * The engine reads mkxp.json from its working folder. For a normal game folder we only write (or
     * update) the file when it is missing or was made by us, so a config the game or the player
     * supplied is never overwritten. A zip game's save folder is ours, so it is always rewritten.
     */
    private fun writeConfig(context: Context, dir: File, game: Game, rtps: List<String>, mustOwn: Boolean): String? {
        val file = File(dir, "mkxp.json")
        if (file.exists() && !mustOwn) {
            val ours = runCatching { JSONObject(file.readText()).optString(MARKER) == MARKER_VALUE }
                .getOrDefault(false)
            if (!ours) return null
        }
        val s = game.settings
        val json = JSONObject().apply {
            put(MARKER, MARKER_VALUE)
            put("printFPS", s.showFps)
            put("fixedFramerate", s.fpsCap)
            put("frameSkip", s.frameSkip)
            put("smoothScaling", s.smoothScaling)
            put("fixedAspectRatio", s.scaleMode != ScaleMode.STRETCH)
            put("integerScalingActive", s.scaleMode == ScaleMode.INTEGER)
            put("vsync", false)
            put("enableSettings", false)
            if (rtps.isNotEmpty()) put("RTP", JSONArray(rtps))
            put("preloadScript", JSONArray(listOf(SHIM_NAME)))
        }
        return runCatching {
            File(dir, SHIM_NAME).writeText(shimFor(context, game))
            file.writeText(json.toString(2))
        }.exceptionOrNull()
            ?.let { "Can't write the game's settings file (is the folder read-only?)." }
    }

    private const val SHIM_NAME = "kihra_preload.rb"

    /** The preload script (see assets/kihra_preload.rb) with this game's RGSS version filled in. */
    private fun shimFor(context: Context, game: Game): String {
        val version = when (game.rgss) {
            Rgss.XP -> 1
            Rgss.VX -> 2
            else -> 3
        }
        return context.assets.open("kihra_preload.rb").bufferedReader().use { it.readText() }
            .replace("__RGSS__", version.toString())
    }
}
