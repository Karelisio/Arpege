package com.karelisio.arpege.theory.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MetronomeTest {

    /** 10 minutes simulées, rendu par petits tampons : chaque attaque tombe à ±1 échantillon de l'idéal. */
    @Test fun noDriftOverTenMinutes() {
        for ((bpm, sub, sr) in listOf(Triple(137, 3, 48000), Triple(60, 1, 44100), Triple(300, 4, 48000), Triple(97, 2, 44100))) {
            val config = MetronomeConfig(bpm = bpm, meter = Meter(7, 8), subdivision = sub, accents = List(7) { Accent.WEAK })
            val renderer = MetronomeRenderer(sr).apply { schedule = MetronomeSchedule(sr, config) }
            val chunk = FloatArray(192)
            val total = 10L * 60 * sr
            var frame = 0L
            var prevSample = 0f
            var silentRun = 10_000
            var onsetIndex = 0L
            var maxErr = 0.0
            while (frame < total) {
                renderer.render(chunk, frame)
                for (i in chunk.indices) {
                    val v = chunk[i]
                    if (v != 0f && prevSample == 0f && silentRun > 50) {
                        val ideal = onsetIndex * sr * 60.0 / (bpm * sub)
                        maxErr = maxOf(maxErr, abs((frame + i) - ideal))
                        onsetIndex++
                    }
                    silentRun = if (v == 0f) silentRun + 1 else 0
                    prevSample = v
                }
                frame += chunk.size
            }
            val expectedTicks = (total * bpm * sub) / (60L * sr)
            assertTrue("bpm=$bpm onsets=$onsetIndex", abs(onsetIndex - expectedTicks) <= 1)
            assertTrue("bpm=$bpm dérive max=$maxErr", maxErr <= 1.0)
        }
    }

    @Test fun tickAtIsConsistent() {
        val s = MetronomeSchedule(48000, MetronomeConfig(bpm = 133, subdivision = 3), originFrame = 1234)
        for (n in 0L..5000L) {
            assertEquals(n, s.tickAt(s.tickFrame(n)))
            assertEquals(n, s.tickAt(s.tickFrame(n + 1) - 1))
        }
        assertEquals(-1, s.tickAt(0))
    }

    @Test fun accentsFollowGroups() {
        assertEquals(listOf(Accent.STRONG, Accent.WEAK, Accent.MEDIUM, Accent.WEAK, Accent.MEDIUM, Accent.WEAK, Accent.WEAK), Meter(7, 8).defaultAccents())
        assertEquals(listOf(Accent.STRONG, Accent.WEAK, Accent.WEAK, Accent.MEDIUM, Accent.WEAK, Accent.WEAK), Meter(6, 8).defaultAccents())
    }

    @Test fun tapTempoAndNames() {
        val tap = TapTempo()
        var bpm: Int? = null
        for (i in 0..4) bpm = tap.tap(i * 500L)
        assertEquals(120, bpm)
        assertEquals("Andante", TempoNames.of(100))
        assertEquals("Allegro", TempoNames.of(140))
    }
}
