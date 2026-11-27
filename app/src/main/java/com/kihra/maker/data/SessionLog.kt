package com.kihra.maker.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Keeps a log for every game session, so an error can be read later (or sent to me) instead of
 * screenshotted. The launcher notes which game it started; when the game process is gone, the
 * launcher saves the app's recent system log and backs up the game's saves.
 */
object SessionLog {
    data class Ended(val game: Game?, val title: String, val crashed: Boolean, val log: File?)

    private fun base(context: Context) = AppPaths.base(context)
    private fun pending(context: Context) = File(base(context), "session.pending")
    private fun running(context: Context) = File(base(context), "session.running")

    fun dir(context: Context) = File(base(context), "logs").apply { mkdirs() }

    /** Launcher side: a game is about to start. */
    fun markStart(context: Context, game: Game) {
        runCatching { pending(context).writeText(game.id.replace('\t', ' ') + "\t" + game.title.replace('\t', ' ')) }
    }

    /** Game process side. */
    @JvmStatic
    fun gameStarted(context: Context) {
        runCatching { running(context).writeText("1") }
    }

    @JvmStatic
    fun gameEnded(context: Context) {
        runCatching { running(context).delete() }
    }

    private fun gameProcessAlive(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return am.runningAppProcesses?.any { it.processName == context.packageName + ":game" } == true
    }

    /**
     * If a game session has ended since the last check: saves its log and a backup of its saves.
     * Returns null when nothing ended (or the game is still running).
     */
    fun finish(context: Context, games: List<Game>): Ended? {
        val p = pending(context)
        if (!p.isFile || gameProcessAlive(context)) return null
        val parts = runCatching { p.readText().split('\t') }.getOrDefault(emptyList())
        val id = parts.getOrNull(0).orEmpty()
        val title = parts.getOrNull(1).orEmpty().ifBlank { "game" }
        val crashed = running(context).isFile
        p.delete()
        running(context).delete()
        val game = games.firstOrNull { it.id == id }
        val log = capture(context, title, if (crashed) "closed unexpectedly (crash or force close)" else "closed normally")
        if (game != null) SaveBackup.backup(context, game)
        return Ended(game, title, crashed, log)
    }

    /** Saves the app's recent system log (only this app's lines are visible to it). */
    fun capture(context: Context, title: String, note: String): File? = runCatching {
        val proc = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", "3000")
            .redirectErrorStream(true).start()
        val text = proc.inputStream.bufferedReader().readText()
        proc.waitFor(5, TimeUnit.SECONDS)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val safe = title.replace(Regex("[^A-Za-z0-9._-]"), "_").take(30)
        val file = File(dir(context), "$stamp-$safe.txt")
        val header = buildString {
            appendLine("KihraMaker log")
            appendLine("Game: $title")
            appendLine("Result: $note")
            appendLine("Time: ${Date()}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("----")
        }
        file.writeText(header + text)
        list(context).drop(20).forEach { it.delete() }
        file
    }.getOrNull()

    fun list(context: Context): List<File> =
        (dir(context).listFiles { f -> f.isFile && f.extension == "txt" } ?: emptyArray())
            .sortedByDescending { it.lastModified() }
}
