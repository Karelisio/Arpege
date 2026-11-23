package com.karelisio.arpege.ui.theory

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.arpege.data.AppSettings
import com.karelisio.arpege.data.PracticeActivity
import com.karelisio.arpege.data.TrackPractice
import com.karelisio.arpege.data.container
import com.karelisio.arpege.theory.CircleOfFifths
import com.karelisio.arpege.theory.Key
import com.karelisio.arpege.theory.Notation
import com.karelisio.arpege.ui.components.ToolScaffold
import com.karelisio.arpege.ui.components.rememberHaptics
import com.karelisio.arpege.theory.Clef
import com.karelisio.arpege.ui.draw.glyph
import com.karelisio.arpege.ui.draw.glyphStep
import com.karelisio.arpege.ui.draw.Smufl
import com.karelisio.arpege.ui.draw.StaffGeometry
import com.karelisio.arpege.ui.draw.drawGlyph
import com.karelisio.arpege.ui.draw.drawStaffLines
import com.karelisio.arpege.ui.theme.LocalReduceMotion
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun CircleScreen(onBack: () -> Unit, onChord: (String) -> Unit) {
    TrackPractice(PracticeActivity.CIRCLE)
    val c = LocalContext.current.container
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val notation = settings.notation
    val haptics = rememberHaptics()
    val reduce = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    // Rotation en degrés : la position p est en haut quand rotation = -30·p.
    val rotation = remember { Animatable(0f) }
    var selected by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        snapshotFlow { Math.floorMod((-rotation.value / 30f).roundToInt(), 12) }.collect {
            if (it != selected) { selected = it; haptics.tick() }
        }
    }

    fun snapTo(position: Int, velocity: Float = 0f) = scope.launch {
        val current = rotation.value
        // Cible la plus proche correspondant à cette position.
        var target = -30f * position
        while (target - current > 180) target -= 360
        while (target - current < -180) target += 360
        if (reduce) rotation.snapTo(target)
        else rotation.animateTo(target, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), initialVelocity = velocity)
    }

    val key = CircleOfFifths.majorAt(selected)
    val (iv, v) = CircleOfFifths.neighbors(selected)

    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val outer = MaterialTheme.colorScheme.primaryContainer
    val onOuter = MaterialTheme.colorScheme.onPrimaryContainer
    val inner = MaterialTheme.colorScheme.secondaryContainer
    val onInner = MaterialTheme.colorScheme.onSecondaryContainer
    val neighborColor = MaterialTheme.colorScheme.tertiaryContainer
    val stroke = MaterialTheme.colorScheme.surface
    val ink = MaterialTheme.colorScheme.onSurface

    ToolScaffold(title = "Cercle des quintes", onBack = onBack, sharedKey = "tool-circle") { p ->
        Column(
            Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .pointerInput(Unit) {
                        val tracker = VelocityTracker()
                        var last = 0f
                        var total = 0f
                        detectDragGestures(
                            onDragStart = { o ->
                                tracker.resetTracking()
                                total = 0f
                                last = Math.toDegrees(atan2((o.y - size.height / 2).toDouble(), (o.x - size.width / 2).toDouble())).toFloat()
                                scope.launch { rotation.stop() }
                            },
                            onDragEnd = {
                                // Inertie : on projette la vitesse angulaire, puis accrochage ressort.
                                val angularVelocity = tracker.calculateVelocity().x.coerceIn(-1500f, 1500f)
                                val projected = rotation.value + angularVelocity * 0.25f
                                snapTo(Math.floorMod((-projected / 30f).roundToInt(), 12), angularVelocity)
                            },
                        ) { change, _ ->
                            val pos = change.position
                            val a = Math.toDegrees(atan2((pos.y - size.height / 2).toDouble(), (pos.x - size.width / 2).toDouble())).toFloat()
                            var d = a - last
                            if (d > 180) d -= 360
                            if (d < -180) d += 360
                            last = a
                            total += d
                            tracker.addPosition(change.uptimeMillis, Offset(total, 0f))
                            scope.launch { rotation.snapTo(rotation.value + d) }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { o ->
                            val dx = o.x - size.width / 2; val dy = o.y - size.height / 2
                            if (hypot(dx, dy) < size.width * 0.18f) return@detectTapGestures
                            val ang = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat() - rotation.value
                            snapTo(Math.floorMod((ang / 30f).roundToInt(), 12))
                        }
                    },
            ) {
                val center = Offset(size.width / 2, size.height / 2)
                val r = size.minDimension / 2
                val rOuter = r * 0.98f; val rMid = r * 0.66f; val rInner = r * 0.40f
                rotate(rotation.value, center) {
                    for (i in 0 until 12) {
                        val isSel = i == selected
                        val isNeighbor = i == Math.floorMod(selected - 1, 12) || i == Math.floorMod(selected + 1, 12)
                        val start = -90f - 15f + i * 30f
                        drawArc(if (isSel) primary else if (isNeighbor) neighborColor else outer, start, 30f, true,
                            topLeft = Offset(center.x - rOuter, center.y - rOuter), size = Size(rOuter * 2, rOuter * 2))
                        drawArc(stroke, start, 30f, true, topLeft = Offset(center.x - rOuter, center.y - rOuter), size = Size(rOuter * 2, rOuter * 2), style = Stroke(3.dp.toPx()))
                    }
                    for (i in 0 until 12) {
                        val isSel = i == selected
                        val start = -90f - 15f + i * 30f
                        drawArc(if (isSel) primary.copy(alpha = 0.75f) else inner, start, 30f, true,
                            topLeft = Offset(center.x - rMid, center.y - rMid), size = Size(rMid * 2, rMid * 2))
                        drawArc(stroke, start, 30f, true, topLeft = Offset(center.x - rMid, center.y - rMid), size = Size(rMid * 2, rMid * 2), style = Stroke(3.dp.toPx()))
                    }
                    drawCircle(stroke, rInner, center)
                    for (i in 0 until 12) {
                        val ang = Math.toRadians((i * 30f - 90f).toDouble())
                        val isSel = i == selected
                        fun label(text: String, radius: Float, size: Int, color: androidx.compose.ui.graphics.Color, bold: Boolean) {
                            val t = measurer.measure(text, TextStyle(fontSize = size.sp, color = color, fontWeight = if (bold) FontWeight.Black else FontWeight.SemiBold))
                            val pos = Offset(center.x + (radius * cos(ang)).toFloat(), center.y + (radius * sin(ang)).toFloat())
                            // Le texte reste droit malgré la rotation du cercle.
                            rotate(-rotation.value, pos) { drawText(t, topLeft = Offset(pos.x - t.size.width / 2, pos.y - t.size.height / 2)) }
                        }
                        label(CircleOfFifths.majorAt(i).tonic.name(notation), (rOuter + rMid) / 2, if (notation == Notation.LATIN) 15 else 20, if (isSel) onPrimary else onOuter, isSel)
                        label(CircleOfFifths.minorAt(i).tonic.name(notation) + "m", (rMid + rInner) / 2, 13, if (isSel) onPrimary else onInner, isSel)
                    }
                }
                // Repère fixe en haut.
                drawCircle(ink, 5.dp.toPx(), Offset(center.x, center.y - rOuter - 2.dp.toPx()))
                val n = key.fifths
                val t = measurer.measure(if (n == 0) "♮" else "${kotlin.math.abs(n)}${if (n > 0) "♯" else "♭"}", TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Black, color = ink))
                drawText(t, topLeft = Offset(center.x - t.size.width / 2, center.y - t.size.height / 2))
            }

            AnimatedContent(selected, label = "keyInfo") { pos ->
                val key = CircleOfFifths.majorAt(pos)
                val minor = key.relative
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(key.name(notation), style = MaterialTheme.typography.headlineMedium)
                        Text("Relative : " + minor.name(notation), style = MaterialTheme.typography.titleMedium)
                        CircleOfFifths.enharmonicAt(pos)?.let { Text("Enharmonie : " + it.name(notation), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(signatureText(key, notation), style = MaterialTheme.typography.bodyLarge)
                        KeySignatureStaff(key)
                    }
                }
            }
            Text("Accords diatoniques", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                key.scale.diatonicChords().forEach { d ->
                    FilledTonalButton(onClick = { onChord(d.chord.toString()) }) { Text(d.roman + " · " + d.chord.name(notation)) }
                }
            }
            Text("Tonalités voisines", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { snapTo(selected - 1) }, label = { Text("IV · " + iv.name(notation)) })
                AssistChip(onClick = { snapTo(selected + 1) }, label = { Text("V · " + v.name(notation)) })
            }
        }
    }
}

private fun signatureText(key: Key, notation: Notation): String {
    val n = key.fifths
    return when {
        n == 0 -> "Aucune altération à l'armure"
        else -> "${kotlin.math.abs(n)} ${if (n > 0) "dièse" else "bémol"}${if (kotlin.math.abs(n) > 1) "s" else ""} : " +
            key.signature.joinToString(" ") { it.name(notation) }
    }
}

/** Armure dessinée en clé de sol avec la police Bravura. */
@Composable
private fun KeySignatureStaff(key: Key) {
    val measurer = rememberTextMeasurer()
    val ink = MaterialTheme.colorScheme.onSurface
    val sharpSteps = listOf(8, 5, 9, 6, 3, 7, 4) // Fa5 Do5 Sol5 Ré5 La4 Mi5 Si4
    val flatSteps = listOf(4, 7, 3, 6, 2, 5, 1)  // Si4 Mi5 La4 Ré5 Sol4 Do5 Fa4
    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
        val space = size.height / 9
        val g = StaffGeometry(0f, size.width, space * 2.5f, space)
        drawStaffLines(g, ink)
        drawGlyph(measurer, Clef.TREBLE.glyph, space * 0.5f, g.y(Clef.TREBLE.glyphStep), space, ink)
        val n = key.fifths
        val steps = if (n >= 0) sharpSteps.take(n) else flatSteps.take(-n)
        steps.forEachIndexed { i, s ->
            drawGlyph(measurer, if (n > 0) Smufl.SHARP else Smufl.FLAT, space * (4f + i * 1.2f), g.y(s), space, ink)
        }
    }
}
