package com.karelisio.arpege.theory.song

import com.karelisio.arpege.theory.dsp.NoteEvent
import com.karelisio.arpege.theory.midiToFrequency

/** Note attaquée sur un temps : hauteur MIDI (accordage + capo + case) et durée (liaisons comprises). */
data class TimedNote(val string: Int, val midi: Int, val durationSec: Double, val dead: Boolean)

/** Temps d'une piste placé dans le temps : [index] global, mesure, rang dans la mesure, début et durée. */
data class TimedBeat(
    val index: Int,
    val measure: Int,
    val beat: Int,
    val startSec: Double,
    val durationSec: Double,
    val rest: Boolean,
    val notes: List<TimedNote>,
)

/**
 * Chronologie d'une piste Guitar Pro : début de chaque temps en secondes et hauteurs jouées.
 * Lecture linéaire des mesures (comme l'affichage), au tempo du fichier multiplié par la vitesse.
 */
class GuitarProTimeline private constructor(
    val bpm: Double,
    val beats: List<TimedBeat>,
    val measureStartSec: List<Double>,
    val durationSec: Double,
) {
    /** Index du temps en cours à l'instant [sec] (‑1 avant le début, dernier temps après la fin). */
    fun beatAt(sec: Double): Int {
        if (beats.isEmpty() || sec < beats[0].startSec) return -1
        var lo = 0
        var hi = beats.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (beats[mid].startSec <= sec) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** Mesure en cours à l'instant [sec] (‑1 avant le début). */
    fun measureAt(sec: Double): Int {
        if (measureStartSec.isEmpty() || sec < 0) return -1
        var m = 0
        while (m + 1 < measureStartSec.size && measureStartSec[m + 1] <= sec) m++
        return m
    }

    /** Notes à synthétiser à partir du temps [fromBeat], l'instant 0 étant le début de ce temps. */
    fun events(a4: Double = 440.0, fromBeat: Int = 0): List<NoteEvent> {
        val origin = beats.getOrNull(fromBeat)?.startSec ?: return emptyList()
        return beats.subList(fromBeat, beats.size).flatMap { b ->
            b.notes.map { n ->
                NoteEvent(
                    b.startSec - origin, midiToFrequency(n.midi.toDouble(), a4),
                    if (n.dead) minOf(0.07, b.durationSec) else n.durationSec, if (n.dead) 0.35 else 0.78,
                )
            }
        }
    }

    companion object {
        /** Durée d'un temps en noires : valeur, point et n-olet (triolet = 3 dans le temps de 2…). */
        fun beatQuarters(b: GpBeat): Double {
            val base = 4.0 / b.value.coerceAtLeast(1)
            val dotted = if (b.dotted) 1.5 else 1.0
            val tuplet = if (b.tuplet > 1) {
                var normal = 1
                while (normal * 2 < b.tuplet) normal *= 2
                normal.toDouble() / b.tuplet
            } else 1.0
            return base * dotted * tuplet
        }

        fun build(gp: GpSong, trackIndex: Int, speed: Double = 1.0): GuitarProTimeline {
            val bpm = gp.tempo.coerceIn(20, 400) * speed.coerceIn(0.1, 4.0)
            val track = gp.tracks.getOrNull(trackIndex) ?: return GuitarProTimeline(bpm, emptyList(), emptyList(), 0.0)
            val quarter = 60.0 / bpm
            val beats = ArrayList<TimedBeat>()
            val starts = ArrayList<Double>()
            // Dernière note attaquée par corde : une note liée prolonge sa durée au lieu d'être rejouée.
            val sounding = HashMap<Int, Pair<Int, Int>>() // corde → (index du temps, index de la note)
            val durations = ArrayList<MutableList<Double>>()
            var t = 0.0
            for ((m, measure) in track.measures.withIndex()) {
                starts += t
                val header = gp.measures.getOrNull(m)
                val nominal = (header?.numerator ?: 4) * 4.0 / (header?.denominator ?: 4).coerceAtLeast(1) * quarter
                if (measure.isEmpty()) { t += nominal; continue }
                for ((bi, b) in measure.withIndex()) {
                    val dur = beatQuarters(b) * quarter
                    val attacks = ArrayList<TimedNote>()
                    val attackDur = ArrayList<Double>()
                    if (b.rest) sounding.clear()
                    for (n in b.notes) {
                        if (n.string !in 1..track.tuning.size) continue
                        val prev = sounding[n.string]
                        if (n.tie && prev != null) {
                            // La note liée tient jusqu'à la fin de ce temps.
                            durations[prev.first][prev.second] = t + dur - beats[prev.first].startSec
                            continue
                        }
                        val midi = track.tuning[n.string - 1] + track.capo + n.fret
                        sounding[n.string] = beats.size to attacks.size
                        attacks += TimedNote(n.string, midi, dur, n.dead)
                        attackDur += dur
                    }
                    durations += attackDur
                    beats += TimedBeat(beats.size, m, bi, t, dur, b.rest, attacks)
                    t += dur
                }
            }
            val resolved = beats.mapIndexed { i, b ->
                b.copy(notes = b.notes.mapIndexed { k, n -> n.copy(durationSec = durations[i][k]) })
            }
            return GuitarProTimeline(bpm, resolved, starts, t)
        }
    }
}
