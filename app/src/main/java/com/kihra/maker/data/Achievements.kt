package com.kihra.maker.data

import java.io.File

/** Milestone achievements derived from play time and screenshots, plus unlocks reported by games. */
object Achievements {
    data class Milestone(val title: String, val description: String, val progress: Float) {
        val unlocked: Boolean get() = progress >= 1f
    }

    fun milestones(segments: List<PlayLog.Segment>, shotCount: Int): List<Milestone> {
        val totalH = segments.sumOf { it.end - it.start } / 3_600_000f
        val games = segments.map { it.gameId }.toSet().size
        val streak = PlayLog.streak(segments)
        fun m(title: String, desc: String, value: Float, goal: Float) =
            Milestone(title, desc, (value / goal).coerceIn(0f, 1f))
        return listOf(
            m("First steps", "Play any game", segments.size.toFloat(), 1f),
            m("Getting hooked", "Play 1 hour in total", totalH, 1f),
            m("Marathon", "Play 10 hours in total", totalH, 10f),
            m("Dedicated", "Play 50 hours in total", totalH, 50f),
            m("Explorer", "Play 3 different games", games.toFloat(), 3f),
            m("Photographer", "Take 10 screenshots", shotCount.toFloat(), 10f),
            m("On a roll", "Play 3 days in a row", streak.toFloat(), 3f),
            m("Regular", "Play 7 days in a row", streak.toFloat(), 7f),
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
