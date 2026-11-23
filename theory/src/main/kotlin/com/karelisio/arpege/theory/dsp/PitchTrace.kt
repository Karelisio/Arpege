package com.karelisio.arpege.theory.dsp

import kotlin.math.abs
import kotlin.math.sign

/**
 * Estimation de hauteur horodatée sur l'horloge de capture : [timeNanos] est l'instant
 * (base CLOCK_MONOTONIC, comme System.nanoTime) du centre de la fenêtre analysée.
 */
data class TimedPitch(val timeNanos: Long, val estimate: PitchEstimate?)

/**
 * Historique glissant de l'écart en cents (courbe de justesse) sur [windowMs].
 * Lissage exponentiel ; le tracé est coupé sur un silence de plus de [gapMs],
 * un saut de plus de [jumpCents] ou un changement de note cible.
 */
class CentsTrace(
    val windowMs: Long = 5_000,
    private val smoothing: Double = 0.35,
    private val gapMs: Long = 250,
    private val jumpCents: Double = 30.0,
    val range: Double = 50.0,
) {
    data class Point(val timeMs: Long, val cents: Double)

    private val segments = ArrayDeque<MutableList<Point>>()
    private var lastKey: Int? = null
    private var lastRaw = 0.0

    /** Temps du dernier point (ou null si l'historique est vide). */
    val latestTimeMs: Long? get() = segments.lastOrNull()?.lastOrNull()?.timeMs

    /**
     * Ajoute une mesure ; [cents] null = pas de note nette. [key] identifie la note cible
     * (ex. son numéro MIDI) : un changement de cible coupe le tracé. Temps croissants.
     */
    fun add(timeMs: Long, cents: Double?, key: Int = 0) {
        prune(timeMs)
        if (cents == null) return
        val current = segments.lastOrNull()
        val last = current?.lastOrNull()
        val clamped = cents.coerceIn(-range, range)
        if (current != null && last != null && key == lastKey && timeMs - last.timeMs <= gapMs && abs(cents - lastRaw) <= jumpCents) {
            current.add(Point(timeMs, last.cents + smoothing * (clamped - last.cents)))
        } else {
            segments.addLast(mutableListOf(Point(timeMs, clamped)))
        }
        lastKey = key
        lastRaw = cents
    }

    /** Supprime les points sortis de la fenêtre se terminant à [nowMs]. */
    fun prune(nowMs: Long) {
        val from = nowMs - windowMs
        while (segments.isNotEmpty()) {
            val first = segments.first()
            while (first.isNotEmpty() && first.first().timeMs < from) first.removeAt(0)
            if (first.isEmpty()) segments.removeFirst() else break
        }
    }

    /** Segments continus, du plus ancien au plus récent (copie). */
    fun segments(): List<List<Point>> = segments.map { it.toList() }

    fun clear() {
        segments.clear()
        lastKey = null
    }
}

/**
 * Accordeur stroboscopique : le motif défile à une vitesse proportionnelle à l'écart
 * (vers la droite si trop haut, vers la gauche si trop bas) et s'immobilise quand c'est juste.
 */
object Strobe {
    /** Périodes de motif par seconde et par cent d'écart. */
    const val PERIODS_PER_CENT = 0.07

    /** Écart (cents) en deçà duquel le motif est considéré immobile (bruit de mesure). */
    const val DEAD_ZONE = 0.5

    /** Vitesse en périodes de motif par seconde (signe = sens de défilement). */
    fun speed(cents: Double): Double {
        val excess = abs(cents) - DEAD_ZONE
        return if (excess <= 0) 0.0 else sign(cents) * excess * PERIODS_PER_CENT
    }

    /** Fait avancer la phase (en périodes, ramenée dans [0, 1)) de [dtSec] secondes. */
    fun advance(phase: Double, cents: Double, dtSec: Double): Double {
        val p = (phase + speed(cents) * dtSec) % 1.0
        return if (p < 0) p + 1.0 else p
    }
}
