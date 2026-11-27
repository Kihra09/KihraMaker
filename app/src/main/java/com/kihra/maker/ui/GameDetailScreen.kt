package com.kihra.maker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.graphics.Color
import com.kihra.maker.data.KeyNames
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kihra.maker.data.Game
import com.kihra.maker.data.GameSettings
import com.kihra.maker.data.PlayLog
import com.kihra.maker.data.ScaleMode
import kotlin.math.roundToInt

class DetailActions(
    val onFavorite: () -> Unit,
    val onStatus: (String) -> Unit,
    val onRename: (String) -> Unit,
    val onPickIcon: () -> Unit,
    val onResetIcon: () -> Unit,
    val onOpenBackups: () -> Unit,
    val onExportSaves: () -> Unit,
    val onImportSaves: () -> Unit,
    val onOpenSetup: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(
    game: Game,
    onBack: () -> Unit,
    onSettingsChange: (GameSettings) -> Unit,
    onPlay: () -> Unit,
    onUnpack: () -> Unit,
    onRemove: () -> Unit,
    playedMs: Long,
    rtpInstalled: Boolean,
    actions: DetailActions,
    backupCount: Int,
) {
    BackHandler(onBack = onBack)
    var confirmRemove by remember { mutableStateOf(false) }
    var editDialog by remember { mutableStateOf(false) }
    val s = game.settings

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(game.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = actions.onFavorite) {
                        Icon(
                            if (game.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (game.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { editDialog = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit game")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!game.rgss.supported) {
                InfoCard(
                    "This doesn't look like a supported RPG Maker game, so it can't be started.",
                    isError = true,
                )
            } else if (game.rtp.isNotBlank() && !rtpInstalled) {
                InfoCard(
                    "This game uses the ${game.rtp} RTP and it isn't installed. If the game can't find its default " +
                        "graphics or sounds, import the RTP in Settings.",
                    isError = true,
                )
            }

            if (game.fromZip) {
                InfoCard(
                    "Playing straight from the zip, nothing is unpacked. Saves and settings are in " +
                        "Android/data/kihra.kihramaker.app/files/saves.",
                    isError = false,
                )
                if (game.zipCompressed) {
                    InfoCard(
                        "This zip is compressed, so loading and music can be slower than from an " +
                            "unpacked copy.",
                        isError = false,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                GameIcon(game, 56.dp)
                Column {
                    Text("Total play time", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    Text(PlayLog.format(playedMs), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                }
            }

            Button(
                onClick = onPlay,
                enabled = game.rgss.supported,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Play", fontWeight = FontWeight.SemiBold)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("" to "No status", "playing" to "Playing", "finished" to "Finished", "hold" to "On hold").forEach { (key, label) ->
                    FilterChip(
                        selected = game.status == key,
                        onClick = { actions.onStatus(key) },
                        label = { Text(label) },
                    )
                }
            }

            OutlinedButton(onClick = actions.onOpenSetup, modifier = Modifier.fillMaxWidth()) {
                Text("Game setup (display, controls, saves, advanced)")
            }

            if (game.fromZip) {
                OutlinedButton(onClick = onUnpack, modifier = Modifier.fillMaxWidth()) {
                    Text("Unpack game (faster, uses more storage)")
                }
            }

            TextButton(onClick = { confirmRemove = true }) {
                Text("Remove from library", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (editDialog) {
        var title by remember(game.id) { mutableStateOf(game.title) }
        AlertDialog(
            onDismissRequest = { editDialog = false },
            title = { Text("Edit game") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Name") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        GameIcon(game, 48.dp)
                        OutlinedButton(onClick = { editDialog = false; actions.onPickIcon() }) { Text("Choose icon") }
                        TextButton(onClick = { editDialog = false; actions.onResetIcon() }) { Text("Reset") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isNotBlank()) actions.onRename(title.trim())
                    editDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editDialog = false }) { Text("Cancel") } },
        )
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove game?") },
            text = {
                Text(
                    when {
                        game.extracted -> "This deletes the unpacked copy made from your zip, including its save files. The zip itself is not touched."
                        game.fromZip -> "This only removes it from the library. The zip and your saves stay where they are."
                        else -> "This only removes it from the library. Your game files stay untouched."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; onRemove() }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
internal fun InfoCard(text: String, isError: Boolean) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Text(
            text,
            modifier = Modifier.padding(16.dp),
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}
