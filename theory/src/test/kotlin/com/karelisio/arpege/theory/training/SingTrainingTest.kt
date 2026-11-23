package com.karelisio.arpege.theory.training

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin
import kotlin.random.Random

class SingTrainingTest {
    /** Estimations à ~43 Hz (fenêtre YIN de 1024 échantillons à 44,1 kHz). */
    private fun feed(d: HoldDetector, fromMs: Long, toMs: Long, midi: (Long) -> Double?): HoldDetector.State {
        var t = fromMs
        var s = d.state
        while (t <= toMs) { s = d.feed(t, midi(t)); t += 23 }
        return s
    }

    @Test fun questionsStayInRangeWithTheRightInterval() {
        val rnd = Random(7)
        for (mode in SingMode.entries) for (range in VoiceRange.entries) for (item in SingTraining.items(mode, SingTraining.levelCount())) {
            repeat(30) {
                val q = SingTraining.question(item, range, rnd)
                assertEquals(SingTraining.semitones(item), q.semitones)
                assertTrue(q.referenceMidi in range.low..range.high)
                assertTrue(q.targetMidi in range.low..range.high)
                when (mode) {
                    SingMode.UNISON -> assertEquals(q.referenceMidi, q.targetMidi)
                    SingMode.UP -> assertTrue(q.targetMidi > q.referenceMidi)
                    SingMode.DOWN -> assertTrue(q.targetMidi < q.referenceMidi)
                }
            }
        }
    }

    @Test fun itemsFollowEarTrainingLevels() {
        assertEquals(listOf("u"), SingTraining.items(SingMode.UNISON, 3).map { it.id })
        assertEquals(listOf("i5+", "i7+", "i12+"), SingTraining.items(SingMode.UP, 1).map { it.id })
        assertEquals(-7, SingTraining.semitones(EarItem("i7-", "")))
        assertEquals(12, SingTraining.items(SingMode.DOWN, SingTraining.levelCount()).size)
        assertEquals(25, SingTraining.allItems().map { it.id }.toSet().size)
    }

    @Test fun deviationCanIgnoreTheOctave() {
        assertEquals(1210.0, SingTraining.deviationCents(72.1, 60, octaveFree = false), 1e-6)
        assertEquals(10.0, SingTraining.deviationCents(72.1, 60, octaveFree = true), 1e-6)
        assertEquals(-20.0, SingTraining.deviationCents(47.8, 60, octaveFree = true), 1e-6)
        assertEquals(-500.0, SingTraining.deviationCents(55.0, 60, octaveFree = true), 1e-6)
    }

    @Test fun steadyNoteIsValidatedAfterTheHold() {
        val d = HoldDetector(60)
        val early = feed(d, 0, 500) { 60.1 }
        assertFalse(early.held)
        assertTrue(early.inZone)
        assertTrue(early.progress in 0.7f..0.95f)
        val late = feed(d, 523, 700) { 60.1 }
        assertTrue(late.held)
        assertEquals(1f, late.progress)
    }

    @Test fun offPitchIsNeverValidated() {
        val d = HoldDetector(60)
        val s = feed(d, 0, 3000) { 60.4 }
        assertFalse(s.held)
        assertFalse(s.inZone)
        assertEquals(40.0, s.cents!!, 0.5)
    }

    @Test fun wrongOctaveOnlyCountsWhenOctaveFree() {
        assertFalse(feed(HoldDetector(60), 0, 2000) { 48.05 }.held)
        assertTrue(feed(HoldDetector(60, octaveFree = true), 0, 2000) { 48.05 }.held)
    }

    @Test fun vibratoAndShortDropoutsAreTolerated() {
        // Vibrato ±15 cents à 5,5 Hz.
        assertTrue(feed(HoldDetector(57), 0, 900) { t -> 57 + 0.15 * sin(t / 1000.0 * 2 * Math.PI * 5.5) }.held)
        // Coupure de 70 ms au milieu de la tenue : pas de remise à zéro.
        val d = HoldDetector(57)
        val s = feed(d, 0, 700) { t -> if (t in 300L..370L) null else 57.0 }
        assertTrue(s.held)
    }

    @Test fun longSilenceOrWrongNoteResetsTheHold() {
        val d = HoldDetector(62)
        feed(d, 0, 450) { 62.0 }
        val afterSilence = feed(d, 473, 800) { null }
        assertEquals(0f, afterSilence.progress)
        val restart = feed(d, 823, 1300) { 62.0 }
        assertFalse(restart.held)
        // Passage par une autre note : la tenue repart de zéro.
        val d2 = HoldDetector(62)
        feed(d2, 0, 400) { 62.0 }
        val wrong = feed(d2, 423, 700) { 64.0 }
        assertEquals(0f, wrong.progress)
        assertFalse(wrong.held)
    }

    @Test fun heldStateIsLatchedUntilReset() {
        val d = HoldDetector(60)
        assertTrue(feed(d, 0, 800) { 60.0 }.held)
        assertTrue(d.feed(830, 70.0).held)
        d.reset()
        assertFalse(d.state.held)
        assertFalse(d.feed(900, 60.0).held)
    }
}
