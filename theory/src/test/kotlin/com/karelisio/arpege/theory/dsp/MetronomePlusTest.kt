package com.karelisio.arpege.theory.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetronomePlusTest {

    /** Attaques (premier échantillon non nul après un silence) d'un flux rendu par tampons. */
    private fun onsets(sr: Int, seconds: Int, chunk: Int = 256, step: (MetronomeRenderer, Long, Int) -> Unit): List<Long> {
        val renderer = MetronomeRenderer(sr)
        val buf = FloatArray(chunk)
        val out = ArrayList<Long>()
        var prev = 0f
        var silent = 10_000
        var frame = 0L
        while (frame < seconds.toLong() * sr) {
            step(renderer, frame, chunk)
            renderer.render(buf, frame)
            for (i in buf.indices) {
                val v = buf[i]
                if (v != 0f && prev == 0f && silent > 50) out += frame + i
                silent = if (v == 0f) silent + 1 else 0
                prev = v
            }
            frame += chunk
        }
        return out
    }

    private fun round(n: Long, sr: Int, bpm: Int): Long {
        val num = sr * 60L
        return (n * num * 2 + bpm) / (2L * bpm)
    }

    @Test fun trainerTargets() {
        val t = SpeedTrainer(stepBpm = 5, everyBars = 4, maxBpm = 120)
        assertEquals(100, t.bpmAfter(100, -1))
        assertEquals(100, t.bpmAfter(100, 0))
        assertEquals(100, t.bpmAfter(100, 3))
        assertEquals(105, t.bpmAfter(100, 4))
        assertEquals(110, t.bpmAfter(100, 8))
        assertEquals(120, t.bpmAfter(100, 16))
        assertEquals(120, t.bpmAfter(100, 1_000_000_000_000L))
        assertEquals(120, SpeedTrainer(7, 1, 120).bpmAfter(100, 3)) // 121 plafonné à 120
        assertEquals(130, t.bpmAfter(130, 50)) // déjà au-dessus du maximum : inchangé
        assertEquals(4, t.barsToNextStep(100, 0))
        assertEquals(1, t.barsToNextStep(100, 7))
        assertNull(t.barsToNextStep(100, 16))
        assertEquals(t, SpeedTrainer.decode(t.encode()))
        assertNull(SpeedTrainer.decode("0,4,120"))
        assertNull(SpeedTrainer.decode("n'importe quoi"))
    }

    /** Le tempo change exactement au premier temps de chaque palier, à l'échantillon près. */
    @Test fun trainerAppliesOnBarStartSampleAccurate() {
        val sr = 48000
        val trainer = SpeedTrainer(stepBpm = 7, everyBars = 2, maxBpm = 150)
        val config = MetronomeConfig(bpm = 97, meter = Meter(3, 4), accents = List(3) { Accent.WEAK })
        var schedule = MetronomeSchedule(sr, config)
        val run = SpeedTrainerRun(trainer, config.bpm)
        val got = onsets(sr, seconds = 40) { r, from, chunk ->
            schedule = run.advance(schedule, from, from + chunk).pruned(from - sr)
            r.schedule = schedule
        }
        // Attendu : à chaque palier, la nouvelle grille part du premier temps de la mesure.
        val expected = ArrayList<Long>()
        var segOrigin = 0L
        var segBpm = 97
        var idx = 0L
        var b = 0L
        while (segOrigin + round(idx, sr, segBpm) < 40L * sr) {
            val bpm = trainer.bpmAfter(97, b)
            if (bpm != segBpm) { segOrigin += round(idx, sr, segBpm); segBpm = bpm; idx = 0 }
            repeat(3) { expected += segOrigin + round(idx++, sr, segBpm) }
            b++
        }
        val n = got.size
        assertTrue("attaques=$n", n > 60)
        assertEquals(expected.take(n), got)
        assertEquals(150, schedule.config.bpm)
    }

    /** Un changement manuel garde l'ancienne grille jusqu'au temps suivant : aucun clic ajouté. */
    @Test fun manualChangeWaitsForNextBeat() {
        val sr = 48000
        val slow = MetronomeConfig(bpm = 60, accents = List(4) { Accent.WEAK })
        val got = onsets(sr, seconds = 3) { r, from, _ ->
            if (from == 0L) r.schedule = MetronomeSchedule(sr, slow)
            if (from == 4864L) r.schedule = r.schedule!!.retimed(slow.copy(bpm = 90), from)
            if (from == 9984L) r.schedule = r.schedule!!.retimed(slow.copy(bpm = 120, subdivision = 4), from)
        }
        // 0 (ancien tempo), puis 120 BPM × 4 à partir du temps suivant (1 s) ; rien de la grille à 90.
        val expected = listOf(0L) + (0..16).map { sr + it * 6000L }
        assertEquals(expected, got)
    }

    @Test fun nextBarAcrossTransition() {
        val sr = 48000
        val s0 = MetronomeSchedule(sr, MetronomeConfig(bpm = 120)) // temps = 24000, mesure = 96000
        assertEquals(0, s0.nextBarFrame(0))
        assertEquals(96000, s0.nextBarFrame(1))
        val s1 = s0.retimed(MetronomeConfig(bpm = 60), 30000) // effet au 3e temps (48000)
        assertEquals(48000, s1.startFrame)
        assertEquals(s0, s1.at(47999))
        assertEquals(s1, s1.at(48000))
        // 3e temps à 48000, 4e à 96000 (60 BPM) : prochaine mesure à 144000.
        assertEquals(144000, s1.nextBarFrame(1))
        // Changement de mesure : on repart au premier temps à la transition.
        val s2 = s0.retimed(MetronomeConfig(bpm = 120, meter = Meter(3, 4)), 30000)
        assertEquals(48000, s2.nextBarFrame(1))
        assertEquals(s1.config, s1.pruned(50000).config)
        assertNull(s1.pruned(50000).previous)
        assertEquals(s0, s1.pruned(10).previous)
    }

    @Test fun favoritesRoundTrip() {
        val favs = listOf(
            MetronomeFavorite.of("Gammes | lentes\n", MetronomeConfig(bpm = 72, meter = Meter(7, 8), subdivision = 2)),
            MetronomeFavorite.of("  ", MetronomeConfig(bpm = 140)),
        )
        assertEquals("Gammes   lentes", favs[0].name)
        assertEquals("140 · 4/4", favs[1].name)
        val decoded = MetronomeFavorite.decode(MetronomeFavorite.encode(favs))
        assertEquals(favs, decoded)
        assertEquals(listOf(2, 2, 3), decoded[0].meter.groups)
        assertTrue(decoded[0].matches(MetronomeConfig(bpm = 72, meter = Meter(7, 8), subdivision = 2)))
        assertTrue(!decoded[0].matches(MetronomeConfig(bpm = 73, meter = Meter(7, 8), subdivision = 2)))
        assertEquals(1, MetronomeFavorite.decode("ok|90|4|4|1\nmauvais|5|4|4|1\n|90|4|4|1\nx|90|4\n").size)
        assertEquals(emptyList<MetronomeFavorite>(), MetronomeFavorite.decode(null))
    }
}
