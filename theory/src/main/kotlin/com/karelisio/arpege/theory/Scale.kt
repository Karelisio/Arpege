package com.karelisio.arpege.theory

import com.karelisio.arpege.theory.Interval.Companion.A4
import com.karelisio.arpege.theory.Interval.Companion.M2
import com.karelisio.arpege.theory.Interval.Companion.M3
import com.karelisio.arpege.theory.Interval.Companion.M6
import com.karelisio.arpege.theory.Interval.Companion.M7
import com.karelisio.arpege.theory.Interval.Companion.P1
import com.karelisio.arpege.theory.Interval.Companion.P4
import com.karelisio.arpege.theory.Interval.Companion.P5
import com.karelisio.arpege.theory.Interval.Companion.d5
import com.karelisio.arpege.theory.Interval.Companion.m2
import com.karelisio.arpege.theory.Interval.Companion.m3
import com.karelisio.arpege.theory.Interval.Companion.m6
import com.karelisio.arpege.theory.Interval.Companion.m7

enum class ScaleType(val label: String, val intervals: List<Interval>) {
    MAJOR("Majeure", listOf(P1, M2, M3, P4, P5, M6, M7)),
    NATURAL_MINOR("Mineure naturelle", listOf(P1, M2, m3, P4, P5, m6, m7)),
    HARMONIC_MINOR("Mineure harmonique", listOf(P1, M2, m3, P4, P5, m6, M7)),
    MELODIC_MINOR("Mineure mélodique", listOf(P1, M2, m3, P4, P5, M6, M7)),
    IONIAN("Ionien", listOf(P1, M2, M3, P4, P5, M6, M7)),
    DORIAN("Dorien", listOf(P1, M2, m3, P4, P5, M6, m7)),
    PHRYGIAN("Phrygien", listOf(P1, m2, m3, P4, P5, m6, m7)),
    LYDIAN("Lydien", listOf(P1, M2, M3, A4, P5, M6, M7)),
    MIXOLYDIAN("Mixolydien", listOf(P1, M2, M3, P4, P5, M6, m7)),
    AEOLIAN("Éolien", listOf(P1, M2, m3, P4, P5, m6, m7)),
    LOCRIAN("Locrien", listOf(P1, m2, m3, P4, d5, m6, m7)),
    MAJOR_PENTATONIC("Pentatonique majeure", listOf(P1, M2, M3, P5, M6)),
    MINOR_PENTATONIC("Pentatonique mineure", listOf(P1, m3, P4, P5, m7)),
    BLUES("Blues", listOf(P1, m3, P4, d5, P5, m7));

    val isHeptatonic: Boolean get() = intervals.size == 7
}

data class DiatonicChord(val degree: Int, val roman: String, val chord: Chord)

data class Scale(val tonic: Note, val type: ScaleType) {
    val notes: List<Note> get() = type.intervals.map { tonic + it }
    val pitchClasses: List<Int> get() = notes.map { it.pitchClass }

    /** Degré (0-based) d'une classe de hauteur dans la gamme, ou null. */
    fun degreeOf(pc: Int): Int? = pitchClasses.indexOf(mod12(pc)).takeIf { it >= 0 }

    /** Gamme montante puis descendante, depuis une octave donnée. */
    fun ascendingPitches(octave: Int = 4): List<Pitch> {
        val start = Pitch(tonic, octave)
        return type.intervals.map { start + it } + (start + Interval.P8)
    }

    /** Accords diatoniques (triades ou tétrades) pour les gammes heptatoniques. */
    fun diatonicChords(sevenths: Boolean = false): List<DiatonicChord> {
        if (!type.isHeptatonic) return emptyList()
        val ns = notes
        return (0 until 7).mapNotNull { i ->
            val stack = (0 until if (sevenths) 4 else 3).map { k -> ns[(i + 2 * k) % 7] }
            val rootPc = stack[0].pitchClass
            val semis = stack.map { mod12(it.pitchClass - rootPc) }
            val chordType = ChordType.fromSemitones(semis) ?: return@mapNotNull null
            DiatonicChord(i + 1, roman(i + 1, chordType), Chord(stack[0], chordType))
        }
    }

    companion object {
        private val ROMANS = listOf("I", "II", "III", "IV", "V", "VI", "VII")

        fun roman(degree: Int, t: ChordType): String {
            val r = ROMANS[degree - 1]
            return when (t) {
                ChordType.MAJOR -> r
                ChordType.MINOR -> r.lowercase()
                ChordType.DIM -> r.lowercase() + "°"
                ChordType.AUG -> "$r+"
                ChordType.DOM7 -> "${r}7"
                ChordType.MAJ7 -> "${r}maj7"
                ChordType.MIN7 -> r.lowercase() + "7"
                ChordType.M7B5 -> r.lowercase() + "ø7"
                ChordType.DIM7 -> r.lowercase() + "°7"
                ChordType.MIN_MAJ7 -> r.lowercase() + "maj7"
                else -> r + t.suffix
            }
        }
    }
}
