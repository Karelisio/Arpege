package com.karelisio.arpege.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.arpege.data.AppSettings
import com.karelisio.arpege.data.PracticeActivity
import com.karelisio.arpege.data.container
import com.karelisio.arpege.data.db.PracticeSession
import com.karelisio.arpege.theory.Instrument
import com.karelisio.arpege.theory.practice.PracticeStats
import com.karelisio.arpege.theory.practice.SessionLite
import com.karelisio.arpege.ui.components.AnimatedCounter
import com.karelisio.arpege.ui.components.cascadeIn
import com.karelisio.arpege.ui.components.rememberHaptics
import com.karelisio.arpege.ui.theme.NumberStyle
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun JournalScreen(onSettings: () -> Unit) {
    val c = LocalContext.current.container
    val sessions by c.db.practice().all().collectAsState(emptyList())
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val haptics = rememberHaptics()
    var adding by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val stats = remember(sessions) { PracticeStats(sessions.map { SessionLite(it.startEpochMs, it.durationSec, it.instrument, it.activity) }) }
    val week = stats.weekMinutes(today)
    val goal = settings.weeklyGoalMinutes

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { haptics.click(); adding = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Séance") })
        },
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Journal", style = MaterialTheme.typography.displayMedium, modifier = Modifier.weight(1f).padding(vertical = 16.dp))
                    IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Réglages") }
                }
            }
            item {
                Card(Modifier.fillMaxWidth().cascadeIn(0), shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        GoalRing(week.toFloat() / goal) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AnimatedCounter(week, style = NumberStyle.copy(fontSize = MaterialTheme.typography.headlineLarge.fontSize))
                                Text("/ $goal min", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Spacer(Modifier.width(20.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Cette semaine", style = MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.LocalFireDepartment, null, tint = MaterialTheme.colorScheme.primary)
                                AnimatedCounter(stats.streak(today), style = MaterialTheme.typography.headlineMedium)
                                Text(" jour${if (stats.streak(today) > 1) "s" else ""} de suite", style = MaterialTheme.typography.bodyLarge)
                            }
                            Text("Record : ${stats.bestStreak()} j · total ${formatMinutes(stats.totalMinutes)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item { Section("7 derniers jours", 1) { WeekBars(stats.lastDays(today)) } }
            item { Section("Une année de pratique", 2) { YearHeatmap(stats.dailyMinutes, today) } }
            if (sessions.isNotEmpty()) {
                item {
                    Section("Par instrument", 3) {
                        HorizontalBars(stats.minutesBy { it.instrument }.map { (k, v) -> (runCatching { Instrument.valueOf(k).label }.getOrDefault(k)) to v })
                    }
                }
                item { Section("Par activité", 4) { HorizontalBars(stats.minutesBy { it.activity }.map { (k, v) -> PracticeActivity.of(k).label to v }) } }
                item { Text("Séances", style = MaterialTheme.typography.titleLarge) }
            } else {
                item { Text("Aucune séance pour l'instant. Utilise un outil plus d'une minute ou ajoute une séance.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(sessions.take(200), key = { it.id }) { s ->
                SessionRow(s, onDelete = {
                    scope.launch {
                        c.db.practice().delete(s); c.onPracticeChanged()
                        val r = snackbar.showSnackbar("Séance supprimée", actionLabel = "Annuler")
                        if (r == SnackbarResult.ActionPerformed) { c.db.practice().insert(s); c.onPracticeChanged() }
                    }
                })
            }
        }
    }

    if (adding) AddSessionSheet(settings.instrument, onDismiss = { adding = false }) { s ->
        adding = false
        scope.launch { c.db.practice().insert(s); c.onPracticeChanged() }
    }
}

@Composable
private fun Section(title: String, index: Int, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().cascadeIn(index), shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

private val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.FRANCE)

@Composable
private fun SessionRow(s: PracticeSession, onDelete: () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Box(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Rounded.Delete, "Supprimer", tint = MaterialTheme.colorScheme.error)
            }
        },
    ) {
        Card(shape = MaterialTheme.shapes.large) {
            ListItem(
                headlineContent = { Text(PracticeActivity.of(s.activity).label + " · " + formatMinutes(s.durationSec / 60)) },
                supportingContent = {
                    val date = Instant.ofEpochMilli(s.startEpochMs).atZone(ZoneId.systemDefault()).format(dateFmt)
                    Text(listOfNotNull(date, runCatching { Instrument.valueOf(s.instrument).label }.getOrNull(), s.note.ifBlank { null }).joinToString(" · "))
                },
                trailingContent = { if (!s.auto) Text("manuel", style = MaterialTheme.typography.labelSmall) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}

@Composable
private fun AddSessionSheet(defaultInstrument: Instrument, onDismiss: () -> Unit, onSave: (PracticeSession) -> Unit) {
    var instrument by remember { mutableStateOf(defaultInstrument) }
    var activity by remember { mutableStateOf(PracticeActivity.FREE) }
    var minutes by remember { mutableFloatStateOf(20f) }
    var daysAgo by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Nouvelle séance", style = MaterialTheme.typography.headlineSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Instrument.entries.forEach { FilterChip(selected = it == instrument, onClick = { instrument = it }, label = { Text(it.label) }) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PracticeActivity.entries.forEach { FilterChip(selected = it == activity, onClick = { activity = it }, label = { Text(it.label) }) }
            }
            Text("Durée : ${minutes.roundToInt()} min", style = MaterialTheme.typography.titleMedium)
            Slider(value = minutes, onValueChange = { minutes = (it / 5).roundToInt() * 5f }, valueRange = 5f..180f)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Aujourd'hui", "Hier", "Avant-hier").forEachIndexed { i, l -> FilterChip(selected = daysAgo == i, onClick = { daysAgo = i }, label = { Text(l) }) }
            }
            OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                val start = System.currentTimeMillis() - daysAgo * 86_400_000L - (minutes * 60_000).toLong()
                onSave(PracticeSession(startEpochMs = start, durationSec = minutes.roundToInt() * 60, instrument = instrument.name,
                    activity = activity.name, note = note.trim(), auto = false))
            }, modifier = Modifier.fillMaxWidth()) { Text("Enregistrer") }
        }
    }
}
