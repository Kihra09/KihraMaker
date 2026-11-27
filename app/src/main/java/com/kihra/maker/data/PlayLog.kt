package com.kihra.maker.data

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Play sessions, one line per play segment: "<game id>\t<start ms>\t<end ms>".
 * Written by the game process every time the game goes to the background or closes.
 */
object PlayLog {
    data class Segment(val gameId: String, val start: Long, val end: Long)

    private fun file(context: Context) = File(AppPaths.base(context), "playtime.log")

    @JvmStatic
    fun append(context: Context, gameId: String, startMs: Long, endMs: Long) {
        if (gameId.isBlank() || endMs - startMs < 1000) return
        runCatching {
            file(context).appendText("${gameId.replace('\t', ' ').replace('\n', ' ')}\t$startMs\t$endMs\n")
        }
    }

    fun read(context: Context): List<Segment> {
        val f = file(context)
        if (!f.isFile) return emptyList()
        return runCatching {
            f.readLines().mapNotNull { line ->
                val p = line.split('\t')
                if (p.size != 3) return@mapNotNull null
                val s = p[1].toLongOrNull() ?: return@mapNotNull null
                val e = p[2].toLongOrNull() ?: return@mapNotNull null
                if (e <= s) null else Segment(p[0], s, e)
            }
        }.getOrDefault(emptyList())
    }

    /** Total milliseconds per game id. */
    fun totals(segments: List<Segment>): Map<String, Long> =
        segments.groupBy { it.gameId }.mapValues { (_, v) -> v.sumOf { it.end - it.start } }

    /** Milliseconds per day and game, for the last [days] days (today included), oldest first. */
    fun perDay(segments: List<Segment>, days: Int, zone: ZoneId = ZoneId.systemDefault()): List<Pair<LocalDate, Map<String, Long>>> {
        val today = LocalDate.now(zone)
        val first = today.minusDays((days - 1).toLong())
        val table = HashMap<LocalDate, HashMap<String, Long>>()
        for (seg in segments) {
            var cursor = seg.start
            while (cursor < seg.end) {
                val day = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
                val nextMidnight = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val stop = minOf(seg.end, nextMidnight)
                if (!day.isBefore(first) && !day.isAfter(today)) {
                    val m = table.getOrPut(day) { HashMap() }
                    m[seg.gameId] = (m[seg.gameId] ?: 0L) + (stop - cursor)
                }
                cursor = stop
            }
        }
        return (0 until days).map { i ->
            val d = first.plusDays(i.toLong())
            d to (table[d] ?: emptyMap())
        }
    }

    /** Number of consecutive days up to today (or yesterday) with any play time. */
    fun streak(segments: List<Segment>, zone: ZoneId = ZoneId.systemDefault()): Int {
        val played = HashSet<LocalDate>()
        for (seg in segments) {
            played += Instant.ofEpochMilli(seg.start).atZone(zone).toLocalDate()
            played += Instant.ofEpochMilli(seg.end).atZone(zone).toLocalDate()
        }
        var d = LocalDate.now(zone)
        if (d !in played) d = d.minusDays(1)
        var n = 0
        while (d in played) { n++; d = d.minusDays(1) }
        return n
    }

    fun format(ms: Long): String {
        val minutes = ms / 60_000
        return when {
            ms <= 0 -> "Not played yet"
            minutes < 1 -> "Under 1 min"
            minutes < 60 -> "$minutes min"
            else -> "${minutes / 60} h ${minutes % 60} min"
        }
    }
}
