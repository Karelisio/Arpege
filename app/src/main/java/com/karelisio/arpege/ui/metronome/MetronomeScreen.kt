package com.karelisio.arpege.ui.metronome

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karelisio.arpege.data.container
import com.karelisio.arpege.theory.dsp.Accent
import com.karelisio.arpege.theory.dsp.Meter
import com.karelisio.arpege.theory.dsp.TapTempo
import com.karelisio.arpege.theory.dsp.TempoNames
import com.karelisio.arpege.ui.components.AnimatedCounter
import com.karelisio.arpege.ui.components.ToolScaffold
import com.karelisio.arpege.ui.components.rememberHaptics
import com.karelisio.arpege.ui.theme.LocalReduceMotion
import com.karelisio.arpege.ui.theme.NumberStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp

private val subdivisionLabels = listOf("Noires", "Croches", "Triolets", "Doubles")

@Composable
fun MetronomeScreen(onBack: () -> Unit, deepLinkBpm: Int?) {
    val c = LocalContext.current.container
    val controller = c.metronome
    val state by controller.state.collectAsState()
    val cfg = state.config
    val haptics = rememberHaptics()
    val reduce = LocalReduceMotion.current
    val tap = remember { TapTempo() }

    var deepLinkConsumed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(deepLinkBpm) {
        if (!deepLinkConsumed && deepLinkBpm != null && deepLinkBpm > 0) { controller.setBpm(deepLinkBpm); controller.start() }
        deepLinkConsumed = true
    }

    // Animation synchronisée sur la position audio réelle (pas sur un timer).
    var beat by remember { mutableIntStateOf(-1) }
    var squash by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state.playing) {
        if (!state.playing) { beat = -1; squash = 0f; return@LaunchedEffect }
        while (true) {
            withFrameNanos { }
            val cur = controller.currentTick() ?: continue
            val (t, p) = cur
            beat = t.beat
            val amp = when {
                t.accent == Accent.MUTE && !t.subdivision -> 0f
                t.subdivision -> 0.25f
                t.beat == 0 -> 1f
                t.accent == Accent.MEDIUM -> 0.6f
                else -> 0.45f
            }
            // Oscillation amortie : écrasement puis étirement, retour au repos.
            squash = amp * (exp(-5.0 * p) * cos(2.5 * PI * p)).toFloat()
        }
    }

    ToolScaffold(title = "Métronome", onBack = onBack, sharedKey = "tool-metronome") { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                AnimatedCounter(cfg.bpm, style = NumberStyle.copy(fontSize = 84.sp), color = MaterialTheme.colorScheme.onSurface)
                Text(" BPM", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 18.dp))
            }
            Text(TempoNames.of(cfg.bpm), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)

            // Forme centrale qui bat la mesure.
            val shape = remember { MaterialShapes.Cookie9Sided }
            val beatColor by animateColorAsState(
                if (beat == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer, label = "beatColor",
            )
            Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(180.dp)
                        .graphicsLayer {
                            val s = if (reduce) 0f else squash
                            scaleX = 1f + 0.22f * s
                            scaleY = 1f - 0.18f * s
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                        }
                        .clip(shape.toShape())
                        .background(beatColor)
                        .clickable { haptics.click(); controller.toggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (beat >= 0) "${beat + 1}" else "",
                        fontSize = 56.sp, fontWeight = FontWeight.Black,
                        color = if (beat == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            // Points de mesure : allumés en séquence, toucher = changer l'accent.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                cfg.accents.forEachIndexed { i, accent ->
                    BeatDot(accent, lit = state.playing && beat == i) { haptics.tick(); controller.cycleAccent(i) }
                }
            }
            Text("Touchez un temps pour changer son accent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            LargeFloatingActionButton(onClick = { haptics.click(); controller.toggle() }, shape = (if (state.playing) MaterialShapes.Square else MaterialShapes.Cookie4Sided).toShape()) {
                Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (state.playing) "Pause" else "Lecture", modifier = Modifier.size(36.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedIconButton(onClick = { haptics.tick(); controller.nudge(-1) }) { Icon(Icons.Rounded.Remove, "−1") }
                TempoWheel(cfg.bpm, onBpm = { controller.setBpm(it) })
                FilledIconButton(onClick = { haptics.tick(); controller.nudge(1) }) { Icon(Icons.Rounded.Add, "+1") }
            }
            FilledTonalButton(onClick = {
                haptics.click()
                tap.tap(System.currentTimeMillis())?.let { controller.setBpm(it) }
            }, modifier = Modifier.fillMaxWidth()) { Text("Tap tempo") }

            Text("Mesure", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Meter.PRESETS.forEach { m ->
                    FilterChip(selected = cfg.meter == m, onClick = { haptics.tick(); controller.setMeter(m) }, label = {
                        Text(m.label + if (m.groups.size > 1 && !m.isCompound) "  (" + m.groups.joinToString("+") + ")" else "")
                    })
                }
            }
            Text("Subdivision", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                subdivisionLabels.forEachIndexed { i, label ->
                    ToggleButton(
                        checked = cfg.subdivision == i + 1,
                        onCheckedChange = { haptics.tick(); controller.setSubdivision(i + 1) },
                        modifier = Modifier.weight(1f),
                    ) { Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}

@Composable
private fun BeatDot(accent: Accent, lit: Boolean, onClick: () -> Unit) {
    val size by animateDpAsState(
        when (accent) { Accent.STRONG -> 34.dp; Accent.MEDIUM -> 28.dp; Accent.WEAK -> 22.dp; Accent.MUTE -> 18.dp },
        label = "dotSize",
    )
    val color by animateColorAsState(
        when {
            lit -> MaterialTheme.colorScheme.primary
            accent == Accent.MUTE -> MaterialTheme.colorScheme.surface
            accent == Accent.STRONG -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.secondaryContainer
        },
        label = "dotColor",
    )
    Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(if (lit) size + 6.dp else size).clip(CircleShape).background(color)
                .then(if (accent == Accent.MUTE) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.outline), CircleShape) else Modifier),
        )
    }
}
