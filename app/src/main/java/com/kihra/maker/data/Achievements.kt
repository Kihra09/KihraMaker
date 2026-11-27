package com.kihra.maker.data

import java.io.File

/** Milestone achievements derived from play time and screenshots, plus unlocks reported by games. */
object Achievements {
    data class Milestone(val title: String, val description: String, val progress: Float) {
        val unlocked: Boolean get() = progress >= 1f
    }

    /** Things the app can only know from files and settings, gathered by the UI. */
    data class Flags(
        val light: Boolean = false,
        val wallpaper: Boolean = false,
        val goalMinutes: Int = 0,
        val customLayout: Boolean = false,
        val pinnedCheat: Boolean = false,
        val backup: Boolean = false,
        val shotGames: Int = 0,
    )

    /** 50 different achievements, each of its own kind (no "10x / 50x" tiers of the same thing). */
    fun milestones(
        segments: List<PlayLog.Segment>,
        shotCount: Int,
        games: List<Game> = emptyList(),
        flags: Flags = Flags(),
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ): List<Milestone> {
        fun day(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone)
        val totalH = segments.sumOf { it.end - it.start } / 3_600_000f
        val longestMin = (segments.maxOfOrNull { it.end - it.start } ?: 0L) / 60_000f
        val shortest = segments.minOfOrNull { it.end - it.start }
        val byDay = segments.groupBy { day(it.start).toLocalDate() }
        val dayMs = byDay.mapValues { (_, v) -> v.sumOf { it.end - it.start } }
        val daysSet = byDay.keys
        val gamesPlayed = segments.map { it.gameId }.toSet().size
        val perGameDays = segments.groupBy { it.gameId }.mapValues { (_, v) -> v.map { day(it.start).toLocalDate() }.toSet().size }
        val sorted = daysSet.sorted()
        var longestStreak = 0
        var run = 0
        for (i in sorted.indices) {
            run = if (i > 0 && sorted[i - 1].plusDays(1) == sorted[i]) run + 1 else 1
            longestStreak = maxOf(longestStreak, run)
        }
        val comeback = sorted.zipWithNext().any { (a, b) -> java.time.temporal.ChronoUnit.DAYS.between(a, b) >= 7 }
        fun hasHour(from: Int, to: Int) = segments.any { day(it.start).hour in from until to }
        val weekend = daysSet.any { it.dayOfWeek == java.time.DayOfWeek.SATURDAY && it.plusDays(1) in daysSet }
        val fridayNight = segments.any { val d = day(it.start); d.dayOfWeek == java.time.DayOfWeek.FRIDAY && d.hour >= 18 }
        val festive = daysSet.any { (it.monthValue == 12 && it.dayOfMonth == 25) || (it.monthValue == 1 && it.dayOfMonth == 1) }
        val crossMidnight = segments.any { day(it.start).toLocalDate() != day(it.end).toLocalDate() }
        val goalMet = flags.goalMinutes > 0 && dayMs.values.any { it >= flags.goalMinutes * 60_000L }
        val engines = games.map { it.rgss }.filter { it != Rgss.UNKNOWN }.toSet().size
        fun b(v: Boolean) = if (v) 1f else 0f
        fun m(title: String, desc: String, value: Float, goal: Float = 1f) =
            Milestone(title, desc, (value / goal).coerceIn(0f, 1f))
        return listOf(
            m("First steps", "Play any game", segments.size.toFloat()),
            m("Dedicated", "Play 25 hours in total", totalH, 25f),
            m("Marathon runner", "One single session of 2 hours", longestMin, 120f),
            m("Quick peek", "Play for under 5 minutes in one go", b(shortest != null && shortest < 300_000L)),
            m("Back for more", "5 play sessions in one day", (byDay.values.maxOfOrNull { it.size } ?: 0).toFloat(), 5f),
            m("Early bird", "Play between 5 and 8 in the morning", b(hasHour(5, 8))),
            m("Night owl", "Play between midnight and 4 am", b(hasHour(0, 4))),
            m("Lunch break", "Play between 12 and 1 pm", b(hasHour(12, 13))),
            m("Weekend warrior", "Play on a Saturday and the Sunday after", b(weekend)),
            m("Comeback", "Play again after a break of 7 days or more", b(comeback)),
            m("Triple threat", "Play 3 different games in one day", (byDay.values.maxOfOrNull { d -> d.map { it.gameId }.toSet().size } ?: 0).toFloat(), 3f),
            m("Loyal fan", "Play the same game on 10 different days", (perGameDays.values.maxOrNull() ?: 0).toFloat(), 10f),
            m("Explorer", "Play 3 different games", gamesPlayed.toFloat(), 3f),
            m("Game hopper", "Play 10 different games", gamesPlayed.toFloat(), 10f),
            m("All-nighter", "Keep playing past midnight", b(crossMidnight)),
            m("Streaker", "Play 7 days in a row", longestStreak.toFloat(), 7f),
            m("Century", "100 play sessions", segments.size.toFloat(), 100f),
            m("Hour a day", "Play for an hour in a single day", (dayMs.values.maxOrNull() ?: 0L) / 60_000f, 60f),
            m("Full day", "Play 5 hours in a single day", (dayMs.values.maxOrNull() ?: 0L) / 3_600_000f, 5f),
            m("Monday starter", "Play on a Monday", b(daysSet.any { it.dayOfWeek == java.time.DayOfWeek.MONDAY })),
            m("Friday night", "Play on a Friday after 6 pm", b(fridayNight)),
            m("Monthly regular", "Play on 30 different days", daysSet.size.toFloat(), 30f),
            m("Festive", "Play on 25 December or 1 January", b(festive)),
            m("Photographer", "Take 10 screenshots", shotCount.toFloat(), 10f),
            m("Gallery curator", "Take screenshots in 3 different games", flags.shotGames.toFloat(), 3f),
            m("Collector", "Have 5 games in your library", games.size.toFloat(), 5f),
            m("Curator", "Mark 3 games as favorites", games.count { it.favorite }.toFloat(), 3f),
            m("Finisher", "Mark a game as finished", b(games.any { it.status == "finished" })),
            m("Patience", "Put a game on hold", b(games.any { it.status == "hold" })),
            m("Zip master", "Add a game that plays straight from a zip", b(games.any { it.fromZip })),
            m("Web warrior", "Add an RPG Maker MV or MZ game", b(games.any { it.rgss.isWeb })),
            m("Old school", "Add an RPG Maker XP game", b(games.any { it.rgss == Rgss.XP })),
            m("VX fan", "Add an RPG Maker VX game", b(games.any { it.rgss == Rgss.VX })),
            m("Ace of spades", "Add an RPG Maker VX Ace game", b(games.any { it.rgss == Rgss.VX_ACE })),
            m("Engine hoarder", "Have games from 3 different RPG Maker versions", engines.toFloat(), 3f),
            m("Tailor-made", "Finish the setup of a game", b(games.any { it.configured })),
            m("Fully set", "Set up every game (at least 2 games)", if (games.size >= 2 && games.all { it.configured }) 1f else 0f),
            m("Bright side", "Use the light theme", b(flags.light)),
            m("Wallpaper artist", "Set your own wallpaper", b(flags.wallpaper)),
            m("Goal setter", "Set a daily play goal", b(flags.goalMinutes > 0)),
            m("Goal crusher", "Reach your daily play goal", b(goalMet)),
            m("Gesture master", "Set up swipe or tap gestures in a game", b(games.any { it.settings.gestures.isNotBlank() })),
            m("Layout designer", "Save your own button layout", b(flags.customLayout)),
            m("Cheat collector", "Pin a cheat to a game's cheat list", b(flags.pinnedCheat)),
            m("Safety net", "Have a save backup made", b(flags.backup)),
            m("Fixer", "Add a custom Ruby fix to a game", b(games.any { it.settings.customRuby.isNotBlank() })),
            m("Controller pro", "Remap a controller button", b(games.any { it.settings.padMap.isNotBlank() })),
            m("Fast lane", "Set fast forward to 4x or more", b(games.any { it.settings.ffSpeed >= 4 })),
            m("Pixel pusher", "Change a game's internal resolution", b(games.any { it.settings.renderScale != 1f })),
            m("Font fan", "Use the app font inside a game", b(games.any { it.settings.gameFont.isNotBlank() })),
        )
    }

    /**
     * Games that used Steam achievements call a Windows DLL that doesn't exist here. The engine
     * shim records those calls in kihra_ach.log; this reads them back (best effort: every game
     * names its achievements differently).
     */
    fun reportedBy(game: Game): List<String> {
        val f = File(game.path, "kihra_ach.log")
        if (!f.isFile) return emptyList()
        return runCatching {
            f.readLines().mapNotNull { line ->
                val p = line.split('\t')
                if (p.size < 3) null else "${p[1]}(${p[2]})"
            }.distinct()
        }.getOrDefault(emptyList())
    }
}
