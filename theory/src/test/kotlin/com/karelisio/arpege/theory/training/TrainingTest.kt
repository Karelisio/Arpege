package com.karelisio.arpege.theory.training

import com.karelisio.arpege.theory.Clef
import com.karelisio.arpege.theory.Letter
import com.karelisio.arpege.theory.Note
import com.karelisio.arpege.theory.Pitch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TrainingTest {
    @Test fun intervalQuestionsHaveTheRightSize() {
        val rnd = Random(1)
        for (item in EarTraining.allItems(EarMode.INTERVALS)) for (dir in IntervalDirection.entries) {
            val q = EarTraining.question(EarMode.INTERVALS, item, rnd, dir)
            val notes = q.blocks.flatten()
            assertEquals(item.id.removePrefix("i").toInt(), notes.max() - notes.min())
        }
        assertEquals(12, EarTraining.allItems(EarMode.INTERVALS).size)
        assertEquals(3, EarTraining.itemsUpTo(EarMode.INTERVALS, 1).size)
    }

    @Test fun everyModeProducesQuestions() {
        val rnd = Random(2)
        for (mode in EarMode.entries) for (item in EarTraining.allItems(mode)) {
            val q = EarTraining.question(mode, item, rnd)
            assertTrue(q.blocks.isNotEmpty())
            assertTrue(q.blocks.flatten().all { it in 30..100 })
        }
    }

    @Test fun weightedPickFavoursMissedItems() {
        val items = EarTraining.itemsUpTo(EarMode.CHORDS, 1)
        val stats = mapOf("MAJOR" to (20 to 20), "MINOR" to (2 to 20))
        val rnd = Random(3)
        val counts = (1..2000).groupingBy { EarTraining.pick(items, stats, rnd).id }.eachCount()
        assertTrue(counts.getValue("MINOR") > counts.getValue("MAJOR") * 3)
    }

    @Test fun rhythmBarsAlwaysFillFourBeats() {
        val rnd = Random(4)
        for (level in 1..RhythmTrainer.LEVELS) repeat(300) {
            val bar = RhythmTrainer.generate(level, rnd)
            assertEquals(RhythmTrainer.BAR_TICKS, bar.totalTicks)
            assertTrue(bar.onsets.isNotEmpty())
            if (level == 1) assertTrue(bar.elements.none { it.rest || it.triplet })
        }
    }

    @Test fun rhythmScoring() {
        val expected = listOf(0.0, 500.0, 1000.0, 1500.0)
        assertEquals(100, RhythmTrainer.score(expected, expected).accuracy)
        val r = RhythmTrainer.score(expected, listOf(20.0, 480.0, 1000.0))
        assertEquals(listOf(20.0, -20.0, 0.0, null), r.deviationsMs)
        assertTrue(r.accuracy in 60..80)
        assertEquals(1, RhythmTrainer.score(expected, expected + 2000.0).extraTaps)
        assertEquals(500.0, RhythmTrainer.ticksToMs(48, 120), 1e-9)
    }

    @Test fun clefsPlaceNotesCorrectly() {
        val c4 = Pitch(Note(Letter.C), 4)
        assertEquals(-2, Clef.TREBLE.step(c4))
        assertEquals(10, Clef.BASS.step(c4))
        assertEquals(4, Clef.ALTO.step(c4))
        assertEquals(6, Clef.TENOR.step(c4))
        assertEquals(Pitch(Note(Letter.F), 5), Clef.TREBLE.pitchAt(8))
        assertEquals(Pitch(Note(Letter.A), 3), Clef.BASS.pitchAt(8))
        assertEquals(Pitch(Note(Letter.F), 3), Clef.BASS.pitchAt(6))
        for (clef in Clef.entries) for (s in -6..14) assertEquals(s, clef.step(clef.pitchAt(s)))
    }

    @Test fun solfegeQuestionsRespectLevel() {
        val rnd = Random(5)
        for (clef in Clef.entries) repeat(200) {
            val p1 = SolfegeTrainer.question(clef, 1, rnd)
            assertTrue(clef.step(p1) in 0..8)
            assertEquals(0, p1.note.accidental)
            val p3 = SolfegeTrainer.question(clef, 3, rnd)
            assertTrue(clef.step(p3) in -4..12)
        }
        val fs = Pitch(Note(Letter.F, 1), 4)
        assertTrue(SolfegeTrainer.checkName(fs, Note(Letter.F, 1)))
        assertFalse(SolfegeTrainer.checkName(fs, Note(Letter.G, -1)))
        assertTrue(SolfegeTrainer.checkKey(fs, 66 + 12))
    }
}
