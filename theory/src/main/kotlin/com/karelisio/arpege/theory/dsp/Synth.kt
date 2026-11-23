package com.karelisio.arpege.theory.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Timbres disponibles. */
enum class Voice { STRING, PIANO, SINE }

/** Note à jouer dans une séquence. */
data class NoteEvent(val startSec: Double, val frequency: Double, val durationSec: Double, val velocity: Double = 0.8)

object Synth {

    /** Corde pincée Karplus-Strong avec retard fractionnaire (pas juste) et filtre de brillance. */
    fun pluck(frequency: Double, sampleRate: Int, durationSec: Double, velocity: Double = 0.8, seed: Int = 1): FloatArray {
        val total = (durationSec * sampleRate).toInt()
        val out = FloatArray(total)
        // Le filtre moyenneur ajoute ~0,5 échantillon de retard.
        val period = sampleRate / frequency - 0.5
        val n = max(2, period.toInt())
        val frac = period - n
        // Passe-tout du 1er ordre pour le retard fractionnaire.
        val c = (1 - frac) / (1 + frac)
        val line = DoubleArray(n)
        val rnd = Random(seed)
        // Excitation : bruit filtré (plus doux pour les graves).
        var prev = 0.0
        val brightness = (0.35 + 0.5 * velocity).coerceIn(0.2, 0.95)
        for (i in 0 until n) {
            val w = rnd.nextDouble(-1.0, 1.0)
            prev = brightness * w + (1 - brightness) * prev
            line[i] = prev * velocity
        }
        // Décroissance dépendante de la fréquence (graves plus longues).
        val decay = (0.9965 + 0.0025 * (1 - min(1.0, frequency / 1000.0))).coerceAtMost(0.9993)
        var idx = 0
        var apX1 = 0.0
        var apY1 = 0.0
        var last = line[n - 1]
        for (t in 0 until total) {
            val cur = line[idx]
            val avg = decay * 0.5 * (cur + last)
            last = cur
            val ap = c * avg + apX1 - c * apY1
            apX1 = avg; apY1 = ap
            line[idx] = ap
            out[t] = cur.toFloat()
            idx = if (idx + 1 == n) 0 else idx + 1
        }
        applyFade(out, sampleRate)
        return out
    }

    /** Piano en synthèse additive : partiels légèrement inharmoniques, enveloppe percussive. */
    fun piano(frequency: Double, sampleRate: Int, durationSec: Double, velocity: Double = 0.8): FloatArray {
        val total = (durationSec * sampleRate).toInt()
        val out = FloatArray(total)
        val b = 0.00008 // inharmonicité légère (reste juste à l'oreille et au détecteur)
        val partials = (1..10).filter { it * frequency < sampleRate * 0.45 }
        val attack = (0.004 * sampleRate).toInt().coerceAtLeast(1)
        for (k in partials) {
            val fk = frequency * k * sqrt(1 + b * k * k)
            val amp = velocity * (1.0 / k) * (if (k == 1) 1.0 else 0.6) * exp(-0.15 * k * (1 - velocity))
            val tau = (1.8 * 261.6 / frequency).coerceIn(0.3, 4.0) / (1 + 0.5 * (k - 1))
            val w = 2 * PI * fk / sampleRate
            // Oscillateur récursif (rotation) et enveloppe multiplicative : pas de sin/exp par échantillon.
            val c = cos(w); val s = sin(w)
            var re = 1.0; var im = 0.0
            val decay = exp(-1.0 / (tau * sampleRate))
            var env = amp
            for (t in 0 until total) {
                val a = if (t < attack) t.toDouble() / attack else 1.0
                out[t] += (env * a * im).toFloat()
                val nre = re * c - im * s
                im = re * s + im * c
                re = nre
                env *= decay
                if ((t and 4095) == 0) { val m = sqrt(re * re + im * im); re /= m; im /= m }
            }
        }
        normalizePeak(out, 0.6f * velocity.toFloat())
        applyFade(out, sampleRate)
        return out
    }

    /** Sinus pur avec enveloppe douce (note de référence de l'accordeur). */
    fun sine(frequency: Double, sampleRate: Int, durationSec: Double, gain: Double = 0.5): FloatArray {
        val total = (durationSec * sampleRate).toInt()
        val w = 2 * PI * frequency / sampleRate
        val ramp = (0.02 * sampleRate).toInt()
        return FloatArray(total) { t ->
            val env = min(1.0, min(t.toDouble() / ramp, (total - t).toDouble() / ramp))
            (gain * env * sin(w * t)).toFloat()
        }
    }

    /** Clic de métronome : cosinus amorti (premier échantillon non nul → attaque nette). */
    fun click(frequency: Double, sampleRate: Int, gain: Double, durationMs: Double = 28.0): FloatArray {
        val total = (durationMs / 1000.0 * sampleRate).toInt()
        val w = 2 * PI * frequency / sampleRate
        val decay = 5.0 / total
        return FloatArray(total) { t -> (gain * exp(-t * decay) * cos(w * t)).toFloat() }
    }

    fun render(voice: Voice, frequency: Double, sampleRate: Int, durationSec: Double, velocity: Double = 0.8): FloatArray =
        when (voice) {
            Voice.STRING -> pluck(frequency, sampleRate, durationSec, velocity, seed = (frequency * 7).toInt())
            Voice.PIANO -> piano(frequency, sampleRate, durationSec, velocity)
            Voice.SINE -> sine(frequency, sampleRate, durationSec, 0.5 * velocity)
        }

    /** Mixe une séquence de notes dans un seul tampon. */
    fun sequence(events: List<NoteEvent>, voice: Voice, sampleRate: Int, tailSec: Double = 0.2): FloatArray {
        if (events.isEmpty()) return FloatArray(0)
        val end = events.maxOf { it.startSec + it.durationSec } + tailSec
        val out = FloatArray((end * sampleRate).toInt())
        for (e in events) {
            val buf = render(voice, e.frequency, sampleRate, e.durationSec, e.velocity)
            mix(out, buf, (e.startSec * sampleRate).toInt(), 1f)
        }
        softLimit(out)
        return out
    }

    fun mix(into: FloatArray, src: FloatArray, offset: Int, gain: Float) {
        val from = max(0, -offset)
        val to = min(src.size, into.size - offset)
        for (i in from until to) into[offset + i] += src[i] * gain
    }

    fun normalizePeak(buf: FloatArray, target: Float) {
        var peak = 0f
        for (v in buf) peak = max(peak, kotlin.math.abs(v))
        if (peak > 0f) { val g = target / peak; for (i in buf.indices) buf[i] *= g }
    }

    /** Limiteur doux (tanh) pour éviter la saturation des accords plaqués. */
    fun softLimit(buf: FloatArray) {
        for (i in buf.indices) buf[i] = kotlin.math.tanh(buf[i].toDouble()).toFloat() * 0.9f
    }

    private fun applyFade(buf: FloatArray, sampleRate: Int) {
        val fade = min(buf.size, (0.03 * sampleRate).toInt())
        for (i in 0 until fade) buf[buf.size - 1 - i] *= i.toFloat() / fade
    }
}
