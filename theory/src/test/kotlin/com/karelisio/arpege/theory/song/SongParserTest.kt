package com.karelisio.arpege.theory.song

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongParserTest {
    private val chordPro = """
        {title: Le Chant}
        {artist: Quelqu'un}
        {capo: 2}
        # commentaire ignoré
        [C]Bonjour le [G]monde
        {soc}
        [Am]Refrain [F]ici
        {eoc}

        {sot}
        e|---0---|
        {eot}
    """.trimIndent()

    @Test fun parsesChordPro() {
        val s = ChordProParser.parse(chordPro)
        assertEquals("Le Chant", s.title)
        assertEquals("Quelqu'un", s.artist)
        assertEquals(2, s.capo)
        val first = s.lines.first() as SongLine.Lyrics
        assertEquals(listOf(Segment("C", "Bonjour le "), Segment("G", "monde")), first.segments)
        val chorus = s.lines.filterIsInstance<SongLine.Lyrics>()[1]
        assertTrue(chorus.chorus)
        assertTrue(s.lines.any { it is SongLine.Tab })
        assertEquals(listOf("C", "G", "Am", "F"), s.chords)
    }

    @Test fun segmentsHandleLeadingText() {
        assertEquals(listOf(Segment(null, "Oh "), Segment("D", "yeah")), ChordProParser.segments("Oh [D]yeah"))
    }

    @Test fun transposesChordPro() {
        val s = ChordProParser.parse(chordPro).transposed(2)
        assertEquals(listOf("D", "A", "Bm", "G"), s.chords)
    }

    private val ascii = """
        Title: Morceau
        [Verse]
        G        D/F#     Em
        Des paroles ici
        e|-----0-----3---|
        B|---0---0-------|
        Capo 3
    """.trimIndent()

    @Test fun parsesAsciiTabs() {
        val s = AsciiTabParser.parse(ascii, "fallback")
        assertEquals("Morceau", s.title)
        assertEquals(3, s.capo)
        assertTrue(s.lines[0] is SongLine.Section)
        val cl = s.lines[1] as SongLine.ChordLine
        assertEquals(listOf(0 to "G", 9 to "D/F#", 18 to "Em"), cl.chords)
        assertTrue(s.lines[2] is SongLine.Text)
        assertEquals(2, s.lines.count { it is SongLine.Tab })
    }

    @Test fun lyricsAreNotChords() {
        assertNull(AsciiTabParser.chordTokens("A day in the life"))
        assertEquals(listOf(0 to "Am", 5 to "C"), AsciiTabParser.chordTokens("Am   C  |"))
    }

    @Test fun transposesAsciiChordLineKeepingColumns() {
        val s = AsciiTabParser.parse(ascii).transposed(1)
        val cl = s.lines[1] as SongLine.ChordLine
        assertEquals(listOf("Ab", "Eb/G", "Fm"), cl.chords.map { it.second })
        assertEquals(0, cl.chords[0].first)
        assertEquals(9, cl.chords[1].first)
        assertEquals(cl.chords.map { it.second }, cl.chords.map { (col, n) -> cl.raw.substring(col, col + n.length) })
    }

    @Test fun formatFromExtension() {
        assertEquals(SongFormat.CHORDPRO, SongFormat.fromFileName("song.chopro"))
        assertEquals(SongFormat.ASCII, SongFormat.fromFileName("Song.TXT"))
        assertNull(SongFormat.fromFileName("song.pdf"))
    }
}
