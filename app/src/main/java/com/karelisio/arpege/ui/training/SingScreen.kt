package com.karelisio.arpege.ui.training

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.karelisio.arpege.audio.TunerEngine
import com.karelisio.arpege.data.AppSettings
import com.karelisio.arpege.data.PracticeActivity
import com.karelisio.arpege.data.TrackPractice
import com.karelisio.arpege.data.container
import com.karelisio.arpege.theory.Notation
import com.karelisio.arpege.theory.Pitch
import com.karelisio.arpege.theory.dsp.NoteEvent
import com.karelisio.arpege.theory.dsp.Voice
import com.karelisio.arpege.theory.frequencyToMidi
import com.karelisio.arpege.theory.midiToFrequency
import com.karelisio.arpege.theory.training.EarTraining
import com.karelisio.arpege.theory.training.HoldDetector
import com.karelisio.arpege.theory.training.SingMode
import com.karelisio.arpege.theory.training.SingQuestion
import com.karelisio.arpege.theory.training.SingTraining
import com.karelisio.arpege.theory.training.VoiceRange
import com.karelisio.arpege.ui.components.MorphShape
import com.karelisio.arpege.ui.components.ToolScaffold
import com.karelisio.arpege.ui.components.rememberHaptics
import com.karelisio.arpege.ui.permissions.AppPermission
import com.karelisio.arpege.ui.permissions.rememberPermissionsController
import com.karelisio.arpege.ui.theme.LocalReduceMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/** Étapes d'une question. */
enum class SingPhase { IDLE, REFERENCE, SINGING, SUCCESS, REVEALED }

/** Question en cours et son détecteur de tenue. */
private class SingRound(val question: SingQuestion, val detector: HoldDetector)

private const val REFERENCE_SEC = 1.3

@Composable
fun SingScreen(onBack: () -> Unit) {
    TrackPractice(PracticeActivity.SING)
    val c = LocalContext.current.container
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val permissions = rememberPermissionsController()
    val micGranted = permissions.granted[AppPermission.MICROPHONE] == true
    val engine = remember { TunerEngine(c.appContext) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val random = remember { Random(System.nanoTime()) }

    var mode by rememberSaveable { mutableStateOf(SingMode.UP) }
    var level by rememberSaveable { mutableIntStateOf(1) }
    var octaveFree by rememberSaveable { mutableStateOf(true) }
    var range by rememberSaveable { mutableStateOf(VoiceRange.LOW) }
    val stats by c.db.stats().forExercise(SingTraining.EXERCISE).collectAsStateWithLifecycle(emptyList())
    val labels = remember { SingTraining.allItems().associate { it.id to it.label } }

    var round by remember { mutableStateOf<SingRound?>(null) }
    var phase by remember { mutableStateOf(SingPhase.IDLE) }
    var hold by remember { mutableStateOf(HoldDetector.State.EMPTY) }
    // Instant (horloge de capture, ns) à partir duquel on écoute : après la fin de la référence.
    var listenAfter by remember { mutableLongStateOf(Long.MAX_VALUE) }
    var plays by remember { mutableIntStateOf(0) }
    var micError by remember { mutableStateOf(false) }
    var correct by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var advance by remember { mutableStateOf<Job?>(null) }

    fun freq(midi: Int) = midiToFrequency(midi.toDouble(), settings.a4.toDouble())

    fun playReference() {
        val r = round ?: return
        r.detector.reset()
        hold = HoldDetector.State.EMPTY
        phase = SingPhase.REFERENCE
        plays++
        // Estimation prudente, affinée dès que la lecture démarre réellement (même horloge que le micro).
        listenAfter = System.nanoTime() + ((REFERENCE_SEC + 0.8) * 1e9).toLong()
        c.sound.playEvents(
            listOf(NoteEvent(0.0, freq(r.question.referenceMidi), REFERENCE_SEC, 0.75)), Voice.PIANO,
            onStart = { start -> listenAfter = start + ((REFERENCE_SEC + 0.2 + 0.15) * 1e9).toLong() },
        )
    }

    fun next() {
        advance?.cancel()
        val items = SingTraining.items(mode, level)
        val statMap = stats.associate { it.item to (it.correct to it.attempts) }
        val item = EarTraining.pick(items, statMap, random, avoid = round?.question?.item?.id)
        val q = SingTraining.question(item, range, random)
        round = SingRound(q, HoldDetector(q.targetMidi, octaveFree))
        playReference()
    }

    fun succeed() {
        val r = round ?: return
        phase = SingPhase.SUCCESS
        correct++; total++; streak++
        haptics.confirm()
        c.stats.record(SingTraining.EXERCISE, r.question.item.id, true)
        c.sound.playEvents(listOf(NoteEvent(0.0, freq(r.question.targetMidi), 0.8, 0.6)), Voice.PIANO)
        advance = scope.launch { delay(1700); next() }
    }

    fun skip() {
        val r = round ?: return
        if (phase != SingPhase.REFERENCE && phase != SingPhase.SINGING) return
        phase = SingPhase.REVEALED
        total++; streak = 0
        haptics.reject()
        c.stats.record(SingTraining.EXERCISE, r.question.item.id, false)
        // Fait entendre la référence puis la cible.
        c.sound.playEvents(
            listOf(NoteEvent(0.0, freq(r.question.referenceMidi), 0.8, 0.7), NoteEvent(0.9, freq(r.question.targetMidi), 1.4, 0.75)),
            Voice.PIANO,
        )
        advance = scope.launch { delay(3300); next() }
    }

    LaunchedEffect(mode, level, range, octaveFree) {
        advance?.cancel()
        round = null
        phase = SingPhase.IDLE
        hold = HoldDetector.State.EMPTY
    }
    // Repli si la lecture ou le micro ne répondent pas : l'invite passe quand même à « chante ».
    LaunchedEffect(plays) {
        delay(((REFERENCE_SEC + 0.4) * 1000).toLong())
        if (phase == SingPhase.REFERENCE) phase = SingPhase.SINGING
    }
    LaunchedEffect(micGranted) {
        if (!micGranted) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            engine.timedPitches().catch { micError = true }.collect { tp ->
                val r = round ?: return@collect
                if (phase != SingPhase.REFERENCE && phase != SingPhase.SINGING) return@collect
                // On n'écoute pas la note de référence jouée par le haut-parleur.
                if (tp.timeNanos < listenAfter) return@collect
                if (phase == SingPhase.REFERENCE) phase = SingPhase.SINGING
                val midi = tp.estimate?.takeIf { it.clarity >= 0.8 }?.let { frequencyToMidi(it.frequency, settings.a4.toDouble()) }
                hold = r.detector.feed(tp.timeNanos / 1_000_000, midi)
                if (hold.held) succeed()
            }
        }
    }

    ToolScaffold(title = "Chante la note", onBack = onBack, sharedKey = "tool-sing") { p ->
        Column(
            Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SingMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { haptics.tick(); mode = m },
                        shape = SegmentedButtonDefaults.itemShape(i, SingMode.entries.size),
                    ) { Text(m.label, maxLines = 1) }
                }
            }
            if (mode != SingMode.UNISON) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Niveau", style = MaterialTheme.typography.titleSmall)
                    (1..SingTraining.levelCount()).forEach { l -> FilterChip(selected = l == level, onClick = { level = l }, label = { Text("$l") }) }
                }
                Text(
                    SingTraining.items(mode, level).joinToString(" · ") { it.label.dropLast(2) },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = octaveFree, onClick = { octaveFree = !octaveFree }, label = { Text("Octave libre") })
                VoiceRange.entries.forEach { v -> FilterChip(selected = range == v, onClick = { range = v }, label = { Text(v.label) }) }
            }

            if (!micGranted || micError) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            if (micError) "Micro indisponible pour le moment." else "Le micro est nécessaire pour t'écouter chanter.",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (!micGranted) Button(onClick = { permissions.request(AppPermission.MICROPHONE) }) { Text("Autoriser le micro") }
                    }
                }
            }

            ScoreHeader(correct, total, streak)
            SingPanel(round?.question, phase, hold, settings.notation, octaveFree)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                LargeFloatingActionButton(
                    onClick = { haptics.click(); if (round == null || phase == SingPhase.IDLE) next() else if (phase == SingPhase.SINGING || phase == SingPhase.REFERENCE) playReference() },
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                ) {
                    if (round == null) Text("Go", style = MaterialTheme.typography.titleLarge)
                    else Icon(Icons.Rounded.Replay, "Réécouter la référence", Modifier.size(32.dp))
                }
                OutlinedButton(onClick = { skip() }, enabled = phase == SingPhase.REFERENCE || phase == SingPhase.SINGING) {
                    Icon(Icons.Rounded.SkipNext, null)
                    Text(" Passer")
                }
            }
            ItemStats(stats, label = { labels[it] ?: it }, onReset = { c.stats.reset(SingTraining.EXERCISE) })
        }
    }
}

/** Consigne, note cible (progression de la tenue) et jauge d'écart en direct. */
@Composable
fun SingPanel(question: SingQuestion?, phase: SingPhase, hold: HoldDetector.State, notation: Notation, octaveFree: Boolean) {
    val reduce = LocalReduceMotion.current
    val done = phase == SingPhase.SUCCESS
    val revealed = done || phase == SingPhase.REVEALED
    Card(
        Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                question?.item?.label ?: "Chante ou joue la note demandée",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            )
            if (question != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag("Référence : " + Pitch.fromMidi(question.referenceMidi).name(notation), highlight = false)
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Tag("Cible : " + if (revealed) Pitch.fromMidi(question.targetMidi).name(notation) else "?", highlight = done)
            }

            // Cible : forme qui fleurit à la réussite, entourée de la progression de la tenue.
            val progress by animateFloatAsState(
                if (done) 1f else hold.progress,
                if (reduce) tween(0) else tween(90, easing = LinearEasing), label = "hold",
            )
            val morph by animateFloatAsState(
                if (done) 1f else 0f,
                if (reduce) tween(0) else spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), label = "bloom",
            )
            val pulse = remember { Animatable(1f) }
            LaunchedEffect(done) {
                if (done && !reduce) {
                    pulse.animateTo(1.12f, tween(110))
                    pulse.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                }
            }
            val shapeMorph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Flower) }
            val good = MaterialTheme.colorScheme.tertiary
            Box(Modifier.size(184.dp), contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = if (hold.inZone || done) good else MaterialTheme.colorScheme.primary,
                    amplitude = { if (reduce) 0f else 1f },
                )
                Box(
                    Modifier
                        .size(136.dp)
                        .graphicsLayer {
                            scaleX = pulse.value; scaleY = pulse.value
                            shape = MorphShape(shapeMorph, morph, rotation = morph * 22f)
                            clip = true
                        }
                        .background(if (done) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    val name = when {
                        question == null -> "♪"
                        revealed -> Pitch.fromMidi(question.targetMidi).name(notation)
                        else -> "?"
                    }
                    Text(
                        name, fontSize = if (name.length > 3) 34.sp else 44.sp, fontWeight = FontWeight.Black,
                        color = if (done) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            val sung = hold.sungMidi
            Text(
                when (phase) {
                    SingPhase.IDLE -> "Touchez Go : écoute la référence, puis chante la cible et tiens-la"
                    SingPhase.REFERENCE -> "Écoute la note de référence…"
                    SingPhase.SINGING -> if (sung == null) "À toi : chante (ou joue) la note et tiens-la" else "Tu chantes : " + Pitch.fromMidi(sung.roundToInt()).name(notation)
                    SingPhase.SUCCESS -> "Bravo, note tenue !"
                    SingPhase.REVEALED -> "Écoute la réponse"
                },
                style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
            )
            PitchGauge(if (phase == SingPhase.SINGING) hold.cents else null, hold.inZone || done)
            Text(
                deviationLabel(if (phase == SingPhase.SINGING) hold.cents else null, octaveFree),
                style = MaterialTheme.typography.bodyLarge,
                color = if (hold.inZone) good else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Étiquette non interactive (note de référence, cible). */
@Composable
private fun Tag(text: String, highlight: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (highlight) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (highlight) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

private fun deviationLabel(cents: Double?, octaveFree: Boolean): String {
    if (cents == null) return if (octaveFree) "Juste à ±25 ¢, à n'importe quelle octave" else "Juste à ±25 ¢"
    if (abs(cents) <= 50) return String.format(Locale.FRANCE, "%+.0f ¢", cents)
    val semis = (abs(cents) / 100).roundToInt()
    return "$semis demi-ton${if (semis > 1) "s" else ""} trop ${if (cents > 0) "haut" else "bas"}"
}

private const val GAUGE_RANGE = 100.0

/** Jauge horizontale −100..+100 cents, zone ±25 c ; marqueur à ressort (bloqué au bord au-delà). */
@Composable
private fun PitchGauge(cents: Double?, inZone: Boolean) {
    val reduce = LocalReduceMotion.current
    val pos = remember { Animatable(0f) }
    val visible by animateFloatAsState(if (cents == null) 0f else 1f, if (reduce) tween(0) else tween(180), label = "marker")
    LaunchedEffect(cents) {
        val target = (cents ?: return@LaunchedEffect).coerceIn(-GAUGE_RANGE, GAUGE_RANGE).toFloat()
        if (reduce) pos.snapTo(target) else pos.animateTo(target, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
    }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val zone = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
    val tick = MaterialTheme.colorScheme.outline
    val marker = if (inZone) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(44.dp).semantics { contentDescription = "Écart à la note cible" }) {
            val r = 12.dp.toPx()
            val left = r
            val w = size.width - 2 * r
            val mid = size.height / 2
            fun x(c: Double) = (left + (c + GAUGE_RANGE) / (2 * GAUGE_RANGE) * w).toFloat()
            val th = 14.dp.toPx()
            drawRoundRect(track, Offset(left, mid - th / 2), Size(w, th), CornerRadius(th / 2))
            drawRoundRect(zone, Offset(x(-25.0), mid - th / 2), Size(x(25.0) - x(-25.0), th), CornerRadius(th / 2))
            for (c in listOf(-100.0, -50.0, 0.0, 50.0, 100.0)) {
                val h = if (c == 0.0) 26.dp.toPx() else 18.dp.toPx()
                drawLine(tick.copy(alpha = if (c == 0.0) 0.9f else 0.5f), Offset(x(c), mid - h / 2), Offset(x(c), mid + h / 2), 2.dp.toPx(), StrokeCap.Round)
            }
            if (visible > 0f) {
                drawCircle(marker.copy(alpha = visible), r * (0.6f + 0.4f * visible), Offset(x(pos.value.toDouble()), mid))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text("♭ trop bas", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("trop haut ♯", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
