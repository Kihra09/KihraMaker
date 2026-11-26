package com.kihra.maker.engine

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun TouchControls(opacity: Float, modifier: Modifier = Modifier) {
    DisposableEffect(Unit) { onDispose { InputState.clear() } }

    Box(modifier.fillMaxSize().alpha(opacity)) {
        DPad(Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 24.dp))

        Box(Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 24.dp).size(190.dp)) {
            RoundButton("A", Button.C, Modifier.align(Alignment.CenterEnd))
            RoundButton("B", Button.B, Modifier.align(Alignment.BottomCenter))
            RoundButton("X", Button.X, Modifier.align(Alignment.TopCenter))
            RoundButton("Y", Button.Y, Modifier.align(Alignment.CenterStart))
        }

        RoundButton("L", Button.L, Modifier.align(Alignment.TopStart).padding(16.dp), small = true)
        RoundButton("R", Button.R, Modifier.align(Alignment.TopEnd).padding(16.dp), small = true)
    }
}

/** One finger, dragging across the pad changes direction. Diagonals are not sent. */
@Composable
private fun DPad(modifier: Modifier) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var active by remember { mutableStateOf<Button?>(null) }

    fun apply(next: Button?) {
        if (next == active) return
        active?.let { InputState.set(it, false) }
        next?.let { InputState.set(it, true) }
        active = next
    }

    Box(
        modifier
            .size(170.dp)
            .onSizeChanged { size = it }
            .background(Color.White.copy(alpha = 0.12f), CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                    fun dir(x: Float, y: Float): Button? {
                        val dx = x - size.width / 2f
                        val dy = y - size.height / 2f
                        val dead = size.width * 0.12f
                        if (abs(dx) < dead && abs(dy) < dead) return null
                        return if (abs(dx) > abs(dy)) {
                            if (dx > 0) Button.RIGHT else Button.LEFT
                        } else {
                            if (dy > 0) Button.DOWN else Button.UP
                        }
                    }
                    apply(dir(down.position.x, down.position.y))
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val p = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!p.pressed) break
                        apply(dir(p.position.x, p.position.y))
                        p.consume()
                    }
                    apply(null)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("✥", color = Color.White.copy(alpha = 0.6f), fontSize = 28.sp)
    }
}

@Composable
private fun BoxScope.RoundButton(
    label: String,
    button: Button,
    modifier: Modifier,
    small: Boolean = false,
) {
    val d = if (small) 48.dp else 64.dp
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(d)
            .background(
                Color.White.copy(alpha = if (pressed) 0.4f else 0.18f),
                CircleShape,
            )
            .pointerInput(button) {
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                    down.consume()
                    pressed = true
                    InputState.set(button, true)
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val p = event.changes.firstOrNull { it.id == down.id } ?: break
                        p.consume()
                        if (!p.pressed) break
                    }
                    pressed = false
                    InputState.set(button, false)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (small) 16.sp else 22.sp)
    }
}
