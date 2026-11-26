package com.kihra.maker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kihra.maker.data.Achievements
import com.kihra.maker.data.Game
import com.kihra.maker.data.PlayLog
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** The "Game assistant": play time per day and game, top games, achievements. Swipe right from the library. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    games: List<Game>,
    segments: List<PlayLog.Segment>,
    shotCount: Int,
) {
    var days by remember { mutableStateOf(7) }
    var selected by remember { mutableStateOf<Int?>(null) }

    val totals = remember(segments) { PlayLog.totals(segments) }
    val perDay = remember(segments, days) { PlayLog.perDay(segments, days) }
    val byId = remember(games) { games.associateBy { it.id } }
    // Colour per game, stable: ordered by all-time play time.
    val order = remember(totals) { totals.entries.sortedByDescending { it.value }.map { it.key } }
    fun colorOf(id: String) = ChartColors[order.indexOf(id).coerceAtLeast(0) % ChartColors.size]

    val todayMs = perDay.lastOrNull()?.second?.values?.sum() ?: 0L
    val allMs = totals.values.sum()
    val streak = remember(segments) { PlayLog.streak(segments) }
    val milestones = remember(segments, shotCount) { Achievements.milestones(segments, shotCount) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Game assistant", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Today", PlayLog.format(todayMs).replace("Not played yet", "0 min"), Modifier.weight(1f))
                    StatCard("All time", PlayLog.format(allMs).replace("Not played yet", "0 min"), Modifier.weight(1f))
                    StatCard("Day streak", "$streak", Modifier.weight(1f))
                }
            }

            item {
                Text("Daily play time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, 14, 30).forEach { n ->
                        FilterChip(
                            selected = days == n,
                            onClick = { days = n; selected = null },
                            label = { Text("$n days") },
                        )
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        val maxMs = maxOf(perDay.maxOfOrNull { it.second.values.sum() } ?: 0L, 30 * 60_000L)
                        Text(
                            "Scale up to ${PlayLog.format(maxMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (days > 14) 2.dp else 6.dp)) {
                            perDay.forEachIndexed { i, (date, map) ->
                                Column(
                                    Modifier.weight(1f).clickable { selected = if (selected == i) null else i },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    DayBar(map, maxMs, order, ::colorOf, selected == i)
                                    val step = if (days <= 7) 1 else if (days <= 14) 2 else 5
                                    Text(
                                        if (i % step == 0 || i == perDay.lastIndex) dayLabel(date, days) else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        softWrap = false,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        val sel = selected?.let { perDay.getOrNull(it) }
                        if (sel != null) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                sel.first.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + sel.first.dayOfMonth,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (sel.second.isEmpty()) {
                                Text("No play time", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                sel.second.entries.sortedByDescending { it.value }.forEach { (id, ms) ->
                                    LegendRow(byId[id], colorOf(id), PlayLog.format(ms))
                                }
                            }
                        }
                    }
                }
            }

            val inRange = perDay.flatMap { it.second.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }
            if (inRange.isNotEmpty()) {
                item {
                    Text("Last $days days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                items(inRange.entries.sortedByDescending { it.value }.toList().size) { idx ->
                    val e = inRange.entries.sortedByDescending { it.value }[idx]
                    GameTimeRow(byId[e.key], colorOf(e.key), e.value, inRange.values.max())
                }
            } else {
                item {
                    Text(
                        "No play time in this period yet. Play a game and it shows up here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                Text("Achievements", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${milestones.count { it.unlocked }} of ${milestones.size} unlocked",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(milestones.size) { i -> MilestoneRow(milestones[i]) }

            val reported = games.map { it to Achievements.reportedBy(it) }.filter { it.second.isNotEmpty() }
            if (reported.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(6.dp))
                    Text("Unlocked inside games", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Steam achievements can't run on Android, so these are the unlocks games tried to send.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(reported.size) { i ->
                    val (game, list) = reported[i]
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GameIcon(game, 32.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(game.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.height(6.dp))
                            list.take(12).forEach {
                                Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (list.size > 12) Text("+ ${list.size - 12} more", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

private fun dayLabel(date: LocalDate, days: Int): String =
    if (days <= 7) date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3)
    else date.dayOfMonth.toString()

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.SemiBold, maxLines = 2)
        }
    }
}

@Composable
private fun DayBar(
    perGame: Map<String, Long>,
    maxMs: Long,
    order: List<String>,
    colorOf: (String) -> Color,
    highlighted: Boolean,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        drawRect(
            color = if (highlighted) track else track.copy(alpha = 0.45f),
            size = Size(size.width, size.height),
        )
        var bottom = size.height
        // Biggest game at the bottom of the stack.
        perGame.entries.sortedBy { order.indexOf(it.key) }.forEach { (id, ms) ->
            val h = size.height * (ms.toFloat() / maxMs.toFloat())
            if (h > 0f) {
                drawRect(color = colorOf(id), topLeft = Offset(0f, bottom - h), size = Size(size.width, h))
                bottom -= h
            }
        }
    }
}

@Composable
private fun LegendRow(game: Game?, color: Color, text: String) {
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        GameIcon(game, 24.dp)
        Spacer(Modifier.width(8.dp))
        Text(game?.title ?: "Removed game", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GameTimeRow(game: Game?, color: Color, ms: Long, maxMs: Long) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            GameIcon(game, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row {
                    Text(game?.title ?: "Removed game", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(PlayLog.format(ms), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (ms.toFloat() / maxMs.toFloat()).coerceIn(0f, 1f) },
                    color = color,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun MilestoneRow(m: Achievements.Milestone) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = if (m.unlocked) Color(0xFFFFC107) else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.title, fontWeight = FontWeight.SemiBold)
                Text(m.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!m.unlocked) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { m.progress }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
