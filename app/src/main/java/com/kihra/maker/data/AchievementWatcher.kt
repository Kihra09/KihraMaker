package com.kihra.maker.data

import android.content.Context
import java.io.File

/**
 * Finds achievements that were unlocked since the last check, so the app (and the game screen) can
 * pop one up like RetroAchievements. The first check only records what is already unlocked.
 */
object AchievementWatcher {
    private fun file(context: Context) = File(AppPaths.base(context), "ach_unlocked.txt")

    fun flags(context: Context, games: List<Game>, shots: List<Shots.Shot>, settings: AppSettings): Achievements.Flags {
        val files = File(AppPaths.base(context), "controls").listFiles()?.toList().orEmpty()
        return Achievements.Flags(
            light = settings.light,
            wallpaper = AppSettings.wallpaperFile(context).isFile,
            goalMinutes = settings.goalMinutes,
            customLayout = files.any { it.name.endsWith(".json") },
            pinnedCheat = files.any { it.name.endsWith(".pins") && it.length() > 0 },
            backup = games.any { SaveBackup.list(context, it).isNotEmpty() },
            shotGames = shots.map { it.gameKey }.toSet().size,
        )
    }

    /** Titles of achievements unlocked since the previous call. Safe to call from any thread. */
    @JvmStatic
    @Synchronized
    fun check(context: Context): Array<String> = runCatching {
        val games = GameStore(context).load()
        val shots = Shots.list(context)
        val settings = AppSettings.load(context)
        val ms = Achievements.milestones(PlayLog.read(context), shots.size, games, flags(context, games, shots, settings))
        val unlocked = ms.filter { it.unlocked }.map { it.title }.toSet()
        val f = file(context)
        if (!f.isFile) {
            f.writeText(unlocked.joinToString("\n"))
            return@runCatching emptyArray<String>()
        }
        val old = f.readLines().toSet()
        val fresh = unlocked - old
        if (fresh.isNotEmpty()) f.writeText((old + unlocked).joinToString("\n"))
        fresh.toTypedArray()
    }.getOrDefault(emptyArray())
}
