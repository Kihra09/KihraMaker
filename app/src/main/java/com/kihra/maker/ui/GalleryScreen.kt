package com.kihra.maker.ui

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kihra.maker.data.Game
import com.kihra.maker.data.Shots
import java.io.File

/** Every screenshot taken in-game, filterable by game. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    shots: List<Shots.Shot>,
    games: List<Game>,
    onBack: () -> Unit,
    onDelete: (Shots.Shot) -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var filter by remember { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<Shots.Shot?>(null) }
    val titleByKey = remember(games) { games.associate { Shots.key(it.id) to it.title } }
    val keys = remember(shots) { shots.map { it.gameKey }.distinct() }
    val shown = remember(shots, filter) { if (filter == null) shots else shots.filter { it.gameKey == filter } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Gallery") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (shots.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No screenshots yet. While playing, tap the small camera at the top of the screen.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All (${shots.size})") }) }
                    items(keys) { k ->
                        FilterChip(
                            selected = filter == k,
                            onClick = { filter = k },
                            label = { Text(titleByKey[k] ?: "Removed game", maxLines = 1) },
                        )
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(120.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it.file.absolutePath }) { shot ->
                        val bmp = rememberImage(shot.file, 360)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.4f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { viewing = shot },
                        ) {
                            if (bmp != null) {
                                Image(bitmap = bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
    }

    viewing?.let { shot ->
        Dialog(
            onDismissRequest = { viewing = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                val bmp = rememberImage(shot.file, 2048)
                if (bmp != null) {
                    Image(bitmap = bmp, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
                Row(
                    Modifier.align(Alignment.TopEnd).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(onClick = { exportToPictures(context, shot.file) }) {
                        Icon(Icons.Filled.SaveAlt, contentDescription = "Save to Pictures", tint = Color.White)
                    }
                    IconButton(onClick = { onDelete(shot); viewing = null }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.White)
                    }
                    IconButton(onClick = { viewing = null }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }
    }
}

/** Copies a screenshot to the phone's Pictures/KihraMaker so it shows in the normal gallery app. */
private fun exportToPictures(context: Context, file: File) {
    val ok = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "KihraMaker")
            dir.mkdirs()
            file.copyTo(File(dir, file.name), overwrite = true)
        } else {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KihraMaker")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
            context.contentResolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
        }
    }.isSuccess
    Toast.makeText(context, if (ok) "Saved to Pictures/KihraMaker" else "Couldn't save the picture", Toast.LENGTH_SHORT).show()
}
