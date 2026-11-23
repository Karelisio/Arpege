package com.karelisio.arpege.theory

import com.karelisio.arpege.theory.Interval.Companion.A5
import com.karelisio.arpege.theory.Interval.Companion.M2
import com.karelisio.arpege.theory.Interval.Companion.M3
import com.karelisio.arpege.theory.Interval.Companion.M6
import com.karelisio.arpege.theory.Interval.Companion.M7
import com.karelisio.arpege.theory.Interval.Companion.M9
import com.karelisio.arpege.theory.Interval.Companion.P1
import com.karelisio.arpege.theory.Interval.Companion.P4
import com.karelisio.arpege.theory.Interval.Companion.P5
import com.karelisio.arpege.theory.Interval.Companion.d5
import com.karelisio.arpege.theory.Interval.Companion.d7
import com.karelisio.arpege.theory.Interval.Companion.m3
import com.karelisio.arpege.theory.Interval.Companion.m7

/** Types d'accords générés à partir de leurs intervalles. */
enum class ChordType(
    val suffix: String,
    val label: String,
    val intervals: List<Interval>,
    val aliases: List<String> = emptyList(),
) {
    MAJOR("", "Majeur", listOf(P1, M3, P5), listOf("M", "maj")),
    MINOR("m", "Mineur", listOf(P1, m3, P5), listOf("min", "-", "mi")),
    POWER("5", "Quinte (power chord)", listOf(P1, P5)),
    DOM7("7", "Septième", listOf(P1, M3, P5, m7), listOf("dom7")),
    MAJ7("maj7", "Septième majeure", listOf(P1, M3, P5, M7), listOf("M7", "Δ", "Δ7", "ma7", "7M")),
    MIN7("m7", "Mineur septième", listOf(P1, m3, P5, m7), listOf("min7", "-7", "mi7")),
    MIN_MAJ7("mMaj7", "Mineur septième majeure", listOf(P1, m3, P5, M7), listOf("m(maj7)", "mM7", "m7M")),
    SUS2("sus2", "Suspendu 2", listOf(P1, M2, P5), listOf("2")),
    SUS4("sus4", "Suspendu 4", listOf(P1, P4, P5), listOf("sus", "4")),
    SEVEN_SUS4("7sus4", "Septième sus4", listOf(P1, P4, P5, m7), listOf("7sus")),
    DIM("dim", "Diminué", listOf(P1, m3, d5), listOf("°", "o")),
    DIM7("dim7", "Septième diminuée", listOf(P1, m3, d5, d7), listOf("°7", "o7")),
    M7B5("m7b5", "Demi-diminué", listOf(P1, m3, d5, m7), listOf("ø", "ø7", "m7(b5)", "m7-5")),
    AUG("aug", "Augmenté", listOf(P1, M3, A5), listOf("+", "#5", "(#5)")),
    SIX("6", "Sixte", listOf(P1, M3, P5, M6), listOf("maj6", "M6")),
    MIN6("m6", "Mineur sixte", listOf(P1, m3, P5, M6), listOf("min6", "-6")),
    ADD9("add9", "Add9", listOf(P1, M3, P5, M9), listOf("add2")),
    DOM9("9", "Neuvième", listOf(P1, M3, P5, m7, M9)),
    MAJ9("maj9", "Neuvième majeure", listOf(P1, M3, P5, M7, M9), listOf("M9", "Δ9")),
    MIN9("m9", "Mineur neuvième", listOf(P1, m3, P5, m7, M9), listOf("min9", "-9"));

    val semitones: List<Int> get() = intervals.map { it.semitones }

    /** La quinte juste peut être omise dans les accords de 4 sons ou plus. */
    val optionalIntervals: Set<Interval> get() = if (intervals.size >= 4 && P5 in intervals) setOf(P5) else emptySet()

    companion object {
        private val bySuffix: Map<String, ChordType> = buildMap {
            ChordType.entries.forEach { t -> put(t.suffix, t); t.aliases.forEach { a -> putIfAbsent(a, t) } }
        }

        fun fromSuffix(s: String): ChordType? = bySuffix[s.trim()]

        /** Reconnaît un type d'après un ensemble de demi-tons (pour les accords diatoniques). */
        fun fromSemitones(semis: List<Int>): ChordType? {
            val set = semis.map { mod12(it) }.toSet()
            return entries.firstOrNull { t -> t != POWER && t.semitones.map { mod12(it) }.toSet() == set }
        }
    }
}

/** Accord : fondamentale + type (+ basse éventuelle). */
data class Chord(val root: Note, val type: ChordType, val bass: Note? = null) {
    val notes: List<Note> get() = type.intervals.map { root + it }
    val pitchClasses: Set<Int> get() = notes.map { it.pitchClass }.toSet()

    fun name(notation: Notation = Notation.ENGLISH, ascii: Boolean = false): String =
        root.name(notation, ascii) + type.suffix + (bass?.let { "/" + it.name(notation, ascii) } ?: "")

    override fun toString() = name(Notation.ENGLISH, ascii = true)

    /** Transpose en conservant une orthographe lisible (dièses si la fondamentale en avait). */
    fun transpose(semitones: Int, preferFlats: Boolean? = null): Chord {
        if (semitones % 12 == 0) return this
        fun move(n: Note): Note = when (preferFlats ?: (n.accidental < 0)) {
            true -> n.transpose(semitones, true)
            false -> if (n.accidental > 0) n.transpose(semitones, false) else Note.common(n.pitchClass + semitones)
        }
        return Chord(move(root), type, bass?.let { move(it) })
    }

    /** Hauteurs en position fondamentale à partir d'une octave donnée (pour le piano / l'écoute). */
    fun pitches(octave: Int = 4): List<Pitch> {
        val rootPitch = Pitch(root, octave)
        val list = type.intervals.map { rootPitch + it }
        return if (bass != null) listOf(Pitch.fromMidi(rootPitch.midi - 12 + mod12(bass.pitchClass - root.pitchClass))) + list else list
    }

    companion object {
        /** Analyse « C#m7 », « Bbmaj7 », « Fa#m », « D/F# », « Ré7 ». */
        fun parse(text: String): Chord? {
            val s = text.trim()
            if (s.isEmpty()) return null
            val (root, len) = Note.parsePrefix(s) ?: return null
            var rest = s.substring(len)
            var bass: Note? = null
            val slash = rest.lastIndexOf('/')
            if (slash >= 0) {
                val b = Note.parse(rest.substring(slash + 1)) ?: return null
                bass = b
                rest = rest.substring(0, slash)
            }
            val type = ChordType.fromSuffix(rest) ?: return null
            if (root.accidental !in -2..2) return null
            return Chord(root, type, bass)
        }
    }
}
