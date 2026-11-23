package com.karelisio.arpege.ui.theory

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.arpege.audio.StreamPlayer
import com.karelisio.arpege.data.AppSettings
import com.karelisio.arpege.data.PracticeActivity
import com.karelisio.arpege.data.TrackPractice
import com.karelisio.arpege.data.container
import com.karelisio.arpege.theory.Chord
import com.karelisio.arpege.theory.Instrument
import com.karelisio.arpege.theory.Key
import com.karelisio.arpege.theory.Notation
import com.karelisio.arpege.theory.Progressions
import com.karelisio.arpege.theory.Tuning
import com.karelisio.arpege.theory.Voicing
import com.karelisio.arpege.theory.VoicingGenerator
import com.karelisio.arpege.theory.dsp.Accompaniment
import com.karelisio.arpege.theory.dsp.LoopPosition
import com.karelisio.arpege.theory.dsp.ProgressionAudio
import com.karelisio.arpege.theory.dsp.Voice
import com.karelisio.arpege.theory.midiToFrequency
import com.karelisio.arpege.ui.components.ToolScaffold
import com.karelisio.arpege.ui.components.rememberHaptics
import com.karelisio.arpege.ui.draw.ChordDiagram
import com.karelisio.arpege.ui.draw.KeyMark
import com.karelisio.arpege.ui.draw.PianoKeyboard
import com.karelisio.arpege.ui.nav.Routes
import com.karelisio.arpege.ui.theme.LocalReduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val BEATS_PER_CHORD = listOf(1, 2, 3, 4, 6, 8)
private val DISPLAYS = listOf(Instrument.GUITAR, Instrument.UKULELE, Instrument.PIANO)

/** Réglages qui déterminent le tampon de la boucle : tout changement relance le rendu. */
private data class LoopParams(
    val voicings: List<List<Double>>,
    val bpm: Int,
    val beatsPerChord: Int,
    val style: Accompaniment,
    val voice: Voice,
    val click: Boolean,
)

private fun keyName(key: Key, notation: Notation) = key.tonic.name(notation) + if (key.minor) " mineur" else " majeur"

@Composable
fun ProgressionsScreen(onBack: () -> Unit, onChord: (String) -> Unit) {
    TrackPractice(PracticeActivity.CHORDS)
    val context = LocalContext.current
    val c = context.container
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val player = remember { StreamPlayer(context.applicationContext) }
    DisposableEffect(player) { onDispose { player.stop() } }

    var minor by rememberSaveable { mutableStateOf(false) }
    var progressionId by rememberSaveable { mutableStateOf("pop") }
    var tonicPc by rememberSaveable { mutableIntStateOf(0) }
    var display by rememberSaveable { mutableStateOf<Instrument?>(null) }
    var bpm by rememberSaveable { mutableIntStateOf(90) }
    var beatsPerChord by rememberSaveable { mutableIntStateOf(4) }
    var style by rememberSaveable { mutableStateOf(Accompaniment.BLOCK) }
    var voice by rememberSaveable { mutableStateOf(Voice.STRING) }
    var click by rememberSaveable { mutableStateOf(true) }
    var playing by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf<LoopPosition?>(null) }

    val notation = settings.notation
    val available = Progressions.ALL.filter { it.minor == minor }
    val progression = available.firstOrNull { it.id == progressionId } ?: available.first()
    val key = Progressions.keyFor(tonicPc, minor)
    val chords = remember(progression, key) { progression.chords(key) }
    val ins = display ?: when (settings.instrument) {
        Instrument.UKULELE -> Instrument.UKULELE
        Instrument.PIANO -> Instrument.PIANO
        else -> Instrument.GUITAR
    }
    // Positions (diagrammes et son « cordes ») : guitare, ou ukulélé si c'est l'instrument affiché.
    val tuning = if (ins == Instrument.UKULELE) Tuning.UKULELE else Tuning.GUITAR_STANDARD
    val voicings by produceState<Map<Chord, Voicing?>>(emptyMap(), chords, tuning) {
        value = withContext(Dispatchers.Default) {
            chords.distinct().associateWith { VoicingGenerator.generate(it, tuning, limit = 1).firstOrNull() }
        }
    }

    val a4 = settings.a4.toDouble()
    val params = remember(chords, voicings, voice, bpm, beatsPerChord, style, click, a4, tuning) {
        val freqs = if (voice == Voice.PIANO) ProgressionAudio.pianoVoicings(chords).map { v -> v.map { midiToFrequency(it.toDouble(), a4) } }
        else chords.map { ch -> (voicings[ch]?.pitches(tuning) ?: ch.pitches(3)).map { it.frequency(a4) } }
        LoopParams(freqs, bpm, beatsPerChord, style, voice, click)
    }
    val waitingVoicings = voice == Voice.STRING && voicings.isEmpty()

    // Boucle : un passage rendu dans un seul tampon, rejoué en flux ; surlignage calé sur la position audio.
    LaunchedEffect(playing, params, waitingVoicings) {
        if (!playing) { player.stop(); position = null; preparing = false; return@LaunchedEffect }
        if (waitingVoicings) return@LaunchedEffect
        preparing = true
        delay(120) // regroupe les réglages modifiés en rafale
        val audio = withContext(Dispatchers.Default) {
            ProgressionAudio.render(params.voicings, player.sampleRate, params.bpm, params.beatsPerChord, params.style, params.voice, params.click)
        }
        preparing = false
        player.start(
            source = { out, frame -> audio.read(out, frame) },
            onError = {
                playing = false
                scope.launch { snackbar.showSnackbar("Sortie audio indisponible") }
            },
        )
        val sr = player.sampleRate
        while (true) {
            withFrameNanos { }
            val start = player.startNanos
            if (start < 0) continue
            position = audio.timing.positionAt((System.nanoTime() - start) * sr / 1_000_000_000L)
        }
    }

    ToolScaffold(title = "Grilles d'accords", onBack = onBack, sharedKey = "tool-${Routes.PROGRESSIONS}", snackbar = snackbar) { p ->
        Box(Modifier.fillMaxSize().padding(p)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(false to "Majeur", true to "Mineur").forEachIndexed { i, (m, label) ->
                        SegmentedButton(selected = minor == m, onClick = { haptics.tick(); minor = m }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                    }
                }
                NotePicker(tonicPc, notation, { tonicPc = it.pitchClass })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    available.forEach { pr ->
                        FilterChip(selected = pr.id == progression.id, onClick = { haptics.tick(); progressionId = pr.id }, label = { Text(pr.name) })
                    }
                }

                Column {
                    Text(progression.name, style = MaterialTheme.typography.headlineMedium)
                    Text("${progression.summary} · en ${keyName(key, notation)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(progression.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    DISPLAYS.forEachIndexed { i, d ->
                        SegmentedButton(selected = ins == d, onClick = { display = d }, shape = SegmentedButtonDefaults.itemShape(i, DISPLAYS.size)) {
                            Text(d.label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chords.forEachIndexed { i, chord ->
                        ChordCell(
                            roman = progression.steps[i].symbol,
                            chord = chord,
                            notation = notation,
                            instrument = ins,
                            voicing = voicings[chord],
                            current = position?.chord == i,
                            onClick = { haptics.click(); onChord(chord.toString()) },
                        )
                    }
                }
                Text("Touche un accord pour l'ouvrir dans Accords.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                PlaybackSettings(
                    bpm = bpm, onBpm = { bpm = it },
                    beatsPerChord = beatsPerChord, onBeats = { beatsPerChord = it },
                    style = style, onStyle = { style = it },
                    voice = voice, onVoice = { voice = it },
                    click = click, onClick = { click = it },
                    position = position,
                )
                Spacer(Modifier.height(88.dp))
            }
            ExtendedFloatingActionButton(
                onClick = { haptics.click(); playing = !playing },
                icon = { Icon(if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null) },
                text = { Text(if (!playing) "Lire en boucle" else if (preparing) "Préparation…" else "Arrêter") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            )
        }
    }
}

@Composable
private fun ChordCell(
    roman: String,
    chord: Chord,
    notation: Notation,
    instrument: Instrument,
    voicing: Voicing?,
    current: Boolean,
    onClick: () -> Unit,
) {
    val reduce = LocalReduceMotion.current
    val scale by animateFloatAsState(if (current && !reduce) 1.06f else 1f, label = "cellScale")
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = Modifier.width(80.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = if (current) colors.primaryContainer else colors.surfaceContainerHigh),
        border = if (current) BorderStroke(3.dp, colors.primary) else null,
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(roman, style = MaterialTheme.typography.labelLarge, color = if (current) colors.primary else colors.tertiary)
            Text(
                chord.name(notation), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                maxLines = 1, textAlign = TextAlign.Center,
            )
            when {
                instrument == Instrument.PIANO -> {
                    val marks = chord.pitchClasses.associate { pc -> (60 + pc) to KeyMark("", colors.primary, colors.onPrimary) }
                    PianoKeyboard(startMidi = 60, octaves = 1, marks = marks, height = 40.dp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
                }
                voicing != null -> ChordDiagram(voicing, Modifier.width(60.dp), showFingers = false)
                else -> Box(Modifier.size(60.dp, 75.dp))
            }
        }
    }
}

@Composable
private fun PlaybackSettings(
    bpm: Int, onBpm: (Int) -> Unit,
    beatsPerChord: Int, onBeats: (Int) -> Unit,
    style: Accompaniment, onStyle: (Accompaniment) -> Unit,
    voice: Voice, onVoice: (Voice) -> Unit,
    click: Boolean, onClick: (Boolean) -> Unit,
    position: LoopPosition?,
) {
    val haptics = rememberHaptics()
    Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Accompagnement", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                // Temps de l'accord en cours, synchronisés avec le son.
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(beatsPerChord) { b ->
                        val on = position?.beat == b
                        Box(
                            Modifier.size(if (b == 0) 12.dp else 10.dp).clip(CircleShape)
                                .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedIconButton(onClick = { haptics.tick(); onBpm((bpm - 1).coerceAtLeast(40)) }) { Icon(Icons.Rounded.Remove, "Tempo −1") }
                Text("$bpm BPM", style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(96.dp), textAlign = TextAlign.Center)
                OutlinedIconButton(onClick = { haptics.tick(); onBpm((bpm + 1).coerceAtMost(220)) }) { Icon(Icons.Rounded.Add, "Tempo +1") }
            }
            // Le rendu de la boucle est regroupé (anti-rebond) : glisser ne relance pas l'audio à chaque pas.
            Slider(value = bpm.toFloat(), onValueChange = { onBpm(it.roundToInt()) }, valueRange = 40f..220f)
            Text("Temps par accord", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BEATS_PER_CHORD.forEach { n ->
                    FilterChip(selected = n == beatsPerChord, onClick = { haptics.tick(); onBeats(n) }, label = { Text("$n") })
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Accompaniment.entries.forEachIndexed { i, a ->
                    SegmentedButton(selected = a == style, onClick = { onStyle(a) }, shape = SegmentedButtonDefaults.itemShape(i, Accompaniment.entries.size)) { Text(a.label) }
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(Voice.STRING to "Cordes", Voice.PIANO to "Piano").forEachIndexed { i, (v, label) ->
                    SegmentedButton(selected = v == voice, onClick = { onVoice(v) }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Clic de métronome", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = click, onCheckedChange = { haptics.tick(); onClick(it) })
            }
        }
    }
}
