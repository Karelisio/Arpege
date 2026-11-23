package com.karelisio.arpege.theory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScaleKeyTest {
    private val tonics = listOf("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B").map { Note.parse(it)!! }

    @Test fun allScalesOnAllTwelveTonics() {
        for (t in tonics) for (type in ScaleType.entries) {
            val s = Scale(t, type)
            assertEquals("$t $type", type.intervals.map { mod12(t.pitchClass + it.semitones) }, s.pitchClasses)
            if (type.isHeptatonic) assertEquals("$t $type lettres", 7, s.notes.map { it.letter }.toSet().size)
        }
    }

    @Test fun diatonicChordsOfMajorScales() {
        val expected = listOf(ChordType.MAJOR, ChordType.MINOR, ChordType.MINOR, ChordType.MAJOR, ChordType.MAJOR, ChordType.MINOR, ChordType.DIM)
        for (t in tonics) assertEquals("$t", expected, Scale(t, ScaleType.MAJOR).diatonicChords().map { it.chord.type })
        assertEquals("C Dm Em F G Am Bdim", Scale(Note(Letter.C), ScaleType.MAJOR).diatonicChords().joinToString(" ") { it.chord.toString() })
        assertEquals(
            listOf("Imaj7", "ii7", "iii7", "IVmaj7", "V7", "vi7", "viiø7"),
            Scale(Note(Letter.G), ScaleType.MAJOR).diatonicChords(sevenths = true).map { it.roman },
        )
        assertEquals("Am Bdim C Dm Em F G", Scale(Note(Letter.A), ScaleType.NATURAL_MINOR).diatonicChords().joinToString(" ") { it.chord.toString() })
    }

    @Test fun circleOfFifthsSignatures() {
        val majors = listOf("C", "G", "D", "A", "E", "B", "F#", "Db", "Ab", "Eb", "Bb", "F")
        val minors = listOf("A", "E", "B", "F#", "C#", "G#", "D#", "Bb", "F", "C", "G", "D")
        val counts = listOf(0, 1, 2, 3, 4, 5, 6, -5, -4, -3, -2, -1)
        for (i in 0 until 12) {
            val k = CircleOfFifths.majorAt(i)
            assertEquals(majors[i], k.tonic.toString())
            assertEquals(counts[i], k.fifths)
            assertEquals(minors[i], CircleOfFifths.minorAt(i).tonic.toString())
            assertEquals(counts[i], CircleOfFifths.minorAt(i).fifths)
        }
        assertEquals("F# C# G#", Key(Note(Letter.A)).signature.joinToString(" "))
        assertEquals("Bb Eb Ab Db", Key(Note(Letter.A, -1)).signature.joinToString(" "))
        assertEquals(7, Key(Note(Letter.C, 1)).fifths)
        assertEquals(-7, Key(Note(Letter.C, -1)).fifths)
    }

    @Test fun signatureMatchesScaleAccidentals() {
        for (f in -7..7) {
            val key = Key.majorFromFifths(f)
            val altered = key.scale.notes.filter { it.accidental != 0 }.toSet()
            assertEquals("$key", key.signature.toSet(), altered)
            assertTrue(key.relative.minor)
            assertEquals(f, key.relative.fifths)
        }
    }
}
