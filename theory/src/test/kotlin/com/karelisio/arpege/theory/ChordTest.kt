package com.karelisio.arpege.theory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordTest {
    private val roots = listOf("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B").map { Note.parse(it)!! }

    @Test fun allTypesOnAllTwelveRoots() {
        for (root in roots) for (type in ChordType.entries) {
            val chord = Chord(root, type)
            val notes = chord.notes
            // Classes de hauteur exactes.
            assertEquals("$chord", type.semitones.map { mod12(root.pitchClass + it) }, notes.map { it.pitchClass })
            // Orthographe : chaque note porte la lettre de son degré.
            type.intervals.zip(notes).forEach { (iv, note) ->
                assertEquals("$chord ${iv.degree}", root.letter.step(iv.degree - 1), note.letter)
                assertTrue("$chord altération raisonnable", note.accidental in -2..2)
            }
            // Le nom se relit.
            assertEquals(chord, Chord.parse(chord.toString()))
        }
    }

    @Test fun knownSpellings() {
        fun notes(s: String) = Chord.parse(s)!!.notes.joinToString(" ")
        assertEquals("C E G", notes("C"))
        assertEquals("F# A# C#", notes("F#"))
        assertEquals("Bb Db F Ab", notes("Bbm7"))
        assertEquals("B D F A", notes("Bm7b5"))
        assertEquals("C Eb Gb Bbb", notes("Cdim7"))
        assertEquals("Ab C E", notes("Abaug"))
        assertEquals("D F# A C E", notes("D9"))
        assertEquals("E G# B D#", notes("Emaj7"))
    }

    @Test fun parsesAliasesAndLatinAndSlash() {
        assertEquals(ChordType.MAJ7, Chord.parse("CΔ7")!!.type)
        assertEquals(ChordType.M7B5, Chord.parse("Bø")!!.type)
        assertEquals(Note.parse("F#"), Chord.parse("D/F#")!!.bass)
        assertEquals(Chord(Note(Letter.D), ChordType.DOM7), Chord.parse("Ré7"))
        assertEquals(ChordType.ADD9, Chord.parse("Fadd9")!!.type)
        assertEquals(Letter.F, Chord.parse("Fadd9")!!.root.letter)
        assertNotNull(Chord.parse("Fa#m"))
    }

    @Test fun transposesWithReadableSpelling() {
        assertEquals("D", Chord.parse("C")!!.transpose(2).toString())
        assertEquals("Bb", Chord.parse("A")!!.transpose(1).toString())
        assertEquals("F#m", Chord.parse("Em")!!.transpose(2).toString())
        assertEquals("Ebmaj7", Chord.parse("Dbmaj7")!!.transpose(2).toString())
        assertEquals("C/E", Chord.parse("A/C#")!!.transpose(3).toString())
    }
}
