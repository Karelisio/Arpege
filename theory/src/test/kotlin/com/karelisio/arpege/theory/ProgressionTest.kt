package com.karelisio.arpege.theory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionTest {
    private fun names(id: String, tonic: String, minor: Boolean = false): List<String> =
        Progressions.byId(id)!!.chords(Key(Note.parse(tonic)!!, minor)).map { it.toString() }

    @Test fun popInG() {
        assertEquals(listOf("G", "D", "Em", "C"), names("pop", "G"))
    }

    @Test fun popInAllTwelveKeys() {
        val pop = Progressions.byId("pop")!!
        for (pc in 0 until 12) {
            val key = Progressions.keyFor(pc, minor = false)
            val chords = pop.chords(key)
            assertEquals(listOf(0, 7, 9, 5).map { (pc + it) % 12 }, chords.map { it.root.pitchClass })
            assertEquals(listOf(ChordType.MAJOR, ChordType.MAJOR, ChordType.MINOR, ChordType.MAJOR), chords.map { it.type })
            // Orthographe diatonique : lettres I, V, VI, IV de la gamme, sans double altération.
            assertEquals(listOf(0, 4, 5, 3).map { key.tonic.letter.step(it) }, chords.map { it.root.letter })
            assertTrue(chords.all { it.root.accidental in -1..1 })
        }
    }

    @Test fun keySpellings() {
        assertEquals("Db", Progressions.keyFor(1, false).tonic.toString())
        assertEquals("C#", Progressions.keyFor(1, true).tonic.toString())
        assertEquals("Ab", Progressions.keyFor(8, false).tonic.toString())
        assertEquals("G#", Progressions.keyFor(8, true).tonic.toString())
        assertEquals("F#", Progressions.keyFor(6, false).tonic.toString())
        assertEquals("Eb", Progressions.keyFor(3, true).tonic.toString())
        assertEquals("Bb", Progressions.keyFor(10, true).tonic.toString())
    }

    @Test fun blues() {
        assertEquals(listOf("A7", "A7", "A7", "A7", "D7", "D7", "A7", "A7", "E7", "D7", "A7", "E7"), names("blues", "A"))
        assertEquals("I7–IV7–I7–V7–IV7–I7–V7", Progressions.byId("blues")!!.summary)
        assertEquals(listOf("Cm7", "Cm7", "Cm7", "Cm7", "Fm7", "Fm7", "Cm7", "Cm7", "Ab7", "G7", "Cm7", "G7"), names("minor_blues", "C", true))
    }

    @Test fun otherProgressions() {
        assertEquals(listOf("Dm7", "G7", "Cmaj7", "Cmaj7"), names("jazz", "C"))
        assertEquals(listOf("Cm7", "F7", "Bbmaj7", "Bbmaj7"), names("jazz", "Bb"))
        assertEquals(listOf("D", "A", "Bm", "F#m", "G", "D", "G", "A"), names("pachelbel", "D"))
        assertEquals(listOf("Am", "G", "F", "E"), names("andalusian", "A", true))
        assertEquals(listOf("Em", "D", "C", "B"), names("andalusian", "E", true))
        assertEquals(listOf("Bm7b5", "E7", "Am7", "Am7"), names("minor_jazz", "A", true))
        assertEquals(listOf("C", "F", "Bdim", "Em", "Am", "Dm", "G", "C"), names("fifths", "C"))
        assertEquals(listOf("Am", "F", "C", "G"), names("sad", "C"))
        assertEquals(listOf("C", "F", "G"), names("three", "C"))
    }

    @Test fun romanParsing() {
        val g = Key(Note.parse("G")!!)
        assertEquals("F", RomanChord.parse("bVII")!!.resolve(g).toString())
        assertEquals("Eb", RomanChord.parse("bVII")!!.resolve(Key(Note.parse("F")!!)).toString())
        assertEquals("♭VII", RomanChord.parse("bVII")!!.symbol)
        assertEquals(ChordType.M7B5, RomanChord.parse("iiø7")!!.type)
        assertEquals(ChordType.DIM, RomanChord.parse("vii°")!!.type)
        assertEquals(ChordType.MIN_MAJ7, RomanChord.parse("imaj7")!!.type)
        assertNull(RomanChord.parse("Vx"))
        assertNull(RomanChord.parse("Iv"))
        assertNull(RomanChord.parse("7"))
        assertTrue(Progressions.ALL.map { it.id }.toSet().size == Progressions.ALL.size)
        assertTrue(Progressions.ALL.any { it.minor } && Progressions.ALL.any { !it.minor })
    }
}
