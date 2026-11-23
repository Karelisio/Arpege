package com.karelisio.arpege.theory.training

import kotlin.math.abs
import kotlin.random.Random

/** Valeurs rythmiques (48 ticks par noire). */
enum class NoteValue(val ticks: Int) { WHOLE(192), HALF(96), QUARTER(48), EIGHTH(24), SIXTEENTH(12) }

data class RhythmElement(val value: NoteValue, val rest: Boolean = false, val dotted: Boolean = false, val triplet: Boolean = false) {
    val ticks: Int get() = when {
        triplet -> value.ticks * 2 / 3
        dotted -> value.ticks * 3 / 2
        else -> value.ticks
    }
}

/** Mesure à 4/4 générée par niveau. */
data class RhythmBar(val elements: List<RhythmElement>) {
    val totalTicks: Int get() = elements.sumOf { it.ticks }

    /** Position (ticks) de chaque élément. */
    val starts: List<Int> get() = elements.runningFold(0) { a, e -> a + e.ticks }.dropLast(1)

    /** Attaques à taper (les silences ne comptent pas). */
    val onsets: List<Int> get() = elements.indices.filter { !elements[it].rest }.map { starts[it] }
}

data class TapResult(val deviationsMs: List<Double?>, val extraTaps: Int, val accuracy: Int)

object RhythmTrainer {
    const val TICKS_PER_BEAT = 48
    const val BAR_TICKS = 192
    const val LEVELS = 4

    private fun n(v: NoteValue, dotted: Boolean = false) = RhythmElement(v, dotted = dotted)
    private fun r(v: NoteValue) = RhythmElement(v, rest = true)
    private fun t() = RhythmElement(NoteValue.EIGHTH, triplet = true)

    private data class Cell(val beats: Int, val elements: List<RhythmElement>, val level: Int)

    private val cells = listOf(
        Cell(1, listOf(n(NoteValue.QUARTER)), 1),
        Cell(2, listOf(n(NoteValue.HALF)), 1),
        Cell(4, listOf(n(NoteValue.WHOLE)), 1),
        Cell(1, listOf(r(NoteValue.QUARTER)), 2),
        Cell(1, listOf(n(NoteValue.EIGHTH), n(NoteValue.EIGHTH)), 2),
        Cell(2, listOf(r(NoteValue.HALF)), 2),
        Cell(2, listOf(n(NoteValue.QUARTER, dotted = true), n(NoteValue.EIGHTH)), 3),
        Cell(1, List(4) { n(NoteValue.SIXTEENTH) }, 3),
        Cell(1, listOf(n(NoteValue.EIGHTH), n(NoteValue.SIXTEENTH), n(NoteValue.SIXTEENTH)), 3),
        Cell(1, listOf(n(NoteValue.SIXTEENTH), n(NoteValue.SIXTEENTH), n(NoteValue.EIGHTH)), 3),
        Cell(1, listOf(r(NoteValue.EIGHTH), n(NoteValue.EIGHTH)), 3),
        Cell(1, listOf(t(), t(), t()), 4),
        Cell(1, listOf(n(NoteValue.EIGHTH, dotted = true), n(NoteValue.SIXTEENTH)), 4),
        Cell(1, listOf(n(NoteValue.SIXTEENTH), n(NoteValue.EIGHTH), n(NoteValue.SIXTEENTH)), 4),
    )

    /** Génère une mesure ; les cellules de 2 temps commencent sur un temps impair, la ronde seule. */
    fun generate(level: Int, random: Random): RhythmBar {
        val available = cells.filter { it.level <= level }
        while (true) {
            val out = mutableListOf<RhythmElement>()
            var beat = 0
            while (beat < 4) {
                val fits = available.filter { c ->
                    beat + c.beats <= 4 && (c.beats != 2 || beat % 2 == 0) && (c.beats != 4 || beat == 0)
                }
                // Les nouvelles cellules du niveau ont plus de chances d'apparaître.
                val weighted = fits.flatMap { c -> List(if (c.level == level) 3 else 1) { c } }
                val c = weighted[random.nextInt(weighted.size)]
                out += c.elements
                beat += c.beats
            }
            val bar = RhythmBar(out)
            if (bar.onsets.size >= 2 || level == 1 && bar.onsets.isNotEmpty()) return bar
        }
    }

    fun ticksToMs(ticks: Int, bpm: Int): Double = ticks * 60000.0 / (bpm * TICKS_PER_BEAT)

    /**
     * Associe chaque attaque attendue au tap le plus proche (non utilisé) dans la fenêtre
     * de tolérance. Précision = moyenne des scores par note (1 − |écart| / tolérance), moins les taps en trop.
     */
    fun score(expectedMs: List<Double>, tapsMs: List<Double>, toleranceMs: Double = 140.0): TapResult {
        val used = BooleanArray(tapsMs.size)
        val devs = expectedMs.map { e ->
            var best = -1
            for (i in tapsMs.indices) {
                if (used[i]) continue
                val d = abs(tapsMs[i] - e)
                if (d <= toleranceMs && (best < 0 || d < abs(tapsMs[best] - e))) best = i
            }
            if (best >= 0) { used[best] = true; tapsMs[best] - e } else null
        }
        val extra = used.count { !it }
        val per = devs.map { d -> if (d == null) 0.0 else (1 - abs(d) / toleranceMs).coerceIn(0.0, 1.0) }
        val base = if (per.isEmpty()) 0.0 else per.average()
        val penalty = extra * 0.1
        return TapResult(devs, extra, (100 * (base - penalty)).coerceIn(0.0, 100.0).toInt())
    }
}
