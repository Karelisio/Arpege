package com.karelisio.arpege.ui.training

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
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
import com.karelisio.arpege.theory.dsp.NoteEvent
import com.karelisio.arpege.theory.dsp.Voice
import com.karelisio.arpege.theory.midiToFrequency
import com.karelisio.arpege.theory.training.EarItem
import com.karelisio.arpege.theory.training.EarMode
import com.karelisio.arpege.theory.training.EarQuestion
import com.karelisio.arpege.theory.training.EarTraining
import com.karelisio.arpege.theory.training.IntervalDirection
import com.karelisio.arpege.ui.components.ToolScaffold
import com.karelisio.arpege.ui.components.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun EarScreen(onBack: () -> Unit) {
    TrackPractice(PracticeActivity.EAR)
    val c = LocalContext.current.container
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val random = remember { Random(System.nanoTime()) }

    var mode by rememberSaveable { mutableStateOf(EarMode.INTERVALS) }
    var level by rememberSaveable { mutableIntStateOf(1) }
    var direction by rememberSaveable { mutableStateOf(IntervalDirection.UP) }
    var enabled by remember(mode) { mutableStateOf(EarTraining.itemsUpTo(mode, 1).map { it.id }.toSet()) }
    val stats by c.db.stats().forExercise(mode.key).collectAsState(emptyList())
    val statMap = stats.associate { it.item to (it.correct to it.attempts) }

    var question by remember { mutableStateOf<EarQuestion?>(null) }
    var answered by remember { mutableStateOf<String?>(null) }
    var correct by remember(mode) { mutableIntStateOf(0) }
    var total by remember(mode) { mutableIntStateOf(0) }
    var streak by remember(mode) { mutableIntStateOf(0) }

    val all = EarTraining.allItems(mode)
    val active = all.filter { it.id in enabled }

    fun play(q: EarQuestion) {
        val step = q.blockMs / 1000.0
        val events = q.blocks.flatMapIndexed { i, block ->
            block.map { m -> NoteEvent(i * step, midiToFrequency(m.toDouble(), settings.a4.toDouble()), step * if (i == q.blocks.lastIndex) 2.0 else 1.3, 0.7) }
        }
        c.sound.playEvents(events, Voice.PIANO)
    }

    fun next() {
        if (active.isEmpty()) return
        val item = EarTraining.pick(active, statMap, random, avoid = question?.item?.id)
        val q = EarTraining.question(mode, item, random, direction)
        question = q
        answered = null
        play(q)
    }

    fun answer(item: EarItem) {
        val q = question ?: return
        if (answered != null) return
        answered = item.id
        val ok = item.id == q.item.id
        total++
        if (ok) { correct++; streak++; haptics.confirm() } else { streak = 0; haptics.reject() }
        c.stats.record(mode.key, q.item.id, ok)
        scope.launch { delay(if (ok) 800 else 1800); next() }
    }

    LaunchedEffect(mode, level) {
        enabled = EarTraining.itemsUpTo(mode, level).map { it.id }.toSet()
        question = null; answered = null
    }

    ToolScaffold(title = "Oreille", onBack = onBack, sharedKey = "tool-ear") { p ->
        Column(
            Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PrimaryScrollableTabRow(selectedTabIndex = mode.ordinal, edgePadding = 0.dp) {
                EarMode.entries.forEach { m -> Tab(selected = m == mode, onClick = { haptics.tick(); mode = m; level = 1 }, text = { Text(m.label, maxLines = 1) }) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Niveau", style = MaterialTheme.typography.titleSmall)
                (1..EarTraining.levelCount(mode)).forEach { l -> FilterChip(selected = l == level, onClick = { level = l }, label = { Text("$l") }) }
            }
            if (mode == EarMode.INTERVALS) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IntervalDirection.entries.forEach { d -> FilterChip(selected = d == direction, onClick = { direction = d }, label = { Text(d.label) }) }
            }
            Text("Éléments inclus", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                all.forEach { item ->
                    FilterChip(selected = item.id in enabled, onClick = {
                        enabled = if (item.id in enabled && enabled.size > 2) enabled - item.id else enabled + item.id
                    }, label = { Text(item.label) })
                }
            }
            ScoreHeader(correct, total, streak)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                LargeFloatingActionButton(
                    onClick = { haptics.click(); question?.let { play(it) } ?: next() },
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                ) {
                    if (question == null) Text("Go", style = MaterialTheme.typography.titleLarge)
                    else Icon(Icons.Rounded.Replay, "Réécouter", Modifier.size(32.dp))
                }
            }
            Text(
                if (question == null) "Touchez Go pour commencer" else "Qu'entends-tu ?",
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            if (question != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                active.forEach { item ->
                    val state = when {
                        answered == null -> AnswerState.NEUTRAL
                        item.id == question!!.item.id && answered == item.id -> AnswerState.CORRECT
                        item.id == answered -> AnswerState.WRONG
                        item.id == question!!.item.id -> AnswerState.REVEAL
                        else -> AnswerState.NEUTRAL
                    }
                    AnswerButton(item.label, state) { answer(item) }
                }
            }
            ItemStats(stats, label = { id -> all.firstOrNull { it.id == id }?.label ?: id }, onReset = { c.stats.reset(mode.key) })
        }
    }
}
