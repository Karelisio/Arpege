package com.karelisio.arpege.theory

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteTest {
    private fun n(s: String) = Note.parse(s)!!

    @Test fun parsesEnglishAndLatin() {
        assertEquals(Note(Letter.F, 1), n("F#"))
        assertEquals(Note(Letter.B, -1), n("Bb"))
        assertEquals(Note(Letter.F, 1), n("Fa♯"))
        assertEquals(Note(Letter.D), n("Ré"))
        assertEquals(Note(Letter.G), n("Sol"))
    }

    @Test fun spelledTransposition() {
        assertEquals(n("E"), n("C") + Interval.M3)
        assertEquals(n("A#"), n("F#") + Interval.M3)
        assertEquals(n("Db"), n("Bb") + Interval.m3)
        assertEquals(n("A#"), n("E") + Interval.A4)
        assertEquals(n("Bb"), n("E") + Interval.d5)
        assertEquals(n("C"), n("C") + Interval.P8)
        assertEquals(n("D"), n("C") + Interval.M9)
        assertEquals(n("A"), n("C") - Interval.m3)
        assertEquals(n("F#"), n("A") - Interval.m3)
    }

    @Test fun pitchAndFrequency() {
        assertEquals(69, Pitch.parse("A4")!!.midi)
        assertEquals(60, Pitch.parse("C4")!!.midi)
        assertEquals(440.0, Pitch.parse("A4")!!.frequency(), 1e-9)
        assertEquals(82.4069, Pitch.parse("E2")!!.frequency(), 1e-3)
        assertEquals(Pitch.parse("E4"), Pitch.parse("C4")!! + Interval.M3)
        assertEquals(Pitch.parse("C5"), Pitch.parse("B4")!! + Interval.m2)
        assertEquals(59, Pitch.parse("Cb4")!!.midi)
    }

    @Test fun enharmonics() {
        assertEquals(n("F"), n("E#").simplified())
        assertEquals(n("B"), n("Cb").simplified())
        assertEquals(n("Gb"), n("F#").transpose(0, preferFlats = true))
        assertEquals("Fa♯", n("F#").name(Notation.LATIN))
        assertEquals("Si♭", n("Bb").name(Notation.LATIN))
    }

    @Test fun readsFrequency() {
        val r = readFrequency(445.0, 440.0)
        assertEquals(69, r.midi)
        assertEquals(19.56, r.cents, 0.05)
        assertEquals(0.0, readFrequency(432.0, 432.0).cents, 1e-9)
    }
}
