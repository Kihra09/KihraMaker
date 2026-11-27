package com.kihra.maker.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kihra.maker.data.Game
import com.kihra.maker.data.PlayLog
import com.kihra.maker.data.Shots
import com.kihra.maker.data.ViewMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    games: List<Game>,
    onAddFolder: () -> Unit,
    onAddZip: () -> Unit,
    onOpen: (Game) -> Unit,
    playtime: Map<String, Long>,
    daysPlayed: Map<String, Int>,
    shotsByKey: Map<String, List<Shots.Shot>>,
    view: ViewMode,
    onViewChange: (ViewMode) -> Unit,
    showHint: Boolean,
    onDismissHint: () -> Unit,
    onOpenGallery: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var addMenu by remember { mutableStateOf(false) }
    var viewMenu by remember { mutableStateOf(false) }
    val shown = remember(games, query) {
        games
            .filter { it.title.contains(query, ignoreCase = true) }
            .sortedByDescending { maxOf(it.lastPlayed, it.addedAt) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Kihra Maker", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onOpenGallery) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = "Screenshot gallery")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { viewMenu = true }) {
                            Icon(Icons.Filled.GridView, contentDescription = "View")
                        }
                        DropdownMenu(expanded = viewMenu, onDismissRequest = { viewMenu = false }) {
                            ViewMode.values().forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m.label + if (m == view) "  ✓" else "") },
                                    onClick = { viewMenu = false; onViewChange(m) },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(
                    onClick = { addMenu = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add game") },
                )
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Game folder") },
                        leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                        onClick = { addMenu = false; onAddFolder() },
                    )
                    DropdownMenuItem(
                        text = { Text("Zip file") },
                        leadingIcon = { Icon(Icons.Filled.Archive, contentDescription = null) },
                        onClick = { addMenu = false; onAddZip() },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (games.isEmpty()) {
                EmptyState(onAddFolder, onAddZip)
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search games") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (showHint) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Swipe right (or tap Assistant below) to see your play time, daily charts and achievements.",
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            TextButton(onClick = onDismissHint) { Text("Got it") }
                        }
                    }
                }
                when (view) {
                    ViewMode.LIST -> LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(shown, key = { it.id }) { g ->
                            GameRow(g, playtime[g.id] ?: 0L, daysPlayed[g.id] ?: 0) { onOpen(g) }
                        }
                    }
                    ViewMode.GRID -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(shown, key = { it.id }) { g ->
                            GameTile(g, playtime[g.id] ?: 0L, daysPlayed[g.id] ?: 0, shotsByKey[Shots.key(g.id)]?.firstOrNull()) { onOpen(g) }
                        }
                    }
                    ViewMode.CARDS -> LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(shown, key = { it.id }) { g ->
                            GameCardBig(g, playtime[g.id] ?: 0L, daysPlayed[g.id] ?: 0, shotsByKey[Shots.key(g.id)] ?: emptyList()) { onOpen(g) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onAddFolder: () -> Unit, onAddZip: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "No games yet",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Add a game folder (the one with Game.ini inside) or a .zip file of the game.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAddFolder) { Text("Game folder") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onAddZip) { Text("Zip file") }
    }
}

private fun lastPlayedText(ms: Long): String {
    if (ms <= 0) return "Never played"
    val days = (System.currentTimeMillis() - ms) / 86_400_000L
    return when {
        days <= 0L -> "Played today"
        days == 1L -> "Played yesterday"
        else -> "Played $days days ago"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameRow(game: Game, playedMs: Long, days: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GameIconWithBadge(game, 68.dp, days)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(game.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (game.rgss.supported) game.rgss.label else "Not an RPG Maker XP/VX/Ace game",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (game.rgss.supported) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        PlayLog.format(playedMs) + "  ·  " + lastPlayedText(game.lastPlayed),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameTile(game: Game, playedMs: Long, days: Int, shot: Shots.Shot?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.25f).background(MaterialTheme.colorScheme.surfaceVariant)) {
            val bmp = rememberImage(shot?.file, 480)
            if (bmp != null) {
                Image(bitmap = bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { GameIcon(game, 72.dp) }
            }
            if (days > 0) FlameBadge(days, Modifier.align(Alignment.TopStart))
        }
        Column(Modifier.padding(12.dp)) {
            Text(game.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                PlayLog.format(playedMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameCardBig(game: Game, playedMs: Long, days: Int, shots: List<Shots.Shot>, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.7f).background(MaterialTheme.colorScheme.surfaceVariant)) {
            val bmp = rememberImage(shots.firstOrNull()?.file, 900)
            if (bmp != null) {
                Image(bitmap = bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { GameIcon(game, 96.dp) }
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp),
            ) {
                Column {
                    Text(game.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    Text(PlayLog.format(playedMs), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                }
            }
            if (days > 0) FlameBadge(days, Modifier.align(Alignment.TopStart))
        }
        if (shots.size > 1) {
            Row(
                Modifier.fillMaxWidth().padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                shots.drop(1).take(4).forEach { s ->
                    val b = rememberImage(s.file, 240)
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1.4f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        if (b != null) Image(bitmap = b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                // Keep the thumbnails the same size when there are fewer than four.
                repeat(4 - shots.drop(1).take(4).size) { Spacer(Modifier.weight(1f)) }
            }
        } else {
            Spacer(Modifier.height(4.dp))
        }
    }
}
