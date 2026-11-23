package com.karelisio.arpege.ui.training

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.arpege.data.AppSettings
import com.karelisio.arpege.data.PracticeActivity
import com.karelisio.arpege.data.TrackPractice
import com.karelisio.arpege.data.container
import com.karelisio.arpege.theory.Clef
import com.karelisio.arpege.theory.Letter
import com.karelisio.arpege.theory.Note
import com.karelisio.arpege.theory.Pitch
import com.karelisio.arpege.theory.dsp.Voice
import com.karelisio.arpege.theory.training.SolfegeTrainer
import com.karelisio.arpege.ui.components.ToolScaffold
import com.karelisio.arpege.ui.components.rememberHaptics
import com.karelisio.arpege.ui.draw.PianoKeyboard
import com.karelisio.arpege.ui.draw.Smufl
import com.karelisio.arpege.ui.draw.StaffGeometry
import com.karelisio.arpege.ui.draw.drawGlyph
import com.karelisio.arpege.ui.draw.drawLedgerLines
import com.karelisio.arpege.ui.draw.drawStaffLines
import com.karelisio.arpege.ui.draw.glyph
import com.karelisio.arpege.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private const val CHRONO_SECONDS = 60

@Composable
fun SolfegeScreen(onBack: () -> Unit) {
    TrackPractice(PracticeActivity.SOLFEGE)
    val c = LocalContext.current.container
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val reduce = LocalReduceMotion.current
    val random = remember { Random(System.nanoTime()) }

    var clef by rememberSaveable { mutableStateOf(Clef.TREBLE) }
    var level by rememberSaveable { mutableIntStateOf(1) }
    var keyboard by rememberSaveable { mutableStateOf(false) }
    var chrono by rememberSaveable { mutableStateOf(false) }
    val exercise = "solfege_${clef.name}"
    val stats by c.db.stats().forExercise(exercise).collectAsState(emptyList())
    val best = stats.firstOrNull { it.item == "chrono_L$level" }?.correct ?: 0

    var target by remember { mutableStateOf(SolfegeTrainer.question(clef, level, random)) }
    var feedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var accidental by remember { mutableIntStateOf(0) }
    var correct by remember(clef, level) { mutableIntStateOf(0) }
    var total by remember(clef, level) { mutableIntStateOf(0) }
    var streak by remember(clef, level) { mutableIntStateOf(0) }
    var remaining by remember { mutableIntStateOf(-1) }
    var pressed by remember { mutableStateOf<Int?>(null) }
    val noteIn = remember { Animatable(1f) }

    fun next() {
        target = SolfegeTrainer.question(clef, level, random, target)
        feedback = null; accidental = 0; pressed = null
        if (!reduce) scope.launch { noteIn.snapTo(0f); noteIn.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
    }

    fun submit(ok: Boolean) {
        if (feedback != null || remaining == 0) return
        total++
        if (ok) { correct++; streak++; haptics.confirm() } else { streak = 0; haptics.reject() }
        feedback = ok to target.name(settings.notation)
        c.stats.record(exercise, target.toString(), ok)
        c.sound.playPitches(listOf(target), Voice.PIANO, settings.a4.toDouble(), noteSec = 1.0)
        scope.launch { delay(if (ok) 450 else 1400); next() }
    }

    LaunchedEffect(clef, level) { next() }
    LaunchedEffect(chrono, clef, level) {
        if (!chrono) { remaining = -1; return@LaunchedEffect }
        correct = 0; total = 0; streak = 0
        remaining = CHRONO_SECONDS
        while (remaining > 0) { delay(1000); remaining-- }
        c.stats.recordBest(exercise, "chrono_L$level", correct)
        haptics.confirm()
    }

    ToolScaffold(title = "Solfège", onBack = onBack, sharedKey = "tool-solfege") { p ->
        Column(
            Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Clef.entries.forEachIndexed { i, cl ->
                    SegmentedButton(selected = cl == clef, onClick = { clef = cl }, shape = SegmentedButtonDefaults.itemShape(i, Clef.entries.size)) { Text(cl.label, maxLines = 1) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("Portée", "+ Lignes sup.", "+ Altérations").forEachIndexed { i, l ->
                    FilterChip(selected = level == i + 1, onClick = { level = i + 1 }, label = { Text(l) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Clavier", style = MaterialTheme.typography.labelLarge)
                Switch(checked = keyboard, onCheckedChange = { keyboard = it })
                Text("Chrono ${CHRONO_SECONDS}s", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 12.dp))
                Switch(checked = chrono, onCheckedChange = { chrono = it })
            }
            if (chrono) {
                LinearWavyProgressIndicator(progress = { (remaining.coerceAtLeast(0)).toFloat() / CHRONO_SECONDS }, modifier = Modifier.fillMaxWidth())
                Text(if (remaining == 0) "Temps écoulé : $correct notes · record $best" else "$remaining s · record $best", style = MaterialTheme.typography.titleMedium)
            }
            ScoreHeader(correct, total, streak)

            Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                NoteStaff(clef, target, noteIn.value, Modifier.fillMaxWidth().height(190.dp).padding(16.dp))
            }
            AnimatedContent(feedback, label = "feedback") { f ->
                Text(
                    when { f == null -> "Quelle est cette note ?"; f.first -> "Bravo : ${f.second}"; else -> "C'était ${f.second}" },
                    style = MaterialTheme.typography.titleLarge,
                    color = when { f == null -> MaterialTheme.colorScheme.onSurface; f.first -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.error },
                )
            }

            if (keyboard) {
                val start = (target.midi / 12) * 12 - 12
                PianoKeyboard(startMidi = start.coerceIn(24, 84), octaves = 2, marks = emptyMap(), height = 150.dp, pressed = pressed) { m ->
                    pressed = m
                    submit(SolfegeTrainer.checkKey(target, m))
                }
                Text("Toutes les octaves sont acceptées.", style = MaterialTheme.typography.bodySmall)
            } else {
                if (level >= 3) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(-1 to "♭", 0 to "♮", 1 to "♯").forEach { (a, l) ->
                        FilterChip(selected = accidental == a, onClick = { accidental = a }, label = { Text(l, style = MaterialTheme.typography.titleLarge) })
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 4) {
                    Letter.entries.forEach { letter ->
                        val n = Note(letter, accidental)
                        val state = when {
                            feedback == null -> AnswerState.NEUTRAL
                            target.note.letter == letter -> if (feedback!!.first) AnswerState.CORRECT else AnswerState.REVEAL
                            else -> AnswerState.NEUTRAL
                        }
                        AnswerButton(n.name(settings.notation), state, Modifier.weight(1f)) { submit(SolfegeTrainer.checkName(target, n)) }
                    }
                }
            }
            Button(onClick = { next() }, modifier = Modifier.align(Alignment.End)) { Text("Passer") }
            ItemStats(stats.filter { !it.item.startsWith("chrono") }, label = { id -> Pitch.parse(id)?.name(settings.notation) ?: id }, onReset = { c.stats.reset(exercise) })
        }
    }
}

@Composable
private fun NoteStaff(clef: Clef, target: Pitch, appear: Float, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val ink = MaterialTheme.colorScheme.onSurface
    val noteColor = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val space = size.height / 12
        val g = StaffGeometry(0f, size.width, size.height / 2 - 2 * space, space)
        drawStaffLines(g, ink)
        drawGlyph(measurer, clef.glyph, space * 0.6f, g.y(clef.refStep), space, ink)
        val step = clef.step(target)
        val x = size.width * 0.6f
        drawLedgerLines(g, step, x + space * 0.6f, ink)
        val y = g.y(step) - (1 - appear) * space * 2
        val a = target.note.accidental
        if (a != 0) drawGlyph(measurer, if (a > 0) Smufl.SHARP else Smufl.FLAT, x - space * 1.6f, y, space, noteColor.copy(alpha = appear.coerceIn(0f, 1f)))
        drawGlyph(measurer, Smufl.NOTEHEAD_WHOLE, x, y, space, noteColor.copy(alpha = appear.coerceIn(0f, 1f)))
    }
}
