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
    var padDialog by remember { mutableStateOf(false) }
    var gestureDialog by remember { mutableStateOf(false) }
    var ruby by remember(game.id) { mutableStateOf(game.settings.customRuby) }
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
                    "Game.ini doesn't point to an RPG Maker XP, VX or VX Ace engine, " +
                        "so this game can't be started.",
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

            SectionTitle("Display")

            Text("Scaling", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScaleMode.values().forEach { mode ->
                    FilterChip(
                        selected = s.scaleMode == mode,
                        onClick = { onSettingsChange(s.copy(scaleMode = mode)) },
                        label = { Text(mode.label) },
                    )
                }
            }

            Text("Frame rate (a cap turns fast forward off)", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Game default", 30 to "30 FPS", 60 to "60 FPS").forEach { (fps, label) ->
                    FilterChip(
                        selected = s.fpsCap == fps,
                        onClick = { onSettingsChange(s.copy(fpsCap = fps)) },
                        label = { Text(label) },
                    )
                }
            }

            SwitchRow(
                "Frame skip (smoother on slow phones)",
                s.frameSkip,
            ) { onSettingsChange(s.copy(frameSkip = it)) }

            SwitchRow(
                "Smooth scaling (blurs pixels)",
                s.smoothScaling,
            ) { onSettingsChange(s.copy(smoothScaling = it)) }

            SwitchRow("Show FPS counter", s.showFps) { onSettingsChange(s.copy(showFps = it)) }

            SectionTitle("Controls")

            Text(
                "On-screen button opacity: ${(s.controlsOpacity * 100).roundToInt()}%",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = s.controlsOpacity,
                onValueChange = { onSettingsChange(s.copy(controlsOpacity = it)) },
                valueRange = 0.1f..1f,
            )

            Text(
                "On-screen button size: ${s.controlsScale}%",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = s.controlsScale.toFloat(),
                onValueChange = { onSettingsChange(s.copy(controlsScale = (it / 5).roundToInt() * 5)) },
                valueRange = 50f..150f,
            )
            SwitchRow("Joystick instead of D-pad", s.joystick) { onSettingsChange(s.copy(joystick = it)) }
            SwitchRow("8-way movement (diagonals)", s.diagonalPad) { onSettingsChange(s.copy(diagonalPad = it)) }
            SwitchRow("Vibrate when pressing buttons", s.haptics) { onSettingsChange(s.copy(haptics = it)) }
            SwitchRow("Screenshot button in game", s.screenshotButton) { onSettingsChange(s.copy(screenshotButton = it)) }

            if (game.fromZip) {
                OutlinedButton(onClick = onUnpack, modifier = Modifier.fillMaxWidth()) {
                    Text("Unpack game (faster, uses more storage)")
                }
            }

            SectionTitle("Saves")
            Text(
                "Backups are made automatically after you play (the last 5 are kept).",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = actions.onOpenBackups, enabled = backupCount > 0) { Text("Backups ($backupCount)") }
                OutlinedButton(onClick = actions.onExportSaves) { Text("Export") }
                OutlinedButton(onClick = actions.onImportSaves) { Text("Import") }
            }

            SectionTitle("Advanced")
            OutlinedButton(onClick = { padDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Controller button mapping")
            }
            OutlinedButton(onClick = { gestureDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Swipe and tap gestures")
            }
            Text("Font inside the game", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("" to "Game's own", "fira" to "Fira Sans", "mplus" to "M PLUS Code").forEach { (id, label) ->
                    FilterChip(selected = s.gameFont == id, onClick = { onSettingsChange(s.copy(gameFont = id)) }, label = { Text(label) })
                }
            }
            Text(
                "Used when the game's own font isn't available. Games that bundle their own font keep it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Custom Ruby fixes for this game (runs before the game starts). Leave empty if unsure.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = ruby,
                onValueChange = { ruby = it },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            if (ruby != game.settings.customRuby) {
                Button(onClick = { onSettingsChange(s.copy(customRuby = ruby)) }) { Text("Apply") }
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

    if (padDialog) {
        val current = remember(game.settings.padMap) { KeyNames.parse(game.settings.padMap) }
        AlertDialog(
            onDismissRequest = { padDialog = false },
            title = { Text("Controller buttons") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Choose which keyboard key each controller button presses. \"Default\" keeps the normal behavior.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    KeyNames.padButtons.forEach { (label, code) ->
                        var open by remember { mutableStateOf(false) }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label)
                            Box {
                                OutlinedButton(onClick = { open = true }) {
                                    Text(current[code]?.let { c -> KeyNames.keys.firstOrNull { it.second == c }?.first } ?: "Default")
                                }
                                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                                    DropdownMenuItem(text = { Text("Default") }, onClick = {
                                        open = false
                                        onSettingsChange(s.copy(padMap = KeyNames.format(current - code)))
                                    })
                                    KeyNames.keys.forEach { (name, keyCode) ->
                                        DropdownMenuItem(text = { Text(name) }, onClick = {
                                            open = false
                                            onSettingsChange(s.copy(padMap = KeyNames.format(current + (code to keyCode))))
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { padDialog = false }) { Text("Done") } },
        )
    }

    if (gestureDialog) {
        val current = remember(game.settings.gestures) {
            game.settings.gestures.split(',').mapNotNull {
                val p = it.split(':')
                val k = p.getOrNull(1)?.toIntOrNull()
                if (p.size == 2 && k != null) p[0] to k else null
            }.toMap()
        }
        fun fmt(m: Map<String, Int>) = m.entries.joinToString(",") { "${it.key}:${it.value}" }
        AlertDialog(
            onDismissRequest = { gestureDialog = false },
            title = { Text("Gestures") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Swipe or tap on the game screen (not on a button) to press a key. Off by default.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    listOf("tap" to "Tap", "up" to "Swipe up", "down" to "Swipe down", "left" to "Swipe left", "right" to "Swipe right").forEach { (id, label) ->
                        var open by remember { mutableStateOf(false) }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label)
                            Box {
                                OutlinedButton(onClick = { open = true }) {
                                    Text(current[id]?.let { c -> KeyNames.keys.firstOrNull { it.second == c }?.first } ?: "Off")
                                }
                                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                                    DropdownMenuItem(text = { Text("Off") }, onClick = {
                                        open = false
                                        onSettingsChange(s.copy(gestures = fmt(current - id)))
                                    })
                                    KeyNames.keys.forEach { (name, keyCode) ->
                                        DropdownMenuItem(text = { Text(name) }, onClick = {
                                            open = false
                                            onSettingsChange(s.copy(gestures = fmt(current + (id to keyCode))))
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { gestureDialog = false }) { Text("Done") } },
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
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
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
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun InfoCard(text: String, isError: Boolean) {
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
