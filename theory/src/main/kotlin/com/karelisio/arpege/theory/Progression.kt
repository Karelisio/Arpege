package com.karelisio.arpege.theory

import kotlin.math.abs

/**
 * Degré chiffré d'une grille (« V7 », « ♭VII », « iiø7 ») : degré de la gamme de la tonalité,
 * altération éventuelle de la fondamentale et type d'accord (majuscules = majeur, minuscules = mineur).
 */
data class RomanChord(val degree: Int, val alteration: Int, val type: ChordType, val symbol: String) {
    init { require(degree in 1..7) }

    /** Accord réel dans la tonalité (orthographe tirée de la gamme : ♭VII en Sol = Fa, en Fa = Mi♭). */
    fun resolve(key: Key): Chord {
        val base = key.scale.notes[degree - 1]
        return Chord(Note(base.letter, base.accidental + alteration), type)
    }

    companion object {
        private val ROMANS = listOf("I", "II", "III", "IV", "V", "VI", "VII")
        private val UPPER = mapOf(
            "" to ChordType.MAJOR, "7" to ChordType.DOM7, "maj7" to ChordType.MAJ7, "6" to ChordType.SIX,
            "9" to ChordType.DOM9, "maj9" to ChordType.MAJ9, "sus4" to ChordType.SUS4, "sus2" to ChordType.SUS2,
            "7sus4" to ChordType.SEVEN_SUS4, "add9" to ChordType.ADD9, "+" to ChordType.AUG, "5" to ChordType.POWER,
        )
        private val LOWER = mapOf(
            "" to ChordType.MINOR, "7" to ChordType.MIN7, "maj7" to ChordType.MIN_MAJ7, "6" to ChordType.MIN6,
            "9" to ChordType.MIN9, "°" to ChordType.DIM, "o" to ChordType.DIM, "°7" to ChordType.DIM7,
            "o7" to ChordType.DIM7, "ø" to ChordType.M7B5, "ø7" to ChordType.M7B5,
        )

        /** Analyse « I », « vi », « V7 », « bVII », « iiø7 », « vii° »… ; null si non reconnu. */
        fun parse(text: String): RomanChord? {
            var i = 0
            var alt = 0
            while (i < text.length && text[i] in "b♭#♯") { alt += if (text[i] == 'b' || text[i] == '♭') -1 else 1; i++ }
            val start = i
            while (i < text.length && text[i] in "IViv") i++
            val numeral = text.substring(start, i)
            if (numeral.isEmpty()) return null
            val upper = numeral == numeral.uppercase()
            if (!upper && numeral != numeral.lowercase()) return null
            val degree = ROMANS.indexOf(numeral.uppercase()) + 1
            if (degree == 0) return null
            val type = (if (upper) UPPER else LOWER)[text.substring(i)] ?: return null
            val prefix = (if (alt < 0) "♭" else "♯").repeat(abs(alt))
            return RomanChord(degree, alt, type, prefix + text.substring(start))
        }
    }
}

/** Grille d'accords en degrés, jouée une case (un accord) après l'autre. */
data class Progression(
    val id: String,
    val name: String,
    val description: String,
    val minor: Boolean,
    val numerals: List<String>,
) {
    val steps: List<RomanChord> = numerals.map { requireNotNull(RomanChord.parse(it)) { "Chiffrage invalide : $it" } }

    /** Chiffrage compact (répétitions consécutives fusionnées) : « I–V–vi–IV ». */
    val summary: String
        get() = steps.map { it.symbol }.fold(mutableListOf<String>()) { acc, s -> if (acc.lastOrNull() != s) acc += s; acc }
            .joinToString("–")

    fun chords(key: Key): List<Chord> = steps.map { it.resolve(key) }
}

object Progressions {
    val ALL: List<Progression> = listOf(
        Progression("pop", "Axe pop", "La grille de milliers de tubes pop.", false, listOf("I", "V", "vi", "IV")),
        Progression("doowop", "Années 50", "Doo-wop et ballades rock'n'roll.", false, listOf("I", "vi", "IV", "V")),
        Progression("sad", "Pop mélancolique", "L'axe pop qui démarre sur le relatif mineur.", false, listOf("vi", "IV", "I", "V")),
        Progression("three", "Trois accords", "Tonique, sous-dominante, dominante : le rock et le folk.", false, listOf("I", "IV", "V")),
        Progression("cadence", "Cadence parfaite", "I–IV–V7–I, la cadence classique.", false, listOf("I", "IV", "V7", "I")),
        Progression("jazz", "ii–V–I", "La cadence du jazz, en accords de septième.", false, listOf("ii7", "V7", "Imaj7", "Imaj7")),
        Progression(
            "blues", "Blues 12 mesures", "Le blues en septièmes de dominante, avec retour sur V7.", false,
            listOf("I7", "I7", "I7", "I7", "IV7", "IV7", "I7", "I7", "V7", "IV7", "I7", "V7"),
        ),
        Progression("pachelbel", "Canon de Pachelbel", "La basse obstinée du célèbre canon.", false, listOf("I", "V", "vi", "iii", "IV", "I", "IV", "V")),
        Progression("fifths", "Cycle des quintes", "Toute la gamme en descendant par quintes.", false, listOf("I", "IV", "vii°", "iii", "vi", "ii", "V", "I")),
        Progression("andalusian", "Cadence andalouse", "Descente flamenca vers la dominante majeure.", true, listOf("i", "VII", "VI", "V")),
        Progression("epic", "Mineur épique", "Ballades rock et musiques de film.", true, listOf("i", "VI", "III", "VII")),
        Progression("minor_cadence", "Cadence mineure", "i–iv–V–i avec la dominante majeure (mineur harmonique).", true, listOf("i", "iv", "V", "i")),
        Progression("minor_jazz", "ii–V–i mineur", "La cadence jazz en mineur : demi-diminué, dominante, tonique.", true, listOf("iiø7", "V7", "i7", "i7")),
        Progression(
            "minor_blues", "Blues mineur", "Le blues 12 mesures en mineur.", true,
            listOf("i7", "i7", "i7", "i7", "iv7", "iv7", "i7", "i7", "VI7", "V7", "i7", "V7"),
        ),
    )

    fun byId(id: String): Progression? = ALL.firstOrNull { it.id == id }

    /**
     * Tonalité usuelle pour une classe de hauteur : l'orthographe avec le moins d'altérations
     * (Ré♭ majeur plutôt que Do♯, mais Do♯ mineur) ; à égalité, l'orthographe courante (Fa♯, Mi♭).
     */
    fun keyFor(pitchClass: Int, minor: Boolean): Key {
        val common = Note.common(pitchClass)
        val candidates = listOf(common, Note.fromPitchClass(pitchClass, false), Note.fromPitchClass(pitchClass, true)).distinct()
        return candidates.map { Key(it, minor) }.minBy { abs(it.fifths) }
    }
}
