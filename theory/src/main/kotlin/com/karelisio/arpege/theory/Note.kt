package com.karelisio.arpege.theory

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

internal fun mod12(n: Int) = ((n % 12) + 12) % 12

/** Les sept lettres de note, avec leur classe de hauteur naturelle. */
enum class Letter(val pc: Int, val latin: String) {
    C(0, "Do"), D(2, "Ré"), E(4, "Mi"), F(5, "Fa"), G(7, "Sol"), A(9, "La"), B(11, "Si");

    fun step(n: Int): Letter = entries[((ordinal + n) % 7 + 7) % 7]
}

/** Note orthographiée (lettre + altération), sans octave. Ex. Fa♯, Si♭. */
data class Note(val letter: Letter, val accidental: Int = 0) {
    val pitchClass: Int get() = mod12(letter.pc + accidental)

    fun name(notation: Notation = Notation.ENGLISH, ascii: Boolean = false): String {
        val base = if (notation == Notation.LATIN) letter.latin else letter.name
        return base + accidentalSymbol(accidental, ascii)
    }

    override fun toString() = name(Notation.ENGLISH, ascii = true)

    /** Transposition orthographiée par intervalle (C + tierce majeure = E, pas Fb). */
    operator fun plus(interval: Interval): Note {
        val steps = interval.degree - 1
        val idx = letter.ordinal + steps
        val target = Letter.entries[idx % 7]
        val natural = target.pc + 12 * (idx / 7) - letter.pc
        return Note(target, accidental + interval.semitones - natural)
    }

    /** Descend d'un intervalle. */
    operator fun minus(interval: Interval): Note {
        val target = letter.step(-(interval.degree - 1))
        // On cherche l'altération telle que target + interval == this.
        val probe = Note(target, 0) + interval
        return Note(target, accidental - probe.accidental)
    }

    /** Orthographe la plus simple (sans double altération ni Mi♯/Si♯/Fa♭/Do♭). */
    fun simplified(preferFlats: Boolean = accidental < 0): Note = fromPitchClass(pitchClass, preferFlats)

    fun isEnharmonic(other: Note) = pitchClass == other.pitchClass

    /** Transposition par demi-tons avec choix d'orthographe. */
    fun transpose(semitones: Int, preferFlats: Boolean = accidental < 0): Note =
        fromPitchClass(pitchClass + semitones, preferFlats)

    companion object {
        private val SHARPS = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        private val FLATS = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

        /** Orthographe usuelle des fondamentales d'accord : C C# D Eb E F F# G Ab A Bb B. */
        private val COMMON = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")

        fun fromPitchClass(pc: Int, preferFlats: Boolean = false): Note =
            parse((if (preferFlats) FLATS else SHARPS)[mod12(pc)])!!

        fun common(pc: Int): Note = parse(COMMON[mod12(pc)])!!

        private val latinNames = listOf("Do" to Letter.C, "Ré" to Letter.D, "Re" to Letter.D, "Mi" to Letter.E,
            "Fa" to Letter.F, "Sol" to Letter.G, "La" to Letter.A, "Si" to Letter.B)

        /** Analyse « C#», « Bb », « Fa♯ », « Ré », « Ebb »… Retourne la note et la longueur lue. */
        fun parsePrefix(s: String): Pair<Note, Int>? {
            if (s.isEmpty()) return null
            var letter: Letter? = null
            var i = 0
            val latin = latinNames.firstOrNull { s.startsWith(it.first, ignoreCase = true) }
            // « Fadd9 » / « Faug » sont des accords anglais en Fa, pas « Fa » + « dd9 ».
            if (latin != null) {
                val rest = s.substring(latin.first.length)
                val englishFa = latin.first == "Fa" && (rest.startsWith("dd") || rest.startsWith("ug"))
                if (!englishFa) { letter = latin.second; i = latin.first.length }
            }
            if (letter == null) {
                val c = s[0].uppercaseChar()
                letter = Letter.entries.firstOrNull { it.name[0] == c } ?: return null
                i = 1
            }
            var acc = 0
            loop@ while (i < s.length) {
                when (s[i]) {
                    '#', '♯' -> acc++
                    'b', '♭' -> acc--
                    else -> break@loop
                }
                i++
            }
            return Note(letter, acc) to i
        }

        fun parse(s: String): Note? = parsePrefix(s.trim())?.takeIf { it.second == s.trim().length }?.first
    }
}

fun accidentalSymbol(acc: Int, ascii: Boolean = false): String = when {
    acc == 0 -> ""
    ascii && acc > 0 -> "#".repeat(acc)
    ascii -> "b".repeat(-acc)
    acc == 2 -> "𝄪"
    acc == -2 -> "𝄫"
    acc > 0 -> "♯".repeat(acc)
    else -> "♭".repeat(-acc)
}

/** Hauteur absolue : note orthographiée + octave scientifique (La4 = 440 Hz). */
data class Pitch(val note: Note, val octave: Int) : Comparable<Pitch> {
    val midi: Int get() = (octave + 1) * 12 + note.letter.pc + note.accidental

    fun frequency(a4: Double = 440.0): Double = midiToFrequency(midi.toDouble(), a4)

    operator fun plus(interval: Interval): Pitch {
        val n = note + interval
        // L'octave se déduit de la hauteur MIDI attendue.
        val targetMidi = midi + interval.semitones
        val oct = (targetMidi - n.letter.pc - n.accidental) / 12 - 1
        return Pitch(n, oct)
    }

    fun name(notation: Notation = Notation.ENGLISH) = note.name(notation) + octave

    override fun compareTo(other: Pitch) = midi.compareTo(other.midi)
    override fun toString() = note.toString() + octave

    companion object {
        fun fromMidi(midi: Int, preferFlats: Boolean = false): Pitch {
            val n = Note.fromPitchClass(midi, preferFlats)
            return Pitch(n, (midi - n.letter.pc - n.accidental) / 12 - 1)
        }

        fun parse(s: String): Pitch? {
            val (n, len) = Note.parsePrefix(s) ?: return null
            val oct = s.substring(len).toIntOrNull() ?: return null
            return Pitch(n, oct)
        }
    }
}

fun midiToFrequency(midi: Double, a4: Double = 440.0): Double = a4 * 2.0.pow((midi - 69.0) / 12.0)
fun frequencyToMidi(freq: Double, a4: Double = 440.0): Double = 69.0 + 12.0 * log2(freq / a4)

/** Résultat d'analyse d'une fréquence : note la plus proche + écart en cents. */
data class NoteReading(val midi: Int, val cents: Double, val frequency: Double) {
    val pitch: Pitch get() = Pitch.fromMidi(midi)
}

fun readFrequency(freq: Double, a4: Double = 440.0): NoteReading {
    val m = frequencyToMidi(freq, a4)
    val nearest = m.roundToInt()
    return NoteReading(nearest, (m - nearest) * 100.0, freq)
}
