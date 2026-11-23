package com.karelisio.arpege.theory.song

enum class SongFormat(val label: String) {
    CHORDPRO("ChordPro"), ASCII("Tablature");

    companion object {
        fun fromFileName(name: String): SongFormat? = when (name.substringAfterLast('.', "").lowercase()) {
            "cho", "chopro", "pro", "crd", "chordpro" -> CHORDPRO
            "txt", "tab" -> ASCII
            else -> null
        }
    }
}

fun parseSong(text: String, format: SongFormat, fallbackTitle: String): Song = when (format) {
    SongFormat.CHORDPRO -> ChordProParser.parse(text, fallbackTitle)
    SongFormat.ASCII -> AsciiTabParser.parse(text, fallbackTitle)
}
