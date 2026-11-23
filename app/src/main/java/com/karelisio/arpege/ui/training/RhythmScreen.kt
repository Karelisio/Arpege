package com.karelisio.arpege.ui.training

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karelisio.arpege.data.PracticeActivity
import com.karelisio.arpege.data.TrackPractice
import com.karelisio.arpege.data.container
import com.karelisio.arpege.theory.dsp.Synth
import com.karelisio.arpege.theory.training.NoteValue
import com.karelisio.arpege.theory.training.RhythmBar
import com.karelisio.arpege.theory.training.RhythmElement
import com.karelisio.arpege.theory.training.RhythmTrainer
import com.karelisio.arpege.theory.training.TapResult
import com.karelisio.arpege.ui.components.ToolScaffold
import com.karelisio.arpege.ui.components.rememberHaptics
import com.karelisio.arpege.ui.draw.Smufl
import com.karelisio.arpege.ui.draw.drawGlyph
import com.karelisio.arpege.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

private enum class Phase { IDLE, COUNT_IN, PLAYING, RESULT }

@Composable
fun RhythmScreen(onBack: () -> Unit) {
    TrackPractice(PracticeActivity.RHYTHM)
    val c = LocalContext.current.container
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val reduce = LocalReduceMotion.current
    val random = remember { Random(System.nanoTime()) }
    val stats by c.db.stats().forExercise("rhythm").collectAsState(emptyList())

    var level by rememberSaveable { mutableIntStateOf(1) }
    var bpm by rememberSaveable { mutableFloatStateOf(80f) }
    var bar by remember { mutableStateOf(RhythmTrainer.generate(1, random)) }
    var phase by remember { mutableStateOf(Phase.IDLE) }
    var result by remember { mutableStateOf<TapResult?>(null) }
    var barStartMs by remember { mutableLongStateOf(0L) }
    var playhead by remember { mutableFloatStateOf(-1f) }
    val taps = remember { mutableStateListOf<Long>() }
    val pad = remember { Animatable(1f) }

    val beatMs = 60000.0 / bpm.roundToInt()

    fun buildBuffer(includeRhythm: Boolean): FloatArray {
        val sr = c.sound.sampleRate
        val beatFrames = sr * 60.0 / bpm.roundToInt()
        val out = FloatArray((beatFrames * 9).toInt())
        val strong = Synth.click(1760.0, sr, 0.8)
        val weak = Synth.click(1100.0, sr, 0.5)
        for (b in 0 until 8) Synth.mix(out, if (b % 4 == 0) strong else weak, (b * beatFrames).toInt(), if (b < 4) 1f else 0.55f)
        if (includeRhythm) {
            val wood = Synth.click(620.0, sr, 0.9, durationMs = 60.0)
            bar.onsets.forEach { t -> Synth.mix(out, wood, (4 * beatFrames + t * beatFrames / RhythmTrainer.TICKS_PER_BEAT).toInt(), 1f) }
        }
        return out
    }

    fun start(demo: Boolean) {
        taps.clear(); result = null
        phase = Phase.COUNT_IN
        c.sound.playBuffer(buildBuffer(demo)) { startNanos ->
            barStartMs = startNanos / 1_000_000 + (4 * beatMs).toLong()
            scope.launch {
                // Tête de lecture et fin de mesure calées sur l'horloge audio.
                while (true) {
                    withFrameNanos { }
                    val now = SystemClock.uptimeMillis()
                    val t = (now - barStartMs) / (4 * beatMs)
                    if (t >= 0 && phase == Phase.COUNT_IN) phase = Phase.PLAYING
                    playhead = t.toFloat()
                    if (t > 1.15) break
                }
                playhead = -1f
                if (demo) { phase = Phase.IDLE; return@launch }
                val expected = bar.onsets.map { RhythmTrainer.ticksToMs(it, bpm.roundToInt()) }
                val r = RhythmTrainer.score(expected, taps.map { (it - barStartMs).toDouble() })
                result = r
                phase = Phase.RESULT
                c.stats.record("rhythm", "L$level", r.accuracy >= 80)
                if (r.accuracy >= 80) haptics.confirm() else haptics.reject()
            }
        }
    }

    ToolScaffold(title = "Rythme", onBack = onBack, sharedKey = "tool-rhythm") { p ->
        Column(
            Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Niveau", style = MaterialTheme.typography.titleSmall)
                (1..RhythmTrainer.LEVELS).forEach { l ->
                    FilterChip(selected = l == level, onClick = { level = l; bar = RhythmTrainer.generate(l, random); result = null; phase = Phase.IDLE }, label = { Text("$l") })
                }
            }
            Text(listOf("Rondes, blanches, noires", "+ croches et silences", "+ pointées et doubles", "+ triolets et syncopes")[level - 1],
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Tempo : ${bpm.roundToInt()} BPM", style = MaterialTheme.typography.titleSmall)
            Slider(value = bpm, onValueChange = { bpm = it.roundToInt().toFloat() }, valueRange = 50f..140f, enabled = phase == Phase.IDLE || phase == Phase.RESULT)

            Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                RhythmStaff(bar, result, playhead, Modifier.fillMaxWidth().height(150.dp).padding(12.dp))
            }

            result?.let { r ->
                val devs = r.deviationsMs.filterNotNull()
                Text("Précision ${r.accuracy} %", style = MaterialTheme.typography.headlineMedium,
                    color = if (r.accuracy >= 80) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error)
                Text(
                    "Écart moyen ${if (devs.isEmpty()) "—" else "${devs.map { abs(it) }.average().roundToInt()} ms"}" +
                        (if (devs.isNotEmpty()) " · tendance ${if (devs.average() > 0) "en retard" else "en avance"}" else "") +
                        " · ${r.deviationsMs.count { it == null }} manquée(s) · ${r.extraTaps} en trop",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // Zone de tap.
            val active = phase == Phase.PLAYING || phase == Phase.COUNT_IN
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .graphicsLayer { scaleX = pad.value; scaleY = pad.value }
                    .clip(MaterialShapes.Cookie12Sided.toShape())
                    .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            taps += down.uptimeMillis
                            haptics.tick()
                            if (!reduce) scope.launch { pad.snapTo(0.94f); pad.animateTo(1f, tween(140)) }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when (phase) {
                        Phase.COUNT_IN -> "Décompte…"
                        Phase.PLAYING -> "Tape !"
                        else -> "Zone de tap"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { haptics.click(); start(demo = false) }, enabled = !active) { Text(if (phase == Phase.RESULT) "Réessayer" else "Commencer") }
                FilledTonalButton(onClick = { start(demo = true) }, enabled = !active) { Text("Écouter") }
                FilledTonalButton(onClick = { bar = RhythmTrainer.generate(level, random); result = null; phase = Phase.IDLE }, enabled = !active) { Text("Nouvelle") }
            }
            Text("Un décompte d'une mesure précède l'exercice. Tape chaque note (pas les silences).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ItemStats(stats, label = { "Niveau ${it.removePrefix("L")}" }, onReset = { c.stats.reset("rhythm") })
        }
    }
}

private fun glyphFor(e: RhythmElement): String = if (e.rest) when (e.value) {
    NoteValue.WHOLE -> Smufl.REST_WHOLE
    NoteValue.HALF -> Smufl.REST_HALF
    NoteValue.QUARTER -> Smufl.REST_QUARTER
    NoteValue.EIGHTH -> Smufl.REST_8TH
    NoteValue.SIXTEENTH -> Smufl.REST_16TH
} else when (e.value) {
    NoteValue.WHOLE -> Smufl.NOTE_WHOLE
    NoteValue.HALF -> Smufl.NOTE_HALF
    NoteValue.QUARTER -> Smufl.NOTE_QUARTER
    NoteValue.EIGHTH -> Smufl.NOTE_8TH
    NoteValue.SIXTEENTH -> Smufl.NOTE_16TH
}

/** Mesure rythmique sur une ligne (Bravura), écarts par note après l'essai. */
@Composable
private fun RhythmStaff(bar: RhythmBar, result: TapResult?, playhead: Float, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val ink = MaterialTheme.colorScheme.onSurface
    val good = MaterialTheme.colorScheme.tertiary
    val mid = MaterialTheme.colorScheme.secondary
    val bad = MaterialTheme.colorScheme.error
    val head = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val space = size.height / 9
        val lineY = size.height * 0.5f
        val left = space * 4.5f
        val right = size.width - space
        drawLine(ink, Offset(0f, lineY), Offset(size.width, lineY), space * 0.1f)
        drawLine(ink, Offset(right, lineY - space * 2), Offset(right, lineY + space * 2), space * 0.15f)
        // Chiffrage 4/4.
        drawGlyph(measurer, Smufl.timeDigit(4), space * 1.2f, lineY - space, space, ink)
        drawGlyph(measurer, Smufl.timeDigit(4), space * 1.2f, lineY + space, space, ink)
        val width = right - left - space
        var noteIndex = 0
        bar.elements.forEachIndexed { i, e ->
            val x = left + width * bar.starts[i] / RhythmTrainer.BAR_TICKS
            drawGlyph(measurer, glyphFor(e), x, lineY, space, ink)
            if (e.dotted) drawGlyph(measurer, Smufl.AUG_DOT, x + space * 1.6f, lineY - space * 0.3f, space, ink)
            if (e.triplet && (i == 0 || !bar.elements[i - 1].triplet || (bar.starts[i] % RhythmTrainer.TICKS_PER_BEAT == 0))) {
                drawGlyph(measurer, Smufl.TUPLET_3, x + space * 1.2f, lineY - space * 4f, space * 0.8f, ink)
            }
            if (!e.rest) {
                val d = result?.deviationsMs?.getOrNull(noteIndex)
                if (result != null) {
                    val (txt, col) = when {
                        d == null -> "×" to bad
                        abs(d) < 40 -> "%+d".format(d.roundToInt()) to good
                        abs(d) < 90 -> "%+d".format(d.roundToInt()) to mid
                        else -> "%+d".format(d.roundToInt()) to bad
                    }
                    val t = measurer.measure(txt, TextStyle(fontSize = 11.sp, color = col, fontWeight = FontWeight.Bold))
                    drawText(t, topLeft = Offset(x, lineY + space * 1.6f))
                }
                noteIndex++
            }
        }
        if (playhead in 0f..1f) {
            val x = left + width * playhead
            drawLine(head, Offset(x, lineY - space * 3), Offset(x, lineY + space * 3), space * 0.2f)
        }
    }
}

