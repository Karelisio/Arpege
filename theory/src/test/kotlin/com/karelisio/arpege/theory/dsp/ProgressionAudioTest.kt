package com.karelisio.arpege.theory.dsp

import com.karelisio.arpege.theory.Key
import com.karelisio.arpege.theory.Note
import com.karelisio.arpege.theory.Progressions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class ProgressionAudioTest {
    private val sr = 8000
    private val chords = listOf(listOf(196.0, 247.0, 294.0), listOf(147.0, 220.0, 294.0), listOf(165.0, 196.0, 247.0), listOf(131.0, 165.0, 196.0))

    @Test fun timingIsSampleExact() {
        val t = LoopTiming(48000, 120, 4, 4)
        assertEquals(384_000L, t.cycleFrames)
        assertEquals(96_000L, t.chordStartFrame(1))
        assertEquals(LoopPosition(0, 1, 0), t.positionAt(96_000))
        assertEquals(LoopPosition(0, 0, 3), t.positionAt(95_999))
        assertEquals(LoopPosition(1, 0, 1), t.positionAt(384_000 + 24_000))
        assertEquals(null, t.positionAt(-1))
        // Tempo non diviseur : aucune dérive, même après 1000 minutes.
        val odd = LoopTiming(44100, 97, 3, 5)
        assertEquals(44100L * 60 * 1000, odd.beatFrame(97L * 1000))
        // Le tampon d'un passage est rejoué tel quel : le temps k du passage c tombe à c × cycle + beatFrame(k).
        for (b in 0L until 500L) {
            val f = (b / odd.totalBeats) * odd.cycleFrames + odd.beatFrame(b % odd.totalBeats)
            val p = odd.positionAt(f)!!
            assertEquals(((b % odd.totalBeats) / 3).toInt(), p.chord)
            assertEquals(((b % odd.totalBeats) % 3).toInt(), p.beat)
            if (f > 0) assertEquals(((b - 1) % odd.totalBeats % 3).toInt(), odd.positionAt(f - 1)!!.beat)
        }
    }

    private fun rms(buf: FloatArray, from: Int, len: Int): Double {
        var s = 0.0
        for (i in from until from + len) s += buf[i] * buf[i]
        return sqrt(s / len)
    }

    @Test fun cycleHasOneBufferWithChordAttacksOnBeat() {
        for (style in Accompaniment.entries) {
            val audio = ProgressionAudio.render(chords, sr, 100, 4, style, Voice.STRING, click = false)
            assertEquals(audio.timing.cycleFrames.toInt(), audio.cycle.size)
            for (i in 1 until chords.size) {
                val at = audio.timing.chordStartFrame(i).toInt()
                val before = rms(audio.cycle, at - 160, 160)
                val after = rms(audio.cycle, at, 160)
                assertTrue("$style accord $i : attaque à l'échantillon prévu", after > before * 1.5)
            }
        }
    }

    @Test fun clicksStartExactlyOnBeats() {
        val with = ProgressionAudio.render(chords, sr, 93, 2, Accompaniment.BLOCK, Voice.SINE, click = true)
        val without = ProgressionAudio.render(chords, sr, 93, 2, Accompaniment.BLOCK, Voice.SINE, click = false)
        val t = with.timing
        for (b in 1 until t.totalBeats) {
            val f = t.beatFrame(b.toLong()).toInt()
            val from = t.beatFrame(b - 1L).toInt() + sr / 10
            val first = (from until with.cycle.size).first { abs(with.cycle[it] - without.cycle[it]) > 1e-6f }
            assertEquals("clic du temps $b", f, first)
        }
    }

    @Test fun loopReadsHeadThenCycle() {
        val audio = ProgressionAudio.render(chords, sr, 120, 1, Accompaniment.BLOCK, Voice.STRING, click = false)
        val n = audio.cycle.size
        val out = FloatArray(3 * n)
        audio.read(out, 0)
        for (f in n until 3 * n) assertEquals(audio.cycle[f % n], out[f], 0f)
        // Premier passage : pas de traîne repliée au tout début.
        assertTrue(out[10] != audio.cycle[10])
        val again = FloatArray(500)
        audio.read(again, 2L * n + 7)
        for (i in again.indices) assertEquals(audio.cycle[(i + 7) % n], again[i], 0f)
    }

    @Test fun pianoVoicingsFollowChords() {
        val prog = Progressions.byId("jazz")!!.chords(Key(Note.parse("C")!!))
        val v = ProgressionAudio.pianoVoicings(prog)
        v.zip(prog).forEach { (notes, chord) ->
            assertTrue(notes[0] in 36..47)
            assertEquals(chord.root.pitchClass, notes[0] % 12)
            val upper = notes.drop(1)
            assertEquals(upper.sorted(), upper)
            assertEquals(chord.pitchClasses, upper.map { it % 12 }.toSet())
            assertTrue(upper.first() >= 55 && upper.last() < 80)
        }
    }
}
