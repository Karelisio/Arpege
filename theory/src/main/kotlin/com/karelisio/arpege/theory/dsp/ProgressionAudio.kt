package com.karelisio.arpege.theory.dsp

import com.karelisio.arpege.theory.Chord
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/** Façon de jouer l'accompagnement d'une grille. */
enum class Accompaniment(val label: String) {
    BLOCK("Plaqué"),
    STRUM("Rythmé"),
    ARPEGGIO("Arpégé"),
}

/** Position de lecture dans une boucle : numéro de passage, accord, temps dans l'accord. */
data class LoopPosition(val cycle: Long, val chord: Int, val beat: Int)

/**
 * Découpage d'une boucle en temps et en accords, à l'échantillon près : la position du temps n
 * est calculée directement depuis n en arithmétique entière (aucune accumulation, aucune dérive).
 */
class LoopTiming(val sampleRate: Int, val bpm: Int, val beatsPerChord: Int, val chordCount: Int) {
    init { require(sampleRate > 0 && bpm in 1..1000 && beatsPerChord >= 1 && chordCount >= 1) }

    val totalBeats: Int get() = chordCount * beatsPerChord

    /** Échantillon du temps fractionnaire [num]/[den] (arrondi au plus proche). */
    fun frameAt(num: Long, den: Long = 1): Long = (num * sampleRate * 60L * 2 + den * bpm) / (2L * den * bpm)

    fun beatFrame(beat: Long): Long = frameAt(beat)

    /** Durée d'un passage complet de la grille, en échantillons. */
    val cycleFrames: Long = beatFrame(totalBeats.toLong())

    fun chordStartFrame(chord: Int): Long = beatFrame(chord.toLong() * beatsPerChord)

    /** Accord et temps joués à l'échantillon [frame] compté depuis le début de la lecture (boucles comprises). */
    fun positionAt(frame: Long): LoopPosition? {
        if (frame < 0) return null
        val cycle = frame / cycleFrames
        val inCycle = frame % cycleFrames
        var b = (inCycle * bpm / (sampleRate * 60L)).toInt().coerceIn(0, totalBeats - 1)
        while (b + 1 < totalBeats && beatFrame(b + 1L) <= inCycle) b++
        while (b > 0 && beatFrame(b.toLong()) > inCycle) b--
        return LoopPosition(cycle, b / beatsPerChord, b % beatsPerChord)
    }
}

/**
 * Un passage de la grille rendu dans un seul tampon ([cycle]) qui boucle sans couture : la traîne
 * des dernières notes est repliée au début. [head] est le début du tout premier passage, sans traîne.
 */
class LoopAudio(val timing: LoopTiming, private val head: FloatArray, val cycle: FloatArray) {
    /** Remplit [out] à partir de l'échantillon absolu [startFrame] ; la boucle est infinie. */
    fun read(out: FloatArray, startFrame: Long): Int {
        val n = cycle.size.toLong()
        for (i in out.indices) {
            val f = startFrame + i
            out[i] = if (f < head.size) head[f.toInt()] else cycle[(f % n).toInt()]
        }
        return out.size
    }
}

object ProgressionAudio {
    private const val MAX_NOTE_SEC = 4.0

    private data class Strike(val start: Long, val frequency: Double, val frames: Int, val velocity: Double)

    /**
     * Rend un passage complet de la grille. [voicings] : fréquences de chaque accord, de la plus grave
     * à la plus aiguë. Les temps, accords et clics tombent exactement sur les échantillons de [LoopTiming].
     */
    fun render(
        voicings: List<List<Double>>,
        sampleRate: Int,
        bpm: Int,
        beatsPerChord: Int,
        style: Accompaniment,
        voice: Voice,
        click: Boolean,
    ): LoopAudio {
        require(voicings.isNotEmpty())
        val timing = LoopTiming(sampleRate, bpm, beatsPerChord, voicings.size)
        val strikes = strikes(voicings, timing, style)
        val cycleLen = timing.cycleFrames.toInt()
        val end = maxOf(timing.cycleFrames, strikes.maxOfOrNull { it.start + it.frames } ?: 0L).toInt()
        // Mémoire : un seul tampon de passage + la traîne (quelques dixièmes de seconde).
        val cycle = FloatArray(cycleLen)
        val tail = FloatArray(end - cycleLen)
        fun mix(src: FloatArray, offset: Int, gain: Float) {
            for (i in src.indices) {
                val j = offset + i
                if (j < cycleLen) cycle[j] += src[i] * gain else if (j - cycleLen < tail.size) tail[j - cycleLen] += src[i] * gain
            }
        }
        val cache = HashMap<Triple<Double, Int, Double>, FloatArray>()
        val maxNotes = voicings.maxOf { it.size }.coerceAtLeast(1)
        val gain = (1.1 / sqrt(maxNotes.toDouble())).toFloat()
        for (s in strikes) {
            val buf = cache.getOrPut(Triple(s.frequency, s.frames, s.velocity)) {
                Synth.render(voice, s.frequency, sampleRate, s.frames.toDouble() / sampleRate, s.velocity)
            }
            mix(buf, s.start.toInt(), gain)
        }
        if (click) {
            val strong = Synth.click(1760.0, sampleRate, 0.55)
            val weak = Synth.click(1100.0, sampleRate, 0.32)
            for (b in 0 until timing.totalBeats) {
                mix(if (b % beatsPerChord == 0) strong else weak, timing.beatFrame(b.toLong()).toInt(), 1f)
            }
        }
        // Premier passage sans traîne, puis traîne repliée au début pour boucler sans couture.
        val head = cycle.copyOfRange(0, min(tail.size, cycleLen))
        for (j in tail.indices) cycle[j % cycleLen] += tail[j]
        Synth.softLimit(head)
        Synth.softLimit(cycle)
        return LoopAudio(timing, head, cycle)
    }

    private fun strikes(voicings: List<List<Double>>, timing: LoopTiming, style: Accompaniment): List<Strike> {
        val sr = timing.sampleRate
        val bpc = timing.beatsPerChord.toLong()
        val maxFrames = (MAX_NOTE_SEC * sr).toInt()
        val overlap = (0.08 * sr).toInt()
        fun ms(v: Double) = (v * sr / 1000).toLong()
        fun frames(from: Long, to: Long) = (to - from).toInt().coerceIn(1, maxFrames)
        val out = ArrayList<Strike>()
        voicings.forEachIndexed { i, notes ->
            if (notes.isEmpty()) return@forEachIndexed
            val chordStart = i * bpc
            val chordEndFrame = timing.beatFrame(chordStart + bpc)
            when (style) {
                Accompaniment.BLOCK -> {
                    val every = when {
                        bpc <= 4 -> bpc
                        bpc % 4 == 0L -> 4L
                        bpc % 3 == 0L -> 3L
                        else -> bpc
                    }
                    var b = 0L
                    while (b < bpc) {
                        val at = timing.beatFrame(chordStart + b)
                        val next = timing.beatFrame(chordStart + minOf(b + every, bpc))
                        notes.forEachIndexed { k, f ->
                            val start = at + ms(15.0 * k)
                            out += Strike(start, f, frames(start, next + overlap), if (b == 0L) 0.8 else 0.68)
                        }
                        b += every
                    }
                }
                Accompaniment.STRUM -> {
                    for (b in 0 until bpc) {
                        val beat = chordStart + b
                        val at = timing.beatFrame(beat)
                        val up = b % 2 == 1L // contretemps « haut » après les temps 2 et 4
                        val downEnd = if (up) timing.frameAt(2 * beat + 1, 2) else timing.beatFrame(beat + 1)
                        notes.forEachIndexed { k, f ->
                            val start = at + ms(11.0 * k)
                            out += Strike(start, f, frames(start, downEnd + overlap), if (b == 0L) 0.85 else 0.62)
                        }
                        if (up) {
                            val upAt = timing.frameAt(2 * beat + 1, 2)
                            val upper = notes.drop(notes.size / 2).reversed()
                            upper.forEachIndexed { k, f ->
                                val start = upAt + ms(9.0 * k)
                                out += Strike(start, f, frames(start, timing.beatFrame(beat + 1) + overlap), 0.42)
                            }
                        }
                    }
                }
                Accompaniment.ARPEGGIO -> {
                    val n = notes.size
                    val pattern = if (n <= 2) notes.indices.toList() else listOf(0) + (1 until n) + (n - 2 downTo 1)
                    for (j in 0 until 2 * bpc) {
                        val start = timing.frameAt(2 * chordStart + j, 2)
                        val ring = timing.frameAt(2 * chordStart + j + 3, 2)
                        val end = minOf(ring, chordEndFrame + overlap)
                        val velocity = when { j == 0L -> 0.85; j % 2 == 0L -> 0.7; else -> 0.6 }
                        out += Strike(start, notes[pattern[(j % pattern.size).toInt()]], frames(start, end), velocity)
                    }
                }
            }
        }
        return out
    }

    /**
     * Voicings de piano enchaînés : fondamentale grave à la main gauche, accord serré à la main droite
     * dans le renversement le plus proche du précédent (conduite des voix). Hauteurs MIDI.
     */
    fun pianoVoicings(chords: List<Chord>): List<List<Int>> {
        var previous: List<Int>? = null
        return chords.map { chord ->
            val pcs = chord.notes.map { it.pitchClass }.distinct()
            val candidates = pcs.indices.map { inv -> closeVoicing(pcs.drop(inv) + pcs.take(inv), low = 55) }
            val prev = previous
            val best = if (prev == null) candidates.minBy { abs(it.average() - 64.0) }
            else candidates.minBy { c -> distance(c, prev) }
            previous = best
            val bass = 36 + Math.floorMod(chord.root.pitchClass - 36, 12)
            listOf(bass) + best
        }
    }

    private fun closeVoicing(order: List<Int>, low: Int): List<Int> {
        val out = ArrayList<Int>()
        var m = low + Math.floorMod(order[0] - low, 12)
        out += m
        for (pc in order.drop(1)) {
            m += Math.floorMod(pc - m, 12).let { if (it == 0) 12 else it }
            out += m
        }
        return out
    }

    private fun distance(a: List<Int>, b: List<Int>): Double =
        if (a.size == b.size) a.zip(b).sumOf { (x, y) -> abs(x - y).toDouble() } else abs(a.average() - b.average()) * a.size
}
