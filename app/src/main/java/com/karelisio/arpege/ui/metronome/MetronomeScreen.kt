package com.karelisio.arpege.ui.metronome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.arpege.audio.MetronomeController
import com.karelisio.arpege.audio.MetronomeState
import com.karelisio.arpege.data.container
import com.karelisio.arpege.theory.dsp.Accent
import com.karelisio.arpege.theory.dsp.Meter
import com.karelisio.arpege.theory.dsp.MetronomeFavorite
import com.karelisio.arpege.theory.dsp.SpeedTrainer
import com.karelisio.arpege.theory.dsp.TapTempo
import com.karelisio.arpege.theory.dsp.TempoNames
import com.karelisio.arpege.ui.components.AnimatedCounter
import com.karelisio.arpege.ui.components.Haptics
import com.karelisio.arpege.ui.components.ToolScaffold
import com.karelisio.arpege.ui.components.rememberHaptics
import com.karelisio.arpege.ui.theme.LocalReduceMotion
import com.karelisio.arpege.ui.theme.NumberStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt

private val subdivisionLabels = listOf("Noires", "Croches", "Triolets", "Doubles")

@Composable
fun MetronomeScreen(onBack: () -> Unit, deepLinkBpm: Int?) {
    val c = LocalContext.current.container
    val controller = c.metronome
    val state by controller.state.collectAsStateWithLifecycle()
    val favorites by controller.favorites.collectAsStateWithLifecycle(emptyList())
    val cfg = state.config
    val haptics = rememberHaptics()
    val tap = remember { TapTempo() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var deepLinkConsumed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(deepLinkBpm) {
        if (!deepLinkConsumed && deepLinkBpm != null && deepLinkBpm > 0) {
            controller.ready()
            controller.setBpm(deepLinkBpm)
            controller.start()
        }
        deepLinkConsumed = true
    }

    // Animation synchronisée sur la position audio réelle (pas sur un timer).
    var beat by remember { mutableIntStateOf(-1) }
    var squash by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state.playing) {
        if (!state.playing) { beat = -1; squash = 0f; return@LaunchedEffect }
        while (true) {
            withFrameNanos { }
            val t = controller.currentTick() ?: continue
            val tick = t.tick
            beat = tick.beat
            val amp = when {
                tick.accent == Accent.MUTE && !tick.subdivision -> 0f
                tick.subdivision -> 0.25f
                tick.beat == 0 -> 1f
                tick.accent == Accent.MEDIUM -> 0.6f
                else -> 0.45f
            }
            // Oscillation amortie : écrasement puis étirement, retour au repos.
            squash = amp * (exp(-5.0 * t.progress) * cos(2.5 * PI * t.progress)).toFloat()
        }
    }

    ToolScaffold(title = "Métronome", onBack = onBack, sharedKey = "tool-metronome", snackbar = snackbar) { pad ->
        // Taille de la roue fixée sur l'espace total (stable quand la barre se replie au défilement) :
        // roue, ±1, tap tempo et accents tiennent sans défiler, même avec la barre de navigation.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wheel = minOf(maxWidth - 32.dp - 48.dp, maxHeight - 330.dp, 340.dp).coerceAtLeast(200.dp)
            Column(
                Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WheelSection(state, wheel, beat, squash, controller, haptics)

                FilledTonalButton(
                    onClick = { haptics.click(); tap.tap(System.currentTimeMillis())?.let { controller.setBpm(it) } },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Rounded.TouchApp, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Tap tempo", style = MaterialTheme.typography.titleSmall)
                }

                // Points de mesure : allumés en séquence, toucher = changer l'accent.
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                        cfg.accents.forEachIndexed { i, accent ->
                            BeatDot(accent, lit = state.playing && beat == i, index = i) { haptics.tick(); controller.cycleAccent(i) }
                        }
                    }
                    Text("Touchez un temps pour changer son accent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                SectionTitle("Mesure")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Meter.PRESETS.forEach { m ->
                        FilterChip(selected = cfg.meter == m, onClick = { haptics.tick(); controller.setMeter(m) }, label = {
                            Text(m.label + if (m.groups.size > 1 && !m.isCompound) "  (" + m.groups.joinToString("+") + ")" else "")
                        })
                    }
                }
                SectionTitle("Subdivision")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    subdivisionLabels.forEachIndexed { i, label ->
                        ToggleButton(
                            checked = cfg.subdivision == i + 1,
                            onCheckedChange = { haptics.tick(); controller.setSubdivision(i + 1) },
                            modifier = Modifier.weight(1f),
                        ) { Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                    }
                }

                FavoritesSection(state, favorites, controller, haptics) { fav, index ->
                    scope.launch {
                        val r = snackbar.showSnackbar("« ${fav.name} » supprimé", actionLabel = "Annuler", duration = SnackbarDuration.Short)
                        if (r == SnackbarResult.ActionPerformed) controller.restoreFavorite(fav, index)
                    }
                }
                TrainerCard(state, controller, haptics)
                TimerCard(state, controller, haptics)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** Roue de tempo autour de la forme qui bat la mesure ; ±1 dans les coins bas, état en haut. */
@Composable
private fun WheelSection(state: MetronomeState, wheel: Dp, beat: Int, squash: Float, controller: MetronomeController, haptics: Haptics) {
    val cfg = state.config
    val reduce = LocalReduceMotion.current
    val scheme = MaterialTheme.colorScheme
    val downbeat = state.playing && beat == 0
    val shapeColor by animateColorAsState(if (downbeat) scheme.primary else scheme.primaryContainer, label = "beatColor")
    val textColor by animateColorAsState(if (downbeat) scheme.onPrimary else scheme.onPrimaryContainer, label = "beatText")
    val shape = remember { MaterialShapes.Cookie9Sided }
    val inner = wheel * WHEEL_INNER

    Box(Modifier.fillMaxWidth().height(wheel)) {
        TempoWheel(cfg.bpm, onBpm = { controller.setBpm(it) }, size = wheel, modifier = Modifier.align(Alignment.Center)) {
            // Centre : toucher = lecture / pause.
            Box(
                Modifier
                    .size(inner)
                    .clip(CircleShape)
                    .clickable(onClickLabel = if (state.playing) "Pause" else "Lecture", role = Role.Button) {
                        haptics.click(); controller.toggle()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(wheel * 0.6f)
                        .graphicsLayer {
                            val s = if (reduce) 0f else squash
                            scaleX = 1f + 0.16f * s
                            scaleY = 1f - 0.14f * s
                            transformOrigin = TransformOrigin.Center
                        }
                        .clip(shape.toShape())
                        .background(shapeColor),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("BPM", style = MaterialTheme.typography.labelMedium, color = textColor.copy(alpha = 0.75f))
                    AnimatedCounter(
                        cfg.bpm,
                        style = NumberStyle.copy(fontSize = (wheel.value * 0.22f).sp, lineHeight = (wheel.value * 0.24f).sp),
                        color = textColor,
                    )
                    Text(TempoNames.of(cfg.bpm), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = textColor)
                    Icon(
                        if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null, tint = textColor, modifier = Modifier.padding(top = 2.dp).size(28.dp),
                    )
                }
            }
        }
        val btn = Modifier.size(IconButtonDefaults.mediumContainerSize())
        FilledTonalIconButton(
            onClick = { haptics.tick(); controller.nudge(-1) }, shapes = IconButtonDefaults.shapes(),
            modifier = btn.align(Alignment.BottomStart),
        ) { Icon(Icons.Rounded.Remove, contentDescription = "Moins 1 BPM") }
        FilledIconButton(
            onClick = { haptics.tick(); controller.nudge(1) }, shapes = IconButtonDefaults.shapes(),
            modifier = btn.align(Alignment.BottomEnd),
        ) { Icon(Icons.Rounded.Add, contentDescription = "Plus 1 BPM") }

        if (state.timerOn) {
            val remaining = rememberRemainingSeconds(state.timerEndsAt)
            StatusPill(
                Icons.Rounded.Timer,
                if (remaining != null) formatClock(remaining) else "${state.timerMinutes} min",
                if (remaining != null) "Arrêt dans ${formatClock(remaining)}" else "Minuteur ${state.timerMinutes} minutes",
                Modifier.align(Alignment.TopStart),
            )
        }
        if (state.trainerOn) {
            StatusPill(
                Icons.AutoMirrored.Rounded.TrendingUp, "${state.trainer.maxBpm}",
                "Accélération jusqu'à ${state.trainer.maxBpm} BPM", Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

@Composable
private fun StatusPill(icon: ImageVector, text: String, description: String, modifier: Modifier) {
    Surface(
        modifier.semantics(mergeDescendants = true) { contentDescription = description },
        shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.fillMaxWidth())
}

// --- Favoris -----------------------------------------------------------------------------------

@Composable
private fun FavoritesSection(
    state: MetronomeState,
    favorites: List<MetronomeFavorite>,
    controller: MetronomeController,
    haptics: Haptics,
    onDeleted: (MetronomeFavorite, Int) -> Unit,
) {
    var saving by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Favoris", Modifier.weight(1f))
            TextButton(onClick = { haptics.click(); saving = true }) {
                Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Enregistrer")
            }
        }
        if (favorites.isEmpty()) {
            Text(
                "Enregistrez le réglage courant (tempo, mesure, subdivision) pour le retrouver d'un geste.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                favorites.forEachIndexed { i, fav ->
                    FavoriteChip(
                        fav, selected = fav.matches(state.config),
                        onClick = { haptics.tick(); controller.applyFavorite(fav) },
                        onLongClick = { haptics.confirm(); controller.deleteFavorite(fav); onDeleted(fav, i) },
                    )
                }
            }
            Text("Appui long sur un favori pour le supprimer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (saving) SaveFavoriteDialog(state, onDismiss = { saving = false }) { name ->
        haptics.confirm()
        controller.saveFavorite(name)
        saving = false
    }
}

private fun describe(bpm: Int, meter: Meter, subdivision: Int) =
    "$bpm BPM · ${meter.label}" + if (subdivision > 1) " · ${subdivisionLabels[subdivision - 1].lowercase()}" else ""

@Composable
private fun FavoriteChip(fav: MetronomeFavorite, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    Surface(
        shape = shape,
        color = if (selected) scheme.secondaryContainer else Color.Transparent,
        contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
        border = if (selected) null else BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.clip(shape).combinedClickable(
            onClickLabel = "Appliquer", onLongClickLabel = "Supprimer", role = Role.Button,
            onClick = onClick, onLongClick = onLongClick,
        ),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Column {
                Text(fav.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
                Text(describe(fav.bpm, fav.meter, fav.subdivision), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SaveFavoriteDialog(state: MetronomeState, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val cfg = state.config
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.BookmarkAdd, contentDescription = null) },
        title = { Text("Enregistrer le réglage") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(describe(cfg.bpm, cfg.meter, cfg.subdivision), style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MetronomeFavorite.MAX_NAME) },
                    label = { Text("Nom") },
                    placeholder = { Text("Ex. Gammes lentes") },
                    singleLine = true,
                    supportingText = { Text("${name.length}/${MetronomeFavorite.MAX_NAME}") },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

// --- Accélération progressive et minuteur -------------------------------------------------------

@Composable
private fun TrainerCard(state: MetronomeState, controller: MetronomeController, haptics: Haptics) {
    val t = state.trainer
    val bars = if (t.everyBars > 1) "toutes les ${t.everyBars} mesures" else "à chaque mesure"
    OptionCard(
        icon = Icons.AutoMirrored.Rounded.TrendingUp,
        title = "Accélération progressive",
        subtitle = "+${t.stepBpm} BPM $bars, jusqu'à ${t.maxBpm} BPM",
        checked = state.trainerOn,
        onChecked = { haptics.tick(); controller.setTrainerOn(it) },
    ) {
        fun set(n: SpeedTrainer) { haptics.tick(); controller.setTrainer(n) }
        Stepper("Palier", "+${t.stepBpm} BPM",
            onMinus = { set(t.copy(stepBpm = (t.stepBpm - 1).coerceIn(SpeedTrainer.STEP_RANGE))) },
            onPlus = { set(t.copy(stepBpm = (t.stepBpm + 1).coerceIn(SpeedTrainer.STEP_RANGE))) })
        Stepper("Toutes les", if (t.everyBars > 1) "${t.everyBars} mesures" else "1 mesure",
            onMinus = { set(t.copy(everyBars = (t.everyBars - 1).coerceIn(SpeedTrainer.BARS_RANGE))) },
            onPlus = { set(t.copy(everyBars = (t.everyBars + 1).coerceIn(SpeedTrainer.BARS_RANGE))) })
        Stepper("Jusqu'à", "${t.maxBpm} BPM",
            onMinus = { set(t.copy(maxBpm = (t.maxBpm - 5).coerceIn(20, 300))) },
            onPlus = { set(t.copy(maxBpm = (t.maxBpm + 5).coerceIn(20, 300))) })
        val start = state.trainerStartBpm
        val note = when {
            start != null && state.config.bpm >= t.maxBpm -> "Tempo maximal atteint ($start → ${t.maxBpm} BPM)."
            start != null -> "En cours : départ à $start BPM, retour à ce tempo à l'arrêt."
            t.maxBpm <= state.config.bpm -> "Le tempo maximal doit dépasser le tempo actuel (${state.config.bpm} BPM)."
            else -> "Démarre au tempo actuel ; le changement tombe sur le premier temps de la mesure."
        }
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TimerCard(state: MetronomeState, controller: MetronomeController, haptics: Haptics) {
    val remaining = rememberRemainingSeconds(state.timerEndsAt)
    OptionCard(
        icon = Icons.Rounded.Timer,
        title = "Minuteur de séance",
        subtitle = if (remaining != null) "Arrêt dans ${formatClock(remaining)}" else "Arrêt automatique après ${state.timerMinutes} min, avec vibration",
        checked = state.timerOn,
        onChecked = { haptics.tick(); controller.setTimerOn(it) },
    ) {
        Slider(
            value = state.timerMinutes.toFloat(),
            onValueChange = { v ->
                val m = ((v / 5f).roundToInt() * 5).coerceIn(5, 60)
                if (m != state.timerMinutes) { haptics.tick(); controller.setTimerMinutes(m) }
            },
            valueRange = 5f..60f,
            steps = 10,
            modifier = Modifier.semantics { contentDescription = "Durée du minuteur" },
        )
        Row(Modifier.fillMaxWidth()) {
            Text("5 min", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${state.timerMinutes} min", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text("60 min", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduce = LocalReduceMotion.current
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(MaterialShapes.Cookie6Sided.toShape()).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp)) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = checked, onCheckedChange = onChecked, modifier = Modifier.semantics { contentDescription = title })
            }
            AnimatedVisibility(
                visible = checked,
                enter = if (reduce) EnterTransition.None else expandVertically() + fadeIn(),
                exit = if (reduce) ExitTransition.None else shrinkVertically() + fadeOut(),
            ) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = onMinus, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Remove, contentDescription = "$label : moins") }
        Text(value, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.width(104.dp))
        FilledTonalIconButton(onClick = onPlus, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Add, contentDescription = "$label : plus") }
    }
}

/** Secondes restantes jusqu'à [endsAt] (ms epoch), rafraîchies chaque seconde ; null sans minuteur. */
@Composable
private fun rememberRemainingSeconds(endsAt: Long?): Long? {
    val v by produceState<Long?>(null, endsAt) {
        if (endsAt == null) { value = null; return@produceState }
        while (true) {
            val ms = (endsAt - System.currentTimeMillis()).coerceAtLeast(0)
            value = (ms + 999) / 1000
            delay(ms % 1000 + 20)
        }
    }
    return v
}

private fun formatClock(seconds: Long) = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

@Composable
private fun BeatDot(accent: Accent, lit: Boolean, index: Int, onClick: () -> Unit) {
    val size by animateDpAsState(
        when (accent) { Accent.STRONG -> 30.dp; Accent.MEDIUM -> 25.dp; Accent.WEAK -> 20.dp; Accent.MUTE -> 16.dp },
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
    val label = when (accent) { Accent.STRONG -> "fort"; Accent.MEDIUM -> "moyen"; Accent.WEAK -> "faible"; Accent.MUTE -> "muet" }
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .clickable(onClickLabel = "Changer l'accent", role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Temps ${index + 1}, accent $label" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(if (lit) size + 6.dp else size).clip(CircleShape).background(color)
                .then(if (accent == Accent.MUTE) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.outline), CircleShape) else Modifier),
        )
    }
}
