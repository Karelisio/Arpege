package com.karelisio.arpege.theory

/** Accordage : cordes de la plus grave à la plus aiguë (ordre du manche). */
data class Tuning(val id: String, val label: String, val instrument: Instrument, val strings: List<Pitch>) {
    fun withCapo(capo: Int): Tuning = if (capo == 0) this else copy(strings = strings.map { Pitch.fromMidi(it.midi + capo, it.note.accidental < 0) })

    /** Corde la plus proche d'une hauteur MIDI (fractionnaire), pour la détection auto. */
    fun nearestString(midi: Double): Int = strings.indices.minBy { kotlin.math.abs(strings[it].midi - midi) }

    companion object {
        private fun t(id: String, label: String, ins: Instrument, vararg p: String) =
            Tuning(id, label, ins, p.map { Pitch.parse(it)!! })

        val GUITAR_STANDARD = t("guitar_std", "Standard (EADGBE)", Instrument.GUITAR, "E2", "A2", "D3", "G3", "B3", "E4")
        val DROP_D = t("drop_d", "Drop D", Instrument.GUITAR, "D2", "A2", "D3", "G3", "B3", "E4")
        val DADGAD = t("dadgad", "DADGAD", Instrument.GUITAR, "D2", "A2", "D3", "G3", "A3", "D4")
        val OPEN_G = t("open_g", "Open G", Instrument.GUITAR, "D2", "G2", "D3", "G3", "B3", "D4")
        val UKULELE = t("ukulele", "Ukulélé (GCEA)", Instrument.UKULELE, "G4", "C4", "E4", "A4")
        val BASS4 = t("bass4", "Basse 4 cordes", Instrument.BASS, "E1", "A1", "D2", "G2")
        val BASS5 = t("bass5", "Basse 5 cordes", Instrument.BASS, "B0", "E1", "A1", "D2", "G2")

        val ALL = listOf(GUITAR_STANDARD, DROP_D, DADGAD, OPEN_G, UKULELE, BASS4, BASS5)

        fun forInstrument(i: Instrument): List<Tuning> = ALL.filter { it.instrument == i }
        fun default(i: Instrument): Tuning = when (i) {
            Instrument.GUITAR, Instrument.PIANO -> GUITAR_STANDARD
            Instrument.UKULELE -> UKULELE
            Instrument.BASS -> BASS4
        }
        fun byId(id: String): Tuning? = ALL.firstOrNull { it.id == id }
    }
}
