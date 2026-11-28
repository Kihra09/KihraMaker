package com.kihra.maker.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Streak rules of the Game assistant: a day counts after 5 minutes of play, one missed day per week
 * is forgiven, a broken streak can be restored (costs 1 restore) and milestones give +1 restore.
 */
object Streaks {
    const val DAY_MS = 300_000L
    const val CLAIM_WINDOW_MS = 72L * 3600_000L
    val MILESTONES = listOf(7, 10, 30, 50, 100, 365)

    enum class Mode { MILESTONE, BROKEN, ACTIVE, NONE }

    data class State(
        val restores: Int = 1,
        val restoredDays: Set<LocalDate> = emptySet(),
        val claimed: Set<Int> = emptySet(),
        val firstSeen: Map<Int, Long> = emptyMap(),
    )

    private fun day(ms: Long, zone: ZoneId) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    /** Play time per day (a session across midnight is split), optionally for one game. */
    fun dayTotals(segments: List<PlayLog.Segment>, gameId: String? = null, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, Long> {
        val out = HashMap<LocalDate, Long>()
        for (s in segments) {
            if (gameId != null && s.gameId != gameId) continue
            var start = s.start
            while (start < s.end) {
                val d = day(start, zone)
                val next = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val stop = minOf(s.end, next)
                out[d] = (out[d] ?: 0L) + (stop - start)
                start = stop
            }
        }
        return out
    }

    fun playedDays(segments: List<PlayLog.Segment>, state: State, gameId: String? = null, zone: ZoneId = ZoneId.systemDefault()): Set<LocalDate> =
        dayTotals(segments, gameId, zone).filter { it.value >= DAY_MS }.keys + state.restoredDays

    private fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

    /** Consecutive played days counting back from [today] (today may still be empty); one missed day per week is forgiven. */
    fun streakAt(played: Set<LocalDate>, today: LocalDate): Int {
        var d = if (today in played) today else today.minusDays(1)
        var n = 0
        val forgiven = HashSet<LocalDate>()
        var guard = 0
        while (guard++ < 5000) {
            if (d in played) {
                n++
            } else {
                val w = weekStart(d)
                if (n > 0 && w !in forgiven && d.minusDays(1) in played) forgiven += w else break
            }
            d = d.minusDays(1)
        }
        return n
    }

    fun longest(played: Set<LocalDate>): Int = played.maxOfOrNull { streakAt(played, it) } ?: 0

    /** Run of at least 2 days that ended within the last 7 days, with no play yesterday or today. */
    fun brokenDays(played: Set<LocalDate>, today: LocalDate): List<LocalDate> {
        if (today in played || today.minusDays(1) in played) return emptyList()
        val last = played.filter { it.isBefore(today) }.maxOrNull() ?: return emptyList()
        if (last.isBefore(today.minusDays(7))) return emptyList()
        if (streakAt(played, last) < 2) return emptyList()
        return generateSequence(last.plusDays(1)) { it.plusDays(1) }.takeWhile { it.isBefore(today) }.toList()
    }

    fun mode(streak: Int, broken: List<LocalDate>, state: State, now: Long): Mode {
        val claimable = MILESTONES.any { it <= streak && it !in state.claimed && now - (state.firstSeen[it] ?: now) <= CLAIM_WINDOW_MS }
        return when {
            claimable -> Mode.MILESTONE
            broken.isNotEmpty() -> Mode.BROKEN
            streak > 0 -> Mode.ACTIVE
            else -> Mode.NONE
        }
    }

    fun claimable(streak: Int, state: State, now: Long): Int? =
        MILESTONES.firstOrNull { it <= streak && it !in state.claimed && now - (state.firstSeen[it] ?: now) <= CLAIM_WINDOW_MS }

    fun nextReward(streak: Int): Int? = MILESTONES.firstOrNull { it > streak }

    // ---- storage (streak.json in the app's folder) ----

    private fun file(context: Context) = File(AppPaths.base(context), "streak.json")

    fun load(context: Context): State = runCatching {
        val o = JSONObject(file(context).readText())
        State(
            restores = o.optInt("restores", 1),
            restoredDays = strings(o.optJSONArray("restored")).map { LocalDate.parse(it) }.toSet(),
            claimed = strings(o.optJSONArray("claimed")).map { it.toInt() }.toSet(),
            firstSeen = seen(o.optJSONObject("firstSeen")),
        )
    }.getOrDefault(State())

    fun save(context: Context, s: State) {
        runCatching {
            val o = JSONObject()
                .put("restores", s.restores)
                .put("restored", JSONArray(s.restoredDays.map { it.toString() }))
                .put("claimed", JSONArray(s.claimed.toList()))
                .put("firstSeen", JSONObject().also { j -> s.firstSeen.forEach { (k, v) -> j.put(k.toString(), v) } })
            file(context).writeText(o.toString())
        }
    }

    /** Records when a milestone was first seen (starts its 72 h claim window). */
    fun noteMilestones(streak: Int, s: State, now: Long): State {
        val add = MILESTONES.filter { it <= streak && it !in s.firstSeen }
        return if (add.isEmpty()) s else s.copy(firstSeen = s.firstSeen + add.associateWith { now })
    }

    fun restore(s: State, missed: List<LocalDate>): State =
        if (s.restores <= 0 || missed.isEmpty()) s else s.copy(restores = s.restores - 1, restoredDays = s.restoredDays + missed)

    fun claim(s: State, milestone: Int): State = s.copy(restores = s.restores + 1, claimed = s.claimed + milestone)

    private fun strings(a: JSONArray?): List<String> {
        val out = ArrayList<String>()
        if (a != null) for (i in 0 until a.length()) out.add(a.get(i).toString())
        return out
    }

    private fun seen(o: JSONObject?): Map<Int, Long> {
        val out = HashMap<Int, Long>()
        if (o != null) {
            val it = o.keys()
            while (it.hasNext()) {
                val k = it.next().toString()
                out[k.toInt()] = o.getLong(k)
            }
        }
        return out
    }
}
