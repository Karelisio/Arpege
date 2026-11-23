package com.karelisio.arpege.theory.song

enum class SongFormat(val label: String, val binary: Boolean = false) {
    CHORDPRO("ChordPro"), ASCII("Tablature"), GUITAR_PRO("Guitar Pro", binary = true);

    companion object {
        fun fromFileName(name: String): SongFormat? = when (name.substringAfterLast('.', "").lowercase()) {
            "cho", "chopro", "pro", "crd", "chordpro" -> CHORDPRO
            "txt", "tab" -> ASCII
            "gp3", "gp4", "gp5" -> GUITAR_PRO
            else -> null
        }
    }
}

/** Formats texte uniquement (Guitar Pro passe par [GuitarProReader]). */
fun parseSong(text: String, format: SongFormat, fallbackTitle: String): Song = when (format) {
    SongFormat.CHORDPRO -> ChordProParser.parse(text, fallbackTitle)
    SongFormat.ASCII, SongFormat.GUITAR_PRO -> AsciiTabParser.parse(text, fallbackTitle)
}
