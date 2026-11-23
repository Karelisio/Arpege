package com.karelisio.arpege.ui.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.TextDecrease
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.arpege.data.AppSettings
import com.karelisio.arpege.data.PracticeActivity
import com.karelisio.arpege.data.TrackPractice
import com.karelisio.arpege.data.container
import com.karelisio.arpege.data.db.TabFile
import com.karelisio.arpege.theory.Chord
import com.karelisio.arpege.theory.Instrument
import com.karelisio.arpege.theory.Tuning
import com.karelisio.arpege.theory.song.GuitarProReader
import com.karelisio.arpege.theory.song.GuitarProTab
import com.karelisio.arpege.theory.song.Song
import java.util.Base64
import androidx.compose.material3.FilterChip
import com.karelisio.arpege.theory.song.SongFormat
import com.karelisio.arpege.theory.song.SongLine
import com.karelisio.arpege.theory.song.parseSong
import com.karelisio.arpege.ui.components.rememberHaptics
import com.karelisio.arpege.ui.nav.sharedBoundsKey
import com.karelisio.arpege.ui.theory.ChordView
import com.karelisio.arpege.ui.theory.rememberCascade
import kotlinx.coroutines.launch

@Composable
fun TabViewerScreen(id: Long, onBack: () -> Unit) {
    TrackPractice(PracticeActivity.TABS)
    val c = LocalContext.current.container
    val file by produceState<TabFile?>(null, id) { value = c.db.tabs().get(id) }
    val f = file
    if (f == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    TabViewer(f, onBack)
}

@Composable
private fun TabViewer(file: TabFile, onBack: () -> Unit) {
    val c = LocalContext.current.container
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val haptics = rememberHaptics()
    val format = runCatching { SongFormat.valueOf(file.format) }.getOrDefault(SongFormat.ASCII)
    val gp = remember(file.id) {
        if (format == SongFormat.GUITAR_PRO) runCatching { GuitarProReader.read(Base64.getDecoder().decode(file.content)) }.getOrNull() else null
    }
    val gpTracks = remember(gp) { gp?.let { GuitarProTab.playableTracks(it) } ?: emptyList() }
    var gpTrack by rememberSaveable { mutableIntStateOf(-1) }
    val trackIndex = if (gpTrack >= 0) gpTrack else gpTracks.firstOrNull() ?: 0
    val base = remember(file.id, trackIndex) {
        if (gp != null) GuitarProTab.toSong(gp, trackIndex) else parseSong(file.content, format, file.title)
    }
    var transpose by rememberSaveable { mutableIntStateOf(file.transpose) }
    var speed by rememberSaveable { mutableFloatStateOf(file.scrollSpeed) }
    var scrolling by rememberSaveable { mutableStateOf(false) }
    var fontSize by rememberSaveable { mutableFloatStateOf(15f) }
    var sheetChord by remember { mutableStateOf<String?>(null) }
    val song = remember(base, transpose) { base.transposed(transpose) }
    val scroll = rememberScrollState()
    val density = LocalDensity.current

    // Écran maintenu allumé pendant la lecture.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(transpose, speed) { c.db.tabs().updatePrefs(file.id, transpose, speed) }
    LaunchedEffect(scrolling, speed) {
        if (!scrolling) return@LaunchedEffect
        var last = 0L
        val pxPerSec = with(density) { 22.dp.toPx() } * speed
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dy = pxPerSec * (now - last) / 1e9f
                    scroll.dispatchRawDelta(dy)
                }
                last = now
            }
            if (scroll.value >= scroll.maxValue) { scrolling = false; break }
        }
    }

    Scaffold(
        modifier = Modifier.sharedBoundsKey("tab-${file.id}"),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(song.title, maxLines = 1) },
                subtitle = {
                    Text(listOfNotNull(song.artist.ifBlank { null }, if (song.capo > 0) "Capo ${song.capo}" else null,
                        if (transpose != 0) "Transposé %+d".format(transpose) else null).joinToString(" · ").ifBlank { file.sourceName })
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                actions = {
                    IconButton(onClick = { fontSize = (fontSize - 1).coerceAtLeast(10f) }) { Icon(Icons.Rounded.TextDecrease, "Texte plus petit") }
                    IconButton(onClick = { fontSize = (fontSize + 1).coerceAtMost(28f) }) { Icon(Icons.Rounded.TextIncrease, "Texte plus grand") }
                },
            )
        },
        bottomBar = {
            BottomAppBar(
                actions = {
                    IconButton(onClick = { haptics.tick(); transpose-- }) { Icon(Icons.Rounded.Remove, "Transposer −1") }
                    Text("%+d".format(transpose), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { haptics.tick(); transpose++ }) { Icon(Icons.Rounded.Add, "Transposer +1") }
                    Slider(value = speed, onValueChange = { speed = it }, valueRange = 0.3f..4f, modifier = Modifier.width(140.dp).padding(start = 8.dp))
                },
                floatingActionButton = {
                    FloatingActionButton(onClick = { haptics.click(); scrolling = !scrolling }) {
                        Icon(if (scrolling) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (scrolling) "Arrêter le défilement" else "Défilement auto")
                    }
                },
            )
        },
    ) { p ->
        Column(
            Modifier.fillMaxSize().padding(p).verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp)
                .then(if (format != SongFormat.CHORDPRO) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
        ) {
            if (gp != null && gpTracks.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                gpTracks.forEach { i ->
                    FilterChip(selected = i == trackIndex, onClick = { gpTrack = i }, label = { Text(gp.tracks[i].name.ifBlank { "Piste ${i + 1}" }) })
                }
            }
            SongContent(song, format, fontSize) { sheetChord = it }
            Box(Modifier.height(240.dp))
        }
    }

    sheetChord?.let { name ->
        val chord = Chord.parse(name)
        ModalBottomSheet(onDismissRequest = { sheetChord = null }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState())) {
                if (chord == null) Text("Accord « $name » non reconnu.")
                else {
                    val ins = if (settings.instrument == Instrument.BASS) Instrument.GUITAR else settings.instrument
                    ChordView(
                        chord = chord, instrument = ins, tuning = Tuning.default(ins), capo = 0, notation = settings.notation,
                        a4 = settings.a4.toDouble(), favorites = emptyList(), onBack = null, header = {}, onToggleFavorite = null,
                        cascade = rememberCascade(), embedded = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun SongContent(song: Song, format: SongFormat, fontSize: Float, onChord: (String) -> Unit) {
    val chordColor = MaterialTheme.colorScheme.primary
    val mono = FontFamily.Monospace
    val chordStyle = TextLinkStyles(SpanStyle(color = chordColor, fontWeight = FontWeight.Bold))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (line in song.lines) {
            when (line) {
                is SongLine.Blank -> Box(Modifier.height((fontSize * 0.8f).dp))
                is SongLine.Section -> Text(line.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 8.dp))
                is SongLine.Comment -> Text(line.text, fontStyle = FontStyle.Italic, fontSize = fontSize.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                is SongLine.Tab -> Text(line.text, fontFamily = mono, fontSize = (fontSize * 0.9f).sp, softWrap = false,
                    modifier = if (format == SongFormat.CHORDPRO) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                is SongLine.Text -> Text(line.text, fontSize = fontSize.sp, fontFamily = if (format != SongFormat.CHORDPRO) mono else null,
                    softWrap = format == SongFormat.CHORDPRO,
                    modifier = if (line.chorus) Modifier.padding(start = 12.dp) else Modifier)
                is SongLine.ChordLine -> {
                    val text = buildAnnotatedString {
                        var cursor = 0
                        for ((col, name) in line.chords) {
                            if (col > cursor) append(" ".repeat(col - cursor))
                            withLink(LinkAnnotation.Clickable("chord", chordStyle) { onChord(name) }) { append(name) }
                            cursor = maxOf(cursor, col) + name.length
                        }
                    }
                    Text(text, fontFamily = mono, fontSize = fontSize.sp, softWrap = false)
                }
                is SongLine.Lyrics -> FlowRow(Modifier.fillMaxWidth().then(if (line.chorus) Modifier.padding(start = 12.dp) else Modifier)) {
                    line.segments.forEach { seg ->
                        Column {
                            if (seg.chord != null) {
                                Text(seg.chord!!, color = chordColor, fontWeight = FontWeight.Bold, fontSize = (fontSize * 0.95f).sp,
                                    modifier = Modifier.clickable { onChord(seg.chord!!) }.padding(end = 4.dp))
                            } else if (line.segments.any { it.chord != null }) {
                                Text(" ", fontSize = (fontSize * 0.95f).sp)
                            }
                            Text(seg.text.ifEmpty { " " }, fontSize = fontSize.sp)
                        }
                    }
                }
            }
        }
    }
}

