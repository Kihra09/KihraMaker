package com.kihra.maker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kihra.maker.data.AppSettings
import com.kihra.maker.data.Rtp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    hasWallpaper: Boolean,
    onPickWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    rtpPath: String,
    onImportRtpZip: () -> Unit,
    onPickRtpFolder: () -> Unit,
    onClearRtp: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Section("Theme")
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppThemes.chunked(4).forEach { rowThemes ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            rowThemes.forEach { t ->
                                val selected = settings.theme == t.id
                                Column(
                                    Modifier.clickable { onChange(settings.copy(theme = t.id)) }.padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box(
                                        Modifier
                                            .size(46.dp)
                                            .background(t.preview, CircleShape)
                                            .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(t.label, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            repeat(4 - rowThemes.size) { Spacer(Modifier.size(54.dp)) }
                        }
                    }
                }
            }

            Section("Wallpaper")
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "A picture behind the app. In games it fills the space beside the game screen instead of black bars.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onPickWallpaper) { Text(if (hasWallpaper) "Change" else "Choose picture") }
                        if (hasWallpaper) OutlinedButton(onClick = onClearWallpaper) { Text("Remove") }
                    }
                    if (hasWallpaper) {
                        ToggleRow("Show in the app", settings.wallpaperInApp) { onChange(settings.copy(wallpaperInApp = it)) }
                        ToggleRow("Show in games", settings.wallpaperInGame) { onChange(settings.copy(wallpaperInGame = it)) }
                    }
                }
            }

            Section("RTP (RPG Maker runtime package)")
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val ok = Rtp.isValid(rtpPath)
                    Text(
                        when {
                            rtpPath.isBlank() -> "Not installed. Games that use the RTP can't find their default graphics and sounds."
                            ok -> "Installed: $rtpPath"
                            else -> "This folder has no Graphics and Audio inside, so it can't be used: $rtpPath"
                        },
                        color = if (rtpPath.isNotBlank() && !ok) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "The RTP can't be bundled with the app (it belongs to its makers). Import your own copy: " +
                            "a .zip of the RPG Maker VX Ace RTP, or its folder (the one with Graphics and Audio).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onImportRtpZip) { Text("Import zip") }
                        OutlinedButton(onClick = onPickRtpFolder) { Text("Choose folder") }
                    }
                    if (rtpPath.isNotBlank()) TextButton(onClick = onClearRtp) { Text("Remove RTP setting") }
                }
            }
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
