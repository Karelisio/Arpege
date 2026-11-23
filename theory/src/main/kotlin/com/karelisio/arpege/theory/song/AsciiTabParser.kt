package com.karelisio.arpege.theory.song

import com.karelisio.arpege.theory.Chord

/** Analyseur de tablatures texte : lignes de tab, lignes d'accords, sections, paroles. */
object AsciiTabParser {
    private val tabLine = Regex("""^\s*([A-Ga-g][#b]?)?\s*[|:]?[-0-9hpbrsxX/\\~|*.()<>^=\s]{6,}$""")
    private val section = Regex("""^\s*\[([^\]]{1,40})]\s*$|^\s*(Intro|Outro|Verse|Chorus|Bridge|Couplet|Refrain|Pont|Solo|Pre-?chorus)\s*\d*\s*:?\s*$""", RegexOption.IGNORE_CASE)
    private val ignorable = setOf("|", "||", "-", "/", "N.C.", "NC", "x2", "x3", "x4", "(x2)", "(x3)", "(x4)", "%")

    fun parse(text: String, fallbackTitle: String = ""): Song {
        val lines = mutableListOf<SongLine>()
        var title = ""
        var artist = ""
        var capo = 0
        for (raw in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            val line = raw.replace("\t", "    ").trimEnd()
            val lower = line.trim().lowercase()
            when {
                line.isBlank() -> lines += SongLine.Blank
                lower.startsWith("title:") || lower.startsWith("titre:") -> title = line.substringAfter(':').trim()
                lower.startsWith("artist:") || lower.startsWith("artiste:") -> artist = line.substringAfter(':').trim()
                lower.startsWith("capo") -> {
                    capo = Regex("""\d+""").find(line)?.value?.toIntOrNull() ?: 0
                    lines += SongLine.Comment(line.trim())
                }
                isTabLine(line) -> lines += SongLine.Tab(line)
                section.matches(line) -> lines += SongLine.Section(line.trim().trim('[', ']', ':'))
                else -> {
                    val chords = chordTokens(line)
                    lines += if (chords != null) SongLine.ChordLine(chords, line) else SongLine.Text(line)
                }
            }
        }
        return Song(title.ifBlank { fallbackTitle }, artist, capo, ChordProParser.collapseBlanks(lines))
    }

    fun isTabLine(line: String): Boolean {
        if (!tabLine.matches(line)) return false
        val dashes = line.count { it == '-' }
        return dashes >= line.trim().length / 3
    }

    /** Si tous les mots de la ligne sont des accords (ou des symboles neutres), renvoie leurs positions. */
    fun chordTokens(line: String): List<Pair<Int, String>>? {
        val tokens = Regex("""\S+""").findAll(line).toList()
        if (tokens.isEmpty()) return null
        val chords = mutableListOf<Pair<Int, String>>()
        for (t in tokens) {
            val v = t.value
            when {
                v in ignorable -> Unit
                Chord.parse(v) != null -> chords += t.range.first to v
                else -> return null
            }
        }
        return chords.ifEmpty { null }
    }
}
