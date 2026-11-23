package com.karelisio.arpege.theory.dsp

import com.karelisio.arpege.theory.frequencyToMidi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class YinTest {
    private fun sine(f: Double, sr: Int, n: Int, phase: Double = 0.3) =
        FloatArray(n) { (0.5 * sin(2 * PI * f * it / sr + phase)).toFloat() }

    private fun cents(measured: Double, expected: Double) = 1200 * kotlin.math.ln(measured / expected) / kotlin.math.ln(2.0)

    @Test fun detectsSyntheticSinesWithinOneCent() {
        for (sr in listOf(44100, 48000)) {
            val yin = YinPitchDetector(sr, 4096)
            for (f in listOf(82.41, 110.0, 440.0, 1046.5)) for (phase in listOf(0.0, 1.1, 2.7)) {
                val est = yin.detect(sine(f, sr, 4096, phase))!!
                val c = cents(est.frequency, f)
                assertEquals("f=$f sr=$sr phase=$phase -> ${est.frequency}", 0.0, c, 1.0)
            }
        }
    }

    @Test fun detectsPluckedString() {
        val sr = 44100
        val buf = Synth.pluck(110.0, sr, 0.5)
        val est = YinPitchDetector(sr, 4096).detect(buf.copyOfRange(2000, 2000 + 4096))!!
        assertEquals(45.0, frequencyToMidi(est.frequency), 0.1)
    }

    @Test fun detectsPianoNote() {
        val sr = 48000
        val buf = Synth.piano(440.0, sr, 0.6)
        val est = YinPitchDetector(sr, 4096).detect(buf.copyOfRange(4000, 4000 + 4096))!!
        assertEquals(0.0, cents(est.frequency, 440.0), 3.0)
    }

    @Test fun silenceGivesNothing() = assertNull(YinPitchDetector(44100, 4096).detect(FloatArray(4096)))
}
