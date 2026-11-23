package com.karelisio.arpege.theory.dsp

/**
 * Accélération progressive (« speed trainer ») : +[stepBpm] toutes les [everyBars] mesures,
 * sans dépasser [maxBpm].
 */
data class SpeedTrainer(val stepBpm: Int = 5, val everyBars: Int = 4, val maxBpm: Int = 160) {
    init { require(stepBpm in STEP_RANGE && everyBars in BARS_RANGE && maxBpm in 20..300) }

    /** Tempo attendu après [bars] mesures complètes en partant de [startBpm] (jamais plus lent). */
    fun bpmAfter(startBpm: Int, bars: Long): Int {
        if (bars <= 0 || startBpm >= maxBpm) return startBpm
        val stepsToMax = (maxBpm - startBpm + stepBpm - 1) / stepBpm
        val steps = minOf(bars / everyBars, stepsToMax.toLong()).toInt()
        return minOf(maxBpm, startBpm + steps * stepBpm)
    }

    /** Mesures restantes avant le prochain palier, ou null si le tempo maximal est atteint. */
    fun barsToNextStep(startBpm: Int, bars: Long): Int? {
        if (bpmAfter(startBpm, bars) >= maxBpm) return null
        val b = maxOf(0L, bars)
        return (everyBars - b % everyBars).toInt()
    }

    fun encode(): String = "$stepBpm,$everyBars,$maxBpm"

    companion object {
        val STEP_RANGE = 1..20
        val BARS_RANGE = 1..16

        fun decode(s: String?): SpeedTrainer? = runCatching {
            val (a, b, c) = s!!.split(',').map { it.trim().toInt() }
            SpeedTrainer(a, b, c)
        }.getOrNull()
    }
}

/**
 * Applique un [SpeedTrainer] à l'échantillon près : avant de rendre chaque tampon, les mesures qui
 * y commencent sont comptées et le tempo change exactement au premier temps de la mesure
 * (via [MetronomeSchedule.retimed], sans aucun timer).
 */
class SpeedTrainerRun(val trainer: SpeedTrainer, val startBpm: Int) {
    /** Mesures commencées depuis le départ, la première comptant 0 (‑1 avant la première). */
    var bars = -1L
        private set
    private var nextBar = UNKNOWN
    private var processedUntil = Long.MIN_VALUE

    val currentTarget: Int get() = trainer.bpmAfter(startBpm, bars)

    /** À appeler quand le planning a changé de l'extérieur (mesure, subdivision…). */
    fun resync() { nextBar = UNKNOWN }

    /** Traite les débuts de mesure situés avant [until] ; renvoie le planning à utiliser. */
    fun advance(schedule: MetronomeSchedule, from: Long, until: Long): MetronomeSchedule {
        var s = schedule
        if (nextBar == UNKNOWN) nextBar = s.nextBarFrame(maxOf(from, processedUntil))
        while (nextBar < until) {
            bars++
            val cur = s.at(nextBar)
            val bpm = trainer.bpmAfter(startBpm, bars)
            if (bpm != cur.config.bpm) s = s.retimed(cur.config.copy(bpm = bpm), nextBar - 1)
            nextBar = s.nextBarFrame(nextBar + 1)
        }
        processedUntil = maxOf(processedUntil, until)
        return s
    }

    private companion object { const val UNKNOWN = Long.MIN_VALUE }
}

/** Réglage favori : tempo, mesure et subdivision sous un nom court. */
data class MetronomeFavorite(val name: String, val bpm: Int, val meter: Meter, val subdivision: Int) {
    fun matches(c: MetronomeConfig): Boolean =
        c.bpm == bpm && c.meter.beats == meter.beats && c.meter.unit == meter.unit && c.subdivision == subdivision

    companion object {
        const val MAX_NAME = 20
        const val MAX_COUNT = 24

        /** Nom nettoyé des séparateurs de la sérialisation. */
        fun cleanName(raw: String): String = raw.replace(Regex("[|\\r\\n\\t]"), " ").trim().take(MAX_NAME).trim()

        fun of(name: String, c: MetronomeConfig) =
            MetronomeFavorite(cleanName(name).ifEmpty { "${c.bpm} · ${c.meter.label}" }, c.bpm, Meter(c.meter.beats, c.meter.unit), c.subdivision)

        /** Une ligne par favori : `nom|bpm|temps|unité|subdivision`. */
        fun encode(list: List<MetronomeFavorite>): String =
            list.joinToString("\n") { "${cleanName(it.name)}|${it.bpm}|${it.meter.beats}|${it.meter.unit}|${it.subdivision}" }

        /** Lecture tolérante : les lignes invalides sont ignorées. */
        fun decode(s: String?): List<MetronomeFavorite> = s.orEmpty().lineSequence().mapNotNull { line ->
            val p = line.split('|')
            if (p.size != 5) return@mapNotNull null
            val bpm = p[1].toIntOrNull()?.takeIf { it in 20..300 } ?: return@mapNotNull null
            val beats = p[2].toIntOrNull()?.takeIf { it in 1..16 } ?: return@mapNotNull null
            val unit = p[3].toIntOrNull()?.takeIf { it in setOf(2, 4, 8, 16) } ?: return@mapNotNull null
            val sub = p[4].toIntOrNull()?.takeIf { it in 1..4 } ?: return@mapNotNull null
            val name = cleanName(p[0]).ifEmpty { return@mapNotNull null }
            MetronomeFavorite(name, bpm, Meter(beats, unit), sub)
        }.take(MAX_COUNT).toList()
    }
}
