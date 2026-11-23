package com.karelisio.arpege.theory

/** Instruments pris en charge. */
enum class Instrument(val label: String, val fretted: Boolean) {
    GUITAR("Guitare", true),
    UKULELE("Ukulélé", true),
    BASS("Basse", true),
    PIANO("Piano", false),
}

/** Système de noms de notes. */
enum class Notation { LATIN, ENGLISH }
