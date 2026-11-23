package com.karelisio.arpege.theory

/** Intervalle orthographié : numéro de degré (1 = unisson) + nombre de demi-tons. */
data class Interval(val degree: Int, val semitones: Int) {
    /** Libellé court de degré, ex. « b3 », « #11 ». */
    val degreeLabel: String
        get() {
            val perfectish = (degree - 1) % 7 in setOf(0, 3, 4)
            val reference = MAJOR_SEMITONES[(degree - 1) % 7] + 12 * ((degree - 1) / 7)
            val diff = semitones - reference
            val prefix = when {
                diff == 0 -> ""
                diff > 0 -> "#".repeat(diff)
                perfectish -> "b".repeat(-diff)
                else -> "b".repeat(-diff)
            }
            return prefix + degree
        }

    val shortName: String get() = SHORT[this] ?: degreeLabel
    val frenchName: String get() = FRENCH[this] ?: degreeLabel

    companion object {
        private val MAJOR_SEMITONES = intArrayOf(0, 2, 4, 5, 7, 9, 11)

        @JvmField val P1 = Interval(1, 0)
        @JvmField val m2 = Interval(2, 1)
        @JvmField val M2 = Interval(2, 2)
        @JvmField val A2 = Interval(2, 3)
        @JvmField val m3 = Interval(3, 3)
        @JvmField val M3 = Interval(3, 4)
        @JvmField val P4 = Interval(4, 5)
        @JvmField val A4 = Interval(4, 6)
        @JvmField val d5 = Interval(5, 6)
        @JvmField val P5 = Interval(5, 7)
        @JvmField val A5 = Interval(5, 8)
        @JvmField val m6 = Interval(6, 8)
        @JvmField val M6 = Interval(6, 9)
        @JvmField val d7 = Interval(7, 9)
        @JvmField val m7 = Interval(7, 10)
        @JvmField val M7 = Interval(7, 11)
        @JvmField val P8 = Interval(8, 12)
        @JvmField val m9 = Interval(9, 13)
        @JvmField val M9 = Interval(9, 14)
        @JvmField val A9 = Interval(9, 15)
        @JvmField val P11 = Interval(11, 17)
        @JvmField val A11 = Interval(11, 18)
        @JvmField val m13 = Interval(13, 20)
        @JvmField val M13 = Interval(13, 21)

        private val SHORT = mapOf(
            P1 to "1", m2 to "2m", M2 to "2M", m3 to "3m", M3 to "3M", P4 to "4J", A4 to "4+",
            d5 to "5−", P5 to "5J", A5 to "5+", m6 to "6m", M6 to "6M", d7 to "7−", m7 to "7m", M7 to "7M", P8 to "8",
        )
        private val FRENCH = mapOf(
            P1 to "Unisson", m2 to "Seconde mineure", M2 to "Seconde majeure", A2 to "Seconde augmentée",
            m3 to "Tierce mineure", M3 to "Tierce majeure", P4 to "Quarte juste", A4 to "Triton",
            d5 to "Quinte diminuée", P5 to "Quinte juste", A5 to "Quinte augmentée", m6 to "Sixte mineure",
            M6 to "Sixte majeure", d7 to "Septième diminuée", m7 to "Septième mineure", M7 to "Septième majeure",
            P8 to "Octave", m9 to "Neuvième mineure", M9 to "Neuvième majeure",
        )

        /** Intervalles simples de l'entraîneur d'oreille, par demi-tons croissants. */
        val SIMPLE = listOf(m2, M2, m3, M3, P4, A4, P5, m6, M6, m7, M7, P8)
    }
}
