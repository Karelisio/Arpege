package com.karelisio.arpege.theory

/** Tonalité majeure ou mineure. */
data class Key(val tonic: Note, val minor: Boolean = false) {
    /** Nombre d'altérations à l'armure : > 0 dièses, < 0 bémols. */
    val fifths: Int
        get() {
            val major = if (minor) tonic + Interval.m3 else tonic
            return LETTER_FIFTHS.getValue(major.letter) + 7 * major.accidental
        }

    val relative: Key get() = if (minor) Key(tonic + Interval.m3, false) else Key(tonic - Interval.m3, true)

    val scale: Scale get() = Scale(tonic, if (minor) ScaleType.NATURAL_MINOR else ScaleType.MAJOR)

    /** Notes altérées de l'armure, dans l'ordre (Fa Do Sol… ou Si Mi La…). */
    val signature: List<Note>
        get() = if (fifths >= 0) SHARP_ORDER.take(fifths).map { Note(it, 1) }
        else FLAT_ORDER.take(-fifths).map { Note(it, -1) }

    fun name(notation: Notation = Notation.ENGLISH): String =
        tonic.name(notation) + if (minor) (if (notation == Notation.LATIN) " mineur" else " minor") else (if (notation == Notation.LATIN) " majeur" else " major")

    companion object {
        private val LETTER_FIFTHS = mapOf(
            Letter.F to -1, Letter.C to 0, Letter.G to 1, Letter.D to 2, Letter.A to 3, Letter.E to 4, Letter.B to 5,
        )
        val SHARP_ORDER = listOf(Letter.F, Letter.C, Letter.G, Letter.D, Letter.A, Letter.E, Letter.B)
        val FLAT_ORDER = SHARP_ORDER.reversed()

        /** Tonalité majeure correspondant à un nombre de quintes (-7..7). */
        fun majorFromFifths(fifths: Int): Key {
            var n = Note(Letter.C)
            repeat(kotlin.math.abs(fifths)) { n = if (fifths > 0) n + Interval.P5 else n + Interval.P4 }
            return Key(n)
        }
    }
}

/** Cercle des quintes : 12 positions, de Do (0) dans le sens horaire. */
object CircleOfFifths {
    /** Armures retenues pour chaque position (Fa♯ plutôt que Sol♭, Ré♭ plutôt que Do♯). */
    val FIFTHS = listOf(0, 1, 2, 3, 4, 5, 6, -5, -4, -3, -2, -1)

    fun majorAt(position: Int): Key = Key.majorFromFifths(FIFTHS[mod12(position)])
    fun minorAt(position: Int): Key = majorAt(position).relative

    /** Tonalités voisines (sous-dominante et dominante). */
    fun neighbors(position: Int): Pair<Key, Key> = majorAt(position - 1) to majorAt(position + 1)

    /** Enharmonie éventuelle (Fa♯ = Sol♭, Ré♭ = Do♯, Si = Do♭). */
    fun enharmonicAt(position: Int): Key? = when (FIFTHS[mod12(position)]) {
        6 -> Key.majorFromFifths(-6)
        5 -> Key.majorFromFifths(-7)
        -5 -> Key.majorFromFifths(7)
        else -> null
    }
}
