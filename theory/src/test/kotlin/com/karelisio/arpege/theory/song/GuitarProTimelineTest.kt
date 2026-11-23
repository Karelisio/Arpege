package com.karelisio.arpege.theory.song

import com.karelisio.arpege.theory.dsp.NoteStream
import com.karelisio.arpege.theory.dsp.Voice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GuitarProTimelineTest {
    private val synthetic = GuitarProReader.read(gp3(listOf(GuitarProTest.SYNTHETIC_MEASURE)))

    private fun beat(quarters: Int, dotted: Boolean = false, tuplet: Int = 0) =
        GpBeat(quarters, dotted, tuplet, rest = false, notes = emptyList(), chord = null, text = null)

    @Test fun beatDurations() {
        assertEquals(1.0, GuitarProTimeline.beatQuarters(beat(4)), 1e-9)
        assertEquals(4.0, GuitarProTimeline.beatQuarters(beat(1)), 1e-9)
        assertEquals(1.5, GuitarProTimeline.beatQuarters(beat(4, dotted = true)), 1e-9)
        assertEquals(1.0 / 3, GuitarProTimeline.beatQuarters(beat(8, tuplet = 3)), 1e-9)
        assertEquals(0.2, GuitarProTimeline.beatQuarters(beat(16, tuplet = 5)), 1e-9)
        assertEquals(0.25, GuitarProTimeline.beatQuarters(beat(16, dotted = true, tuplet = 6)), 1e-9)
    }

    @Test fun syntheticTimeline() {
        val tl = GuitarProTimeline.build(synthetic, 0)
        assertEquals(96.0, tl.bpm, 1e-9)
        assertEquals(listOf(0.0, 0.625, 1.25, 1.875), tl.beats.map { it.startSec })
        // Corde 2 = Si3 (59), corde 3 = Sol3 (55), corde 4 = Ré3 (50) + cases 0, 1, 3, 2.
        assertEquals(listOf(59, 56, 53, 61), tl.beats.map { it.notes.single().midi })
        assertEquals(2.5, tl.durationSec, 1e-9)
        assertEquals(-1, tl.beatAt(-0.01))
        assertEquals(0, tl.beatAt(0.0))
        assertEquals(2, tl.beatAt(1.3))
        assertEquals(3, tl.beatAt(99.0))
        assertEquals(0, tl.measureAt(2.0))
    }

    @Test fun speedScalesTimes() {
        val slow = GuitarProTimeline.build(synthetic, 0, speed = 0.5)
        assertEquals(48.0, slow.bpm, 1e-9)
        assertEquals(1.25, slow.beats[1].startSec, 1e-9)
        assertEquals(5.0, slow.durationSec, 1e-9)
        val fast = GuitarProTimeline.build(synthetic, 0, speed = 1.5)
        assertEquals(2.5 / 1.5, fast.durationSec, 1e-9)
    }

    @Test fun tiesRestsAndCapo() {
        val gp = GuitarProReader.read(
            gp3(
                listOf(
                    listOf(
                        BeatSpec(listOf(NoteSpec(1, 3), NoteSpec(6, 0))),
                        BeatSpec(listOf(NoteSpec(3, 2))),
                        BeatSpec(listOf(NoteSpec(1, 3, tie = true))),
                        BeatSpec(rest = true),
                    ),
                    listOf(BeatSpec(listOf(NoteSpec(2, 1)), duration = -1, dotted = true), BeatSpec(listOf(NoteSpec(2, 0, dead = true)))),
                ),
                tempo = 120, capo = 2,
            ),
        )
        val tl = GuitarProTimeline.build(gp, 0)
        assertEquals(6, tl.beats.size)
        val first = tl.beats[0]
        // Sol4 (67) + capo 2 ; la note liée tient trois noires (jusqu'à la fin du 3e temps).
        val high = first.notes.first { it.string == 1 }
        assertEquals(64 + 2 + 3, high.midi)
        assertEquals(1.5, high.durationSec, 1e-9)
        assertEquals(0.5, first.notes.first { it.string == 6 }.durationSec, 1e-9)
        assertTrue(tl.beats[2].notes.isEmpty()) // liée : pas de nouvelle attaque
        assertTrue(tl.beats[3].rest)
        assertEquals(listOf(0.0, 2.0), tl.measureStartSec)
        assertEquals(1, tl.beats[4].measure)
        assertEquals(1.5, tl.beats[4].durationSec, 1e-9) // blanche pointée à 120
        assertEquals(4.0, tl.durationSec, 1e-9)
        val events = tl.events(fromBeat = 4)
        assertEquals(2, events.size)
        assertEquals(0.0, events[0].startSec, 1e-9)
        assertTrue(events[1].durationSec < 0.1) // note étouffée : brève
    }

    @Test fun layoutExposesBeatColumns() {
        val measures = List(6) { GuitarProTest.SYNTHETIC_MEASURE }
        val gp = GuitarProReader.read(gp3(measures))
        val tl = GuitarProTimeline.build(gp, 0)
        for (maxChars in listOf(Int.MAX_VALUE, 30)) {
            val layout = GuitarProTab.layout(gp, 0, maxChars = maxChars)
            assertEquals(tl.beats.size, layout.beats.size)
            layout.beats.zip(tl.beats).forEach { (pos, b) ->
                assertEquals(b.measure, pos.measure)
                assertEquals(b.beat, pos.beat)
                val note = gp.tracks[0].measures[b.measure][b.beat].notes.single()
                val row = (layout.song.lines[pos.line + note.string - 1] as SongLine.Tab).text
                assertTrue(row.substring(pos.column).startsWith(note.fret.toString()))
                assertTrue(pos.column >= pos.measureColumn && pos.column + pos.width <= pos.measureColumn + pos.measureWidth)
                assertEquals('|', row[pos.measureColumn - 1])
                assertEquals('|', row[pos.measureColumn + pos.measureWidth])
            }
            val systems = layout.beats.map { it.line }.distinct().size
            if (maxChars == 30) {
                assertTrue("lignes de 30 caractères au plus", layout.song.lines.filterIsInstance<SongLine.Tab>().all { it.text.length <= 30 })
                assertEquals(6, systems)
            } else assertEquals(2, systems)
        }
    }

    @Test fun streamRendersWholeSongInBlocks() {
        val tl = GuitarProTimeline.build(synthetic, 0)
        val sr = 8000
        val stream = NoteStream(tl.events(), Voice.STRING, sr)
        val block = FloatArray(1000)
        var frame = 0L
        var peakAtThirdBeat = 0f
        while (true) {
            val n = stream.read(block, frame)
            for (i in 0 until n) {
                val f = frame + i
                if (f in (1.25 * sr).toLong() until (1.30 * sr).toLong()) peakAtThirdBeat = maxOf(peakAtThirdBeat, abs(block[i]))
            }
            frame += n
            if (n < block.size) break
        }
        assertEquals(stream.totalFrames, frame)
        assertTrue(frame >= (2.5 * sr).toLong())
        assertTrue(peakAtThirdBeat > 0.01f)
    }
}
