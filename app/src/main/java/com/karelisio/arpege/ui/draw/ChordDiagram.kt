package com.karelisio.arpege.ui.draw

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.karelisio.arpege.theory.Voicing

/**
 * Diagramme d'accord vertical (cordes verticales, cases horizontales).
 * [cascade] : nombre de notes « allumées » (fractionnaire) pour l'animation d'arpège.
 */
@Composable
fun ChordDiagram(voicing: Voicing, modifier: Modifier = Modifier, cascade: Float = Float.MAX_VALUE, showFingers: Boolean = true) {
    val measurer = rememberTextMeasurer()
    val line = MaterialTheme.colorScheme.onSurfaceVariant
    val dot = MaterialTheme.colorScheme.primary
    val onDot = MaterialTheme.colorScheme.onPrimary
    val muted = MaterialTheme.colorScheme.outline
    val strings = voicing.frets.size
    val fretsShown = 5
    val base = voicing.baseFret
    Canvas(modifier.aspectRatio(0.8f)) {
        val padTop = size.height * 0.16f
        val padSide = size.width * 0.16f
        val gridW = size.width - padSide * 2
        val gridH = size.height - padTop - size.height * 0.06f
        val sx = gridW / (strings - 1)
        val fy = gridH / fretsShown
        val stroke = size.width * 0.012f
        // Sillet ou numéro de case.
        if (base == 1) {
            drawRect(line, Offset(padSide - stroke, padTop - stroke * 3), Size(gridW + stroke * 2, stroke * 4))
        } else {
            val t = measurer.measure("${base}fr", TextStyle(fontSize = 12.sp, color = line, fontWeight = FontWeight.Bold))
            drawText(t, topLeft = Offset(padSide - t.size.width - sx * 0.25f, padTop + fy * 0.5f - t.size.height / 2))
        }
        for (i in 0..fretsShown) drawLine(line, Offset(padSide, padTop + i * fy), Offset(padSide + gridW, padTop + i * fy), stroke)
        for (s in 0 until strings) drawLine(line, Offset(padSide + s * sx, padTop), Offset(padSide + s * sx, padTop + gridH), stroke * (1.4f - 0.08f * s))

        // Ordre d'arpège : de la corde grave à l'aiguë.
        var order = 0
        val r = minOf(sx, fy) * 0.36f
        voicing.barre?.let { b ->
            val y = padTop + (b.fret - base + 0.5f) * fy
            val k = (cascade - order).coerceIn(0f, 1f)
            if (k > 0f) drawRoundRect(
                dot, Offset(padSide + b.fromString * sx - r, y - r * 0.8f),
                Size((b.toString - b.fromString) * sx * k + 2 * r, r * 1.6f), CornerRadius(r, r),
            )
        }
        voicing.frets.forEachIndexed { s, f ->
            val x = padSide + s * sx
            when {
                f < 0 -> {
                    val y = padTop * 0.45f; val d = r * 0.7f
                    drawLine(muted, Offset(x - d, y - d), Offset(x + d, y + d), stroke * 1.4f, StrokeCap.Round)
                    drawLine(muted, Offset(x - d, y + d), Offset(x + d, y - d), stroke * 1.4f, StrokeCap.Round)
                }
                f == 0 -> {
                    val k = (cascade - order++).coerceIn(0f, 1f)
                    drawCircle(line.copy(alpha = 0.4f + 0.6f * k), r * 0.7f, Offset(x, padTop * 0.45f), style = Stroke(stroke * 1.4f))
                }
                else -> {
                    val k = (cascade - order++).coerceIn(0f, 1f)
                    val c = Offset(x, padTop + (f - base + 0.5f) * fy)
                    scale(0.4f + 0.6f * k, c) {
                        drawCircle(dot.copy(alpha = 0.35f + 0.65f * k), r, c)
                        val finger = voicing.fingers[s]
                        if (showFingers && finger > 0) {
                            val t = measurer.measure("$finger", TextStyle(fontSize = (r / density * 1.1f).sp, color = onDot, fontWeight = FontWeight.Bold))
                            drawText(t, topLeft = Offset(c.x - t.size.width / 2, c.y - t.size.height / 2))
                        }
                    }
                }
            }
        }
    }
}
