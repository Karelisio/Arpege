package com.karelisio.arpege.theory

/**
 * Accord proposé pour un ensemble de notes. [score] : plus il est bas, plus l'accord est probable.
 * [missing] : intervalles de l'accord absents (accord incomplet). [bassInterval] : intervalle de la
 * basse au-dessus de la fondamentale si l'accord est renversé ; [foreignBass] : basse hors accord (« D/C »).
 */
data class ChordCandidate(
    val chord: Chord,
    val score: Double,
    val missing: List<Interval>,
    val bassInterval: Interval?,
    val foreignBass: Boolean,
) {
    val complete: Boolean get() = missing.isEmpty()
    val rootPosition: Boolean get() = bassInterval == null && !foreignBass
}

/** Identification d'accords à partir de notes jouées (manche ou clavier), renversements compris. */
object ChordIdentifier {
    /** A priori par type : les accords usuels passent devant les plus rares à égalité de notes. */
    private val PRIOR = mapOf(
        ChordType.MAJOR to 0.0, ChordType.MINOR to 0.1, ChordType.POWER to 0.2, ChordType.DOM7 to 0.3,
        ChordType.MIN7 to 0.35, ChordType.MAJ7 to 0.4, ChordType.SUS4 to 0.5, ChordType.SUS2 to 0.6,
        ChordType.SIX to 0.6, ChordType.MIN6 to 0.7, ChordType.ADD9 to 0.7, ChordType.M7B5 to 0.7,
        ChordType.DIM to 0.8, ChordType.SEVEN_SUS4 to 0.8, ChordType.AUG to 0.9, ChordType.DIM7 to 0.9,
        ChordType.DOM9 to 1.0, ChordType.MAJ9 to 1.1, ChordType.MIN9 to 1.1, ChordType.MIN_MAJ7 to 1.2,
    )

    /** Pénalité pour une note absente, selon son rôle (la quinte juste est souvent omise). */
    private fun missingPenalty(i: Interval): Double = when {
        i == Interval.P5 -> 0.6
        i.degree == 5 -> 1.8
        i.degree == 3 -> 1.8
        i.degree == 7 -> 2.2
        else -> 2.0
    }

    /** Pénalité de renversement selon la note à la basse. */
    private fun bassPenalty(i: Interval): Double = when (i.degree) {
        3 -> 0.5
        5 -> 0.8
        7 -> 1.0
        else -> 1.2
    }

    private const val FOREIGN_BASS = 1.5

    /** Hauteurs MIDI jouées par une position ([frets] : -1 = étouffée, 0 = à vide), de la corde grave à l'aiguë. */
    fun pitchesFromFrets(frets: List<Int>, tuning: Tuning, capo: Int = 0): List<Int> =
        frets.mapIndexedNotNull { s, f -> if (f < 0 || s >= tuning.strings.size) null else tuning.strings[s].midi + capo + f }

    /** Analyse une position notée « x32010 » (un caractère par corde, de la grave à l'aiguë). */
    fun parseFrets(text: String): List<Int> = text.map { c -> if (c == 'x' || c == 'X') -1 else c.digitToInt() }

    /**
     * Accords possibles pour des hauteurs MIDI (la plus grave = basse), du plus probable au moins
     * probable. Les notes hors accord ne sont admises qu'à la basse ; les accords incomplets sont
     * pénalisés. Vide s'il y a moins de deux classes de hauteur.
     */
    fun identify(midi: Collection<Int>, limit: Int = 8): List<ChordCandidate> {
        if (midi.isEmpty()) return emptyList()
        val sorted = midi.sorted()
        val bassPc = mod12(sorted.first())
        val pcs = sorted.map { mod12(it) }.toSet()
        if (pcs.size < 2) return emptyList()
        val upper = sorted.drop(1).map { mod12(it) }.toSet()
        val out = ArrayList<ChordCandidate>()
        for (rootPc in pcs) {
            val root = Note.common(rootPc)
            for (type in ChordType.entries) {
                val tones = type.intervals.associateBy { mod12(rootPc + it.semitones) }
                if (!tones.keys.containsAll(upper)) continue
                val bassTone = tones[bassPc]
                val foreign = bassTone == null
                val missing = type.intervals.filter { mod12(rootPc + it.semitones) !in pcs }
                val present = type.intervals.size - missing.size
                if (present < 2 || missing.size > 2) continue
                var score = PRIOR.getValue(type) + missing.sumOf { missingPenalty(it) }
                val inversion = bassTone?.takeIf { it != Interval.P1 }
                if (inversion != null) score += bassPenalty(inversion)
                if (foreign) score += FOREIGN_BASS
                val bass = when {
                    inversion != null -> root + inversion
                    foreign -> if (root.accidental < 0) Note.fromPitchClass(bassPc, true) else Note.common(bassPc)
                    else -> null
                }
                out += ChordCandidate(Chord(root, type, bass), score, missing, inversion, foreign)
            }
        }
        return out.sortedWith(
            compareBy<ChordCandidate> { it.score }
                .thenBy { it.chord.type.intervals.size }
                .thenBy { if (it.rootPosition) 0 else 1 },
        ).take(limit)
    }
}
