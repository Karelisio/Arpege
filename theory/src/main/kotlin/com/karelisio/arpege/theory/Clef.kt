package com.karelisio.arpege.theory

/** Clés : [bottomLine] = hauteur de la ligne du bas ; [refStep] = pas de la ligne de référence de la clé. */
enum class Clef(val label: String, val bottomLine: Pitch, val refStep: Int) {
    TREBLE("Sol", Pitch(Note(Letter.E), 4), 2),
    BASS("Fa", Pitch(Note(Letter.G), 2), 6),
    ALTO("Ut 3", Pitch(Note(Letter.F), 3), 4),
    TENOR("Ut 4", Pitch(Note(Letter.D), 3), 6);

    private val base: Int get() = bottomLine.note.letter.ordinal + 7 * bottomLine.octave

    /** Pas de portée d'une hauteur (0 = ligne du bas, 8 = ligne du haut, 1 pas = une ligne ou un interligne). */
    fun step(p: Pitch): Int = (p.note.letter.ordinal + 7 * p.octave) - base

    /** Hauteur naturelle située au pas donné. */
    fun pitchAt(step: Int): Pitch {
        val idx = base + step
        return Pitch(Note(Letter.entries[Math.floorMod(idx, 7)]), Math.floorDiv(idx, 7))
    }
}
