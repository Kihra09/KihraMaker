package com.kihra.maker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kihra.maker.data.Game
import com.kihra.maker.data.GameSettings
import com.kihra.maker.data.KeyNames
import com.kihra.maker.data.ScaleMode
import kotlin.math.roundToInt

/**
 * The settings of a game (or the defaults for new games): Display, Controls, Saves, Advanced.
 * [forGame] adds the parts that only make sense for one game (font, custom Ruby, saves).
 */
@Composable
fun GameSettingsEditor(
    s: GameSettings,
    onChange: (GameSettings) -> Unit,
    forGame: Boolean,
    game: Game? = null,
    actions: DetailActions? = null,
    backupCount: Int = 0,
) {
    val tabs = if (forGame) listOf("Display", "Controls", "Saves", "Advanced") else listOf("Display", "Controls", "Advanced")
    var tab by remember { mutableStateOf(0) }
    var padDialog by remember { mutableStateOf(false) }
    var gestureDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.forEachIndexed { i, name ->
                FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(name) })
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (tabs[tab]) {
                "Display" -> {
                    Text("Scaling", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScaleMode.values().forEach { mode ->
                            FilterChip(selected = s.scaleMode == mode, onClick = { onChange(s.copy(scaleMode = mode)) }, label = { Text(mode.label) })
                        }
                    }
                    Text("Frame rate cap (a cap turns fast forward off)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Game default", 30 to "30 FPS", 60 to "60 FPS").forEach { (fps, label) ->
                            FilterChip(selected = s.fpsCap == fps, onClick = { onChange(s.copy(fpsCap = fps)) }, label = { Text(label) })
                        }
                    }
                    SwitchRow("Smooth scaling (blurs pixels)", s.smoothScaling) { onChange(s.copy(smoothScaling = it)) }
                    SwitchRow("Show FPS counter", s.showFps) { onChange(s.copy(showFps = it)) }
                    if (forGame) {
                        Text("Font inside the game", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("" to "Game's own", "fira" to "Fira Sans", "mplus" to "M PLUS Code").forEach { (id, label) ->
                                FilterChip(selected = s.gameFont == id, onClick = { onChange(s.copy(gameFont = id)) }, label = { Text(label) })
                            }
                        }
                        Text(
                            "Used when the game's own font isn't available. Games that bundle their own font keep it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                "Controls" -> {
                    Text("Button opacity: ${(s.controlsOpacity * 100).roundToInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(value = s.controlsOpacity, onValueChange = { onChange(s.copy(controlsOpacity = it)) }, valueRange = 0.1f..1f)
                    Text("Button size: ${s.controlsScale}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = s.controlsScale.toFloat(),
                        onValueChange = { onChange(s.copy(controlsScale = (it / 5).roundToInt() * 5)) },
                        valueRange = 50f..150f,
                    )
                    SwitchRow("8-way movement (diagonals)", s.diagonalPad) { onChange(s.copy(diagonalPad = it)) }
                    SwitchRow("Vibrate when pressing buttons", s.haptics) { onChange(s.copy(haptics = it)) }
                    if (forGame) {
                        Text(
                            "Move, resize, re-key, add or delete buttons, and switch between the D-pad and the joystick in the game: Menu button > Edit layout.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = { gestureDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Swipe and tap gestures") }
                        OutlinedButton(onClick = { padDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Controller button mapping") }
                    }
                }
                "Saves" -> {
                    Text(
                        "Backups are made automatically after you play (the last 5 are kept). Quick save and quick load are in the in-game menu.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (actions != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = actions.onOpenBackups, enabled = backupCount > 0) { Text("Backups ($backupCount)") }
                            OutlinedButton(onClick = actions.onExportSaves) { Text("Export") }
                            OutlinedButton(onClick = actions.onImportSaves) { Text("Import") }
                        }
                    }
                }
                else -> {
                    Text("Fast forward speed (menu button switches between 1x and this)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 4, 8, 16).forEach { x ->
                            FilterChip(selected = s.ffSpeed == x, onClick = { onChange(s.copy(ffSpeed = x)) }, label = { Text("${x}x") })
                        }
                    }
                    Text(
                        "Speeds above 2x need frame skip on, and are limited by how fast your phone can run the game's logic.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SwitchRow("Frame skip (smoother on slow phones)", s.frameSkip) { onChange(s.copy(frameSkip = it)) }
                    if (forGame) {
                        SwitchRow("Safe mode: no fixes, cheats or extras", s.safeMode) { onChange(s.copy(safeMode = it)) }
                        Text(
                            "Starts the plain engine only. Turn it on if this game hangs on loading or crashes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("Internal resolution", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.horizontalScroll(rememberScrollState()).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { x ->
                            FilterChip(selected = s.renderScale == x, onClick = { onChange(s.copy(renderScale = x)) }, label = { Text("${x}x") })
                        }
                    }
                    Text(
                        "Below 1x is blurrier and saves a little graphics work. It does not make the game's logic faster, " +
                            "so a game that is slow because of its scripts stays slow. Above 1x only draws a sharper copy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (forGame && game != null) {
                        var ruby by remember(game.id) { mutableStateOf(s.customRuby) }
                        Text(
                            "Custom Ruby fixes for this game (runs before the game starts). Leave empty if unsure.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(value = ruby, onValueChange = { ruby = it }, minLines = 3, modifier = Modifier.fillMaxWidth())
                        if (ruby != s.customRuby) Button(onClick = { onChange(s.copy(customRuby = ruby)) }) { Text("Apply") }
                    }
                }
            }
        }
    }

    if (padDialog) {
        val current = remember(s.padMap) { KeyNames.parse(s.padMap) }
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
                                        onChange(s.copy(padMap = KeyNames.format(current - code)))
                                    })
                                    KeyNames.keys.forEach { (name, keyCode) ->
                                        DropdownMenuItem(text = { Text(name) }, onClick = {
                                            open = false
                                            onChange(s.copy(padMap = KeyNames.format(current + (code to keyCode))))
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
        val current = remember(s.gestures) {
            s.gestures.split(',').mapNotNull {
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
                                        onChange(s.copy(gestures = fmt(current - id)))
                                    })
                                    KeyNames.keys.forEach { (name, keyCode) ->
                                        DropdownMenuItem(text = { Text(name) }, onClick = {
                                            open = false
                                            onChange(s.copy(gestures = fmt(current + (id to keyCode))))
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
}

/** Full-screen setup for one game; opens the first time a game is tapped and on long-press. */
@Composable
fun GameSetupDialog(
    game: Game,
    sharedNote: Boolean = false,
    onChange: (GameSettings) -> Unit,
    onDone: () -> Unit,
    actions: DetailActions,
    backupCount: Int,
) {
    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    "Setup: ${game.title}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
                    maxLines = 1,
                )
                Text(
                    if (sharedNote) "Shared settings are on: display, controls and advanced options are the same for every game (change this in Settings). Font, fixes, gestures and controller mapping stay per game. Long-press a game to open this screen again."
                    else "These settings are saved for this game only. Open this screen again any time by long-pressing the game in the library.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                Box(Modifier.weight(1f)) {
                    GameSettingsEditor(game.settings, onChange, forGame = true, game = game, actions = actions, backupCount = backupCount)
                }
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("Done") }
            }
        }
    }
}

/** Defaults for new games, opened from the main settings. */
@Composable
fun DefaultsDialog(s: GameSettings, onChange: (GameSettings) -> Unit, onDone: () -> Unit) {
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    "Default game settings",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
                )
                Text(
                    "Used for games you add from now on. Existing games keep their own settings. Font and custom fixes are set per game.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                Box(Modifier.weight(1f)) { GameSettingsEditor(s, onChange, forGame = false) }
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("Done") }
            }
        }
    }
}
