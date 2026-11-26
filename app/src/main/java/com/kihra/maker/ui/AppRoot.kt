package com.kihra.maker.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.kihra.maker.data.Game
import com.kihra.maker.data.GameScanner
import com.kihra.maker.data.GameStore
import com.kihra.maker.engine.EngineLauncher

@Composable
fun AppRoot(store: GameStore) {
    val context = LocalContext.current
    var games by remember { mutableStateOf(store.load()) }
    var openId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    fun update(new: List<Game>) {
        games = new
        store.save(new)
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        when (val result = GameScanner.scan(context, uri)) {
            is GameScanner.Result.Ok -> {
                if (games.any { it.id == result.game.id }) {
                    message = "That game is already in your library."
                } else {
                    update(games + result.game)
                    message = "Added ${result.game.title}"
                }
            }
            is GameScanner.Result.Fail -> message = result.reason
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            message = null
        }
    }

    val open = games.firstOrNull { it.id == openId }

    Box {
        if (open == null) {
            LibraryScreen(
                games = games,
                onAdd = { picker.launch(null) },
                onOpen = { openId = it.id },
            )
        } else {
            GameDetailScreen(
                game = open,
                onBack = { openId = null },
                onSettingsChange = { s ->
                    update(games.map { if (it.id == open.id) it.copy(settings = s) else it })
                },
                onPlay = {
                    val played = open.copy(lastPlayed = System.currentTimeMillis())
                    update(games.map { if (it.id == open.id) played else it })
                    EngineLauncher.launch(context, played)
                },
                onRemove = {
                    update(games.filter { it.id != open.id })
                    openId = null
                },
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
