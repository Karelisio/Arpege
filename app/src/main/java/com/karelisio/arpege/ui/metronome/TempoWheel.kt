package com.karelisio.arpege.ui.metronome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.arpege.ui.components.rememberHaptics
import kotlin.math.atan2

/** Roue de tempo : un cran (1 BPM) tous les 8°, retour haptique à chaque cran. */
@Composable
fun TempoWheel(bpm: Int, onBpm: (Int) -> Unit, size: Dp = 168.dp) {
    val haptics = rememberHaptics()
    val currentBpm by rememberUpdatedState(bpm)
    var rotation by remember { mutableFloatStateOf(0f) }
    var accumulated by remember { mutableFloatStateOf(0f) }
    val ring = MaterialTheme.colorScheme.surfaceContainerHighest
    val tick = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    Canvas(
        Modifier
            .size(size)
            .pointerInput(Unit) {
                var lastAngle = 0f
                detectDragGestures(
                    onDragStart = { o -> lastAngle = angle(o, this.size.width / 2f, this.size.height / 2f) },
                ) { change, _ ->
                    val a = angle(change.position, this.size.width / 2f, this.size.height / 2f)
                    var d = a - lastAngle
                    if (d > 180) d -= 360
                    if (d < -180) d += 360
                    lastAngle = a
                    rotation += d
                    accumulated += d
                    val steps = (accumulated / 8f).toInt()
                    if (steps != 0) {
                        accumulated -= steps * 8f
                        val next = (currentBpm + steps).coerceIn(20, 300)
                        if (next != currentBpm) { haptics.segment(); onBpm(next) }
                    }
                }
            },
    ) {
        val r = this.size.minDimension / 2
        val c = Offset(this.size.width / 2, this.size.height / 2)
        drawCircle(ring, r, c)
        drawCircle(tick.copy(alpha = 0.25f), r * 0.62f, c, style = Stroke(2.dp.toPx()))
        rotate(rotation, c) {
            for (i in 0 until 45) {
                rotate(i * 8f, c) {
                    drawLine(tick.copy(alpha = 0.5f), Offset(c.x, c.y - r + 8.dp.toPx()), Offset(c.x, c.y - r + 16.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                }
            }
            drawCircle(accent, 9.dp.toPx(), Offset(c.x, c.y - r + 30.dp.toPx()))
        }
    }
}

private fun angle(p: Offset, cx: Float, cy: Float): Float =
    Math.toDegrees(atan2((p.y - cy).toDouble(), (p.x - cx).toDouble())).toFloat()
