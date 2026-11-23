package com.karelisio.arpege.theory.dsp

import kotlin.math.max
import kotlin.math.tanh

/**
 * Rendu en flux d'une longue suite de notes (lecture de tablature) : chaque note est synthétisée
 * au moment où la lecture l'atteint et mixée dans un tampon circulaire. La mémoire reste bornée
 * (durée maximale d'une note + un bloc), quelle que soit la longueur du morceau.
 *
 * Les lectures doivent être séquentielles ([read] avec des [startFrame] contigus croissants).
 */
class NoteStream(
    events: List<NoteEvent>,
    private val voice: Voice,
    private val sampleRate: Int,
    private val gain: Float = 0.7f,
    maxNoteSec: Double = 4.0,
    tailSec: Double = 0.4,
) {
    private val notes = events.sortedBy { it.startSec }
    private val maxNoteFrames = (maxNoteSec * sampleRate).toInt().coerceAtLeast(1)
    private val ring: FloatArray
    private val mask: Int
    private var next = 0

    /** Longueur totale du flux (fin de la dernière note + traîne), en échantillons. */
    val totalFrames: Long = if (notes.isEmpty()) 0L
    else ((notes.maxOf { it.startSec + minOf(it.durationSec, maxNoteSec) } + tailSec) * sampleRate).toLong()

    init {
        var cap = 1
        while (cap < maxNoteFrames + MAX_BLOCK * 2) cap = cap shl 1
        ring = FloatArray(cap)
        mask = cap - 1
    }

    /**
     * Remplit [out] pour les échantillons [startFrame, startFrame + out.size). Renvoie le nombre
     * d'échantillons valides : moins que out.size (voire 0) en fin de morceau.
     */
    fun read(out: FloatArray, startFrame: Long): Int {
        require(out.size <= MAX_BLOCK) { "Bloc trop grand" }
        val end = startFrame + out.size
        // Synthétise toutes les notes qui commencent avant la fin du bloc.
        while (next < notes.size) {
            val e = notes[next]
            val start = (e.startSec * sampleRate).toLong()
            if (start >= end) break
            val dur = minOf(e.durationSec, maxNoteFrames.toDouble() / sampleRate)
            val buf = Synth.render(voice, e.frequency, sampleRate, dur, e.velocity)
            val from = max(0L, startFrame - start).toInt()
            for (i in from until buf.size) {
                val idx = ((start + i) and mask.toLong()).toInt()
                ring[idx] += buf[i]
            }
            next++
        }
        val valid = (totalFrames - startFrame).coerceIn(0L, out.size.toLong()).toInt()
        for (i in out.indices) {
            val idx = ((startFrame + i) and mask.toLong()).toInt()
            out[i] = if (i < valid) (tanh((ring[idx] * gain).toDouble()) * 0.9).toFloat() else 0f
            ring[idx] = 0f
        }
        return valid
    }

    companion object {
        /** Taille maximale d'un bloc lu en une fois. */
        const val MAX_BLOCK = 8192
    }
}
