package com.karelisio.arpege.theory.dsp

/** Niveau d'accent d'un temps. */
enum class Accent { STRONG, MEDIUM, WEAK, MUTE;
    fun next(): Accent = entries[(ordinal + 1) % entries.size]
}

/**
 * Mesure : [beats] temps de valeur 1/[unit]. [groups] regroupe les temps (ex. 7/8 = 2+2+3) ;
 * le premier temps de chaque groupe reçoit un accent moyen.
 */
data class Meter(val beats: Int, val unit: Int, val groups: List<Int> = defaultGroups(beats, unit)) {
    val label: String get() = "$beats/$unit"
    val isCompound: Boolean get() = unit == 8 && beats % 3 == 0 && beats > 3

    fun defaultAccents(): List<Accent> {
        val starts = groups.runningFold(0) { a, g -> a + g }.dropLast(1).toSet()
        return List(beats) { i -> when { i == 0 -> Accent.STRONG; i in starts -> Accent.MEDIUM; else -> Accent.WEAK } }
    }

    companion object {
        fun defaultGroups(beats: Int, unit: Int): List<Int> = when {
            unit == 8 && beats % 3 == 0 && beats > 3 -> List(beats / 3) { 3 }
            unit == 8 && beats == 5 -> listOf(3, 2)
            unit == 8 && beats == 7 -> listOf(2, 2, 3)
            unit == 8 && beats == 11 -> listOf(3, 3, 3, 2)
            unit == 4 && beats == 5 -> listOf(3, 2)
            else -> listOf(beats)
        }

        val PRESETS = listOf(
            Meter(2, 4), Meter(3, 4), Meter(4, 4), Meter(5, 4), Meter(6, 8),
            Meter(7, 8), Meter(5, 8), Meter(9, 8), Meter(12, 8), Meter(7, 4),
        )
    }
}

/** Réglages du métronome. BPM entier : l'ordonnancement est alors exact en arithmétique entière. */
data class MetronomeConfig(
    val bpm: Int = 100,
    val meter: Meter = Meter(4, 4),
    val subdivision: Int = 1,
    val accents: List<Accent> = meter.defaultAccents(),
) {
    init { require(bpm in 1..1000 && subdivision in 1..4) }
}

data class Tick(val index: Long, val beat: Int, val sub: Int, val accent: Accent, val subdivision: Boolean)

/**
 * Ordonnanceur à l'échantillon près. La position du tick n est calculée directement depuis n
 * (jamais par accumulation) : aucune dérive, même sur des heures.
 */
class MetronomeSchedule(val sampleRate: Int, val config: MetronomeConfig, val originFrame: Long = 0) {
    private val num = sampleRate.toLong() * 60L              // échantillons par minute
    private val den = config.bpm.toLong() * config.subdivision // ticks par minute
    val ticksPerBar: Int get() = config.meter.beats * config.subdivision

    /** Échantillon de départ du tick n (arrondi au plus proche). */
    fun tickFrame(n: Long): Long = originFrame + (n * num * 2 + den) / (2 * den)

    /** Index du dernier tick commencé à l'échantillon [frame] (‑1 avant le premier). */
    fun tickAt(frame: Long): Long {
        if (frame < originFrame) return -1
        var n = ((frame - originFrame) * den) / num
        while (tickFrame(n + 1) <= frame) n++
        while (n > 0 && tickFrame(n) > frame) n--
        return n
    }

    fun tick(n: Long): Tick {
        val inBar = (n % ticksPerBar).toInt()
        val beat = inBar / config.subdivision
        val sub = inBar % config.subdivision
        val accent = if (sub == 0) config.accents.getOrElse(beat) { Accent.WEAK } else Accent.WEAK
        return Tick(n, beat, sub, accent, sub != 0)
    }
}

/** Génère le flux audio des clics dans des tampons successifs (pure, testable). */
class MetronomeRenderer(sampleRate: Int) {
    private val strong = Synth.click(1760.0, sampleRate, 0.95)
    private val medium = Synth.click(1320.0, sampleRate, 0.75)
    private val weak = Synth.click(880.0, sampleRate, 0.6)
    private val subdiv = Synth.click(660.0, sampleRate, 0.32, durationMs = 18.0)
    private val maxLen = maxOf(strong.size, medium.size, weak.size, subdiv.size)

    var schedule: MetronomeSchedule? = null

    fun clickFor(t: Tick): FloatArray? = when {
        t.subdivision -> subdiv
        t.accent == Accent.STRONG -> strong
        t.accent == Accent.MEDIUM -> medium
        t.accent == Accent.WEAK -> weak
        else -> null
    }

    /** Remplit [out] pour les échantillons [startFrame, startFrame + out.size). */
    fun render(out: FloatArray, startFrame: Long) {
        out.fill(0f)
        val s = schedule ?: return
        val end = startFrame + out.size
        var n = maxOf(0L, s.tickAt(startFrame - maxLen))
        while (true) {
            val f = s.tickFrame(n)
            if (f >= end) break
            val click = clickFor(s.tick(n))
            if (click != null && f + click.size > startFrame) {
                Synth.mix(out, click, (f - startFrame).toInt(), 1f)
            }
            n++
        }
    }
}

/** Noms de tempo usuels. */
object TempoNames {
    private val names = listOf(
        0 to "Larghissimo", 25 to "Grave", 40 to "Largo", 60 to "Larghetto", 66 to "Adagio",
        76 to "Andante", 108 to "Moderato", 120 to "Allegro", 156 to "Vivace", 176 to "Presto", 200 to "Prestissimo",
    )

    fun of(bpm: Int): String = names.last { bpm >= it.first }.second
}

/** Tap tempo : moyenne des derniers intervalles, réinitialisé après une pause. */
class TapTempo(private val maxTaps: Int = 6, private val resetMs: Long = 2000) {
    private val taps = ArrayDeque<Long>()

    fun tap(timeMs: Long): Int? {
        if (taps.isNotEmpty() && timeMs - taps.last() > resetMs) taps.clear()
        taps.addLast(timeMs)
        while (taps.size > maxTaps) taps.removeFirst()
        if (taps.size < 2) return null
        val avg = (taps.last() - taps.first()).toDouble() / (taps.size - 1)
        return (60000.0 / avg).toInt().coerceIn(20, 300)
    }
}
