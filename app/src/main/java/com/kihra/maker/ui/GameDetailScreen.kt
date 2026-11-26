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
import androidx.compose.material.icons.filled.PlayArrow
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
import com.kihra.maker.data.ScaleMode
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(
    game: Game,
    onBack: () -> Unit,
    onSettingsChange: (GameSettings) -> Unit,
    onPlay: () -> Unit,
    onUnpack: () -> Unit,
    onRemove: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var confirmRemove by remember { mutableStateOf(false) }
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
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
            } else if (game.rtp.isNotBlank()) {
                InfoCard(
                    "This game uses the ${game.rtp} RTP. If it can't find its default " +
                        "graphics or sounds, set the RTP folder with the gear icon in the library.",
                    isError = false,
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

            Text("Frame rate", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
