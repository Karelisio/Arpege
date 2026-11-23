package com.karelisio.arpege.theory.song

import com.karelisio.arpege.theory.Pitch

/**
 * Place d'un temps dans la tablature texte : [line] = index (dans Song.lines) de la ligne de la
 * corde aiguë du système, [strings] lignes consécutives ; colonnes du temps et de sa mesure.
 */
data class TabBeatPosition(
    val measure: Int,
    val beat: Int,
    val line: Int,
    val strings: Int,
    val column: Int,
    val width: Int,
    val measureColumn: Int,
    val measureWidth: Int,
)

/** Tablature texte + position de chaque temps (même ordre que [GuitarProTimeline.beats]). */
data class GpTabLayout(val song: Song, val beats: List<TabBeatPosition>)

/** Convertit une piste Guitar Pro en tablature texte (systèmes de 4 mesures au plus). */
object GuitarProTab {
    fun playableTracks(gp: GpSong): List<Int> = gp.tracks.indices.filter { !gp.tracks[it].percussion && gp.tracks[it].tuning.isNotEmpty() }

    fun toSong(gp: GpSong, trackIndex: Int, measuresPerLine: Int = 4): Song = layout(gp, trackIndex, measuresPerLine).song

    /**
     * Rendu en tablature texte. [maxChars] : largeur visée d'une ligne (en caractères) ; un système
     * s'arrête avant de la dépasser (une mesure trop large reste seule sur sa ligne).
     */
    fun layout(gp: GpSong, trackIndex: Int, measuresPerLine: Int = 4, maxChars: Int = Int.MAX_VALUE): GpTabLayout {
        val track = gp.tracks.getOrNull(trackIndex) ?: return GpTabLayout(Song(gp.title, gp.artist, lines = emptyList()), emptyList())
        val strings = track.tuning.size
        if (strings == 0) return GpTabLayout(Song(gp.title, gp.artist, lines = emptyList()), emptyList())
        val names = track.tuning.map { Pitch.fromMidi(it).note.toString() }
        val nameWidth = names.maxOf { it.length }
        val lines = mutableListOf<SongLine>()
        lines += SongLine.Comment(
            "${track.name.ifBlank { "Piste ${trackIndex + 1}" }} · ${gp.tempo} BPM · accordage " +
                track.tuning.reversed().joinToString(" ") { Pitch.fromMidi(it).toString() } +
                if (track.capo > 0) " · capo ${track.capo}" else "",
        )
        lines += SongLine.Blank

        // Cellules et largeur de chaque temps, puis découpage en systèmes.
        class Cell(val texts: Array<String>, val width: Int)
        val cells = track.measures.map { measure ->
            measure.map { beat ->
                val texts = Array(strings) { "" }
                for (n in beat.notes) if (n.string in 1..strings) texts[n.string - 1] = if (n.dead) "x" else n.fret.toString()
                Cell(texts, maxOf(1, texts.maxOf { it.length }) + spacing(beat.value, beat.dotted))
            }
        }
        val measureWidths = cells.map { m -> m.sumOf { it.width } + 1 }
        val systems = mutableListOf<IntRange>()
        var start = 0
        while (start < track.measures.size) {
            var end = start
            var width = nameWidth + 1
            while (end < track.measures.size && end - start < measuresPerLine &&
                (end == start || width + measureWidths[end] <= maxChars)
            ) { width += measureWidths[end]; end++ }
            systems += start until end
            start = end
        }

        data class Pending(val system: Int, val measure: Int, val beat: Int, val column: Int, val width: Int, val mColumn: Int, val mWidth: Int)
        val pending = mutableListOf<Pending>()
        for ((si, range) in systems.withIndex()) {
            val rows = Array(strings) { StringBuilder(names[it].padEnd(nameWidth) + "|") }
            val chordRow = StringBuilder(" ".repeat(nameWidth + 1))
            val chords = mutableListOf<Pair<Int, String>>()
            for (m in range) {
                gp.measures.getOrNull(m)?.marker?.let { if (m == range.first || it.isNotBlank()) lines += SongLine.Section(it) }
                val mColumn = rows[0].length
                for ((bi, beat) in track.measures[m].withIndex()) {
                    val cell = cells[m][bi]
                    beat.chord?.let { c -> chords += rows[0].length to c }
                    pending += Pending(si, m, bi, rows[0].length, cell.width, mColumn, measureWidths[m] - 1)
                    for (s in 0 until strings) rows[s].append(cell.texts[s].ifEmpty { "-" }.padEnd(cell.width, '-'))
                }
                for (s in 0 until strings) rows[s].append('|')
            }
            if (chords.isNotEmpty()) {
                for ((col, name) in chords) {
                    if (chordRow.length < col) chordRow.append(" ".repeat(col - chordRow.length)) else if (chordRow.isNotBlank()) chordRow.append(' ')
                    chordRow.append(name)
                }
                val positioned = mutableListOf<Pair<Int, String>>()
                var cursor = 0
                for ((_, name) in chords) { val i = chordRow.indexOf(name, cursor); positioned += i to name; cursor = i + name.length }
                lines += SongLine.ChordLine(positioned, chordRow.toString())
            }
            rows.forEach { lines += SongLine.Tab(it.toString()) }
            lines += SongLine.Blank
        }
        val collapsed = ChordProParser.collapseBlanks(lines)
        // Index de la première ligne de tablature de chaque système (après fusion des lignes vides).
        val systemLines = collapsed.indices.filter { i -> collapsed[i] is SongLine.Tab && (i == 0 || collapsed[i - 1] !is SongLine.Tab) }
        val positions = pending.map { p ->
            TabBeatPosition(p.measure, p.beat, systemLines.getOrElse(p.system) { -1 }, strings, p.column, p.width, p.mColumn, p.mWidth)
        }
        return GpTabLayout(Song(gp.title, gp.artist, track.capo, collapsed), positions)
    }

    private fun spacing(value: Int, dotted: Boolean): Int {
        val base = when (value) { 1 -> 7; 2 -> 5; 4 -> 3; 8 -> 2; else -> 1 }
        return base + if (dotted) 1 else 0
    }
}
