package com.karelisio.arpege.theory.song

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class GuitarProTest {

    /** Petit fichier GP3 : 1 piste guitare, 1 mesure 4/4, 4 noires (accord Am sur le premier temps). */
    private fun syntheticGp3(): ByteArray = gp3(listOf(SYNTHETIC_MEASURE))

    companion object {
        /** Cases 0, 1, 3, 2 sur les cordes 2, 3, 4, 2. */
        internal val SYNTHETIC_MEASURE = listOf(0, 1, 3, 2).mapIndexed { i, f ->
            BeatSpec(listOf(NoteSpec(2 + i % 3, f)), chord = if (i == 0) "Am" else null)
        }
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
