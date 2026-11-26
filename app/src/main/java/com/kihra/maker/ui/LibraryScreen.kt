package com.kihra.maker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kihra.maker.data.Game
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    games: List<Game>,
    rtpPath: String,
    onAddFolder: () -> Unit,
    onAddZip: () -> Unit,
    onSetRtp: () -> Unit,
    onOpen: (Game) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var addMenu by remember { mutableStateOf(false) }
    var rtpDialog by remember { mutableStateOf(false) }
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
                actions = {
                    IconButton(onClick = { rtpDialog = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "RTP folder")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
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
        if (rtpDialog) {
            AlertDialog(
                onDismissRequest = { rtpDialog = false },
                title = { Text("RTP folder") },
                text = {
                    Text(
                        if (rtpPath.isBlank()) {
                            "Many games need the RPG Maker VX Ace RTP (Runtime Package) for their default " +
                                "graphics and sounds. Pick the folder that contains it, for example RPGVXAce. " +
                                "Games that ship their own files don't need this."
                        } else {
                            "Current: $rtpPath"
                        }
                    )
                },
                confirmButton = {
                    TextButton(onClick = { rtpDialog = false; onSetRtp() }) { Text("Choose folder") }
                },
                dismissButton = {
                    TextButton(onClick = { rtpDialog = false }) { Text("Close") }
                },
            )
        }
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
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(shown, key = { it.id }) { game ->
                        GameCard(game) { onOpen(game) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameCard(game: Game, onClick: () -> Unit) {
    val hue = (abs(game.title.hashCode()) % 360).toFloat()
    val top = Color.hsv(hue, 0.45f, 0.62f)
    val bottom = Color.hsv((hue + 40f) % 360f, 0.55f, 0.30f)

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.25f)
                .background(Brush.verticalGradient(listOf(top, bottom))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = game.title.firstOrNull()?.uppercase() ?: "?",
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.9f),
            )
        }
        Column(Modifier.padding(12.dp)) {
            Text(
                game.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (game.rgss.supported) game.rgss.label else "Not an RPG Maker XP/VX/Ace game",
                style = MaterialTheme.typography.labelSmall,
                color = if (game.rgss.supported) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
    }
}
