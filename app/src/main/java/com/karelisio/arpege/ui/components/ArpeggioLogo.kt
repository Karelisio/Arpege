package com.karelisio.arpege.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.arpege.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Logo animé : trois croches liées qui montent en escalier, apparaissant en cascade. */
@Composable
fun ArpeggioLogo(size: Dp = 120.dp, color: Color = MaterialTheme.colorScheme.primary, key: Any = Unit) {
    val reduce = LocalReduceMotion.current
    val notes = remember { List(3) { Animatable(if (reduce) 1f else 0f) } }
    val beam = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(key) {
        if (reduce) return@LaunchedEffect
        notes.forEach { it.snapTo(0f) }; beam.snapTo(0f)
        notes.forEachIndexed { i, a ->
            launch { delay(i * 140L); a.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) }
        }
        delay(3 * 140L + 120)
        beam.animateTo(1f, tween(260))
    }
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 108f
        val heads = listOf(Offset(34f, 74f), Offset(52f, 63f), Offset(70f, 52f))
        heads.forEachIndexed { i, h ->
            val p = notes[i].value
            scale(p, p, pivot = Offset(h.x * u, h.y * u)) {
                rotate(-20f, pivot = Offset(h.x * u, h.y * u)) {
                    drawOval(color, topLeft = Offset((h.x - 7f) * u, (h.y - 5.4f) * u), size = Size(14f * u, 10.8f * u))
                }
                drawRect(color, topLeft = Offset((h.x + 4.6f) * u, (h.y - 27f) * u), size = Size(2.4f * u, 25.5f * u))
            }
        }
        val b = beam.value
        if (b > 0f) {
            val path = Path().apply {
                moveTo(38.6f * u, 47f * u)
                lineTo((38.6f + 38.4f * b) * u, (47f - 22f * b) * u)
                lineTo((38.6f + 38.4f * b) * u, (53.5f - 22f * b) * u)
                lineTo(38.6f * u, 53.5f * u)
                close()
            }
            drawPath(path, color)
        }
    }
}
