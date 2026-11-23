package com.karelisio.arpege.theory.song

import com.karelisio.arpege.theory.Chord

/** Morceau importé (ChordPro ou tablature ASCII), prêt à afficher. */
data class Song(
    val title: String,
    val artist: String = "",
    val capo: Int = 0,
    val lines: List<SongLine>,
) {
    /** Accords distincts, dans l'ordre d'apparition. */
    val chords: List<String>
        get() = lines.flatMap { l ->
            when (l) {
                is SongLine.Lyrics -> l.segments.mapNotNull { it.chord }
                is SongLine.ChordLine -> l.chords.map { it.second }
                else -> emptyList()
            }
        }.distinct()

    fun transposed(semitones: Int): Song =
        if (semitones % 12 == 0) this else copy(lines = lines.map { it.transposed(semitones) })
}

data class Segment(val chord: String?, val text: String)

sealed interface SongLine {
    fun transposed(semitones: Int): SongLine = this

    /** Paroles avec accords intégrés (ChordPro). */
    data class Lyrics(val segments: List<Segment>, val chorus: Boolean = false) : SongLine {
        override fun transposed(semitones: Int) =
            copy(segments = segments.map { s -> s.copy(chord = s.chord?.let { transposeChordName(it, semitones) }) })
    }

    /** Ligne d'accords seule (ASCII) : colonne de départ + nom. */
    data class ChordLine(val chords: List<Pair<Int, String>>, val raw: String) : SongLine {
        override fun transposed(semitones: Int): SongLine {
            val moved = chords.map { (col, name) -> col to transposeChordName(name, semitones) }
            // Reconstruit la ligne en conservant au mieux les colonnes.
            val sb = StringBuilder()
            for ((col, name) in moved) {
                if (sb.length < col) sb.append(" ".repeat(col - sb.length)) else if (sb.isNotEmpty()) sb.append(' ')
                sb.append(name)
            }
            var cursor = 0
            val positions = moved.map { (_, name) ->
                val idx = sb.indexOf(name, cursor)
                cursor = idx + name.length
                idx to name
            }
            return ChordLine(positions, sb.toString())
        }
    }

    data class Text(val text: String, val chorus: Boolean = false) : SongLine
    data class Tab(val text: String) : SongLine
    data class Section(val label: String) : SongLine
    data class Comment(val text: String) : SongLine
    data object Blank : SongLine
}

/** Transpose un nom d'accord s'il est reconnu, sinon le renvoie tel quel. */
fun transposeChordName(name: String, semitones: Int): String =
    Chord.parse(name)?.transpose(semitones)?.toString() ?: name
