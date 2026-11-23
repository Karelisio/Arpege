package com.karelisio.arpege.ui.theory

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.karelisio.arpege.theory.ChordCandidate
import com.karelisio.arpege.theory.ChordIdentifier
import com.karelisio.arpege.theory.Instrument
import com.karelisio.arpege.theory.Notation
import com.karelisio.arpege.theory.Note
import com.karelisio.arpege.theory.Pitch
import com.karelisio.arpege.theory.Tuning
import com.karelisio.arpege.theory.dsp.Voice
import com.karelisio.arpege.ui.components.ToolScaffold
import com.karelisio.arpege.ui.components.rememberHaptics
import com.karelisio.arpege.ui.draw.FretPicker
import com.karelisio.arpege.ui.draw.KeyMark
import com.karelisio.arpege.ui.draw.PianoKeyboard
import com.karelisio.arpege.ui.nav.Routes

private const val KEYBOARD_START = 48

private fun encode(list: List<Int>) = list.joinToString(",")
private fun decode(text: String): List<Int> = if (text.isBlank()) emptyList() else text.split(',').mapNotNull { it.trim().toIntOrNull() }

/**
 * Accord inverse : on touche des cases (ou des touches) et Arpège propose les noms d'accords,
 * du plus probable au moins probable. [initialFrets] (« x32010 ») sert d'exemple de départ.
 */
@Composable
fun ChordFinderScreen(onBack: () -> Unit, initialFrets: String? = null, onChord: (String) -> Unit) {
    TrackPractice(PracticeActivity.CHORDS)
    val c = LocalContext.current.container
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val haptics = rememberHaptics()
    val notation = settings.notation

    var instrument by rememberSaveable { mutableStateOf<Instrument?>(null) }
    var tuningId by rememberSaveable { mutableStateOf<String?>(null) }
    var fretsText by rememberSaveable { mutableStateOf(initialFrets?.let { encode(ChordIdentifier.parseFrets(it)) } ?: "") }
    var keysText by rememberSaveable { mutableStateOf("") }
    val ins = instrument ?: settings.instrument
    val tuning = Tuning.byId(tuningId ?: "")?.takeIf { it.instrument == ins } ?: Tuning.default(ins)
    val n = tuning.strings.size
    val frets = decode(fretsText).takeIf { it.size == n } ?: List(n) { -1 }
    val keys = decode(keysText).toSortedSet()

    val midi = if (ins.fretted) ChordIdentifier.pitchesFromFrets(frets, tuning) else keys.toList()
    val candidates = remember(midi) { ChordIdentifier.identify(midi, limit = 6) }
    val bassMidi = midi.minOrNull()
    fun noteName(m: Int) = Note.common(m).name(notation)

    ToolScaffold(title = "Accord inverse", onBack = onBack, sharedKey = "tool-${Routes.CHORD_FINDER}") { p ->
        Column(
            Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            InstrumentPicker(ins, { instrument = it; tuningId = null }, Modifier.fillMaxWidth())
            if (ins.fretted) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tuning.forInstrument(ins).forEach { t ->
                        FilterChip(selected = t.id == tuning.id, onClick = { tuningId = t.id }, label = { Text(t.label) })
                    }
                }
                Text(
                    "Touche les cases jouées ; avant le sillet : corde à vide ○ ou étouffée ×.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val bassString = frets.indices.filter { frets[it] >= 0 }.minByOrNull { tuning.strings[it].midi + frets[it] }
                FretPicker(
                    tuning = tuning,
                    frets = frets,
                    onChange = { s, f -> haptics.tick(); fretsText = encode(frets.toMutableList().also { it[s] = f }) },
                    label = { s, f -> noteName(tuning.strings[s].midi + f) },
                    bassString = bassString,
                )
            } else {
                Text("Touche les notes de l'accord sur le clavier.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val colors = MaterialTheme.colorScheme
                PianoKeyboard(
                    startMidi = KEYBOARD_START, octaves = 2, height = 150.dp,
                    marks = keys.associateWith { m ->
                        if (m == bassMidi) KeyMark("", colors.tertiary, colors.onTertiary) else KeyMark("", colors.primary, colors.onPrimary)
                    },
                    onKey = { m -> haptics.tick(); keysText = encode((if (m in keys) keys - m else keys + m).sorted()) },
                )
            }

            Text(
                if (midi.isEmpty()) "Aucune note sélectionnée."
                else "Notes : " + midi.sorted().map { it % 12 }.distinct().joinToString(" · ") { noteName(it) } +
                    "   (basse : ${noteName(bassMidi ?: 0)})",
                style = MaterialTheme.typography.titleSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val voice = if (ins == Instrument.PIANO) Voice.PIANO else Voice.STRING
                val pitches = midi.sorted().map { Pitch.fromMidi(it) }
                Button(enabled = midi.isNotEmpty(), onClick = { haptics.click(); c.sound.playPitches(pitches, voice, settings.a4.toDouble(), strumMs = 35) }) { Text("Écouter") }
                FilledTonalButton(enabled = midi.isNotEmpty(), onClick = { haptics.click(); c.sound.playPitches(pitches, voice, settings.a4.toDouble(), strumMs = 190, noteSec = 2.6) }) { Text("Arpégé") }
                TextButton(enabled = midi.isNotEmpty(), onClick = { haptics.click(); fretsText = ""; keysText = "" }) { Text("Effacer") }
            }

            Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    midi.isEmpty() -> Unit
                    candidates.isEmpty() -> Text(
                        if (midi.map { it % 12 }.toSet().size < 2) "Une seule note : ajoute-en au moins une autre." else "Aucun accord reconnu pour ces notes.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    else -> candidates.forEachIndexed { i, cand ->
                        CandidateCard(cand, i == 0, notation) { haptics.click(); onChord(cand.chord.toString()) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateCard(candidate: ChordCandidate, best: Boolean, notation: Notation, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val chord = candidate.chord
    val details = buildList {
        add(chord.type.label)
        candidate.bassInterval?.let { add("renversement, ${chord.bass?.name(notation)} à la basse") }
        if (candidate.foreignBass) add("basse ${chord.bass?.name(notation)} hors accord")
        if (candidate.missing.isNotEmpty()) add("incomplet : sans " + candidate.missing.joinToString(", ") { (chord.root + it).name(notation) })
    }
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = if (best) colors.primaryContainer else colors.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (best) Text("Le plus probable", style = MaterialTheme.typography.labelMedium, color = colors.primary)
                Text(chord.name(notation), style = if (best) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall)
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Text(chord.notes.joinToString(" – ") { it.name(notation) }, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Ouvrir dans Accords")
        }
    }
}
