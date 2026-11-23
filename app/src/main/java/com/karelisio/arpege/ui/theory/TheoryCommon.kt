package com.karelisio.arpege.ui.theory

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.karelisio.arpege.theory.Instrument
import com.karelisio.arpege.theory.Notation
import com.karelisio.arpege.theory.Note
import com.karelisio.arpege.ui.components.rememberHaptics
import com.karelisio.arpege.ui.theme.LocalReduceMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun NotePicker(selectedPc: Int, notation: Notation, onSelect: (Note) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0 until 12).forEach { pc ->
            val n = Note.common(pc)
            FilterChip(selected = pc == selectedPc, onClick = { haptics.tick(); onSelect(n) }, label = { Text(n.name(notation)) })
        }
    }
}

@Composable
fun InstrumentPicker(selected: Instrument, onSelect: (Instrument) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier) {
        Instrument.entries.forEachIndexed { i, ins ->
            SegmentedButton(
                selected = ins == selected, onClick = { onSelect(ins) },
                shape = SegmentedButtonDefaults.itemShape(i, Instrument.entries.size),
            ) { Text(ins.label, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

/** Couleurs par degré (tonique = primaire). Renvoie (fond, texte). */
@Composable
fun degreeColors(): List<Pair<Color, Color>> {
    val s = MaterialTheme.colorScheme
    return listOf(
        s.primary to s.onPrimary,
        s.tertiaryContainer to s.onTertiaryContainer,
        s.secondary to s.onSecondary,
        s.primaryContainer to s.onPrimaryContainer,
        s.tertiary to s.onTertiary,
        s.secondaryContainer to s.onSecondaryContainer,
        s.inverseSurface to s.inverseOnSurface,
    )
}

/** Animation d'arpège : nombre de notes allumées, synchronisé avec le son. */
class Cascade(private val scope: CoroutineScope, private val reduce: Boolean) {
    val anim = Animatable(Float.MAX_VALUE)
    val value: Float get() = anim.value

    fun run(count: Int, stepMs: Int) {
        scope.launch {
            if (reduce) { anim.snapTo(Float.MAX_VALUE); return@launch }
            anim.snapTo(0f)
            anim.animateTo(count.toFloat() + 1f, tween((count + 1) * stepMs, easing = LinearEasing))
            anim.snapTo(Float.MAX_VALUE)
        }
    }

    /** Index de la note en cours (pour surligner), ou null. */
    fun current(): Int? = value.takeIf { it < 1e6f }?.toInt()
}

@Composable
fun rememberCascade(): Cascade {
    val scope = rememberCoroutineScope()
    val reduce = LocalReduceMotion.current
    return remember { Cascade(scope, reduce) }
}
