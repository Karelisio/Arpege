package com.karelisio.arpege.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.arpege.BuildConfig
import com.karelisio.arpege.data.AppSettings
import com.karelisio.arpege.data.container
import com.karelisio.arpege.theory.Instrument
import com.karelisio.arpege.theory.Notation
import com.karelisio.arpege.ui.components.ToolScaffold
import com.karelisio.arpege.update.UpdateState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(onBack: () -> Unit, onPermissions: () -> Unit) {
    val c = LocalContext.current.container
    val s by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val update by c.updates.state.collectAsState()
    val scope = rememberCoroutineScope()

    ToolScaffold(title = "Réglages", onBack = onBack) { p ->
        Column(
            Modifier.padding(p).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Group("Notation") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Notation.entries.forEachIndexed { i, n ->
                        SegmentedButton(
                            selected = s.notation == n,
                            onClick = { scope.launch { c.settings.setNotation(n) } },
                            shape = SegmentedButtonDefaults.itemShape(i, Notation.entries.size),
                        ) { Text(if (n == Notation.LATIN) "Do Ré Mi" else "C D E") }
                    }
                }
            }
            Group("Instrument principal") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Instrument.entries.forEachIndexed { i, ins ->
                        SegmentedButton(
                            selected = s.instrument == ins,
                            onClick = { scope.launch { c.settings.setInstrument(ins) } },
                            shape = SegmentedButtonDefaults.itemShape(i, Instrument.entries.size),
                        ) { Text(ins.label, maxLines = 1) }
                    }
                }
            }
            Group("La4 de référence") {
                var a4 by remember { mutableFloatStateOf(s.a4) }
                LaunchedEffect(s.a4) { a4 = s.a4 }
                Text("${a4.roundToInt()} Hz", style = MaterialTheme.typography.headlineMedium)
                Slider(
                    value = a4, onValueChange = { a4 = it.roundToInt().toFloat() },
                    onValueChangeFinished = { scope.launch { c.settings.setA4(a4) } },
                    valueRange = 432f..446f, steps = 13,
                )
            }
            Group("Objectif hebdomadaire") {
                var goal by remember { mutableFloatStateOf(s.weeklyGoalMinutes.toFloat()) }
                LaunchedEffect(s.weeklyGoalMinutes) { goal = s.weeklyGoalMinutes.toFloat() }
                Text("${goal.roundToInt()} min / semaine", style = MaterialTheme.typography.titleLarge)
                Slider(
                    value = goal, onValueChange = { goal = (it / 10).roundToInt() * 10f },
                    onValueChangeFinished = { scope.launch { c.settings.setWeeklyGoal(goal.roundToInt()) } },
                    valueRange = 30f..1200f,
                )
            }
            Card(shape = MaterialTheme.shapes.large) {
                ListItem(
                    headlineContent = { Text("Vibration sur le temps fort") },
                    supportingContent = { Text("Métronome") },
                    trailingContent = {
                        Switch(checked = s.vibrateDownbeat, onCheckedChange = { v -> scope.launch { c.settings.setVibrate(v) } })
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                ListItem(
                    modifier = Modifier.clickable(onClick = onPermissions),
                    headlineContent = { Text("Autorisations") },
                    supportingContent = { Text("Micro, notifications, batterie, installation") },
                    leadingContent = { Icon(Icons.Rounded.Shield, null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                ListItem(
                    modifier = Modifier.clickable { c.updates.checkIfDue(force = true) },
                    headlineContent = { Text("Rechercher une mise à jour") },
                    supportingContent = {
                        Text(
                            when (update) {
                                UpdateState.Checking -> "Vérification…"
                                UpdateState.UpToDate -> "Arpège est à jour (${BuildConfig.VERSION_NAME})"
                                is UpdateState.Failed -> (update as UpdateState.Failed).message
                                else -> "Version ${BuildConfig.VERSION_NAME}"
                            },
                        )
                    },
                    leadingContent = { Icon(Icons.Rounded.SystemUpdate, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            Text(
                "Arpège ${BuildConfig.VERSION_NAME} · locale, sans compte, hors ligne.\n" +
                    "Police musicale Bravura © Steinberg, SIL Open Font License 1.1.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

