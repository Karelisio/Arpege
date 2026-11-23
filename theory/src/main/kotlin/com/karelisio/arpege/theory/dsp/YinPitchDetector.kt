package com.karelisio.arpege.theory.dsp

import kotlin.math.sqrt

data class PitchEstimate(val frequency: Double, val clarity: Double, val rms: Double)

/**
 * Détection de hauteur YIN (de Cheveigné & Kawahara, 2002) avec interpolation parabolique
 * sur la fonction de différence brute pour une précision sub-échantillon.
 */
class YinPitchDetector(
    private val sampleRate: Int,
    private val bufferSize: Int,
    private val threshold: Double = 0.12,
    minFrequency: Double = 25.0,
    maxFrequency: Double = 2200.0,
) {
    private val window = bufferSize / 2
    private val maxTau = minOf(window - 1, (sampleRate / minFrequency).toInt())
    private val minTau = maxOf(2, (sampleRate / maxFrequency).toInt())
    private val diff = DoubleArray(maxTau + 2)
    private val cmnd = DoubleArray(maxTau + 2)

    fun detect(buffer: FloatArray, silenceRms: Double = 0.005): PitchEstimate? {
        require(buffer.size >= bufferSize)
        var sq = 0.0
        for (i in 0 until bufferSize) sq += buffer[i] * buffer[i]
        val rms = sqrt(sq / bufferSize)
        if (rms < silenceRms) return null

        // 1. Fonction de différence.
        for (tau in 1..maxTau + 1) {
            var sum = 0.0
            for (j in 0 until window) {
                val d = buffer[j] - buffer[j + tau]
                sum += d * d
            }
            diff[tau] = sum
        }
        // 2. Différence moyenne normalisée cumulée.
        cmnd[0] = 1.0
        var running = 0.0
        for (tau in 1..maxTau + 1) {
            running += diff[tau]
            cmnd[tau] = if (running == 0.0) 1.0 else diff[tau] * tau / running
        }
        // 3. Premier creux sous le seuil, puis minimum local.
        var tau = -1
        var t = minTau
        while (t <= maxTau) {
            if (cmnd[t] < threshold) {
                while (t + 1 <= maxTau && cmnd[t + 1] < cmnd[t]) t++
                tau = t
                break
            }
            t++
        }
        if (tau < 0) {
            // Pas de creux net : minimum global si raisonnablement périodique.
            var best = minTau
            for (k in minTau..maxTau) if (cmnd[k] < cmnd[best]) best = k
            if (cmnd[best] > 0.35) return null
            tau = best
        }
        // 4. Interpolation parabolique sur la différence brute.
        val refined = if (tau in 2..maxTau) {
            val a = diff[tau - 1]; val b = diff[tau]; val c = diff[tau + 1]
            val den = a - 2 * b + c
            if (den > 0) tau + 0.5 * (a - c) / den else tau.toDouble()
        } else tau.toDouble()
        return PitchEstimate(sampleRate / refined, 1.0 - cmnd[tau].coerceIn(0.0, 1.0), rms)
    }
}
