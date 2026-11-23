package com.karelisio.arpege.theory.song

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class GuitarProTest {

    /** Construit un petit fichier GP3 : 1 piste guitare, 1 mesure 4/4, 4 noires. */
    private fun syntheticGp3(): ByteArray {
        val out = ByteArrayOutputStream()
        fun u8(v: Int) = out.write(v and 0xFF)
        fun i32(v: Int) { u8(v); u8(v shr 8); u8(v shr 16); u8(v shr 24) }
        fun byteSizeString(s: String, size: Int) { u8(s.length); val b = s.toByteArray(); out.write(b); repeat(size - b.size) { u8(0) } }
        fun intByteSizeString(s: String) { i32(s.length + 1); byteSizeString(s, s.length) }
        byteSizeString("FICHIER GUITAR PRO v3.00", 30)
        listOf("Essai", "", "Moi", "", "", "", "", "").forEach { intByteSizeString(it) }
        i32(0) // notices
        u8(0) // triplet feel
        i32(96) // tempo
        i32(0) // tonalité
        repeat(64) { i32(25); repeat(6) { u8(0) }; u8(0); u8(0) }
        i32(1); i32(1) // mesures, pistes
        u8(1 or 2); u8(4); u8(4) // 4/4
        u8(0); byteSizeString("Guitare", 40); i32(6)
        listOf(64, 59, 55, 50, 45, 40, 0).forEach { i32(it) }
        i32(1); i32(1); i32(2); i32(24); i32(0); repeat(4) { u8(0) }
        i32(4)
        val frets = listOf(0, 1, 3, 2)
        for ((i, f) in frets.withIndex()) {
            u8(if (i == 0) 2 else 0) // accord sur le premier temps
            u8(0) // noire
            if (i == 0) { u8(0); intByteSizeString("Am"); i32(0) }
            val string = 2 + i % 3
            u8(1 shl (7 - string))
            u8(32); u8(1); u8(f) // note normale, case f
        }
        return out.toByteArray()
    }

    @Test fun readsSyntheticGp3() {
        val gp = GuitarProReader.read(syntheticGp3())
        assertEquals("Essai", gp.title)
        assertEquals("Moi", gp.artist)
        assertEquals(96, gp.tempo)
        assertEquals(1, gp.tracks.size)
        val beats = gp.tracks[0].measures[0]
        assertEquals(4, beats.size)
        assertEquals(listOf(0, 1, 3, 2), beats.map { it.notes.single().fret })
        assertEquals("Am", beats[0].chord)
        val song = GuitarProTab.toSong(gp, 0)
        assertEquals(6, song.lines.count { it is SongLine.Tab })
        assertEquals(listOf("Am"), song.chords)
    }

    @Test fun rejectsGarbage() {
        val r = runCatching { GuitarProReader.read(ByteArray(40) { 7 }) }
        assertTrue(r.exceptionOrNull() is GuitarProException)
    }

    /** Fichiers d'exemple optionnels (non versionnés) : -Dgp.samples=/chemin. */
    @Test fun readsSampleFilesIfAvailable() {
        val dir = System.getProperty("gp.samples")?.let(::File)
        assumeTrue(dir != null && dir.isDirectory)
        val files = dir!!.listFiles { f -> f.extension.lowercase() in setOf("gp3", "gp4", "gp5") }!!
        assumeTrue(files.isNotEmpty())
        for (f in files) {
            val gp = GuitarProReader.read(f.readBytes())
            assertTrue("${f.name} : pistes", gp.tracks.isNotEmpty())
            assertTrue("${f.name} : mesures", gp.tracks.all { it.measures.size == gp.measures.size })
            val idx = GuitarProTab.playableTracks(gp).firstOrNull() ?: continue
            val song = GuitarProTab.toSong(gp, idx)
            println("${f.name}: « ${gp.title} » ${gp.tracks.size} piste(s), ${gp.measures.size} mesures, ${song.lines.count { it is SongLine.Tab }} lignes de tab")
            song.lines.take(10).forEach { l ->
                println("  " + when (l) { is SongLine.Tab -> l.text; is SongLine.ChordLine -> l.raw; is SongLine.Comment -> "# " + l.text; is SongLine.Section -> "[" + l.label + "]"; else -> "" })
            }
        }
    }
}
