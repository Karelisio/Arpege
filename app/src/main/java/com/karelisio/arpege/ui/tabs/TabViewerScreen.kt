package com.karelisio.arpege.ui.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.ScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
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
import com.karelisio.arpege.theory.song.GuitarProTimeline
import com.karelisio.arpege.theory.song.TabBeatPosition
import com.karelisio.arpege.theory.dsp.NoteStream
import com.karelisio.arpege.theory.dsp.Voice
import com.karelisio.arpege.audio.StreamPlayer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import com.karelisio.arpege.ui.theme.LocalReduceMotion
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
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

/** [previewBeat] : temps surligné d'office (captures d'écran). */
@Composable
internal fun TabViewer(file: TabFile, onBack: () -> Unit, previewBeat: Int = -1) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val haptics = rememberHaptics()
    val format = runCatching { SongFormat.valueOf(file.format) }.getOrDefault(SongFormat.ASCII)
    val gp = remember(file.id) {
        if (format == SongFormat.GUITAR_PRO) runCatching { GuitarProReader.read(Base64.getDecoder().decode(file.content)) }.getOrNull() else null
    }
    val gpTracks = remember(gp) { gp?.let { GuitarProTab.playableTracks(it) } ?: emptyList() }
    var gpTrack by rememberSaveable { mutableIntStateOf(-1) }
    val trackIndex = if (gpTrack >= 0) gpTrack else gpTracks.firstOrNull() ?: 0
    var transpose by rememberSaveable { mutableIntStateOf(file.transpose) }
    var speed by rememberSaveable { mutableFloatStateOf(file.scrollSpeed) }
    var scrolling by rememberSaveable { mutableStateOf(false) }
    var fontSize by rememberSaveable { mutableFloatStateOf(15f) }
    var sheetChord by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val density = LocalDensity.current
    val reduce = LocalReduceMotion.current
    val snackbar = remember { SnackbarHostState() }

    // Largeur d'un caractère de tablature : les systèmes Guitar Pro sont coupés à la largeur de l'écran.
    val measurer = rememberTextMeasurer()
    val tabStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = (fontSize * 0.9f).sp)
    val charPx = remember(fontSize) { measurer.measure("0".repeat(20), tabStyle).size.width / 20f }
    val windowWidth = LocalWindowInfo.current.containerSize.width
    val maxChars = with(density) { ((windowWidth - 34.dp.toPx()) / charPx).toInt() }.coerceAtLeast(24)
    val gpLayout = remember(gp, trackIndex, maxChars) { gp?.let { GuitarProTab.layout(it, trackIndex, maxChars = maxChars) } }
    val base = remember(file.id, trackIndex, gpLayout) { gpLayout?.song ?: parseSong(file.content, format, file.title) }
    val song = remember(base, transpose) { base.transposed(transpose) }

    // Lecture audio Guitar Pro : flux synthétisé par blocs, curseur calé sur la position audio.
    var speedPct by rememberSaveable { mutableIntStateOf(100) }
    var playing by remember { mutableStateOf(false) }
    var cursor by remember { mutableIntStateOf(previewBeat) }
    val timeline = remember(gp, trackIndex, speedPct) { gp?.let { GuitarProTimeline.build(it, trackIndex, speedPct / 100.0) } }
    val player = remember { StreamPlayer(context.applicationContext) }
    DisposableEffect(player) { onDispose { player.stop() } }
    LaunchedEffect(trackIndex) { if (playing) playing = false; cursor = previewBeat }
    LaunchedEffect(playing, timeline) {
        val tl = timeline
        if (!playing || tl == null || tl.beats.isEmpty()) { player.stop(); return@LaunchedEffect }
        delay(80) // anti-rebond : changement de vitesse en glissant
        val from = cursor.takeIf { it in tl.beats.indices } ?: 0
        val origin = tl.beats[from].startSec
        val stream = NoteStream(tl.events(settings.a4.toDouble(), from), Voice.STRING, player.sampleRate)
        player.start(
            source = { out, frame -> stream.read(out, frame) },
            onEnd = { playing = false; cursor = -1 },
            onError = { playing = false; scope.launch { snackbar.showSnackbar("Sortie audio indisponible") } },
        )
        while (true) {
            withFrameNanos { }
            val start = player.startNanos
            if (start < 0) continue
            val b = tl.beatAt(origin + (System.nanoTime() - start) / 1e9)
            if (b >= 0) cursor = b
        }
    }
    val highlight: TabBeatPosition? = gpLayout?.beats?.getOrNull(cursor)

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
        snackbarHost = { SnackbarHost(snackbar) },
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
                    if (gp == null || song.chords.isNotEmpty()) {
                        IconButton(onClick = { haptics.tick(); transpose-- }) { Icon(Icons.Rounded.Remove, "Transposer −1") }
                        Text("%+d".format(transpose), style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { haptics.tick(); transpose++ }) { Icon(Icons.Rounded.Add, "Transposer +1") }
                    }
                    if (gp != null) {
                        Column(Modifier.width(if (song.chords.isEmpty()) 230.dp else 130.dp).padding(start = 12.dp)) {
                            Text("Vitesse $speedPct %", style = MaterialTheme.typography.labelLarge)
                            Slider(value = speedPct.toFloat(), onValueChange = { speedPct = (it / 5f).roundToInt() * 5 }, valueRange = 50f..150f)
                        }
                    } else {
                        Slider(value = speed, onValueChange = { speed = it }, valueRange = 0.3f..4f, modifier = Modifier.width(140.dp).padding(start = 8.dp))
                    }
                },
                floatingActionButton = {
                    if (gp != null) {
                        FloatingActionButton(onClick = { haptics.click(); playing = !playing }) {
                            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Lire la piste")
                        }
                    } else {
                        FloatingActionButton(onClick = { haptics.click(); scrolling = !scrolling }) {
                            Icon(if (scrolling) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (scrolling) "Arrêter le défilement" else "Défilement auto")
                        }
                    }
                },
            )
        },
    ) { p ->
        val viewport = remember { arrayOfNulls<LayoutCoordinates>(1) }
        val lineCoords = remember { HashMap<Int, LayoutCoordinates>() }
        // Défilement automatique pour garder le curseur de lecture visible (vertical et horizontal).
        LaunchedEffect(highlight?.line, highlight?.column) {
            val hl = highlight ?: return@LaunchedEffect
            if (!playing) return@LaunchedEffect
            followCursor(hl, viewport[0], lineCoords, charPx, scroll, hScroll, reduce)
        }
        Column(
            Modifier.fillMaxSize().padding(p).onGloballyPositioned { viewport[0] = it }.verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .then(if (format != SongFormat.CHORDPRO) Modifier.horizontalScroll(hScroll) else Modifier),
        ) {
            if (gp != null && gpTracks.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                gpTracks.forEach { i ->
                    FilterChip(selected = i == trackIndex, onClick = { gpTrack = i }, label = { Text(gp.tracks[i].name.ifBlank { "Piste ${i + 1}" }) })
                }
            }
            SongContent(song, format, fontSize, highlight, onLine = { i, coords -> lineCoords[i] = coords }) { sheetChord = it }
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

/** Fait défiler pour que le temps [hl] reste dans la zone visible confortable. */
private suspend fun followCursor(
    hl: TabBeatPosition,
    viewport: LayoutCoordinates?,
    lines: Map<Int, LayoutCoordinates>,
    charPx: Float,
    scroll: ScrollState,
    hScroll: ScrollState,
    reduce: Boolean,
) {
    val vp = viewport ?: return
    val first = lines[hl.line] ?: return
    val last = lines[hl.line + hl.strings - 1] ?: first
    if (!vp.isAttached || !first.isAttached || !last.isAttached) return
    val top = vp.localPositionOf(first, Offset.Zero)
    val bottom = vp.localPositionOf(last, Offset.Zero).y + last.size.height
    val h = vp.size.height.toFloat()
    val w = vp.size.width.toFloat()
    val dy = if (top.y < h * 0.12f || bottom > h * 0.8f) top.y - h * 0.22f else 0f
    val x = top.x + hl.column * charPx
    val dx = if (x < w * 0.08f || x + hl.width * charPx > w * 0.92f) x - w * 0.3f else 0f
    if (reduce) {
        if (dy != 0f) scroll.scrollBy(dy)
        if (dx != 0f) hScroll.scrollBy(dx)
    } else {
        if (dx != 0f) hScroll.animateScrollBy(dx)
        if (dy != 0f) scroll.animateScrollBy(dy)
    }
}

@Composable
private fun SongContent(
    song: Song,
    format: SongFormat,
    fontSize: Float,
    highlight: TabBeatPosition? = null,
    onLine: (Int, LayoutCoordinates) -> Unit = { _, _ -> },
    onChord: (String) -> Unit,
) {
    val chordColor = MaterialTheme.colorScheme.primary
    val mono = FontFamily.Monospace
    val chordStyle = TextLinkStyles(SpanStyle(color = chordColor, fontWeight = FontWeight.Bold))
    val measureBg = MaterialTheme.colorScheme.secondaryContainer
    val beatBg = MaterialTheme.colorScheme.primary
    val beatFg = MaterialTheme.colorScheme.onPrimary
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        song.lines.forEachIndexed { index, line ->
            when (line) {
                is SongLine.Blank -> Box(Modifier.height((fontSize * 0.8f).dp))
                is SongLine.Section -> Text(line.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 8.dp))
                is SongLine.Comment -> Text(line.text, fontStyle = FontStyle.Italic, fontSize = fontSize.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                is SongLine.Tab -> {
                    val hl = highlight?.takeIf { index in it.line until it.line + it.strings }
                    val text = if (hl == null) AnnotatedString(line.text) else buildAnnotatedString {
                        append(line.text)
                        fun span(from: Int, len: Int, style: SpanStyle) {
                            val a = from.coerceIn(0, line.text.length)
                            val b = (from + len).coerceIn(a, line.text.length)
                            if (b > a) addStyle(style, a, b)
                        }
                        span(hl.measureColumn, hl.measureWidth, SpanStyle(background = measureBg))
                        span(hl.column, hl.width, SpanStyle(background = beatBg, color = beatFg, fontWeight = FontWeight.Bold))
                    }
                    Text(
                        text, fontFamily = mono, fontSize = (fontSize * 0.9f).sp, softWrap = false,
                        modifier = Modifier.onGloballyPositioned { onLine(index, it) }
                            .then(if (format == SongFormat.CHORDPRO) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
                    )
                }
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

