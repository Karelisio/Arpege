package com.karelisio.arpege.ui.draw

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karelisio.arpege.theory.Tuning

/**
 * Manche de saisie : toucher une case la sélectionne (une case par corde), la toucher à nouveau
 * étouffe la corde ; la zone avant le sillet bascule entre corde à vide (○) et étouffée (×).
 * [frets] : -1 = étouffée, 0 = à vide, n = case. [label] : nom affiché sur une case jouée.
 * [bassString] : corde de la note la plus grave (mise en valeur).
 */
@Composable
fun FretPicker(
    tuning: Tuning,
    frets: List<Int>,
    onChange: (string: Int, fret: Int) -> Unit,
    label: (string: Int, fret: Int) -> String,
    modifier: Modifier = Modifier,
    fretCount: Int = 15,
    bassString: Int? = null,
) {
    val measurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val n = tuning.strings.size
    val fretW = 56.dp
    val rowH = 38.dp
    val current = rememberUpdatedState(frets)
    val change = rememberUpdatedState(onChange)
    Canvas(
        modifier
            .horizontalScroll(rememberScrollState())
            .width(fretW * (fretCount + 1))
            .height(rowH * n + 28.dp)
            .pointerInput(n, fretCount) {
                detectTapGestures { p ->
                    val top = 8.dp.toPx()
                    val sh = rowH.toPx()
                    val row = ((p.y - top) / sh).toInt().coerceIn(0, n - 1)
                    val string = n - 1 - row
                    val fw = fretW.toPx()
                    val fret = if (p.x < fw) 0 else ((p.x - fw) / fw).toInt() + 1
                    if (fret > fretCount) return@detectTapGestures
                    val now = current.value.getOrElse(string) { -1 }
                    val next = when {
                        fret == 0 -> if (now == 0) -1 else 0
                        now == fret -> -1
                        else -> fret
                    }
                    change.value(string, next)
                }
            },
    ) {
        val fw = fretW.toPx()
        val sh = rowH.toPx()
        val top = 8.dp.toPx()
        val yOf = { s: Int -> top + (n - 1 - s + 0.5f) * sh }
        val firstY = yOf(n - 1)
        val lastY = yOf(0)
        drawRect(colors.surfaceContainerHighest, Offset(fw, firstY - sh * 0.4f), Size(fw * fretCount, lastY - firstY + sh * 0.8f))
        for (f in listOf(3, 5, 7, 9, 15)) if (f <= fretCount) drawCircle(colors.outlineVariant, sh * 0.16f, Offset(fw + (f - 0.5f) * fw, (firstY + lastY) / 2))
        if (fretCount >= 12) {
            drawCircle(colors.outlineVariant, sh * 0.16f, Offset(fw + 11.5f * fw, firstY + sh * 0.5f))
            drawCircle(colors.outlineVariant, sh * 0.16f, Offset(fw + 11.5f * fw, lastY - sh * 0.5f))
        }
        drawLine(colors.onSurfaceVariant, Offset(fw, firstY), Offset(fw, lastY), 6.dp.toPx())
        for (f in 1..fretCount) {
            val x = fw + f * fw
            drawLine(colors.onSurfaceVariant.copy(alpha = 0.5f), Offset(x, firstY), Offset(x, lastY), 2.dp.toPx())
            val t = measurer.measure("$f", TextStyle(fontSize = 10.sp, color = colors.onSurfaceVariant))
            drawText(t, topLeft = Offset(x - fw / 2 - t.size.width / 2, size.height - t.size.height))
        }
        val r = minOf(sh, fw) * 0.38f
        for (s in 0 until n) {
            val y = yOf(s)
            val f = frets.getOrElse(s) { -1 }
            val muted = f < 0
            drawLine(
                if (muted) colors.outline.copy(alpha = 0.5f) else colors.onSurfaceVariant,
                Offset(fw * 0.75f, y), Offset(fw * (fretCount + 1), y), (1f + 1.2f * (n - 1 - s) / n.coerceAtLeast(1)).dp.toPx(),
            )
            val bass = s == bassString
            val fill = if (bass) colors.tertiary else colors.primary
            val onFill = if (bass) colors.onTertiary else colors.onPrimary
            val cx = fw * 0.4f
            when {
                muted -> {
                    val d = r * 0.5f
                    drawLine(colors.outline, Offset(cx - d, y - d), Offset(cx + d, y + d), 2.5.dp.toPx(), StrokeCap.Round)
                    drawLine(colors.outline, Offset(cx - d, y + d), Offset(cx + d, y - d), 2.5.dp.toPx(), StrokeCap.Round)
                }
                f == 0 -> {
                    drawCircle(fill, r, Offset(cx, y))
                    val t = measurer.measure(label(s, 0), TextStyle(fontSize = 11.sp, color = onFill, fontWeight = FontWeight.Bold))
                    drawText(t, topLeft = Offset(cx - t.size.width / 2, y - t.size.height / 2))
                }
                else -> {
                    // Corde frettée : simple rappel de la note à vide.
                    val open = measurer.measure(label(s, 0), TextStyle(fontSize = 11.sp, color = colors.onSurfaceVariant))
                    drawText(open, topLeft = Offset(cx - open.size.width / 2, y - open.size.height / 2))
                    val c = Offset(fw + (f - 0.5f) * fw, y)
                    drawCircle(fill, r, c)
                    val t = measurer.measure(label(s, f), TextStyle(fontSize = 11.sp, color = onFill, fontWeight = FontWeight.Bold))
                    drawText(t, topLeft = Offset(c.x - t.size.width / 2, c.y - t.size.height / 2))
                }
            }
        }
    }
}
