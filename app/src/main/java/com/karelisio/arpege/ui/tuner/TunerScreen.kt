package com.karelisio.arpege.ui.tuner

import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.karelisio.arpege.theory.Pitch
import com.karelisio.arpege.theory.Tuning
import com.karelisio.arpege.theory.dsp.Voice
import com.karelisio.arpege.theory.frequencyToMidi
import com.karelisio.arpege.theory.midiToFrequency
import com.karelisio.arpege.ui.components.MorphShape
import com.karelisio.arpege.ui.components.ToolScaffold
import com.karelisio.arpege.ui.components.rememberHaptics
import com.karelisio.arpege.ui.permissions.AppPermission
import com.karelisio.arpege.ui.permissions.rememberPermissionsController
import com.karelisio.arpege.ui.theme.LocalReduceMotion
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Lecture affichée par l'accordeur. */
private data class TunerReading(val target: Pitch, val targetFreq: Double, val cents: Double, val freq: Double, val stringIndex: Int?)

@Composable
fun TunerScreen(onBack: () -> Unit) {
    TrackPractice(PracticeActivity.TUNER)
    val c = LocalContext.current.container
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val permissions = rememberPermissionsController()
    val micGranted = permissions.granted[AppPermission.MICROPHONE] == true
    val engine = remember { TunerEngine(c.appContext) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val reduce = LocalReduceMotion.current

    var tuningId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(settings.instrument) { if (tuningId == null) tuningId = Tuning.default(settings.instrument).id }
    val chromatic = tuningId == CHROMATIC
    val tuning = Tuning.byId(tuningId ?: "") ?: Tuning.GUITAR_STANDARD

    var reading by remember { mutableStateOf<TunerReading?>(null) }
    var lastHeard by remember { mutableLongStateOf(0L) }
    var smoothMidi by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(micGranted, tuningId, settings.a4) {
        if (!micGranted) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            engine.pitches().collect { est ->
                val now = System.currentTimeMillis()
                if (est == null || est.clarity < 0.85) {
                    if (now - lastHeard > 900) { reading = null; smoothMidi = null }
                    return@collect
                }
                val midi = frequencyToMidi(est.frequency, settings.a4.toDouble())
                val prev = smoothMidi
                // Lissage exponentiel ; on repart de zéro sur un saut de plus d'un demi-ton.
                val s = if (prev == null || abs(prev - midi) > 0.6) midi else prev + 0.3 * (midi - prev)
                smoothMidi = s
                lastHeard = now
                reading = if (chromatic) {
                    val nearest = s.roundToInt()
                    TunerReading(Pitch.fromMidi(nearest), midiToFrequency(nearest.toDouble(), settings.a4.toDouble()), (s - nearest) * 100, est.frequency, null)
                } else {
                    val idx = tuning.nearestString(s)
                    val target = tuning.strings[idx]
                    TunerReading(target, target.frequency(settings.a4.toDouble()), (s - target.midi) * 100, est.frequency, idx)
                }
            }
        }
    }

    // Aiguille à ressort.
    val needle = remember { Animatable(0f) }
    LaunchedEffect(reading?.cents) {
        val target = (reading?.cents ?: 0.0).toFloat().coerceIn(-50f, 50f)
        if (reduce) needle.snapTo(target)
        else needle.animateTo(target, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
    }
    val inTune = reading != null && abs(reading!!.cents) < 3.0
    val morph by animateFloatAsState(
        if (inTune) 1f else 0f,
        if (reduce) tween(0) else spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "morph",
    )
    val pulse = remember { Animatable(1f) }
    var celebrated by remember { mutableStateOf(false) }
    LaunchedEffect(inTune, reading?.target) {
        if (inTune && !celebrated) {
            celebrated = true
            haptics.confirm()
            if (!reduce) {
                pulse.animateTo(1.14f, tween(110))
                pulse.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
            }
        } else if (!inTune && (reading == null || abs(reading!!.cents) > 6)) celebrated = false
    }

    ToolScaffold(title = "Accordeur", onBack = onBack, sharedKey = "tool-tuner") { p ->
        Column(
            Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Choix de l'accordage.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (Tuning.ALL.map { it.id to it.label } + (CHROMATIC to "Chromatique")).forEach { (id, label) ->
                    FilterChip(selected = tuningId == id, onClick = { haptics.tick(); tuningId = id; reading = null }, label = { Text(label) })
                }
            }

            if (!micGranted) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Le micro est nécessaire pour accorder.", style = MaterialTheme.typography.titleMedium)
                        Button(onClick = { permissions.request(AppPermission.MICROPHONE) }) { Text("Autoriser le micro") }
                    }
                }
            }

            // Jauge + note.
            val primary = MaterialTheme.colorScheme.primary
            val good = MaterialTheme.colorScheme.tertiary
            val track = MaterialTheme.colorScheme.outlineVariant
            Box(Modifier.fillMaxWidth().aspectRatio(1.25f), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height * 0.62f)
                    val radius = size.width * 0.46f
                    for (cent in -50..50 step 5) {
                        val major = cent % 10 == 0
                        rotate(cent * 1.2f, pivot = center) {
                            drawLine(
                                color = if (abs(cent) <= 3) good else track,
                                start = Offset(center.x, center.y - radius),
                                end = Offset(center.x, center.y - radius + if (major) 22f else 12f),
                                strokeWidth = if (major) 5f else 3f, cap = StrokeCap.Round,
                            )
                        }
                    }
                    rotate(needle.value * 1.2f, pivot = center) {
                        drawLine(
                            color = if (inTune) good else primary,
                            start = Offset(center.x, center.y - radius * 0.22f),
                            end = Offset(center.x, center.y - radius + 28f),
                            strokeWidth = 9f, cap = StrokeCap.Round,
                        )
                    }
                }
                val shapeMorph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Flower) }
                Box(
                    Modifier
                        .padding(top = 64.dp)
                        .size(150.dp)
                        .graphicsLayer {
                            scaleX = pulse.value; scaleY = pulse.value
                            shape = MorphShape(shapeMorph, morph, rotation = morph * 22f)
                            clip = true
                        }
                        .background(if (inTune) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    val r = reading
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            r?.target?.note?.name(settings.notation) ?: "—",
                            fontSize = if (settings.notation == com.karelisio.arpege.theory.Notation.LATIN) 46.sp else 60.sp,
                            fontWeight = FontWeight.Black,
                            color = if (inTune) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        if (r != null) Text("${r.target.octave}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 10.dp))
                    }
                }
            }

            val r = reading
            Text(
                if (r == null) "Joue une note…" else String.format(Locale.FRANCE, "%+.1f ¢", r.cents),
                style = MaterialTheme.typography.headlineMedium,
                color = if (inTune) good else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                if (r == null) " " else String.format(Locale.FRANCE, "%.2f Hz  ·  cible %.2f Hz", r.freq, r.targetFreq),
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Cordes (détection auto + note de référence au toucher).
            if (!chromatic) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tuning.strings.forEachIndexed { i, s ->
                        ToggleButton(
                            checked = r?.stringIndex == i,
                            onCheckedChange = {
                                haptics.click()
                                c.sound.playFrequency(s.frequency(settings.a4.toDouble()), 2.5, Voice.STRING)
                            },
                        ) { Text(s.note.name(settings.notation, ascii = false), maxLines = 1) }
                    }
                }
                Text("Touchez une corde pour entendre la note de référence", style = MaterialTheme.typography.bodySmall)
            } else {
                Button(onClick = {
                    val f = r?.targetFreq ?: settings.a4.toDouble()
                    c.sound.playFrequency(f, 2.5, Voice.SINE)
                }) {
                    Icon(Icons.AutoMirrored.Rounded.VolumeUp, null)
                    Text("  Note de référence")
                }
            }

            // La4 de référence.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedIconButton(onClick = { scope.launch { c.settings.setA4(settings.a4 - 1) } }, enabled = settings.a4 > 432) {
                    Icon(Icons.Rounded.Remove, "Baisser")
                }
                Text("La4 = ${settings.a4.roundToInt()} Hz", style = MaterialTheme.typography.titleMedium)
                FilledIconButton(onClick = { scope.launch { c.settings.setA4(settings.a4 + 1) } }, enabled = settings.a4 < 446) {
                    Icon(Icons.Rounded.Add, "Monter")
                }
                IconButton(onClick = { c.sound.playFrequency(settings.a4.toDouble(), 2.5, Voice.SINE) }) {
                    Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Écouter le La")
                }
            }
        }
    }
}

private const val CHROMATIC = "chromatic"
