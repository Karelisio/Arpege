package com.karelisio.arpege.theory.song

import com.karelisio.arpege.theory.Pitch

/** Convertit une piste Guitar Pro en tablature texte (lignes de 4 mesures). */
object GuitarProTab {
    fun playableTracks(gp: GpSong): List<Int> = gp.tracks.indices.filter { !gp.tracks[it].percussion && gp.tracks[it].tuning.isNotEmpty() }

    fun toSong(gp: GpSong, trackIndex: Int, measuresPerLine: Int = 4): Song {
        val track = gp.tracks.getOrNull(trackIndex) ?: return Song(gp.title, gp.artist, lines = emptyList())
        val strings = track.tuning.size
        val names = track.tuning.map { Pitch.fromMidi(it).note.toString() }
        val nameWidth = names.maxOf { it.length }
        val lines = mutableListOf<SongLine>()
        lines += SongLine.Comment("${track.name.ifBlank { "Piste ${trackIndex + 1}" }} · ${gp.tempo} BPM · accordage " +
            track.tuning.reversed().joinToString(" ") { Pitch.fromMidi(it).toString() })
        lines += SongLine.Blank

        for (start in track.measures.indices step measuresPerLine) {
            val end = minOf(start + measuresPerLine, track.measures.size)
            val rows = Array(strings) { StringBuilder(names[it].padEnd(nameWidth) + "|") }
            val chordRow = StringBuilder(" ".repeat(nameWidth + 1))
            val chords = mutableListOf<Pair<Int, String>>()
            for (m in start until end) {
                gp.measures.getOrNull(m)?.marker?.let { if (m == start || it.isNotBlank()) lines += SongLine.Section(it) }
                for (beat in track.measures[m]) {
                    val cells = Array(strings) { "" }
                    for (n in beat.notes) if (n.string in 1..strings) cells[n.string - 1] = if (n.dead) "x" else n.fret.toString()
                    val width = maxOf(1, cells.maxOf { it.length }) + spacing(beat.value, beat.dotted)
                    beat.chord?.let { c ->
                        chords += rows[0].length to c
                    }
                    for (s in 0 until strings) rows[s].append(cells[s].ifEmpty { "-" }.padEnd(width, '-'))
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
        return Song(gp.title, gp.artist, 0, ChordProParser.collapseBlanks(lines))
    }

    private fun spacing(value: Int, dotted: Boolean): Int {
        val base = when (value) { 1 -> 7; 2 -> 5; 4 -> 3; 8 -> 2; else -> 1 }
        return base + if (dotted) 1 else 0
    }
}
