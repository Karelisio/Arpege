package com.karelisio.arpege.ui.training

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.karelisio.arpege.data.db.ExerciseStat
import com.karelisio.arpege.ui.components.AnimatedCounter
import com.karelisio.arpege.ui.theme.LocalReduceMotion

enum class AnswerState { NEUTRAL, CORRECT, WRONG, REVEAL }

@Composable
fun ScoreHeader(correct: Int, total: Int, streak: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), shape = MaterialTheme.shapes.extraLarge) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceAround) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row { AnimatedCounter(correct, style = MaterialTheme.typography.headlineMedium); Text(" / $total", style = MaterialTheme.typography.headlineMedium) }
                Text("Score", style = MaterialTheme.typography.labelMedium)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocalFireDepartment, null, tint = MaterialTheme.colorScheme.primary)
                    AnimatedCounter(streak, style = MaterialTheme.typography.headlineMedium)
                }
                Text("Série", style = MaterialTheme.typography.labelMedium)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedCounter(if (total == 0) 0 else correct * 100 / total, style = MaterialTheme.typography.headlineMedium, suffix = " %")
                Text("Précision", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Bouton de réponse : vert si juste, secousse si faux, contour si c'était la bonne réponse. */
@Composable
fun AnswerButton(label: String, state: AnswerState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val reduce = LocalReduceMotion.current
    val shake = remember { Animatable(0f) }
    LaunchedEffect(state) {
        if (state == AnswerState.WRONG && !reduce) {
            shake.animateTo(0f, keyframes { durationMillis = 360; -14f at 60; 14f at 120; -10f at 180; 8f at 240; -4f at 300 })
        }
    }
    val container by animateColorAsState(
        when (state) {
            AnswerState.CORRECT, AnswerState.REVEAL -> MaterialTheme.colorScheme.tertiary
            AnswerState.WRONG -> MaterialTheme.colorScheme.error
            AnswerState.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        label = "answer",
    )
    val content by animateColorAsState(
        when (state) {
            AnswerState.CORRECT, AnswerState.REVEAL -> MaterialTheme.colorScheme.onTertiary
            AnswerState.WRONG -> MaterialTheme.colorScheme.onError
            AnswerState.NEUTRAL -> MaterialTheme.colorScheme.onSurface
        },
        label = "answerText",
    )
    Button(
        onClick = onClick,
        modifier = modifier.graphicsLayer { translationX = shake.value },
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) { Text(label, maxLines = 2) }
}

/** Stats par élément, du moins réussi au plus réussi. */
@Composable
fun ItemStats(stats: List<ExerciseStat>, label: (String) -> String, onReset: () -> Unit) {
    if (stats.isEmpty()) return
    Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("À retravailler en priorité", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onReset) { Text("Réinitialiser") }
            }
            stats.filter { it.attempts > 0 }.sortedBy { it.correct.toFloat() / it.attempts }.take(8).forEach { s ->
                val acc = s.correct.toFloat() / s.attempts
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label(s.item), Modifier.width(140.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(acc).clip(RoundedCornerShape(6.dp))
                            .background(if (acc < 0.6f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary))
                    }
                    Text("${(acc * 100).toInt()} % (${s.attempts})", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
