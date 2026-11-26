package com.kihra.maker.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kihra.maker.data.Game
import com.kihra.maker.data.GameArt
import com.kihra.maker.data.Thumbs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/** Distinct colours for the play-time chart, one per game. */
val ChartColors = listOf(
    Color(0xFF7C9CFF), Color(0xFFFF8A65), Color(0xFF4DD0A1), Color(0xFFFFD54F),
    Color(0xFFBA68C8), Color(0xFF4FC3F7), Color(0xFFF06292), Color(0xFFAED581),
)

@Composable
fun rememberImage(file: File?, maxPx: Int): ImageBitmap? {
    val state by produceState<ImageBitmap?>(null, file?.absolutePath, file?.lastModified(), maxPx) {
        value = if (file == null) null else withContext(Dispatchers.IO) {
            Thumbs.load(file, maxPx)?.asImageBitmap()
        }
    }
    return state
}

/** Game icon (title picture) or a coloured letter tile when the game has none. */
@Composable
fun GameIcon(game: Game?, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val iconFile by produceState<File?>(null, game?.id) {
        value = if (game == null) null else withContext(Dispatchers.IO) { GameArt.iconFile(context, game) }
    }
    val bmp = rememberImage(iconFile, 192)
    val shape = RoundedCornerShape(size / 4)
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(shape),
        )
    } else {
        val title = game?.title ?: "?"
        val hue = (abs(title.hashCode()) % 360).toFloat()
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color.hsv(hue, 0.45f, 0.62f), Color.hsv((hue + 40f) % 360f, 0.55f, 0.30f)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                title.firstOrNull()?.uppercase() ?: "?",
                fontSize = (size.value * 0.45f).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.9f),
            )
        }
    }
}
