package com.karelisio.arpege.ui.metronome

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.arpege.ui.components.rememberHaptics
import com.karelisio.arpege.ui.theme.LocalReduceMotion
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Proportion du rayon occupée par le centre (la couronne crantée est autour). */
const val WHEEL_INNER = 0.70f

private const val DEG_PER_BPM = 8f

/**
 * Roue de tempo : couronne crantée qu'on fait tourner du doigt, 1 BPM tous les 8°, retour haptique
 * à chaque cran. La couronne suit le tempo (aussi pour ±1, tap tempo, favoris) ; [content] est
 * dessiné au centre, par-dessus, et reçoit seul les touchers qui y commencent.
 */
@Composable
fun TempoWheel(
    bpm: Int,
    onBpm: (Int) -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val haptics = rememberHaptics()
    val reduce = LocalReduceMotion.current
    val currentBpm by rememberUpdatedState(bpm)
    val currentOnBpm by rememberUpdatedState(onBpm)
    var residual by remember { mutableFloatStateOf(0f) } // fraction de cran en cours de geste
    var dragging by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        bpm * DEG_PER_BPM + residual,
        animationSpec = if (dragging || reduce) snap() else spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "wheel",
    )
    val ring = MaterialTheme.colorScheme.surfaceContainerHighest
    val groove = MaterialTheme.colorScheme.surfaceContainerLow
    val tick = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier
            .size(size)
            .semantics {
                contentDescription = "Roue de tempo"
                progressBarRangeInfo = ProgressBarRangeInfo(bpm.toFloat(), 20f..300f, steps = 279)
                setProgress { v -> currentOnBpm(v.roundToInt().coerceIn(20, 300)); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val c = Offset(this.size.width / 2f, this.size.height / 2f)
                    val r = this.size.width / 2f
                    val d = hypot(down.position.x - c.x, down.position.y - c.y)
                    // Seule la couronne fait tourner : le centre garde ses propres touchers.
                    if (d < r * WHEEL_INNER || d > r * 1.08f) return@awaitEachGesture
                    down.consume()
                    dragging = true
                    var last = angle(down.position, c)
                    var acc = 0f
                    try {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            if (ch.positionChange() == Offset.Zero) continue
                            ch.consume()
                            val a = angle(ch.position, c)
                            var delta = a - last
                            if (delta > 180) delta -= 360
                            if (delta < -180) delta += 360
                            last = a
                            acc += delta
                            val steps = (acc / DEG_PER_BPM).toInt()
                            if (steps != 0) {
                                acc -= steps * DEG_PER_BPM
                                val next = (currentBpm + steps).coerceIn(20, 300)
                                if (next != currentBpm) { haptics.segment(); currentOnBpm(next) }
                                // Butée à 20 / 300 : la couronne s'arrête avec le tempo.
                                if (next == 20 || next == 300) acc = acc.coerceIn(-DEG_PER_BPM / 2, DEG_PER_BPM / 2)
                            }
                            residual = acc
                        }
                    } finally {
                        residual = 0f
                        dragging = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2
            val c = Offset(this.size.width / 2, this.size.height / 2)
            val inner = r * WHEEL_INNER
            drawCircle(ring, r, c)
            drawCircle(groove, inner, c)
            // Jauge fine 20 → 300 BPM le long du bord intérieur de la couronne.
            val gauge = inner + 5.dp.toPx()
            val stroke = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
            val topLeft = Offset(c.x - gauge, c.y - gauge)
            val arc = Size(gauge * 2, gauge * 2)
            drawArc(track, 135f, 270f, false, topLeft, arc, style = stroke)
            drawArc(accent, 135f, 270f * (bpm - 20) / 280f, false, topLeft, arc, style = stroke)
            // Crans qui tournent avec le doigt, et repère.
            rotate(rotation, c) {
                val outer = r - 7.dp.toPx()
                for (i in 0 until 45) {
                    val long = i % 5 == 0
                    rotate(i * DEG_PER_BPM, c) {
                        drawLine(
                            tick.copy(alpha = if (long) 0.7f else 0.35f),
                            Offset(c.x, c.y - outer), Offset(c.x, c.y - outer + (if (long) 14 else 9).dp.toPx()),
                            (if (long) 3 else 2).dp.toPx(), StrokeCap.Round,
                        )
                    }
                }
                // Repère entre la jauge et les crans.
                drawCircle(accent, 6.dp.toPx(), Offset(c.x, c.y - inner - (r - inner) * 0.38f))
            }
        }
        content()
    }
}

private fun angle(p: Offset, c: Offset): Float =
    Math.toDegrees(atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble())).toFloat()
