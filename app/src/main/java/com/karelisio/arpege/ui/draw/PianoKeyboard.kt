package com.karelisio.arpege.ui.draw

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class KeyMark(val label: String, val color: Color, val textColor: Color, val order: Int = 0)

private val BLACK = setOf(1, 3, 6, 8, 10)

/** Clavier de piano dessiné ; touches marquées, animation en cascade et saisie au toucher. */
@Composable
fun PianoKeyboard(
    startMidi: Int,
    octaves: Int,
    marks: Map<Int, KeyMark>,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    cascade: Float = Float.MAX_VALUE,
    pressed: Int? = null,
    onKey: ((Int) -> Unit)? = null,
) {
    val measurer = rememberTextMeasurer()
    val white = MaterialTheme.colorScheme.surfaceContainerLowest
    val black = MaterialTheme.colorScheme.inverseSurface
    val border = MaterialTheme.colorScheme.outline
    val press = MaterialTheme.colorScheme.secondaryContainer
    val keys = (startMidi until startMidi + 12 * octaves + 1).toList()
    val whites = keys.filter { (it % 12) !in BLACK }
    val onKeyState = rememberUpdatedState(onKey)

    fun layout(size: Size): Map<Int, Rect> {
        val ww = size.width / whites.size
        val map = HashMap<Int, Rect>()
        whites.forEachIndexed { i, m -> map[m] = Rect(i * ww, 0f, (i + 1) * ww, size.height) }
        for (m in keys) if ((m % 12) in BLACK) {
            val left = map[m - 1] ?: continue
            val bw = ww * 0.62f
            map[m] = Rect(left.right - bw / 2, 0f, left.right + bw / 2, size.height * 0.62f)
        }
        return map
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(keys.first(), octaves) {
                detectTapGestures { p ->
                    val cb = onKeyState.value ?: return@detectTapGestures
                    val rects = layout(Size(size.width.toFloat(), size.height.toFloat()))
                    val hit = rects.entries.filter { (it.key % 12) in BLACK }.firstOrNull { it.value.contains(p) }
                        ?: rects.entries.firstOrNull { (it.key % 12) !in BLACK && it.value.contains(p) }
                    hit?.let { cb(it.key) }
                }
            },
    ) {
        val rects = layout(size)
        val radius = CornerRadius(6.dp.toPx())
        fun drawMark(m: Int, r: Rect, isBlack: Boolean) {
            val mark = marks[m] ?: return
            val k = (cascade - mark.order).coerceIn(0f, 1f)
            if (k <= 0f) return
            val rad = r.width * 0.36f * (0.5f + 0.5f * k)
            val c = Offset(r.center.x, r.bottom - r.width * 0.55f)
            drawCircle(mark.color.copy(alpha = k), rad, c)
            if (mark.label.isNotEmpty()) {
                val t = measurer.measure(mark.label, TextStyle(fontSize = if (isBlack) 9.sp else 11.sp, color = mark.textColor, fontWeight = FontWeight.Bold))
                drawText(t, topLeft = Offset(c.x - t.size.width / 2, c.y - t.size.height / 2))
            }
        }
        for (m in whites) {
            val r = rects.getValue(m)
            drawRoundRect(if (pressed == m) press else white, r.topLeft, r.size, radius)
            drawRoundRect(border, r.topLeft, r.size, radius, style = Stroke(1.dp.toPx()))
            drawMark(m, r, false)
        }
        for (m in keys) if ((m % 12) in BLACK) {
            val r = rects[m] ?: continue
            drawRoundRect(if (pressed == m) press else black, r.topLeft, r.size, radius)
            drawMark(m, r, true)
        }
    }
}
