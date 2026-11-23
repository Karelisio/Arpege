package com.karelisio.arpege.theory.training

import kotlin.math.abs
import kotlin.random.Random

/** Ce que l'on demande de chanter à partir de la note de référence. */
enum class SingMode(val label: String, val sign: Int) {
    UNISON("Unisson", 0),
    UP("Montant", 1),
    DOWN("Descendant", -1),
}

/** Tessiture : bornes MIDI (incluses) des notes de référence et cible. */
enum class VoiceRange(val label: String, val low: Int, val high: Int) {
    LOW("Voix grave", 45, 65), // La2 – Fa4
    HIGH("Voix aiguë", 57, 77), // La3 – Fa5
}

/** Une question : note de référence jouée, note cible à chanter (numéros MIDI). */
data class SingQuestion(val item: EarItem, val referenceMidi: Int, val targetMidi: Int) {
    val semitones: Int get() = targetMidi - referenceMidi
}

object SingTraining {
    /** Clé des statistiques (StatsRepository). */
    const val EXERCISE = "sing"

    val UNISON_ITEM = EarItem("u", "Unisson")

    fun levelCount(): Int = EarTraining.levelCount(EarMode.INTERVALS)

    /** Éléments jusqu'au niveau donné ; identifiants « i7+ » (montant) / « i7- » (descendant). */
    fun items(mode: SingMode, level: Int): List<EarItem> =
        if (mode == SingMode.UNISON) listOf(UNISON_ITEM)
        else EarTraining.itemsUpTo(EarMode.INTERVALS, level).map { directed(it, mode) }

    /** Tous les éléments possibles (libellés des statistiques). */
    fun allItems(): List<EarItem> =
        listOf(UNISON_ITEM) + SingMode.entries.filter { it != SingMode.UNISON }.flatMap { items(it, levelCount()) }

    private fun directed(item: EarItem, mode: SingMode): EarItem {
        val up = mode == SingMode.UP
        return EarItem(item.id + if (up) "+" else "-", item.label + if (up) " ↑" else " ↓")
    }

    /** Demi-tons signés d'un élément (« i7- » → −7, « u » → 0). */
    fun semitones(item: EarItem): Int {
        if (item.id == UNISON_ITEM.id) return 0
        val n = item.id.removePrefix("i").dropLast(1).toInt()
        return if (item.id.endsWith("-")) -n else n
    }

    /** Tire une note de référence telle que la référence et la cible restent dans la tessiture. */
    fun question(item: EarItem, range: VoiceRange, random: Random): SingQuestion {
        val semis = semitones(item)
        val low = maxOf(range.low, range.low - semis)
        val high = minOf(range.high, range.high - semis)
        val ref = if (high >= low) random.nextInt(low, high + 1) else (range.low + range.high) / 2
        return SingQuestion(item, ref, ref + semis)
    }

    /**
     * Écart en cents entre la hauteur chantée et la cible ; en [octaveFree], ramené à
     * l'octave la plus proche (dans [−600, 600[).
     */
    fun deviationCents(sungMidi: Double, targetMidi: Int, octaveFree: Boolean): Double {
        val cents = (sungMidi - targetMidi) * 100
        if (!octaveFree) return cents
        val r = ((cents + 600) % 1200 + 1200) % 1200 - 600
        return r
    }
}

/**
 * Valide une note tenue à partir d'une suite d'estimations horodatées :
 * la hauteur doit rester à ±[toleranceCents] de la cible pendant [holdMs].
 * Les écarts ou silences plus courts que [graceMs] (attaque, vibrato, souffle) sont tolérés.
 */
class HoldDetector(
    val targetMidi: Int,
    val octaveFree: Boolean = false,
    val toleranceCents: Double = 25.0,
    val holdMs: Long = 600,
    val graceMs: Long = 120,
    private val smoothing: Double = 0.45,
) {
    /**
     * [cents] : écart lissé à la cible (null = rien entendu) ; [inZone] : dans la tolérance ;
     * [progress] de la tenue 0..1 ; [held] : note validée.
     */
    data class State(val sungMidi: Double?, val cents: Double?, val inZone: Boolean, val progress: Float, val held: Boolean) {
        companion object {
            val EMPTY = State(null, null, false, 0f, false)
        }
    }

    private companion object {
        const val NEVER = Long.MIN_VALUE / 2
    }

    private var smooth: Double? = null
    private var lastHeardMs = NEVER
    private var holdStartMs: Long? = null
    private var lastInMs = NEVER
    private var held = false
    var state = State.EMPTY
        private set

    fun reset() {
        smooth = null
        lastHeardMs = NEVER
        holdStartMs = null
        lastInMs = NEVER
        held = false
        state = State.EMPTY
    }

    /** Ajoute une estimation ([midi] null = silence ou hauteur peu nette) ; temps croissants. */
    fun feed(timeMs: Long, midi: Double?): State {
        if (held) return state
        if (midi == null) {
            if (timeMs - lastHeardMs > graceMs) {
                smooth = null
                holdStartMs = null
                state = State.EMPTY
            }
            return state
        }
        val prev = smooth
        // Lissage exponentiel, réinitialisé après un silence ou un saut de plus d'un demi-ton.
        val s = if (prev == null || timeMs - lastHeardMs > graceMs || abs(midi - prev) > 0.6) midi else prev + smoothing * (midi - prev)
        smooth = s
        lastHeardMs = timeMs
        val cents = SingTraining.deviationCents(s, targetMidi, octaveFree)
        val inZone = abs(cents) <= toleranceCents
        if (inZone) {
            if (holdStartMs == null) holdStartMs = timeMs
            lastInMs = timeMs
        } else if (timeMs - lastInMs > graceMs) {
            holdStartMs = null
        }
        val start = holdStartMs
        // Hors tolérance (écart toléré), la progression est figée à la dernière mesure juste.
        val progress = if (start == null) 0f else (((if (inZone) timeMs else lastInMs) - start).toFloat() / holdMs).coerceIn(0f, 1f)
        held = inZone && progress >= 1f
        state = State(s, cents, inZone, progress, held)
        return state
    }
}
