package com.karelisio.arpege.theory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordIdentifierTest {
    private fun guitar(frets: String) =
        ChordIdentifier.identify(ChordIdentifier.pitchesFromFrets(ChordIdentifier.parseFrets(frets), Tuning.GUITAR_STANDARD))

    private fun best(frets: String) = guitar(frets).first().chord.toString()

    private fun notes(vararg midi: Int) = ChordIdentifier.identify(midi.toList())

    @Test fun openGuitarChords() {
        assertEquals("C", best("x32010"))
        assertEquals("Em", best("022000"))
        assertEquals("Am", best("x02210"))
        // x02010 = La Mi Sol Do Mi : La mineur septième.
        assertEquals("Am7", best("x02010"))
        assertEquals("G", best("320003"))
        assertEquals("D", best("xx0232"))
        assertEquals("F", best("133211"))
        assertEquals("E7", best("020100"))
        assertEquals("Cmaj7", best("x32000"))
        assertEquals("Dm7", best("xx0211"))
        assertEquals("Bm7b5", best("x2323x"))
        assertTrue(guitar("x32010").first().complete && guitar("x32010").first().rootPosition)
    }

    @Test fun keyboardChords() {
        assertEquals("Cmaj7", notes(60, 64, 67, 71).first().chord.toString())
        // Mi–Sol–Do : premier renversement.
        val inv = notes(64, 67, 72).first()
        assertEquals("C/E", inv.chord.toString())
        assertEquals(Interval.M3, inv.bassInterval)
        assertEquals("C/G", notes(55, 60, 64).first().chord.toString())
        assertEquals("Cdim7", notes(60, 63, 66, 69).first().chord.toString())
        assertEquals("C5", notes(48, 55, 60).first().chord.toString())
    }

    @Test fun ambiguousSetsDependOnBass() {
        val c6 = notes(60, 64, 67, 69).map { it.chord.toString() }
        assertEquals("C6", c6[0])
        assertTrue("Am7/C" in c6)
        assertEquals("Am7", notes(57, 60, 64, 67).first().chord.toString())
        assertEquals("Em7", notes(52, 55, 59, 62).first().chord.toString())
        assertEquals("G6", notes(55, 59, 62, 64).first().chord.toString())
    }

    @Test fun incompleteChordsArePenalized() {
        // Do–Mi–Si♭ : C7 sans quinte.
        val c7 = notes(48, 52, 58).first()
        assertEquals("C7", c7.chord.toString())
        assertEquals(listOf(Interval.P5), c7.missing)
        // Do–Mi : triade sans quinte, mais derrière un accord complet équivalent.
        val two = notes(60, 64)
        assertEquals("C", two.first().chord.toString())
        assertTrue(!two.first().complete)
        assertTrue(two.zipWithNext().all { (a, b) -> a.score <= b.score })
    }

    @Test fun slashChords() {
        // Ré7 avec Do à la basse : troisième renversement plutôt que « D/C ».
        assertEquals("D7/C", notes(48, 62, 66, 69).first().chord.toString())
        // Basse étrangère : Ré majeur sur Mi.
        val foreign = notes(40, 62, 66, 69).first { it.foreignBass }
        assertEquals("D/E", foreign.chord.toString())
    }

    @Test fun nothingForSingleNote() {
        assertTrue(notes(60, 72).isEmpty())
        assertTrue(ChordIdentifier.identify(emptyList()).isEmpty())
    }
}
