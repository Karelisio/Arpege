package com.karelisio.arpege.ui.theory

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.arpege.data.AppSettings
import com.karelisio.arpege.data.PracticeActivity
import com.karelisio.arpege.data.TrackPractice
import com.karelisio.arpege.data.container
import com.karelisio.arpege.theory.Instrument
import com.karelisio.arpege.theory.Note
import com.karelisio.arpege.theory.Scale
import com.karelisio.arpege.theory.ScaleType
import com.karelisio.arpege.theory.Tuning
import com.karelisio.arpege.theory.dsp.Voice
import com.karelisio.arpege.ui.components.ToolScaffold
import com.karelisio.arpege.ui.components.rememberHaptics
import com.karelisio.arpege.ui.draw.FretMark
import com.karelisio.arpege.ui.draw.Fretboard
import com.karelisio.arpege.ui.draw.KeyMark
import com.karelisio.arpege.ui.draw.PianoKeyboard

private const val STEP_MS = 300

@Composable
fun ScalesScreen(onBack: () -> Unit, onChord: (String) -> Unit) {
    TrackPractice(PracticeActivity.SCALES)
    val c = LocalContext.current.container
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val haptics = rememberHaptics()
    val cascade = rememberCascade()

    var tonicName by rememberSaveable { mutableStateOf("C") }
    var tonicPc by rememberSaveable { mutableIntStateOf(0) }
    var type by rememberSaveable { mutableStateOf(ScaleType.MAJOR) }
    var instrument by rememberSaveable { mutableStateOf<Instrument?>(null) }
    var tuningId by rememberSaveable { mutableStateOf<String?>(null) }
    var showNames by rememberSaveable { mutableStateOf(false) }
    var sevenths by rememberSaveable { mutableStateOf(false) }
    val ins = instrument ?: settings.instrument
    val tuning = Tuning.byId(tuningId ?: "")?.takeIf { it.instrument == ins } ?: Tuning.default(ins)
    val scale = Scale(Note.parse(tonicName) ?: Note.common(tonicPc), type)
    val colors = degreeColors()
    val notation = settings.notation

    // Ordre de jeu (montée puis descente) pour le surlignage synchronisé.
    val up = scale.ascendingPitches(if (ins == Instrument.BASS) 2 else if (ins == Instrument.PIANO) 4 else 3)
    val sequence = up + up.dropLast(1).reversed()
    val playingIndex = cascade.current()?.let { if (it in sequence.indices) it else null }
    val playingMidi = playingIndex?.let { sequence[it].midi }

    fun label(deg: Int): String = if (showNames) scale.notes[deg].name(notation) else type.intervals[deg].degreeLabel

    ToolScaffold(title = "Gammes & modes", onBack = onBack, sharedKey = "tool-scales") { p ->
        Column(
            Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            InstrumentPicker(ins, { instrument = it; tuningId = null }, Modifier.fillMaxWidth())
            if (ins.fretted) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tuning.forInstrument(ins).forEach { t ->
                    FilterChip(selected = t.id == tuning.id, onClick = { tuningId = t.id }, label = { Text(t.label) })
                }
            }
            NotePicker(scale.tonic.pitchClass, notation, { tonicName = it.toString(); tonicPc = it.pitchClass })
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ScaleType.entries.forEach { t ->
                    FilterChip(selected = t == type, onClick = { haptics.tick(); type = t }, label = { Text(t.label) })
                }
            }
            Text(scale.tonic.name(notation) + " " + type.label.lowercase(), style = MaterialTheme.typography.headlineMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                scale.notes.forEachIndexed { i, n ->
                    val (bg, fg) = colors[i % colors.size]
                    AssistChip(onClick = {}, label = { Text(n.name(notation) + "  " + type.intervals[i].degreeLabel) },
                        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(containerColor = bg, labelColor = fg))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    val voice = if (ins == Instrument.PIANO) Voice.PIANO else Voice.STRING
                    c.sound.playMelody(sequence, voice, settings.a4.toDouble(), STEP_MS) { cascade.run(sequence.size, STEP_MS) }
                }) { Text("Écouter ↑↓") }
                Text("Noms", style = MaterialTheme.typography.labelLarge)
                Switch(checked = showNames, onCheckedChange = { showNames = it })
            }

            if (ins.fretted) {
                val marks = buildList {
                    tuning.strings.forEachIndexed { s, open ->
                        for (f in 0..15) {
                            val deg = scale.degreeOf(open.midi + f) ?: continue
                            val (bg, fg) = colors[deg % colors.size]
                            add(FretMark(s, f, label(deg), bg, fg, emphasized = deg == 0,
                                order = if (playingMidi != null && open.midi + f == playingMidi) -1 else 0))
                        }
                    }
                }
                Fretboard(tuning, marks, frets = 15, highlightOrder = if (playingMidi != null) -1 else null)
            } else {
                val start = 60
                val marks = (start..start + 24).mapNotNull { m ->
                    val deg = scale.degreeOf(m) ?: return@mapNotNull null
                    val (bg, fg) = colors[deg % colors.size]
                    m to KeyMark(label(deg), bg, fg)
                }.toMap()
                PianoKeyboard(startMidi = start, octaves = 2, marks = marks, pressed = playingMidi?.let { m -> start + ((m - start) % 24 + 24) % 24 })
            }

            if (type.isHeptatonic) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Accords diatoniques", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("Tétrades", style = MaterialTheme.typography.labelLarge)
                    Switch(checked = sevenths, onCheckedChange = { sevenths = it }, modifier = Modifier.padding(start = 8.dp))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    scale.diatonicChords(sevenths).forEach { d ->
                        FilledTonalButton(onClick = { onChord(d.chord.toString()) }) {
                            Text(d.roman + "  ·  " + d.chord.name(notation))
                        }
                    }
                }
            }
        }
    }
}
