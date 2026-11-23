package com.karelisio.arpege.ui.draw

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karelisio.arpege.theory.Tuning

/** Repère sur le manche : corde, case, libellé et couleur. [order] sert à l'animation en cascade. */
data class FretMark(val string: Int, val fret: Int, val label: String, val color: Color, val textColor: Color, val order: Int = 0, val emphasized: Boolean = false)

/** Manche horizontal défilant (corde grave en bas, comme vu par le musicien). */
@Composable
fun Fretboard(
    tuning: Tuning,
    marks: List<FretMark>,
    modifier: Modifier = Modifier,
    frets: Int = 15,
    cascade: Float = Float.MAX_VALUE,
    highlightOrder: Int? = null,
) {
    val measurer = rememberTextMeasurer()
    val wood = MaterialTheme.colorScheme.surfaceContainerHighest
    val line = MaterialTheme.colorScheme.onSurfaceVariant
    val inlay = MaterialTheme.colorScheme.outlineVariant
    val n = tuning.strings.size
    val fretW = 64.dp
    Canvas(
        modifier
            .horizontalScroll(rememberScrollState())
            .width(fretW * (frets + 1))
            .height((34 * n + 44).dp),
    ) {
        val fw = fretW.toPx()
        val top = 16.dp.toPx()
        val sh = (size.height - top - 34.dp.toPx()) / (n - 1)
        val nutX = fw
        drawRect(wood, Offset(nutX, top - sh * 0.4f), androidx.compose.ui.geometry.Size(fw * frets, sh * (n - 1) + sh * 0.8f))
        // Repères de touche.
        for (f in listOf(3, 5, 7, 9, 15, 17)) if (f <= frets) drawCircle(inlay, sh * 0.18f, Offset(nutX + (f - 0.5f) * fw, top + sh * (n - 1) / 2))
        if (frets >= 12) {
            drawCircle(inlay, sh * 0.18f, Offset(nutX + 11.5f * fw, top + sh * 0.5f))
            drawCircle(inlay, sh * 0.18f, Offset(nutX + 11.5f * fw, top + sh * (n - 1.5f)))
        }
        drawLine(line, Offset(nutX, top), Offset(nutX, top + sh * (n - 1)), 6.dp.toPx())
        for (f in 1..frets) {
            val x = nutX + f * fw
            drawLine(line.copy(alpha = 0.6f), Offset(x, top), Offset(x, top + sh * (n - 1)), 2.dp.toPx())
            val t = measurer.measure("$f", TextStyle(fontSize = 10.sp, color = line))
            drawText(t, topLeft = Offset(x - fw / 2 - t.size.width / 2, size.height - t.size.height))
        }
        // Cordes : la plus grave (index 0) en bas.
        for (s in 0 until n) {
            val y = top + (n - 1 - s) * sh
            drawLine(line, Offset(0f, y), Offset(nutX + frets * fw, y), (1f + 0.5f * (n - 1 - s) / n + 0.6f * (1 - s.toFloat() / n)).dp.toPx())
        }
        val r = minOf(sh, fw) * 0.36f
        for (m in marks) {
            if (m.fret > frets) continue
            val k = (cascade - m.order).coerceIn(0f, 1f)
            if (k <= 0f) continue
            val x = if (m.fret == 0) nutX * 0.5f else nutX + (m.fret - 0.5f) * fw
            val y = top + (n - 1 - m.string) * sh
            val hl = highlightOrder != null && highlightOrder == m.order
            val c = Offset(x, y)
            scale(k * if (hl) 1.25f else 1f, c) {
                drawCircle(m.color, r * if (m.emphasized) 1.12f else 1f, c)
                val t = measurer.measure(m.label, TextStyle(fontSize = 11.sp, color = m.textColor, fontWeight = FontWeight.Bold))
                drawText(t, topLeft = Offset(x - t.size.width / 2, y - t.size.height / 2))
            }
        }
    }
}
