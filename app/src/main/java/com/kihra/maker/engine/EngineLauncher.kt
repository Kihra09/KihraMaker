package com.kihra.maker.engine

import android.content.Context
import android.content.Intent
import com.kihra.maker.data.Game
import com.kihra.maker.data.ScaleMode
import com.kihra.maker.data.ZipImporter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object EngineLauncher {
    const val EXTRA_PATH = "path"
    const val EXTRA_TITLE = "title"
    const val EXTRA_CONTROLS_OPACITY = "controlsOpacity"

    private const val MARKER = "_generatedBy"
    private const val MARKER_VALUE = "KihraMaker"

    /** Returns an error message, or null when the game was started. */
    fun launch(context: Context, game: Game, rtpPath: String): String? {
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
        writeConfig(dir, game, rtps, mustOwn = game.fromZip)?.let { return it }

        context.startActivity(
            Intent(context, GameActivity::class.java)
                .putExtra(EXTRA_PATH, game.path)
                .putExtra(EXTRA_TITLE, game.title)
                .putExtra(EXTRA_CONTROLS_OPACITY, game.settings.controlsOpacity)
        )
        return null
    }

    /**
     * The engine reads mkxp.json from its working folder. For a normal game folder we only write (or
     * update) the file when it is missing or was made by us, so a config the game or the player
     * supplied is never overwritten. A zip game's save folder is ours, so it is always rewritten.
     */
    private fun writeConfig(dir: File, game: Game, rtps: List<String>, mustOwn: Boolean): String? {
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
        }
        return runCatching { file.writeText(json.toString(2)) }.exceptionOrNull()
            ?.let { "Can't write the game's settings file (is the folder read-only?)." }
    }
}
