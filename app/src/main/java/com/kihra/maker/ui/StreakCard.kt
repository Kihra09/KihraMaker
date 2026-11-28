package com.kihra.maker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kihra.maker.data.Game
import com.kihra.maker.data.PlayLog
import com.kihra.maker.data.Streaks
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private class Tokens(dark: Boolean) {
    val surface = if (dark) Color(0xFF1E1F20) else Color(0xFFF3F4F6)
    val head = if (dark) Color(0xFFE3E3E3) else Color(0xFF1F1F1F)
    val sub = if (dark) Color(0xFFC4C7C5) else Color(0xFF444746)
    val errText = if (dark) Color(0xFFF28B82) else Color(0xFFB3261E)
    val errBg = if (dark) Color(0xFF3C1E1E) else Color(0xFFF9DEDC)
    val goldText = if (dark) Color(0xFFFCD34D) else Color(0xFF92400E)
    val goldBg = if (dark) Color(0xFF3B2E08) else Color(0xFFFEF3C7)
    val brand = if (dark) Color(0xFF00A86B) else Color(0xFF01875F)
}

/** The "Gaming streaks" card: 7-day tracker, status line with action, longest streak and next reward. */
@Composable
fun StreakCard(
    segments: List<PlayLog.Segment>,
    state: Streaks.State,
    games: List<Game>,
    onState: (Streaks.State) -> Unit,
) {
    val t = Tokens(MaterialTheme.colorScheme.background.luminance() < 0.5f)
    val accent = t.brand
    val today = LocalDate.now()
    val now = System.currentTimeMillis()
    val played = remember(segments, state) { Streaks.playedDays(segments, state) }
    val totals = remember(segments) { Streaks.dayTotals(segments) }
    val streak = remember(played) { Streaks.streakAt(played, today) }
    val broken = remember(played) { Streaks.brokenDays(played, today) }
    val mode = Streaks.mode(streak, broken, state, now)
    val longest = remember(played) { Streaks.longest(played) }
    val firstDay = played.minOrNull()

    var weekOffset by remember { mutableStateOf(0) }
    var picked by remember { mutableStateOf<LocalDate?>(null) }
    val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).plusWeeks(weekOffset.toLong())
    val week = (0..6).map { start.plusDays(it.toLong()) }
    val perGame = remember(segments, games) { games.associate { it.id to Streaks.dayTotals(segments, it.id) } }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(t.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Zone A
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (streak > 0) "Gaming streaks" else "Build your streak",
                color = t.head, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
            )
            Text("<", color = t.sub, modifier = Modifier.clickable { weekOffset-- }.padding(8.dp))
            val f = { d: LocalDate -> d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + d.dayOfMonth }
            Text(f(week.first()) + " - " + f(week.last()), color = t.sub, fontSize = 12.sp)
            Text(">", color = if (weekOffset < 0) t.sub else t.sub.copy(alpha = 0.3f),
                modifier = Modifier.clickable(enabled = weekOffset < 0) { weekOffset++ }.padding(8.dp))
        }

        // Zone B
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            week.forEach { d ->
                val ms = totals[d] ?: 0L
                val isPlayed = d in played
                val isToday = d == today
                val future = d.isAfter(today)
                val missed = !isPlayed && !future && !isToday && firstDay != null && d.isAfter(firstDay)
                val milestone = isPlayed && Streaks.streakAt(played, d) in Streaks.MILESTONES
                val shape = RoundedCornerShape(10.dp)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    val mod = Modifier.fillMaxWidth().height(38.dp).clip(shape).clickable { picked = if (picked == d) null else d }
                    val missedFirst = broken.isNotEmpty() && d == broken.first()
                    val plus = mode == Streaks.Mode.BROKEN && streak == 0 && isToday && !isPlayed
                    val top = if (isPlayed) games.maxByOrNull { g -> perGame[g.id]?.get(d) ?: 0L }
                        ?.takeIf { g -> (perGame[g.id]?.get(d) ?: 0L) > 0 } else null
                    Box(
                        when {
                            milestone -> mod.background(t.goldBg).border(BorderStroke(1.5.dp, t.goldText), shape)
                            isPlayed -> mod.background(accent)
                            missed -> mod.background(t.errBg)
                            isToday -> mod.border(BorderStroke(1.5.dp, accent), shape)
                            else -> mod.border(BorderStroke(1.dp, t.sub.copy(alpha = 0.3f)), shape)
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            isPlayed -> {
                                if (top != null) GameIcon(top, 26.dp) else Text("\u2713", color = Color.White, fontWeight = FontWeight.Bold)
                                if (milestone) Text("\u2605", color = t.goldText, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopEnd).padding(end = 3.dp, top = 1.dp))
                            }
                            missed -> Text("\u2715", color = t.errText)
                            plus -> Text("+", color = accent, fontWeight = FontWeight.Bold)
                            isToday -> Text("${maxOf(0L, 5L - ms / 60_000L)}m", color = accent, fontSize = 11.sp)
                            else -> Text("\u2014", color = t.sub.copy(alpha = 0.5f))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        color = if (isToday) accent else if (missed) t.errText else t.sub,
                        fontSize = 11.sp, fontWeight = if (isToday || missed) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
        picked?.let { d ->
            val list = games.filter { g -> (perGame[g.id]?.get(d) ?: 0L) > 0 }
            Text(
                d.toString() + ": " + if (list.isEmpty()) "no play" else list.joinToString { g ->
                    g.title + " (" + PlayLog.format(perGame[g.id]?.get(d) ?: 0L) + ")"
                },
                color = t.sub, fontSize = 12.sp,
            )
        }

        // Zone C
        val claim = Streaks.claimable(streak, state, now)
        val headline: String
        val helper: String
        val button: String
        val buttonBg: Color
        val buttonFg: Color
        when (mode) {
            Streaks.Mode.MILESTONE -> {
                headline = "$claim-day milestone reached!"
                helper = "Claim your streak restore reward within 72 hours."
                button = "Claim reward"; buttonBg = t.goldBg; buttonFg = t.goldText
            }
            Streaks.Mode.BROKEN -> {
                val lost = Streaks.streakAt(played, broken.first().minusDays(1))
                if (state.restores > 0) {
                    headline = "Streak broken"
                    helper = "Use a Streak Restore within 7 days to save your $lost-day streak."
                    button = "Restore streak"; buttonBg = accent; buttonFg = Color.White
                } else {
                    headline = "0-day streak"
                    helper = "No restores left. Play any game for 5 min today to start a new streak."
                    button = "See more"; buttonBg = t.sub.copy(alpha = 0.18f); buttonFg = t.head
                }
            }
            Streaks.Mode.ACTIVE -> {
                headline = "$streak-day streak"
                helper = if (today in played) "You reached today's goal. Come back tomorrow." else "Play any game for 5 min today to reach ${streak + 1} days."
                button = "See more"; buttonBg = t.sub.copy(alpha = 0.18f); buttonFg = t.head
            }
            Streaks.Mode.NONE -> {
                headline = "No streak yet"
                helper = "Play at least 5 minutes in a day to start one. One missed day per week is forgiven."
                button = "See more"; buttonBg = t.sub.copy(alpha = 0.18f); buttonFg = t.head
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(headline, color = t.head, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(helper, color = t.sub, fontSize = 13.sp, maxLines = 2)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                button, color = buttonFg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(buttonBg)
                    .clickable {
                        when (mode) {
                            Streaks.Mode.MILESTONE -> claim?.let { onState(Streaks.claim(state, it)) }
                            Streaks.Mode.BROKEN -> if (state.restores > 0) onState(Streaks.restore(state, broken))
                            else -> picked = today
                        }
                    }.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** Zone D: Longest streak and Next reward, shown directly under the card. */
@Composable
fun StreakPills(segments: List<PlayLog.Segment>, state: Streaks.State) {
    val t = Tokens(MaterialTheme.colorScheme.background.luminance() < 0.5f)
    val played = remember(segments, state) { Streaks.playedDays(segments, state) }
    val streak = remember(played) { Streaks.streakAt(played, LocalDate.now()) }
    val longest = remember(played) { Streaks.longest(played) }
    val next = Streaks.nextReward(streak)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Longest streak", "$longest day${if (longest == 1) "" else "s"}", t, Modifier.weight(1f))
        Pill("Next reward", if (next == null) "All claimed" else "$next days", t, Modifier.weight(1f))
    }
}

@Composable
private fun Pill(label: String, value: String, t: Tokens, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(t.sub.copy(alpha = 0.12f)).padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = t.sub, fontSize = 12.sp)
        Text(value, color = t.head, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}
