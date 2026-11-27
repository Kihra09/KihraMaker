package com.kihra.maker.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
/** Bumped when an icon changes so every icon on screen reloads. */
object IconVersion {
    var value by androidx.compose.runtime.mutableIntStateOf(0)
}

@Composable
fun GameIcon(game: Game?, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = IconVersion.value
    val iconFile by produceState<File?>(null, game?.id, version) {
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

/** The game's title picture as a wide cover; falls back to the icon. */
@Composable
fun GameCover(game: Game, modifier: Modifier = Modifier, maxSide: Int = 720) {
    val context = LocalContext.current
    val version = IconVersion.value
    val file by produceState<File?>(null, game.id, version) {
        value = withContext(Dispatchers.IO) { GameArt.coverFile(context, game) }
    }
    val bmp = rememberImage(file, maxSide)
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier.background(Brush.verticalGradient(listOf(Color(0xFF3A3A5A), Color(0xFF16162A)))))
    }
}

/** Game icon with the 🔥 days-played badge at its top-left corner. */
@Composable
fun GameIconWithBadge(game: Game?, size: Dp, days: Int, modifier: Modifier = Modifier, active: Boolean = true) {
    Box(modifier) {
        GameIcon(game, size)
        if (days > 0) FlameBadge(days, Modifier.align(Alignment.TopStart), active)
    }
}

@Composable
fun FlameBadge(days: Int, modifier: Modifier = Modifier, active: Boolean = true) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 12.dp, bottomEnd = 10.dp, topEnd = 2.dp, bottomStart = 2.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        // A grey flame means the streak is inactive; playing the game again lights it back up.
        Text(
            "\uD83D\uDD25$days",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) Color.White else Color(0xFF9E9E9E),
            modifier = if (active) Modifier else Modifier.alpha(0.6f),
        )
    }
}
