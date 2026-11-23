package com.karelisio.arpege.ui.journal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karelisio.arpege.theory.practice.PracticeStats
import com.karelisio.arpege.ui.theme.LocalReduceMotion
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

@Composable
private fun rememberGrow(key: Any?, durationMs: Int = 900): Animatable<Float, *> {
    val reduce = LocalReduceMotion.current
    val a = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(key) {
        if (!reduce) { a.snapTo(0f); a.animateTo(1f, tween(durationMs)) }
    }
    return a
}

/** Anneau de progression hebdomadaire animé. */
@Composable
fun GoalRing(fraction: Float, size: Dp = 150.dp, content: @Composable () -> Unit) {
    val grow = rememberGrow(fraction)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = MaterialTheme.colorScheme.primary
    val over = MaterialTheme.colorScheme.tertiary
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = 16.dp.toPx()
            val inset = w / 2
            val arcSize = Size(this.size.width - w, this.size.height - w)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
            val f = fraction * grow.value
            drawArc(fill, -90f, 360f * f.coerceAtMost(1f), false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
            if (f > 1f) drawArc(over, -90f, 360f * (f - 1f).coerceAtMost(1f), false, Offset(inset, inset), arcSize, style = Stroke(w * 0.5f, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Barres des 7 derniers jours, qui poussent en cascade. */
@Composable
fun WeekBars(days: List<Pair<LocalDate, Int>>, modifier: Modifier = Modifier) {
    val grow = rememberGrow(days)
    val max = (days.maxOfOrNull { it.second } ?: 0).coerceAtLeast(30)
    val measurer = rememberTextMeasurer()
    val bar = MaterialTheme.colorScheme.secondary
    val today = MaterialTheme.colorScheme.primary
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier.fillMaxWidth().height(140.dp)) {
        val n = days.size
        val slot = size.width / n
        val bw = slot * 0.5f
        val chartH = size.height - 22.dp.toPx()
        days.forEachIndexed { i, (d, m) ->
            val local = ((grow.value * (n + 2)) - i).coerceIn(0f, 1f)
            val h = chartH * m / max * local
            val x = i * slot + (slot - bw) / 2
            drawRoundRect(if (i == n - 1) today else bar, Offset(x, chartH - h), Size(bw, h.coerceAtLeast(2f)), CornerRadius(bw / 3))
            val t = measurer.measure(d.dayOfWeek.getDisplayName(JTextStyle.NARROW, Locale.FRANCE), TextStyle(fontSize = 11.sp, color = label))
            drawText(t, topLeft = Offset(i * slot + slot / 2 - t.size.width / 2, size.height - t.size.height))
        }
    }
}

/** Heatmap annuelle (53 semaines × 7 jours), défilant jusqu'à aujourd'hui. */
@Composable
fun YearHeatmap(daily: Map<LocalDate, Int>, today: LocalDate) {
    val colors = listOf(
        MaterialTheme.colorScheme.surfaceContainerHighest,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
        MaterialTheme.colorScheme.primary,
    )
    val weeks = 53
    val cell = 13.dp
    val gap = 3.dp
    val scroll = rememberScrollState()
    LaunchedEffect(Unit) { scroll.scrollTo(scroll.maxValue) }
    val grow = rememberGrow(daily.size, 1200)
    val start = today.minusWeeks((weeks - 1).toLong()).with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    Box(Modifier.horizontalScroll(scroll)) {
        Canvas(Modifier.width((cell + gap) * weeks).height((cell + gap) * 7)) {
            val c = cell.toPx(); val g = gap.toPx()
            for (w in 0 until weeks) for (d in 0 until 7) {
                val date = start.plusDays((w * 7 + d).toLong())
                if (date.isAfter(today)) continue
                val reveal = ((grow.value * (weeks + 8)) - w).coerceIn(0f, 1f)
                val lvl = PracticeStats.level(daily[date] ?: 0)
                val color = if (reveal < 1f && lvl > 0) colors[0] else colors[lvl]
                drawRoundRect(color, Offset(w * (c + g), d * (c + g)), Size(c, c), CornerRadius(c / 4))
            }
        }
    }
}

/** Barres horizontales animées (stats par instrument / activité). */
@Composable
fun HorizontalBars(items: List<Pair<String, Int>>) {
    val max = (items.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { i, (label, minutes) ->
            val reduce = LocalReduceMotion.current
            val a = remember(label) { Animatable(if (reduce) 1f else 0f) }
            LaunchedEffect(minutes) { a.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessVeryLow)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.width(110.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Box(Modifier.weight(1f).height(18.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth(minutes.toFloat() / max * a.value.coerceIn(0f, 1.05f))
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (i % 2 == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary),
                    )
                }
                Text(formatMinutes(minutes), Modifier.width(64.dp).padding(start = 8.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

fun formatMinutes(m: Int): String = if (m < 60) "$m min" else "${m / 60} h ${"%02d".format(m % 60)}"
