package com.karelisio.arpege.theory.song

import java.io.ByteArrayOutputStream

/** Note d'un temps de test : [string] 1 = corde aiguë. */
internal data class NoteSpec(val string: Int, val fret: Int, val tie: Boolean = false, val dead: Boolean = false)

/** Temps de test : [duration] -2 = ronde, -1 = blanche, 0 = noire, 1 = croche, 2 = double croche. */
internal data class BeatSpec(
    val notes: List<NoteSpec> = emptyList(),
    val duration: Int = 0,
    val dotted: Boolean = false,
    val tuplet: Int = 0,
    val rest: Boolean = false,
    val chord: String? = null,
)

/** Construit un petit fichier GP3 (une piste guitare standard, mesures en 4/4). */
internal fun gp3(measures: List<List<BeatSpec>>, tempo: Int = 96, capo: Int = 0): ByteArray {
    val out = ByteArrayOutputStream()
    fun u8(v: Int) = out.write(v and 0xFF)
    fun i32(v: Int) { u8(v); u8(v shr 8); u8(v shr 16); u8(v shr 24) }
    fun byteSizeString(s: String, size: Int) { u8(s.length); val b = s.toByteArray(); out.write(b); repeat(size - b.size) { u8(0) } }
    fun intByteSizeString(s: String) { i32(s.length + 1); byteSizeString(s, s.length) }
    byteSizeString("FICHIER GUITAR PRO v3.00", 30)
    listOf("Essai", "", "Moi", "", "", "", "", "").forEach { intByteSizeString(it) }
    i32(0) // notices
    u8(0) // triplet feel
    i32(tempo)
    i32(0) // tonalité
    repeat(64) { i32(25); repeat(6) { u8(0) }; u8(0); u8(0) }
    i32(measures.size); i32(1) // mesures, pistes
    measures.indices.forEach { i -> if (i == 0) { u8(1 or 2); u8(4); u8(4) } else u8(0) }
    u8(0); byteSizeString("Guitare", 40); i32(6)
    listOf(64, 59, 55, 50, 45, 40, 0).forEach { i32(it) }
    i32(1); i32(1); i32(2); i32(24); i32(capo); repeat(4) { u8(0) }
    for (m in measures) {
        i32(m.size)
        for (b in m) {
            var flags = 0
            if (b.dotted) flags = flags or 1
            if (b.chord != null) flags = flags or 2
            if (b.tuplet > 0) flags = flags or 32
            if (b.rest) flags = flags or 64
            u8(flags)
            if (b.rest) u8(2)
            u8(b.duration)
            if (b.tuplet > 0) i32(b.tuplet)
            if (b.chord != null) { u8(0); intByteSizeString(b.chord); i32(0) }
            val notes = b.notes.sortedBy { it.string }
            u8(notes.fold(0) { acc, n -> acc or (1 shl (7 - n.string)) })
            for (n in notes) { u8(32); u8(if (n.tie) 2 else if (n.dead) 3 else 1); u8(n.fret) }
        }
    }
    return out.toByteArray()
}
