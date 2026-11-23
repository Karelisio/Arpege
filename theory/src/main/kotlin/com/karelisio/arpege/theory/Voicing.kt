package com.karelisio.arpege.theory

/** Barré : un doigt couvre plusieurs cordes à la même case. */
data class Barre(val fret: Int, val fromString: Int, val toString: Int)

/**
 * Position jouable d'un accord sur un instrument à cordes.
 * [frets] : -1 = étouffée, 0 = à vide (ou au capo), n = case relative au capo.
 * [fingers] : 0 = aucun doigt, 1 à 4 = index à auriculaire.
 */
data class Voicing(
    val frets: List<Int>,
    val fingers: List<Int>,
    val barre: Barre?,
    val difficulty: Double,
) {
    val fretted: List<Int> get() = frets.filter { it > 0 }
    val minFret: Int get() = fretted.minOrNull() ?: 0
    val maxFret: Int get() = fretted.maxOrNull() ?: 0

    /** Première case affichée dans le diagramme. */
    val baseFret: Int get() = if (maxFret <= 4) 1 else minFret

    /** Hauteurs jouées (cordes non étouffées), pour l'écoute. */
    fun pitches(tuning: Tuning, capo: Int = 0): List<Pitch> =
        frets.mapIndexedNotNull { s, f -> if (f < 0) null else Pitch.fromMidi(tuning.strings[s].midi + capo + f) }

    override fun toString() = frets.joinToString("") { if (it < 0) "x" else if (it > 9) "($it)" else it.toString() }
}

/** Génère les positions d'un accord par recherche exhaustive sur des fenêtres de 4 cases. */
object VoicingGenerator {

    fun generate(chord: Chord, tuning: Tuning, capo: Int = 0, limit: Int = 8, maxFret: Int = 14): List<Voicing> {
        val strings = tuning.withCapo(capo).strings
        val n = strings.size
        val chordPcs = chord.pitchClasses
        val required = chord.type.intervals.filter { it !in chord.type.optionalIntervals }
            .map { mod12(chord.root.pitchClass + it.semitones) }.toSet()
        val bassPc = (chord.bass ?: chord.root).pitchClass
        val reentrant = strings.zipWithNext().any { (a, b) -> b.midi < a.midi }
        val minSounding = when {
            n <= 4 -> n
            chordPcs.size <= 2 -> 2
            else -> n - 2
        }
        val results = HashMap<List<Int>, Voicing>()

        for (start in 0..(maxFret - 3)) {
            val windowLo = maxOf(1, start)
            val windowHi = start + 3
            val options = strings.map { s ->
                buildList {
                    if (mod12(s.midi) in chordPcs) add(0)
                    for (f in windowLo..windowHi) if (mod12(s.midi + f) in chordPcs) add(f)
                }
            }
            val current = IntArray(n) { -1 }

            fun evaluate() {
                val frets = current.toList()
                val sounding = frets.indices.filter { frets[it] >= 0 }
                if (sounding.size < minSounding) return
                // Les cordes étouffées ne sont admises que côté grave (pas au milieu).
                val firstSounding = sounding.first()
                if (sounding.last() - firstSounding + 1 != sounding.size) return
                if (frets.lastIndex != sounding.last() && n > 4) return
                val pcs = sounding.map { mod12(strings[it].midi + frets[it]) }
                if (!pcs.toSet().containsAll(required)) return
                if (!reentrant) {
                    val lowest = sounding.minBy { strings[it].midi + frets[it] }
                    if (mod12(strings[lowest].midi + frets[lowest]) != bassPc) return
                }
                val fing = fingering(frets) ?: return
                val v = score(frets, fing.first, fing.second, pcs.toSet().size < chordPcs.size, start)
                val prev = results[frets]
                if (prev == null || prev.difficulty > v.difficulty) results[frets] = v
            }

            fun dfs(i: Int) {
                if (i == n) { evaluate(); return }
                current[i] = -1
                if (n > 4) dfs(i + 1) // pas de cordes étouffées sur 4 cordes (ukulélé)
                for (f in options[i]) { current[i] = f; dfs(i + 1) }
                current[i] = -1
            }
            dfs(0)
        }
        return results.values.sortedBy { it.difficulty }.take(limit)
    }

    /** Attribue les doigts (avec barré si nécessaire). Null si injouable (> 4 doigts). */
    internal fun fingering(frets: List<Int>): Pair<List<Int>, Barre?>? {
        val positions = frets.indices.filter { frets[it] > 0 }
        if (positions.isEmpty()) return List(frets.size) { 0 } to null
        val minF = positions.minOf { frets[it] }
        val fingers = MutableList(frets.size) { 0 }

        fun assignRest(candidates: List<Int>, firstFinger: Int): Boolean {
            val sorted = candidates.sortedWith(compareBy({ frets[it] }, { it }))
            if (firstFinger + sorted.size - 1 > 4) return false
            // Un doigt par case et par corde ; on garde l'ordre des cases.
            sorted.forEachIndexed { k, s -> fingers[s] = firstFinger + k }
            return true
        }

        if (positions.size <= 4) {
            if (assignRest(positions, 1)) return fingers to null
        }
        // Barré sur la case minimale : de la première à la dernière corde jouée à cette case.
        val atMin = positions.filter { frets[it] == minF }
        if (atMin.size < 2) return null
        val from = atMin.first()
        val to = frets.lastIndex
        if ((from..to).any { frets[it] in 0 until minF }) return null
        (from..to).filter { frets[it] == minF }.forEach { fingers[it] = 1 }
        val rest = positions.filter { frets[it] > minF }
        if (!assignRest(rest, 2)) return null
        return fingers to Barre(minF, from, to)
    }

    private fun score(frets: List<Int>, fingers: List<Int>, barre: Barre?, omitted: Boolean, start: Int): Voicing {
        val fretted = frets.filter { it > 0 }
        val span = if (fretted.isEmpty()) 0 else fretted.max() - fretted.min()
        val muted = frets.count { it < 0 }
        val open = frets.count { it == 0 }
        val sounding = frets.size - muted
        val used = fingers.filter { it > 0 }.toSet().size
        val position = fretted.minOrNull() ?: 0
        var d = 0.0
        d += span * 1.0
        d += used * 0.7
        d += if (barre != null) 2.0 else 0.0
        d += muted * 0.8
        d -= open * 0.5
        d -= sounding * 0.3
        d += position * 0.25 + if (position > 3) (position - 3) * 0.2 else 0.0
        d += if (span >= 3) 1.5 else 0.0
        d += if (omitted) 0.4 else 0.0
        if (start == 0 && open == 0 && fretted.isNotEmpty()) d += 0.0
        return Voicing(frets, fingers, barre, d)
    }
}
