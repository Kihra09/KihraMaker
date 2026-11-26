package com.kihra.maker.engine

import android.content.Context
import android.content.Intent
import com.kihra.maker.data.Game
import com.kihra.maker.data.ScaleMode
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
        if (!dir.isDirectory) return "The game folder is missing: ${game.path}"
        writeConfig(dir, game, rtpPath)?.let { return it }

        context.startActivity(
            Intent(context, GameActivity::class.java)
                .putExtra(EXTRA_PATH, game.path)
                .putExtra(EXTRA_TITLE, game.title)
                .putExtra(EXTRA_CONTROLS_OPACITY, game.settings.controlsOpacity)
        )
        return null
    }

    /**
     * The engine reads mkxp.json from the game folder. We only write (or update) the file when it is
     * missing or was made by us, so a config the game or the player supplied is never overwritten.
     */
    private fun writeConfig(dir: File, game: Game, rtpPath: String): String? {
        val file = File(dir, "mkxp.json")
        if (file.exists()) {
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
            if (rtpPath.isNotBlank()) put("RTP", JSONArray().put(rtpPath))
        }
        return runCatching { file.writeText(json.toString(2)) }.exceptionOrNull()
            ?.let { "Can't write the game's settings file (is the folder read-only?)." }
    }
}
