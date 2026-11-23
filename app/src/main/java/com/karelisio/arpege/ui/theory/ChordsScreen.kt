package com.karelisio.arpege.ui.theory

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.arpege.data.AppSettings
import com.karelisio.arpege.data.PracticeActivity
import com.karelisio.arpege.data.TrackPractice
import com.karelisio.arpege.data.container
import com.karelisio.arpege.data.db.FavoriteChord
import com.karelisio.arpege.theory.Chord
import com.karelisio.arpege.theory.ChordType
import com.karelisio.arpege.theory.Instrument
import com.karelisio.arpege.theory.Notation
import com.karelisio.arpege.theory.Note
import com.karelisio.arpege.theory.Pitch
import com.karelisio.arpege.theory.Tuning
import com.karelisio.arpege.theory.Voicing
import com.karelisio.arpege.theory.VoicingGenerator
import com.karelisio.arpege.theory.dsp.Voice
import com.karelisio.arpege.ui.components.ToolScaffold
import com.karelisio.arpege.ui.components.rememberHaptics
import com.karelisio.arpege.ui.draw.ChordDiagram
import com.karelisio.arpege.ui.draw.FretMark
import com.karelisio.arpege.ui.draw.Fretboard
import com.karelisio.arpege.ui.draw.KeyMark
import com.karelisio.arpege.ui.draw.PianoKeyboard
import kotlinx.coroutines.launch

private const val ARPEGGIO_MS = 190
private const val STRUM_MS = 35

@Composable
fun ChordsScreen(onBack: () -> Unit, initialName: String?) {
    TrackPractice(PracticeActivity.CHORDS)
    val c = LocalContext.current.container
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val favorites by c.db.favorites().all().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val cascade = rememberCascade()

    val initial = remember(initialName) { initialName?.let { Chord.parse(it) } }
    var rootPc by rememberSaveable { mutableIntStateOf(initial?.root?.pitchClass ?: 0) }
    var rootName by rememberSaveable { mutableStateOf(initial?.root?.toString() ?: "C") }
    var type by rememberSaveable { mutableStateOf(initial?.type ?: ChordType.MAJOR) }
    var instrument by rememberSaveable { mutableStateOf<Instrument?>(null) }
    var capo by rememberSaveable { mutableIntStateOf(0) }
    val ins = instrument ?: settings.instrument
    val tuning = Tuning.default(ins)
    val root = Note.parse(rootName) ?: Note.common(rootPc)
    val chord = Chord(root, type)

    ChordView(
        chord = chord, instrument = ins, tuning = tuning, capo = capo, notation = settings.notation, a4 = settings.a4.toDouble(),
        favorites = favorites,
        onBack = onBack,
        header = {
            InstrumentPicker(ins, { instrument = it; capo = 0 }, Modifier.fillMaxWidth())
            if (favorites.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    favorites.forEach { f ->
                        AssistChip(onClick = {
                            Chord.parse(f.name)?.let { ch -> rootName = ch.root.toString(); rootPc = ch.root.pitchClass; type = ch.type }
                            runCatching { Instrument.valueOf(f.instrument) }.getOrNull()?.let { instrument = it }
                        }, label = { Text(Chord.parse(f.name)?.name(settings.notation) ?: f.name) }, leadingIcon = { Icon(Icons.Rounded.Favorite, null) })
                    }
                }
            }
            NotePicker(rootPc, settings.notation, { rootPc = it.pitchClass; rootName = it.toString() })
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChordType.entries.forEach { t ->
                    FilterChip(selected = t == type, onClick = { haptics.tick(); type = t }, label = { Text(if (t.suffix.isEmpty()) "Maj" else t.suffix) })
                }
            }
            if (ins == Instrument.GUITAR || ins == Instrument.UKULELE) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Capo", style = MaterialTheme.typography.titleSmall)
                    OutlinedIconButton(onClick = { if (capo > 0) capo-- }) { Icon(Icons.Rounded.Remove, "Capo −") }
                    Text(if (capo == 0) "aucun" else "case $capo")
                    OutlinedIconButton(onClick = { if (capo < 9) capo++ }) { Icon(Icons.Rounded.Add, "Capo +") }
                }
            }
        },
        onToggleFavorite = { fav ->
            scope.launch {
                if (fav) c.db.favorites().add(FavoriteChord(name = chord.toString(), instrument = ins.name))
                else c.db.favorites().remove(chord.toString(), ins.name)
            }
        },
        cascade = cascade,
    )
}

/** Vue d'un accord réutilisée par l'écran Accords et la bottom sheet des tablatures. */
@Composable
fun ChordView(
    chord: Chord,
    instrument: Instrument,
    tuning: Tuning,
    capo: Int,
    notation: Notation,
    a4: Double,
    favorites: List<FavoriteChord>,
    onBack: (() -> Unit)?,
    header: @Composable () -> Unit,
    onToggleFavorite: ((Boolean) -> Unit)?,
    cascade: Cascade,
    embedded: Boolean = false,
) {
    val c = LocalContext.current.container
    val haptics = rememberHaptics()
    val voicings = remember(chord, tuning, capo, instrument) {
        if (instrument == Instrument.GUITAR || instrument == Instrument.UKULELE) VoicingGenerator.generate(chord, tuning, capo) else emptyList()
    }
    val pager = rememberPagerState { voicings.size.coerceAtLeast(1) }
    LaunchedEffect(chord, instrument) { cascade.run(chord.notes.size + 2, 70) } // apparition en cascade
    val isFav = favorites.any { it.name == chord.toString() && it.instrument == instrument.name }

    fun pitchesToPlay(): List<Pitch> = when (instrument) {
        Instrument.GUITAR, Instrument.UKULELE -> voicings.getOrNull(pager.currentPage)?.pitches(tuning, capo) ?: chord.pitches(3)
        Instrument.BASS -> chord.pitches(2)
        Instrument.PIANO -> chord.pitches(4)
    }
    val voice = if (instrument == Instrument.PIANO) Voice.PIANO else Voice.STRING

    val content: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            header()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    AnimatedContent(chord.name(notation), label = "chordName") { Text(it, style = MaterialTheme.typography.displayMedium) }
                    Text(chord.type.label + " · " + chord.notes.joinToString(" – ") { it.name(notation) }, style = MaterialTheme.typography.bodyLarge)
                    Text(chord.type.intervals.joinToString("  ") { it.degreeLabel }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (onToggleFavorite != null) IconToggleButton(checked = isFav, onCheckedChange = { haptics.click(); onToggleFavorite(it) }) {
                    Icon(if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = "Favori",
                        tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    val p = pitchesToPlay()
                    c.sound.playPitches(p, voice, a4, strumMs = STRUM_MS) { cascade.run(p.size, STRUM_MS) }
                }) { Text("Plaqué") }
                FilledTonalButton(onClick = {
                    val p = pitchesToPlay()
                    c.sound.playPitches(p, voice, a4, strumMs = ARPEGGIO_MS, noteSec = 2.6) { cascade.run(p.size, ARPEGGIO_MS) }
                }) { Text("Arpégé") }
            }
            when (instrument) {
                Instrument.GUITAR, Instrument.UKULELE -> {
                    if (voicings.isEmpty()) Text("Aucune position jouable trouvée.")
                    else {
                        HorizontalPager(state = pager, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 48.dp), pageSpacing = 12.dp) { i ->
                            VoicingCard(voicings[i], i, voicings.size, if (pager.currentPage == i) cascade.value else Float.MAX_VALUE)
                        }
                        Text("Glisse pour voir d'autres positions, classées par facilité.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Instrument.BASS -> {
                    val colors = degreeColors()
                    val marks = buildList {
                        tuning.strings.forEachIndexed { s, open ->
                            for (f in 0..12) {
                                val pc = (open.midi + f) % 12
                                val idx = chord.type.intervals.indexOfFirst { (chord.root.pitchClass + it.semitones) % 12 == pc }
                                if (idx >= 0) {
                                    val iv = chord.type.intervals[idx]
                                    val (bg, fg) = colors[idx % colors.size]
                                    add(FretMark(s, f, if (idx == 0) "R" else iv.degreeLabel, bg, fg, order = f / 3, emphasized = idx == 0))
                                }
                            }
                        }
                    }
                    Fretboard(tuning, marks, frets = 12, cascade = cascade.value)
                    Text("R = fondamentale ; les autres repères indiquent l'intervalle.", style = MaterialTheme.typography.bodySmall)
                }
                Instrument.PIANO -> {
                    val pitches = chord.pitches(4)
                    val fingers = when (pitches.size) { 2 -> listOf(1, 5); 3 -> listOf(1, 3, 5); 4 -> listOf(1, 2, 3, 5); else -> listOf(1, 2, 3, 4, 5) }
                    val colors = degreeColors()
                    val marks = pitches.mapIndexed { i, p -> p.midi to KeyMark("${fingers.getOrElse(i) { 5 }}", colors[i % colors.size].first, colors[i % colors.size].second, i) }.toMap()
                    PianoKeyboard(startMidi = 60, octaves = 2, marks = marks, cascade = cascade.value)
                    Text("Chiffres : doigté main droite (1 = pouce).", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (embedded) content()
    else ToolScaffold(title = "Accords", onBack = onBack, sharedKey = "tool-chords") { p ->
        Box(Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) { content() }
    }
}

@Composable
private fun VoicingCard(v: Voicing, index: Int, total: Int, cascade: Float) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ChordDiagram(v, Modifier.width(200.dp), cascade = cascade)
            Text("Position ${index + 1}/$total · " + difficultyLabel(v.difficulty), style = MaterialTheme.typography.labelLarge)
            Text(v.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun difficultyLabel(d: Double) = when {
    d < 1.5 -> "facile"
    d < 4.5 -> "moyen"
    else -> "difficile"
}
