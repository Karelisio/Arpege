package com.karelisio.arpege.theory.song

/** Analyseur ChordPro (.cho, .chopro, .pro) : directives usuelles, accords [X] intégrés. */
object ChordProParser {
    private val directive = Regex("""^\{\s*([A-Za-z_]+)\s*(?::\s*(.*?))?\s*\}\s*$""")

    fun parse(text: String, fallbackTitle: String = ""): Song {
        var title = ""
        var artist = ""
        var capo = 0
        var chorus = false
        var inTab = false
        val lines = mutableListOf<SongLine>()
        for (rawLine in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            val line = rawLine.trimEnd()
            val d = directive.matchEntire(line.trim())
            if (d != null) {
                val name = d.groupValues[1].lowercase()
                val value = d.groupValues[2]
                when (name) {
                    "title", "t" -> title = value
                    "subtitle", "st", "artist" -> if (artist.isEmpty()) artist = value
                    "capo" -> capo = value.trim().toIntOrNull() ?: 0
                    "comment", "c", "comment_italic", "ci", "comment_box", "cb", "highlight" -> lines += SongLine.Comment(value)
                    "start_of_chorus", "soc" -> { chorus = true; lines += SongLine.Section(value.ifBlank { "Refrain" }) }
                    "end_of_chorus", "eoc" -> chorus = false
                    "start_of_verse", "sov" -> lines += SongLine.Section(value.ifBlank { "Couplet" })
                    "start_of_bridge", "sob" -> lines += SongLine.Section(value.ifBlank { "Pont" })
                    "start_of_tab", "sot" -> inTab = true
                    "end_of_tab", "eot" -> inTab = false
                    "chorus" -> lines += SongLine.Comment("Refrain")
                    else -> Unit // directives ignorées (définitions, mise en page…)
                }
                continue
            }
            if (line.startsWith("#")) continue
            when {
                inTab -> lines += SongLine.Tab(rawLine)
                line.isBlank() -> lines += SongLine.Blank
                '[' in line -> lines += SongLine.Lyrics(segments(line), chorus)
                else -> lines += SongLine.Text(line, chorus)
            }
        }
        return Song(title.ifBlank { fallbackTitle }, artist, capo, collapseBlanks(lines))
    }

    /** « [C]Hello [G]world » → (C, "Hello "), (G, "world"). */
    fun segments(line: String): List<Segment> {
        val out = mutableListOf<Segment>()
        var i = 0
        var pendingChord: String? = null
        val text = StringBuilder()
        while (i < line.length) {
            val ch = line[i]
            if (ch == '[') {
                val end = line.indexOf(']', i)
                if (end > i) {
                    if (pendingChord != null || text.isNotEmpty()) out += Segment(pendingChord, text.toString())
                    text.clear()
                    pendingChord = line.substring(i + 1, end).trim()
                    i = end + 1
                    continue
                }
            }
            text.append(ch)
            i++
        }
        if (pendingChord != null || text.isNotEmpty()) out += Segment(pendingChord, text.toString())
        return out
    }

    internal fun collapseBlanks(lines: List<SongLine>): List<SongLine> {
        val out = mutableListOf<SongLine>()
        for (l in lines) if (!(l is SongLine.Blank && (out.isEmpty() || out.last() is SongLine.Blank))) out += l
        while (out.lastOrNull() is SongLine.Blank) out.removeAt(out.lastIndex)
        return out
    }
}
