package com.karelisio.arpege.theory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicingTest {
    private fun top(chord: String, tuning: Tuning = Tuning.GUITAR_STANDARD, capo: Int = 0) =
        VoicingGenerator.generate(Chord.parse(chord)!!, tuning, capo)

    @Test fun classicOpenChordsComeFirst() {
        assertEquals("x32010", top("C").first().toString())
        assertEquals("022100", top("E").first().toString())
        assertEquals("xx0232", top("D").first().toString())
        assertEquals("022000", top("Em").first().toString())
        assertEquals("x02210", top("Am").first().toString())
        assertTrue(top("G").take(3).any { it.toString() == "320003" || it.toString() == "320033" })
        assertEquals("0003", top("C", Tuning.UKULELE).first().toString())
    }

    @Test fun barreChordsAreDetected() {
        val f = top("F").first { it.toString() == "133211" }
        assertEquals(1, f.barre?.fret)
        assertEquals(listOf(1, 3, 4, 2, 1, 1), f.fingers)
    }

    @Test fun capoShiftsShapes() {
        // Avec capo 2, un accord de D se joue en forme de C.
        assertEquals("x32010", top("D", capo = 2).first().toString())
    }

    @Test fun everyVoicingIsCorrectOnAllTwelveRoots() {
        val types = listOf(ChordType.MAJOR, ChordType.MINOR, ChordType.DOM7, ChordType.MAJ7, ChordType.MIN7, ChordType.SUS2,
            ChordType.SUS4, ChordType.DIM, ChordType.AUG, ChordType.DOM9, ChordType.ADD9, ChordType.SIX, ChordType.M7B5)
        for (pc in 0 until 12) for (type in types) for (tuning in listOf(Tuning.GUITAR_STANDARD, Tuning.UKULELE)) {
            val chord = Chord(Note.common(pc), type)
            val list = VoicingGenerator.generate(chord, tuning)
            assertTrue("$chord sur ${tuning.id}", list.isNotEmpty())
            for (v in list) {
                val pitches = v.pitches(tuning)
                assertTrue("$chord $v notes", pitches.all { mod12(it.midi) in chord.pitchClasses })
                val required = (chord.type.intervals - chord.type.optionalIntervals).map { mod12(pc + it.semitones) }.toSet()
                assertTrue("$chord $v couverture", pitches.map { mod12(it.midi) }.toSet().containsAll(required))
                if (tuning == Tuning.GUITAR_STANDARD) assertEquals("$chord $v basse", pc, mod12(pitches.minOf { it.midi }))
                assertTrue("$chord $v doigts", v.fingers.max() <= 4)
            }
            // Classement par facilité.
            assertEquals(list.map { it.difficulty }.sorted(), list.map { it.difficulty })
        }
    }
}
