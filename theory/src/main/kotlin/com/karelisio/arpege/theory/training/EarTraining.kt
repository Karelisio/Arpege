package com.karelisio.arpege.theory.training

import com.karelisio.arpege.theory.ChordType
import com.karelisio.arpege.theory.Interval
import com.karelisio.arpege.theory.ScaleType
import kotlin.random.Random

enum class EarMode(val label: String, val key: String) {
    INTERVALS("Intervalles", "ear_intervals"),
    CHORDS("Accords", "ear_chords"),
    SCALES("Gammes", "ear_scales"),
    CADENCES("Cadences", "ear_cadences"),
}

enum class IntervalDirection(val label: String) { UP("Montant"), DOWN("Descendant"), HARMONIC("Harmonique") }

/** Élément d'exercice : identifiant stable (stats) + libellé. */
data class EarItem(val id: String, val label: String)

/** Une question : blocs de notes MIDI joués successivement ; un bloc de plusieurs notes est plaqué. */
data class EarQuestion(val item: EarItem, val blocks: List<List<Int>>, val blockMs: Int)

enum class Cadence(val label: String, val degrees: List<Int>) {
    PERFECT("Parfaite (V–I)", listOf(1, 4, 5, 1)),
    PLAGAL("Plagale (IV–I)", listOf(1, 5, 4, 1)),
    HALF("Demi-cadence (–V)", listOf(1, 4, 1, 5)),
    DECEPTIVE("Rompue (V–vi)", listOf(1, 4, 5, 6)),
}

object EarTraining {
    private val intervalLevels = listOf(
        listOf(Interval.P4, Interval.P5, Interval.P8),
        listOf(Interval.m3, Interval.M3),
        listOf(Interval.M2, Interval.M6),
        listOf(Interval.m2, Interval.m6, Interval.m7, Interval.M7),
        listOf(Interval.A4),
    )
    private val chordLevels = listOf(
        listOf(ChordType.MAJOR, ChordType.MINOR),
        listOf(ChordType.DIM, ChordType.AUG),
        listOf(ChordType.DOM7, ChordType.MAJ7, ChordType.MIN7),
        listOf(ChordType.SUS2, ChordType.SUS4, ChordType.M7B5, ChordType.DIM7),
    )
    private val scaleLevels = listOf(
        listOf(ScaleType.MAJOR, ScaleType.NATURAL_MINOR),
        listOf(ScaleType.HARMONIC_MINOR, ScaleType.MAJOR_PENTATONIC, ScaleType.MINOR_PENTATONIC),
        listOf(ScaleType.DORIAN, ScaleType.MIXOLYDIAN, ScaleType.LYDIAN, ScaleType.PHRYGIAN),
        listOf(ScaleType.MELODIC_MINOR, ScaleType.LOCRIAN, ScaleType.BLUES),
    )
    private val cadenceLevels = listOf(listOf(Cadence.PERFECT, Cadence.HALF), listOf(Cadence.PLAGAL, Cadence.DECEPTIVE))

    fun levelCount(mode: EarMode): Int = when (mode) {
        EarMode.INTERVALS -> intervalLevels.size
        EarMode.CHORDS -> chordLevels.size
        EarMode.SCALES -> scaleLevels.size
        EarMode.CADENCES -> cadenceLevels.size
    }

    /** Tous les éléments disponibles d'un mode, dans l'ordre de difficulté. */
    fun allItems(mode: EarMode): List<EarItem> = (1..levelCount(mode)).flatMap { levelItems(mode, it) }

    /** Éléments introduits jusqu'au niveau donné (cumulatif). */
    fun itemsUpTo(mode: EarMode, level: Int): List<EarItem> = (1..level.coerceIn(1, levelCount(mode))).flatMap { levelItems(mode, it) }

    private fun levelItems(mode: EarMode, level: Int): List<EarItem> = when (mode) {
        EarMode.INTERVALS -> intervalLevels[level - 1].map { EarItem("i${it.semitones}", it.frenchName) }
        EarMode.CHORDS -> chordLevels[level - 1].map { EarItem(it.name, it.label) }
        EarMode.SCALES -> scaleLevels[level - 1].map { EarItem(it.name, it.label) }
        EarMode.CADENCES -> cadenceLevels[level - 1].map { EarItem(it.name, it.label) }
    }

    /**
     * Choix pondéré : les éléments souvent ratés ou peu vus reviennent plus souvent.
     * [stats] : id → (réussites, tentatives).
     */
    fun pick(items: List<EarItem>, stats: Map<String, Pair<Int, Int>>, random: Random, avoid: String? = null): EarItem {
        val pool = if (items.size > 1 && avoid != null) items.filter { it.id != avoid } else items
        val weights = pool.map { item ->
            val (ok, n) = stats[item.id] ?: (0 to 0)
            val accuracy = if (n == 0) 0.5 else ok.toDouble() / n
            0.25 + (1 - accuracy) * 2 + if (n < 3) 0.75 else 0.0
        }
        var r = random.nextDouble() * weights.sum()
        pool.forEachIndexed { i, item -> r -= weights[i]; if (r <= 0) return item }
        return pool.last()
    }

    fun question(mode: EarMode, item: EarItem, random: Random, direction: IntervalDirection = IntervalDirection.UP): EarQuestion =
        when (mode) {
            EarMode.INTERVALS -> {
                val semis = item.id.removePrefix("i").toInt()
                val low = random.nextInt(48, 67)
                when (direction) {
                    IntervalDirection.UP -> EarQuestion(item, listOf(listOf(low), listOf(low + semis)), 750)
                    IntervalDirection.DOWN -> EarQuestion(item, listOf(listOf(low + semis), listOf(low)), 750)
                    IntervalDirection.HARMONIC -> EarQuestion(item, listOf(listOf(low, low + semis)), 1500)
                }
            }
            EarMode.CHORDS -> {
                val t = ChordType.valueOf(item.id)
                val root = random.nextInt(50, 63)
                val notes = t.semitones.map { root + it }
                // Arpégé puis plaqué, pour aider l'oreille.
                EarQuestion(item, notes.map { listOf(it) } + listOf(notes), 420)
            }
            EarMode.SCALES -> {
                val t = ScaleType.valueOf(item.id)
                val root = random.nextInt(55, 66)
                EarQuestion(item, (t.semitones() + 12).map { listOf(root + it) }, 330)
            }
            EarMode.CADENCES -> {
                val c = Cadence.valueOf(item.id)
                val tonic = random.nextInt(55, 63)
                val major = intArrayOf(0, 2, 4, 5, 7, 9, 11)
                val blocks = c.degrees.map { d ->
                    val chordTones = listOf(0, 2, 4).map { k ->
                        val idx = d - 1 + k
                        tonic + major[idx % 7] + 12 * (idx / 7)
                    }
                    // Basse à l'octave inférieure pour asseoir l'harmonie.
                    listOf(chordTones[0] - 12) + chordTones
                }
                EarQuestion(item, blocks, 900)
            }
        }

    private fun ScaleType.semitones(): List<Int> = intervals.map { it.semitones }
}
