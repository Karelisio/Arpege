package com.karelisio.arpege.theory.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class PitchTraceTest {
    @Test fun keepsOnlyTheLastWindow() {
        val trace = CentsTrace(windowMs = 5000)
        for (t in 0L..8000L step 20) trace.add(t, 5.0)
        val points = trace.segments().flatten()
        assertTrue(points.first().timeMs >= 3000)
        assertEquals(8000L, trace.latestTimeMs)
        trace.prune(20_000)
        assertTrue(trace.segments().isEmpty())
    }

    @Test fun breaksOnSilenceJumpOrNewTarget() {
        val trace = CentsTrace()
        for (t in 0L..400L step 20) trace.add(t, 2.0, key = 40)
        for (t in 420L..700L step 20) trace.add(t, null, key = 40) // silence
        for (t in 720L..900L step 20) trace.add(t, 2.0, key = 40)
        trace.add(920, 45.0, key = 40) // saut
        trace.add(940, 44.0, key = 45) // autre corde
        assertEquals(4, trace.segments().size)
    }

    @Test fun smoothsAndClamps() {
        val trace = CentsTrace(jumpCents = 100.0)
        val rnd = Random(1)
        for (t in 0L..2000L step 20) trace.add(t, 10.0 + rnd.nextDouble(-8.0, 8.0))
        val pts = trace.segments().single()
        val maxStep = pts.zipWithNext().maxOf { (a, b) -> abs(a.cents - b.cents) }
        assertTrue("pas lissé : $maxStep", maxStep < 6.0)
        assertEquals(10.0, pts.map { it.cents }.average(), 1.5)
        trace.add(2020, 200.0)
        assertEquals(50.0, trace.segments().last().last().cents, 1e-9)
    }

    @Test fun strobeSpeedFollowsTheDeviation() {
        assertEquals(0.0, Strobe.speed(0.0), 0.0)
        assertEquals(0.0, Strobe.speed(-0.4), 0.0)
        assertTrue(Strobe.speed(10.0) > 0)
        assertTrue(Strobe.speed(-10.0) < 0)
        assertEquals(Strobe.speed(20.5) / Strobe.speed(10.5), 2.0, 1e-9)
        // Phase toujours dans [0, 1[, immobile quand c'est juste.
        assertEquals(0.3, Strobe.advance(0.3, 0.2, 10.0), 1e-12)
        var p = 0.0
        repeat(1000) { p = Strobe.advance(p, -37.0, 1 / 60.0); assertTrue(p >= 0 && p < 1) }
        assertEquals(0.25, Strobe.advance(0.0, 1 / (4 * Strobe.PERIODS_PER_CENT) + Strobe.DEAD_ZONE, 1.0), 1e-9)
    }
}
