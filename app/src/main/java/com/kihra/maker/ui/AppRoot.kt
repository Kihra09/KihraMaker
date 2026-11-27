package com.kihra.maker.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kihra.maker.data.PlayLog
import com.kihra.maker.data.Shots
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kihra.maker.data.Game
import com.kihra.maker.data.GameScanner
import com.kihra.maker.data.GameStore
import com.kihra.maker.data.PathUtils
import com.kihra.maker.data.Storage
import com.kihra.maker.data.ZipImporter
import com.kihra.maker.engine.EngineLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRoot(store: GameStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var games by remember { mutableStateOf(store.load()) }
    var rtpPath by remember { mutableStateOf(store.rtpPath) }
    var openId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    /** Megabytes unpacked so far while a zip is being imported, null when idle. */
    var importedMb by remember { mutableStateOf<Long?>(null) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingDenied by remember { mutableStateOf<(() -> Unit)?>(null) }
    val snackbar = remember { SnackbarHostState() }

    // Play time and screenshots are written by the game process: reload them when we come back.
    var segments by remember { mutableStateOf(emptyList<PlayLog.Segment>()) }
    var shots by remember { mutableStateOf(emptyList<Shots.Shot>()) }
    var reload by remember { mutableStateOf(0) }
    var showGallery by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) reload++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(reload) {
        val (seg, sh) = withContext(Dispatchers.IO) { PlayLog.read(context) to Shots.list(context) }
        segments = seg
        shots = sh
    }
    val playtime = remember(segments) { PlayLog.totals(segments) }
    val pagerState = rememberPagerState(initialPage = 1) { 2 }

    fun update(new: List<Game>) {
        games = new
        store.save(new)
    }

    fun addGame(result: GameScanner.Result) {
        when (result) {
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

    // ---- Storage access (folders on shared storage are plain paths for the engine) ----
    fun runPendingIfAllowed() {
        val action = pending
        val denied = pendingDenied
        pending = null
        pendingDenied = null
        if (Storage.hasAccess(context)) action?.invoke()
        else if (denied != null) denied()
        else message = "File access is needed to use game folders."
    }

    val allFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { runPendingIfAllowed() }

    val legacyPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { runPendingIfAllowed() }

    fun withStorage(onDenied: (() -> Unit)? = null, action: () -> Unit) {
        if (Storage.hasAccess(context)) {
            action()
            return
        }
        pending = action
        pendingDenied = onDenied
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            allFilesLauncher.launch(Storage.allFilesIntent(context))
        } else {
            legacyPermLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                )
            )
        }
    }

    // ---- Pickers ----
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val path = PathUtils.treeUriToPath(uri)
        if (path == null) {
            message = "That location has no real folder path. Pick a folder on internal storage or an SD card."
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) { GameScanner.scan(File(path)) }
            addGame(result)
        }
    }

    /** Unpacks a zip into app storage (works from any file provider, no file access needed). */
    fun unpackUri(uri: Uri, note: String? = null) {
        scope.launch {
            importedMb = 0
            val result = withContext(Dispatchers.IO) {
                ZipImporter.import(context, uri) { mb -> importedMb = mb }
            }
            importedMb = null
            addGame(result)
            if (note != null && result is GameScanner.Result.Ok) {
                message = "Added ${result.game.title}. $note"
            }
        }
    }

    /** Plays straight from the zip when possible, otherwise unpacks it. */
    fun addZip(uri: Uri) {
        val path = PathUtils.documentUriToPath(uri)
        if (path == null) {
            unpackUri(uri)
            return
        }
        withStorage(onDenied = { unpackUri(uri, "File access wasn't allowed, so it was unpacked.") }) {
            scope.launch {
                val file = File(path)
                val analysis = withContext(Dispatchers.IO) {
                    if (file.canRead()) ZipImporter.analyze(file) else null
                }
                when {
                    analysis == null -> unpackUri(uri, "It can't be read directly, so it was unpacked.")
                    !analysis.ok -> unpackUri(uri, "Unpacked: ${analysis.reason}")
                    else -> addGame(GameScanner.scanZip(context, file, analysis))
                }
            }
        }
    }

    /** Switches a zip game to an unpacked copy (faster loading) and carries its saves over. */
    fun unpackGame(game: Game) {
        scope.launch {
            importedMb = 0
            val result = withContext(Dispatchers.IO) {
                ZipImporter.importFile(context, File(game.zipPath)) { mb -> importedMb = mb }
            }
            importedMb = null
            when (result) {
                is GameScanner.Result.Ok -> {
                    val fresh = result.game.copy(settings = game.settings, lastPlayed = game.lastPlayed)
                    withContext(Dispatchers.IO) { ZipImporter.copySaves(File(game.path), File(fresh.path)) }
                    update(games.map { if (it.id == game.id) fresh else it }.distinctBy { it.id })
                    openId = fresh.id
                    message = "Unpacked. Your saves were copied over."
                }
                is GameScanner.Result.Fail -> message = result.reason
            }
        }
    }

    val zipPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) addZip(uri)
    }

    val rtpPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val path = PathUtils.treeUriToPath(uri)
        if (path == null) {
            message = "That location has no real folder path."
        } else {
            rtpPath = path
            store.rtpPath = path
            message = "RTP folder set."
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
        if (showGallery) {
            GalleryScreen(
                shots = shots,
                games = games,
                onBack = { showGallery = false },
                onDelete = { shot ->
                    scope.launch {
                        withContext(Dispatchers.IO) { shot.file.delete() }
                        reload++
                    }
                },
            )
        } else if (open == null) {
            // Swipe right on the library to reach the Game assistant (page 0).
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                if (page == 0) {
                    AssistantScreen(games = games, segments = segments, shotCount = shots.size)
                } else {
                    LibraryScreen(
                        games = games,
                        rtpPath = rtpPath,
                        onAddFolder = { withStorage { folderPicker.launch(null) } },
                        onAddZip = { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                        onSetRtp = { withStorage { rtpPicker.launch(null) } },
                        onOpen = { openId = it.id },
                        playtime = playtime,
                        onOpenGallery = { showGallery = true },
                    )
                }
            }
        } else {
            GameDetailScreen(
                game = open,
                onBack = { openId = null },
                onSettingsChange = { s ->
                    update(games.map { if (it.id == open.id) it.copy(settings = s) else it })
                },
                onPlay = {
                    val start: () -> Unit = {
                        val played = open.copy(lastPlayed = System.currentTimeMillis())
                        update(games.map { if (it.id == open.id) played else it })
                        EngineLauncher.launch(context, played, rtpPath)?.let { message = it }
                    }
                    // Unpacked games live in the app's own storage; folders, zips and the RTP need file access.
                    if (open.extracted && rtpPath.isBlank()) start() else withStorage(action = start)
                },
                onUnpack = { unpackGame(open) },
                playedMs = playtime[open.id] ?: 0L,
                onRemove = {
                    val removed = open
                    update(games.filter { it.id != removed.id })
                    openId = null
                    if (removed.extracted && removed.extractRoot.isNotBlank()) {
                        scope.launch(Dispatchers.IO) { File(removed.extractRoot).deleteRecursively() }
                    }
                },
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    importedMb?.let { mb ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Unpacking zip") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(16.dp))
                    Text("$mb MB done. Large games can take a few minutes.")
                }
            },
        )
    }
}
