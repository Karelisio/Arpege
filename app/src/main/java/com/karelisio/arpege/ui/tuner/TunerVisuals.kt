package com.karelisio.arpege.ui.tuner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DoubleState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.karelisio.arpege.theory.dsp.CentsTrace
import com.karelisio.arpege.theory.dsp.Strobe
import kotlin.math.abs

/**
 * Courbe de justesse : écart en cents (−50..+50) sur la fenêtre de [windowMs] se terminant à
 * [nowMs] ; zone ±3 c mise en évidence, tracé lissé (Bézier quadratiques entre points milieux).
 * Les lambdas sont lues pendant le dessin : seul le dessin est invalidé à chaque image.
 */
@Composable
fun CentsTraceChart(
    segments: () -> List<List<CentsTrace.Point>>,
    nowMs: () -> Long,
    windowMs: Long,
    modifier: Modifier = Modifier,
) {
    val line = MaterialTheme.colorScheme.primary
    val good = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val labels = remember(measurer, labelStyle) { listOf("+50", "0", "−50").map { measurer.measure(it, labelStyle) } }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .semantics { contentDescription = "Courbe de justesse des 5 dernières secondes" },
    ) {
        val pad = 10.dp.toPx()
        val left = 34.dp.toPx()
        val w = size.width - left - pad
        val mid = size.height / 2
        val half = mid - pad
        fun y(c: Double) = (mid - c / 50.0 * half).toFloat()
        val now = nowMs()
        fun x(t: Long) = left + w - (now - t).toFloat() / windowMs * w

        // Zone juste (±3 c) et repères.
        drawRect(good.copy(alpha = 0.22f), Offset(left, y(3.0)), Size(w, y(-3.0) - y(3.0)))
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        for (c in listOf(-50.0, -25.0, 25.0, 50.0)) {
            drawLine(grid, Offset(left, y(c)), Offset(left + w, y(c)), 1.dp.toPx(), pathEffect = dash)
        }
        drawLine(good.copy(alpha = 0.8f), Offset(left, mid), Offset(left + w, mid), 1.5.dp.toPx())
        listOf(50.0, 0.0, -50.0).forEachIndexed { i, c ->
            val l = labels[i]
            drawText(l, topLeft = Offset(left - l.size.width - 6.dp.toPx(), y(c) - l.size.height / 2))
        }

        // Tracé.
        val stroke = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        var lastPoint: CentsTrace.Point? = null
        for (all in segments()) {
            val seg = all.filter { now - it.timeMs <= windowMs }
            if (seg.isEmpty()) continue
            val pts = seg.map { Offset(x(it.timeMs), y(it.cents)) }
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) {
                    val a = pts[i - 1]
                    val b = pts[i]
                    quadraticTo(a.x, a.y, (a.x + b.x) / 2, (a.y + b.y) / 2)
                }
                lineTo(pts.last().x, pts.last().y)
            }
            drawPath(path, line, style = stroke)
            lastPoint = seg.last()
        }
        lastPoint?.let { p ->
            if (now - p.timeMs < 400) {
                drawCircle(if (abs(p.cents) < 3) good else line, 4.5.dp.toPx(), Offset(x(p.timeMs), y(p.cents)))
            }
        }
    }
}

/**
 * Phase du stroboscope, avancée à chaque image (withFrameNanos) selon l'écart courant.
 * Sans défilement quand [running] est faux (aucune note, ou animations réduites).
 */
@Composable
fun rememberStrobePhase(cents: Double?, running: Boolean): DoubleState {
    val phase = remember { mutableDoubleStateOf(0.0) }
    val current by rememberUpdatedState(cents)
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last != 0L) phase.doubleValue = Strobe.advance(phase.doubleValue, current ?: 0.0, (t - last) / 1e9)
                last = t
            }
        }
    }
    return phase
}

/**
 * Bandes stroboscopiques (trois « octaves », motifs de plus en plus fins) : [phase] en périodes
 * du motif le plus large. Toutes les bandes défilent à la même vitesse, comme un disque de strobe.
 */
@Composable
fun StrobeBand(phase: () -> Double, inTune: Boolean, active: Boolean, modifier: Modifier = Modifier) {
    val stripe = when {
        !active -> MaterialTheme.colorScheme.outlineVariant
        inTune -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val bg = MaterialTheme.colorScheme.surfaceContainerHighest
    val marker = MaterialTheme.colorScheme.onSurface
    Canvas(
        modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(bg)
            .semantics { contentDescription = "Stroboscope : immobile quand la note est juste" },
    ) {
        val rows = 3
        val gap = 6.dp.toPx()
        val vPad = 10.dp.toPx()
        val rowH = (size.height - 2 * vPad - gap * (rows - 1)) / rows
        val base = 72.dp.toPx()
        val shift = (phase() % 1.0 + 1.0) % 1.0 * base
        for (r in 0 until rows) {
            val period = base / (1 shl r)
            val top = vPad + r * (rowH + gap)
            var x = -period + (shift % period).toFloat()
            val radius = CornerRadius(minOf(period / 4, rowH / 2) * 0.6f)
            while (x < size.width) {
                drawRoundRect(stripe, Offset(x, top), Size(period / 2, rowH), radius)
                x += period
            }
        }
        // Repère central et fondu des bords.
        drawLine(marker.copy(alpha = 0.55f), Offset(size.width / 2, 2.dp.toPx()), Offset(size.width / 2, size.height - 2.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
        val fade = size.width * 0.12f
        drawRect(Brush.horizontalGradient(listOf(bg, Color.Transparent), 0f, fade), Offset.Zero, Size(fade, size.height))
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, bg), size.width - fade, size.width), Offset(size.width - fade, 0f), Size(fade, size.height))
    }
}
