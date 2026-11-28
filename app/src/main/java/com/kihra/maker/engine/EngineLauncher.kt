package com.kihra.maker.engine

import android.content.Context
import android.content.Intent
import com.kihra.maker.data.Game
import com.kihra.maker.data.Rgss
import com.kihra.maker.data.ScaleMode
import com.kihra.maker.data.Shots
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
    const val EXTRA_JOYSTICK = "joystick"
    const val EXTRA_PADMAP = "padMap"
    const val EXTRA_GESTURES = "gestures"
    const val EXTRA_FF_SPEED = "ffSpeed"
    const val EXTRA_LAYOUT = "layout"
    const val EXTRA_SHOW_FPS = "showFps"
    const val EXTRA_RENDER_SCALE = "renderScale"

    private const val MARKER = "_generatedBy"
    private const val MARKER_VALUE = "KihraMaker"

    /** Returns an error message, or null when the game was started. */
    fun launch(
        context: Context,
        game: Game,
        rtpPath: String,
        wallpaperPath: String = "",
        safe: Boolean = false,
        sharedLayout: Boolean = false,
    ): String? {
        runCatching { File(game.path, "kihra_boot.txt").delete() }
        if (game.rgss.isWeb) return launchWeb(context, game, wallpaperPath, sharedLayout)
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
        gameFontDir(context, game.settings.gameFont)?.let { rtps.add(0, it) }
        writeConfig(context, dir, game, rtps, mustOwn = game.fromZip, safe = safe || game.settings.safeMode)?.let { return it }

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
                .putExtra(EXTRA_JOYSTICK, game.settings.joystick)
                .putExtra(EXTRA_PADMAP, game.settings.padMap)
                .putExtra(EXTRA_GESTURES, game.settings.gestures)
                .putExtra(EXTRA_FF_SPEED, game.settings.ffSpeed.toFloat())
                .putExtra(EXTRA_RENDER_SCALE, game.settings.renderScale)
                .putExtra(EXTRA_LAYOUT, if (sharedLayout) "shared" else Shots.key(game.id))
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
    private fun writeConfig(context: Context, dir: File, game: Game, rtps: List<String>, mustOwn: Boolean, safe: Boolean = false): String? {
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
            // Safe mode: no KihraMaker script at all, just the plain engine.
            if (!safe) put("preloadScript", JSONArray(listOf(SHIM_NAME)))
            fontFamily(s.gameFont)?.takeIf { !safe }?.let {
                put("defaultFontFamily", it)
                put("fontSub", JSONArray(listOf("Arial>$it", "MS Gothic>$it", "MS PGothic>$it", "VL Gothic>$it", "Verdana>$it", "Tahoma>$it")))
            }
        }
        return runCatching {
            File(dir, SHIM_NAME).writeText(shimFor(context, game))
            file.writeText(json.toString(2))
        }.exceptionOrNull()
            ?.let { "Can't write the game's settings file (is the folder read-only?)." }
    }

    private fun launchWeb(context: Context, game: Game, wallpaperPath: String, sharedLayout: Boolean): String? {
        val dir = File(game.path)
        if (!dir.isDirectory) return "The game folder is missing: ${game.path}"
        val st = game.settings
        val intent = Intent(context, WebGameActivity::class.java)
            .putExtra(EXTRA_PATH, game.path)
            .putExtra(EXTRA_TITLE, game.title)
            .putExtra(EXTRA_GAME_ID, game.id)
            .putExtra(EXTRA_CONTROLS_OPACITY, st.controlsOpacity)
            .putExtra(EXTRA_CONTROLS_SCALE, st.controlsScale)
            .putExtra(EXTRA_DIAGONAL, st.diagonalPad)
            .putExtra(EXTRA_HAPTICS, st.haptics)
            .putExtra(EXTRA_JOYSTICK, st.joystick)
            .putExtra(EXTRA_GESTURES, st.gestures)
            .putExtra(EXTRA_FF_SPEED, st.ffSpeed.toFloat())
            .putExtra(EXTRA_SHOW_FPS, st.showFps)
            .putExtra(EXTRA_WALLPAPER, wallpaperPath)
            .putExtra(EXTRA_LAYOUT, if (sharedLayout) "shared" else Shots.key(game.id))
            .putExtra(EXTRA_FILL, st.scaleMode == ScaleMode.STRETCH)
        return runCatching { context.startActivity(intent); null }
            .getOrElse { "Couldn't start the game: ${it.message}" }
    }

    private fun fontFamily(id: String) = when (id) {
        "fira" -> "Fira Sans"
        "mplus" -> "M PLUS 1 Code"
        else -> null
    }

    /** App-owned folder with a Fonts/ directory, mounted like an RTP so the game can find the font. */
    private fun gameFontDir(context: Context, id: String): String? {
        val family = fontFamily(id) ?: return null
        val asset = if (id == "fira") "fonts/FiraSans-Regular.ttf" else "fonts/MPLUS1Code-Regular.ttf"
        val root = File(context.filesDir, "kihra_fonts")
        val target = File(root, "Fonts/$family.ttf")
        return runCatching {
            if (!target.isFile) {
                target.parentFile?.mkdirs()
                context.assets.open(asset).use { i -> target.outputStream().use { o -> i.copyTo(o) } }
            }
            root.absolutePath
        }.getOrNull()
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
            .replace("__RGSS__", version.toString()) +
            (if (game.settings.customRuby.isNotBlank()) {
                // Evaluated inside begin/rescue so a typo in a custom fix can't stop the game.
                "\n# ---- Custom fixes for this game ----\nbegin\n  eval(<<'KIHRA_CUSTOM_END')\n" +
                    game.settings.customRuby.trimEnd() + "\nKIHRA_CUSTOM_END\nrescue Exception\nend\n"
            } else "")
    }
}
