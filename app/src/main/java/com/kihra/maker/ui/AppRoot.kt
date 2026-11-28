package com.kihra.maker.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
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
import com.kihra.maker.data.AppBackup
import com.kihra.maker.data.AppSettings
import com.kihra.maker.data.Downloads
import com.kihra.maker.data.GameArt
import com.kihra.maker.data.SaveBackup
import com.kihra.maker.data.SessionLog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.kihra.maker.data.PlayLog
import com.kihra.maker.data.Rtp
import com.kihra.maker.data.Wallpaper
import com.kihra.maker.data.Shots
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.kihra.maker.data.sharedWith
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
    var logs by remember { mutableStateOf(emptyList<File>()) }
    var achFlags by remember { mutableStateOf(com.kihra.maker.data.Achievements.Flags()) }
    var viewingLog by remember { mutableStateOf<File?>(null) }
    var autoSafe by remember { mutableStateOf<String?>(null) }
    var popups by remember { mutableStateOf<List<String>>(emptyList()) }
    var crashed by remember { mutableStateOf<SessionLog.Ended?>(null) }
    var backupsFor by remember { mutableStateOf<Game?>(null) }
    var gameForPicker by remember { mutableStateOf<Game?>(null) }
    var setupId by remember { mutableStateOf<String?>(null) }
    var defaultsOpen by remember { mutableStateOf(false) }
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
        val ended = withContext(Dispatchers.IO) { SessionLog.finish(context, games) }
        val fresh = withContext(Dispatchers.IO) { runCatching { com.kihra.maker.data.AchievementWatcher.check(context).toList() }.getOrDefault(emptyList()) }
        if (fresh.isNotEmpty()) popups = popups + fresh
        logs = withContext(Dispatchers.IO) { SessionLog.list(context) }
        achFlags = withContext(Dispatchers.IO) {
            val ctl = File(com.kihra.maker.data.AppPaths.base(context), "controls")
            val files = ctl.listFiles()?.toList().orEmpty()
            val st = AppSettings.load(context)
            com.kihra.maker.data.Achievements.Flags(
                light = st.light,
                wallpaper = AppSettings.wallpaperFile(context).isFile,
                goalMinutes = st.goalMinutes,
                customLayout = files.any { it.name.endsWith(".json") },
                pinnedCheat = files.any { it.name.endsWith(".pins") && it.length() > 0 },
                backup = games.any { SaveBackup.list(context, it).isNotEmpty() },
                shotGames = sh.map { it.gameKey }.toSet().size,
            )
        }
        if (ended != null && ended.crashed) {
            crashed = ended
            val g = ended.game
            // A game that never drew a frame starts in safe mode next time (can be turned off in Game setup).
            if (ended.stuck && g != null && !g.settings.safeMode) {
                update(games.map { if (it.id == g.id) it.copy(settings = it.settings.copy(safeMode = true)) else it })
                autoSafe = g.title
            }
        }
    }
    val playtime = remember(segments) { PlayLog.totals(segments) }
    val pagerState = rememberPagerState(initialPage = 1) { 4 }

    var settings by remember { mutableStateOf(AppSettings.load(context)) }
    fun changeSettings(new: AppSettings) {
        settings = new
        AppSettings.save(context, new)
    }
    var wallpaperVersion by remember { mutableStateOf(0) }
    val wallpaperFile = remember(wallpaperVersion) { AppSettings.wallpaperFile(context).takeIf { it.isFile } }
    val wallpaperImage = rememberImage(wallpaperFile, 1600)
    val daysPlayed = remember(segments) { PlayLog.daysPlayed(segments) }
    val activeStreak = remember(segments) { PlayLog.activeStreak(segments) }
    val shotsByKey = remember(shots) { shots.groupBy { it.gameKey } }

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
                    update(games + result.game.copy(settings = settings.defaults))
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

    val exePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val path = PathUtils.documentUriToPath(uri)
        val folder = path?.let { File(it).parentFile }
        if (folder == null) {
            message = "Can't find that file's folder. Use \"Game folder\" and pick the folder that holds the .exe."
            return@rememberLauncherForActivityResult
        }
        withStorage {
            scope.launch {
                val result = withContext(Dispatchers.IO) { GameScanner.scan(folder) }
                addGame(result)
                if (result is GameScanner.Result.Fail) {
                    message = "The .exe itself can't run on Android, and no RPG Maker game files were found next to it."
                }
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
        val root = path?.let { Rtp.findRoot(File(it)) }
        if (root == null) {
            message = "No Graphics and Audio folders found there. Pick the RTP folder (for example RPGVXAce)."
        } else {
            rtpPath = root.absolutePath
            store.rtpPath = root.absolutePath
            message = "RTP folder set."
        }
    }

    val rtpZipPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importedMb = 0
            val (root, error) = withContext(Dispatchers.IO) { Rtp.importZip(context, uri) { mb -> importedMb = mb } }
            importedMb = null
            if (root != null) {
                rtpPath = root.absolutePath
                store.rtpPath = root.absolutePath
                message = "RTP installed."
            } else {
                message = error ?: "Couldn't import the RTP."
            }
        }
    }

    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) { Wallpaper.import(context, uri) }
            if (ok) wallpaperVersion++ else message = "Couldn't use that picture."
        }
    }

    val importSavesPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val g = gameForPicker
        if (uri == null || g == null) return@rememberLauncherForActivityResult
        scope.launch {
            val n = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { SaveBackup.importZip(context, g, it) } ?: 0 }
                    .getOrDefault(0)
            }
            message = if (n > 0) "Imported $n save files." else "No save files found in that zip."
            reload++
        }
    }

    val iconPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val g = gameForPicker
        if (uri == null || g == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) { GameArt.setCustomIcon(context, g, uri) }
            if (ok) IconVersion.value++ else message = "Couldn't use that picture."
        }
    }

    val restoreAllPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val n = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { AppBackup.restore(context, it) } ?: -1 }
                    .getOrDefault(-1)
            }
            if (n < 0) {
                message = "That isn't a KihraMaker backup."
            } else {
                games = store.load()
                rtpPath = store.rtpPath
                settings = AppSettings.load(context)
                wallpaperVersion++
                reload++
                message = "Restored $n files."
            }
        }
    }

    fun exportSaves(game: Game) {
        scope.launch {
            val name = game.title.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40) + "-saves.zip"
            var count = 0
            val where = withContext(Dispatchers.IO) {
                Downloads.save(context, name, "application/zip") { out -> count = SaveBackup.exportZip(game, out) }
            }
            message = if (where == null) "Couldn't save the zip." else if (count == 0) "This game has no save files yet." else "Saved $count files to $where/$name"
        }
    }

    fun exportEverything() {
        scope.launch {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
            val where = withContext(Dispatchers.IO) {
                Downloads.save(context, "KihraMaker-backup-$stamp.zip", "application/zip") { out -> AppBackup.export(context, out) }
            }
            message = if (where == null) "Couldn't save the backup." else "Backup saved to $where"
        }
    }

    fun launchGame(g: Game, safe: Boolean = false) {
        val started = g.copy(lastPlayed = System.currentTimeMillis())
        val run: () -> Unit = {
            update(games.map { if (it.id == g.id) started else it })
            val wall = if (settings.wallpaperInGame) wallpaperFile?.absolutePath.orEmpty() else ""
            val toRun = if (settings.sharedSettings) started.copy(settings = started.settings.sharedWith(settings.defaults)) else started
            val err = EngineLauncher.launch(context, toRun, rtpPath, wall, safe, settings.sharedLayout)
            if (err == null) SessionLog.markStart(context, started) else message = err
        }
        // Unpacked games live in the app's own storage; folders, zips and the RTP need file access.
        if (g.extracted && rtpPath.isBlank()) run() else withStorage(action = run)
    }

    LaunchedEffect(popups) {
        if (popups.isNotEmpty()) {
            kotlinx.coroutines.delay(4000)
            popups = popups.drop(1)
        }
    }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            message = null
        }
    }

    val open = games.firstOrNull { it.id == openId }
    val wallpaperActive = settings.wallpaperInApp && wallpaperImage != null
    val wide = LocalConfiguration.current.screenWidthDp >= 720

    KihraTheme(settings.theme, wallpaperActive, settings.light) {
        Box(Modifier.fillMaxSize().background(if (wallpaperActive) Color.Black else themeBackground(settings.theme, settings.light))) {
            if (wallpaperActive && wallpaperImage != null) {
                Image(
                    bitmap = wallpaperImage,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (settings.light) 0.2f else 0.55f)))
            }

            popups.firstOrNull()?.let { t ->
                androidx.compose.ui.window.Popup(alignment = Alignment.TopStart) {
                    androidx.compose.material3.Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        color = Color(0xE6202020),
                        modifier = Modifier.padding(12.dp),
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text("Achievement unlocked", color = Color(0xFFFFC107), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            Text(t, color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }

            if (open != null) {
                BackHandler { openId = null }
                GameDetailScreen(
                    game = open,
                    onBack = { openId = null },
                    onSettingsChange = { s ->
                        update(games.map { if (it.id == open.id) it.copy(settings = s) else it })
                    },
                    onPlay = { launchGame(open) },
                    onUnpack = { unpackGame(open) },
                    playedMs = playtime[open.id] ?: 0L,
                    rtpInstalled = Rtp.isValid(rtpPath),
                    backupCount = remember(open.id, reload, backupsFor) { SaveBackup.list(context, open).size },
                    actions = DetailActions(
                        onFavorite = { update(games.map { if (it.id == open.id) it.copy(favorite = !it.favorite) else it }) },
                        onStatus = { st -> update(games.map { if (it.id == open.id) it.copy(status = st) else it }) },
                        onRename = { name -> update(games.map { if (it.id == open.id) it.copy(title = name) else it }) },
                        onPickIcon = { gameForPicker = open; iconPicker.launch(arrayOf("image/*")) },
                        onResetIcon = { GameArt.resetIcon(context, open); IconVersion.value++ },
                        onOpenBackups = { backupsFor = open },
                        onExportSaves = { exportSaves(open) },
                        onOpenSetup = { setupId = open.id },
                        onImportSaves = {
                            gameForPicker = open
                            importSavesPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                        },
                    ),
                    onRemove = {
                        val removed = open
                        update(games.filter { it.id != removed.id })
                        openId = null
                        if (removed.extracted && removed.extractRoot.isNotBlank()) {
                            scope.launch(Dispatchers.IO) { File(removed.extractRoot).deleteRecursively() }
                        }
                    },
                )
            } else {
                val tabs = listOf(
                    "Assistant" to Icons.Filled.Insights,
                    "Library" to Icons.Filled.SportsEsports,
                    "Gallery" to Icons.Filled.PhotoLibrary,
                    "Settings" to Icons.Filled.Settings,
                )
                val pager: @Composable (Modifier) -> Unit = { mod ->
                    // Pages: Assistant, Library, Gallery, Settings. Swiping and the bar both work.
                    HorizontalPager(state = pagerState, modifier = mod) { page ->
                        when (page) {
                            0 -> AssistantScreen(games = games, segments = segments, shotCount = shots.size, goalMinutes = settings.goalMinutes, flags = achFlags)
                            1 -> LibraryScreen(
                                games = games,
                                onAddFolder = { withStorage { folderPicker.launch(null) } },
                                onAddExe = { exePicker.launch(arrayOf("*/*")) },
                                onAddZip = { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                                onOpen = { if (it.configured) openId = it.id else setupId = it.id },
                                onSetup = { setupId = it.id },
                                activeStreak = activeStreak,
                                playtime = playtime,
                                daysPlayed = daysPlayed,
                                shotsByKey = shotsByKey,
                                view = settings.view,
                                onViewChange = { changeSettings(settings.copy(view = it)) },
                                sort = settings.sort,
                                onSortChange = { changeSettings(settings.copy(sort = it)) },
                                filter = settings.filter,
                                onFilterChange = { changeSettings(settings.copy(filter = it)) },
                                onContinue = { launchGame(it) },
                                showHint = !settings.hintSeen,
                                onDismissHint = { changeSettings(settings.copy(hintSeen = true)) },
                                onOpenGallery = { scope.launch { pagerState.animateScrollToPage(2) } },
                            )
                            2 -> GalleryScreen(
                                shots = shots,
                                games = games,
                                onBack = null,
                                onDelete = { shot ->
                                    scope.launch {
                                        withContext(Dispatchers.IO) { shot.file.delete() }
                                        reload++
                                    }
                                },
                            )
                            else -> SettingsScreen(
                                settings = settings,
                                onChange = { changeSettings(it) },
                                hasWallpaper = wallpaperFile != null,
                                onPickWallpaper = { wallpaperPicker.launch(arrayOf("image/*")) },
                                onClearWallpaper = {
                                    AppSettings.wallpaperFile(context).delete()
                                    wallpaperVersion++
                                },
                                rtpPath = rtpPath,
                                onImportRtpZip = { rtpZipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                                onPickRtpFolder = { withStorage { rtpPicker.launch(null) } },
                                onClearRtp = {
                                    rtpPath = ""
                                    store.rtpPath = ""
                                    scope.launch(Dispatchers.IO) { Rtp.home(context).deleteRecursively() }
                                },
                                logs = logs,
                                onOpenLog = { viewingLog = it },
                                onCaptureLog = {
                                    scope.launch {
                                        val f = withContext(Dispatchers.IO) { SessionLog.capture(context, "manual", "saved by hand") }
                                        logs = withContext(Dispatchers.IO) { SessionLog.list(context) }
                                        if (f != null) viewingLog = f else message = "Couldn't read the system log."
                                    }
                                },
                                onExportAll = { exportEverything() },
                                onOpenDefaults = { defaultsOpen = true },
                                onRestoreAll = { restoreAllPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                            )
                        }
                    }
                }
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        NavigationRail {
                            tabs.forEachIndexed { i, (label, icon) ->
                                NavigationRailItem(
                                    selected = pagerState.currentPage == i,
                                    onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label) },
                                )
                            }
                        }
                        pager(Modifier.weight(1f))
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        pager(Modifier.weight(1f))
                        NavigationBar {
                            tabs.forEachIndexed { i, (label, icon) ->
                                NavigationBarItem(
                                    selected = pagerState.currentPage == i,
                                    onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    KihraTheme(settings.theme, false, settings.light) {
        importedMb?.let { mb ->
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text("Working") },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
                        CircularProgressIndicator()
                        Spacer(Modifier.width(16.dp))
                        Text("$mb MB done. Large files can take a few minutes.")
                    }
                },
            )
        }

        games.firstOrNull { it.id == setupId }?.let { g ->
            val shared = settings.sharedSettings
            GameSetupDialog(
                game = if (shared) g.copy(settings = g.settings.sharedWith(settings.defaults)) else g,
                sharedNote = shared,
                onChange = { st ->
                    if (shared) {
                        // Shared fields live in the defaults; the per-game ones stay on the game.
                        changeSettings(settings.copy(defaults = st.copy(customRuby = "", gameFont = "", padMap = "", gestures = "", safeMode = false)))
                        update(games.map {
                            if (it.id == g.id) it.copy(settings = it.settings.copy(customRuby = st.customRuby, gameFont = st.gameFont, padMap = st.padMap, gestures = st.gestures, safeMode = st.safeMode)) else it
                        })
                    } else {
                        update(games.map { if (it.id == g.id) it.copy(settings = st) else it })
                    }
                },
                onDone = {
                    val wasNew = !g.configured
                    update(games.map { if (it.id == g.id) it.copy(configured = true) else it })
                    setupId = null
                    if (wasNew && openId == null) openId = g.id
                },
                actions = DetailActions(
                    onFavorite = {}, onStatus = {}, onRename = {},
                    onPickIcon = {}, onResetIcon = {},
                    onOpenBackups = { backupsFor = g },
                    onExportSaves = { exportSaves(g) },
                    onImportSaves = {
                        gameForPicker = g
                        importSavesPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                    },
                ),
                backupCount = SaveBackup.list(context, g).size,
            )
        }

        if (defaultsOpen) {
            DefaultsDialog(
                s = settings.defaults,
                onChange = { changeSettings(settings.copy(defaults = it)) },
                onDone = { defaultsOpen = false },
            )
        }

        crashed?.let { c ->
            AlertDialog(
                onDismissRequest = { crashed = null },
                title = { Text(if (c.stuck) "${c.title} got stuck while loading" else "${c.title} closed unexpectedly") },
                text = {
                    Text(
                        (if (c.stuck) "The game never drew its first screen. Large games can take a while, but this one didn't finish. " +
                            "Safe mode starts the plain engine without any extras and often fixes it. " +
                            (if (autoSafe != null) "It is now on for this game automatically (Game setup > Advanced turns it off). " else "") else "") +
                            "A log was saved. If this keeps happening, open the log, copy it and send it to get the game fixed. " +
                            "Your saves were backed up.",
                    )
                },
                confirmButton = {
                    if (c.stuck && c.game != null) {
                        TextButton(onClick = { val g = c.game; crashed = null; launchGame(g, safe = true) }) { Text("Retry in safe mode") }
                    } else {
                        TextButton(onClick = { viewingLog = c.log; crashed = null }, enabled = c.log != null) { Text("View log") }
                    }
                },
                dismissButton = {
                    Row {
                        if (c.stuck) TextButton(onClick = { viewingLog = c.log; crashed = null }, enabled = c.log != null) { Text("View log") }
                        TextButton(onClick = { crashed = null }) { Text("Close") }
                    }
                },
            )
        }

        viewingLog?.let { f ->
            val text = remember(f) { runCatching { f.readLines().takeLast(400).joinToString("\n") }.getOrDefault("(can't read this log)") }
            AlertDialog(
                onDismissRequest = { viewingLog = null },
                title = { Text(f.nameWithoutExtension, maxLines = 1) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(text, fontFamily = KihraMono, fontSize = 10.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val where = Downloads.save(context, f.name, "text/plain") { out -> f.inputStream().use { it.copyTo(out) } }
                        message = if (where != null) "Saved to $where/${f.name}" else "Couldn't save the log."
                    }) { Text("Save to Downloads") }
                },
                dismissButton = { TextButton(onClick = { viewingLog = null }) { Text("Close") } },
            )
        }

        backupsFor?.let { g ->
            val list = SaveBackup.list(context, g)
            val fmt = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
            AlertDialog(
                onDismissRequest = { backupsFor = null },
                title = { Text("Save backups") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if (list.isEmpty()) Text("No backups yet.")
                        list.forEach { b ->
                            TextButton(onClick = {
                                val ok = SaveBackup.restore(context, g, b)
                                message = if (ok) "Restored the backup from ${fmt.format(Date(b.time))}." else "Couldn't restore that backup."
                                backupsFor = null
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text("${fmt.format(Date(b.time))}  ·  ${b.files} files")
                            }
                        }
                        Text(
                            "Restoring keeps a copy of your current saves first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = { TextButton(onClick = { backupsFor = null }) { Text("Close") } },
            )
        }
    }
}
