package com.kihra.maker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

/** Simple rounded bar chart: one bar per value, with the first and last label underneath. */
@Composable
fun MiniBars(values: List<Long>, firstLabel: String, lastLabel: String, minMax: Long = 30 * 60_000L) {
    val bar = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val max = maxOf(values.maxOrNull() ?: 0L, minMax)
    Column {
        Canvas(Modifier.fillMaxWidth().height(80.dp)) {
            if (values.isEmpty()) return@Canvas
            val w = size.width / values.size
            values.forEachIndexed { i, v ->
                drawRoundRect(track, Offset(i * w + 2f, 0f), Size(w - 4f, size.height), CornerRadius(6f))
                val h = size.height * v / max
                if (h > 0f) drawRoundRect(bar, Offset(i * w + 2f, size.height - h), Size(w - 4f, h), CornerRadius(6f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
            Text(firstLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(lastLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
