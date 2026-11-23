package com.karelisio.arpege.theory.song

import java.nio.charset.Charset

/** Morceau Guitar Pro (GP3 à GP5) réduit à ce qu'il faut pour afficher une tablature. */
data class GpSong(
    val title: String,
    val artist: String,
    val tempo: Int,
    val measures: List<GpMeasureHeader>,
    val tracks: List<GpTrack>,
)

data class GpMeasureHeader(val numerator: Int, val denominator: Int, val marker: String?, val repeatOpen: Boolean, val repeatClose: Int)

/** [tuning] : hauteurs MIDI des cordes, de la plus aiguë (corde 1) à la plus grave. */
data class GpTrack(val name: String, val tuning: List<Int>, val percussion: Boolean, val measures: List<List<GpBeat>>)

/** [value] : 1 = ronde, 2 = blanche, 4 = noire, 8 = croche… */
data class GpBeat(
    val value: Int,
    val dotted: Boolean,
    val tuplet: Int,
    val rest: Boolean,
    val notes: List<GpNote>,
    val chord: String?,
    val text: String?,
)

/** [string] : 1 = corde la plus aiguë. */
data class GpNote(val string: Int, val fret: Int, val tie: Boolean, val dead: Boolean)

class GuitarProException(message: String) : Exception(message)

/** Lecteur binaire des formats Guitar Pro 3, 4 et 5 (.gp3, .gp4, .gp5). */
class GuitarProReader private constructor(private val data: ByteArray) {
    private var pos = 0
    private val cp1252: Charset = Charset.forName("windows-1252")
    private var major = 3
    private var minor = 0

    private fun skip(n: Int) { pos += n; check() }
    private fun check() { if (pos > data.size) throw GuitarProException("Fichier tronqué") }
    private fun u8(): Int { check(); if (pos >= data.size) throw GuitarProException("Fichier tronqué"); return data[pos++].toInt() and 0xFF }
    private fun i8(): Int = u8().toByte().toInt()
    private fun bool(): Boolean = u8() != 0
    private fun i16(): Int = (u8() or (u8() shl 8)).toShort().toInt()
    private fun i32(): Int = u8() or (u8() shl 8) or (u8() shl 16) or (u8() shl 24)
    private fun byteSizeString(count: Int): String {
        val size = u8()
        val start = pos
        skip(count)
        return String(data, start, minOf(size, count).coerceAtLeast(0), cp1252)
    }
    private fun intSizeString(): String {
        val n = i32()
        if (n < 0 || n > 1_000_000) throw GuitarProException("Chaîne invalide")
        val start = pos
        skip(n)
        return String(data, start, n, cp1252)
    }
    private fun intByteSizeString(): String {
        val n = i32()
        if (n < 0 || n > 100_000) throw GuitarProException("Chaîne invalide")
        return byteSizeString(n - 1)
    }

    private val isV5 get() = major == 5
    private val isV500 get() = major == 5 && minor == 0

    private fun read(): GpSong {
        val version = byteSizeString(30)
        val m = Regex("""FICHIER GUITAR PRO [vL](\d)\.(\d\d)""").find(version)
            ?: throw GuitarProException("Version non reconnue : « $version »")
        major = m.groupValues[1].toInt()
        minor = m.groupValues[2].toInt()
        if (major !in 3..5) throw GuitarProException("Guitar Pro $major non pris en charge")

        // Informations.
        val title = intByteSizeString()
        intByteSizeString() // sous-titre
        val artist = intByteSizeString()
        intByteSizeString() // album
        intByteSizeString() // paroles
        if (isV5) intByteSizeString() // musique
        intByteSizeString() // copyright
        intByteSizeString() // tablature
        intByteSizeString() // instructions
        repeat(i32()) { intByteSizeString() } // notices

        if (!isV5) bool() // triplet feel
        if (major >= 4) { i32(); repeat(5) { i32(); intSizeString() } } // paroles
        if (isV5) {
            if (!isV500) { i32(); i32(); repeat(11) { i8() } } // RSE master
            repeat(7) { i32() }; i16() // page
            repeat(10) { intByteSizeString() } // en-têtes de page
            intByteSizeString() // nom du tempo
        }
        val tempo = i32()
        if (isV5) { if (!isV500) bool(); i8(); i32() }
        else { i32(); if (major == 4) i8() }
        // 64 canaux MIDI.
        repeat(64) { i32(); repeat(6) { i8() }; skip(2) }
        if (isV5) { repeat(19) { i16() }; i32() } // directions + réverbération

        val measureCount = i32()
        val trackCount = i32()
        if (measureCount !in 0..5000 || trackCount !in 1..64) throw GuitarProException("En-tête incohérent")

        val headers = ArrayList<GpMeasureHeader>()
        var prevNum = 4
        var prevDen = 4
        for (i in 0 until measureCount) {
            if (isV5 && i > 0) skip(1)
            val flags = u8()
            val num = if (flags and 1 != 0) i8() else prevNum
            val den = if (flags and 2 != 0) i8() else prevDen
            var repeatClose = -1
            if (flags and 8 != 0) repeatClose = i8()
            if (!isV5 && flags and 16 != 0) u8()
            var marker: String? = null
            if (flags and 32 != 0) { marker = intByteSizeString(); skip(4) }
            if (flags and 64 != 0) { i8(); i8() }
            if (isV5) {
                if (flags and 16 != 0) u8()
                if (flags and 3 != 0) skip(4)
                if (flags and 16 == 0) skip(1)
                u8() // triplet feel
            }
            headers += GpMeasureHeader(num, den, marker, flags and 4 != 0, repeatClose)
            prevNum = num; prevDen = den
        }

        data class TrackInfo(val name: String, val tuning: List<Int>, val percussion: Boolean)
        val infos = (1..trackCount).map { number ->
            if (isV5 && (number == 1 || isV500)) skip(1)
            val flags = u8()
            val name = byteSizeString(40)
            val stringCount = i32()
            val tuning = (0 until 7).map { i32() }.take(stringCount.coerceIn(0, 7))
            i32() // port
            val channel = i32() - 1
            i32() // canal d'effets
            i32() // nombre de cases
            i32() // capo
            skip(4) // couleur
            if (isV5) {
                i16(); u8(); u8(); u8(); i32(); i32(); i32(); skip(12)
                i32(); i32(); i32()
                if (isV500) { i16(); skip(1) } else i32()
                if (!isV500) { repeat(4) { i8() }; intByteSizeString(); intByteSizeString() }
            }
            TrackInfo(name.trim(), tuning, flags and 1 != 0 || channel == 9)
        }
        if (isV5) skip(if (isV500) 2 else 1)

        val trackMeasures = List(trackCount) { ArrayList<List<GpBeat>>() }
        for (mi in 0 until measureCount) {
            for (t in 0 until trackCount) {
                val voices = if (isV5) 2 else 1
                var first: List<GpBeat> = emptyList()
                for (v in 0 until voices) {
                    val beatCount = i32()
                    if (beatCount !in 0..1000) throw GuitarProException("Mesure ${mi + 1} illisible")
                    val beats = (0 until beatCount).map { readBeat(infos[t].tuning.size) }
                    if (v == 0) first = beats
                }
                if (isV5) { if (pos < data.size) u8() } // saut de ligne
                trackMeasures[t] += resolveTies(first, trackMeasures[t])
            }
        }
        val tracks = infos.mapIndexed { i, info -> GpTrack(info.name, info.tuning, info.percussion, trackMeasures[i]) }
        return GpSong(title.trim(), artist.trim(), tempo, headers, tracks)
    }

    /** Une note liée reprend la case de la note précédente sur la même corde. */
    private fun resolveTies(beats: List<GpBeat>, previous: List<List<GpBeat>>): List<GpBeat> {
        val last = HashMap<Int, Int>()
        for (m in previous.takeLast(2)) for (b in m) for (n in b.notes) last[n.string] = n.fret
        return beats.map { b ->
            val notes = b.notes.map { n ->
                val fixed = if (n.tie) n.copy(fret = last[n.string] ?: n.fret) else n
                last[n.string] = fixed.fret
                fixed
            }
            b.copy(notes = notes)
        }
    }

    private fun readBeat(stringCount: Int): GpBeat {
        val flags = u8()
        var status = 1
        if (flags and 64 != 0) status = u8()
        val value = 1 shl (i8() + 2).coerceIn(0, 7)
        val dotted = flags and 1 != 0
        var tuplet = 0
        if (flags and 32 != 0) tuplet = i32()
        var chord: String? = null
        if (flags and 2 != 0) chord = readChord()
        var text: String? = null
        if (flags and 4 != 0) text = intByteSizeString()
        if (flags and 8 != 0) readBeatEffects()
        if (flags and 16 != 0) readMixTable()
        val stringFlags = u8()
        val notes = ArrayList<GpNote>()
        for (s in 1..7) {
            if (s <= stringCount && stringFlags and (1 shl (7 - s)) != 0) notes += readNote(s)
        }
        if (isV5) {
            val f2 = i16()
            if (f2 and 2048 != 0) u8()
        }
        return GpBeat(value, dotted, tuplet, status == 2 || (status == 1 && notes.isEmpty()), notes, chord?.trim()?.ifBlank { null }, text)
    }

    private fun readNote(string: Int): GpNote {
        val flags = u8()
        var type = 1
        if (flags and 32 != 0) type = u8()
        if (!isV5 && flags and 1 != 0) { i8(); i8() }
        if (flags and 16 != 0) i8()
        var fret = 0
        if (flags and 32 != 0) fret = i8()
        if (flags and 128 != 0) { i8(); i8() }
        if (isV5) {
            if (flags and 1 != 0) skip(8)
            u8()
        }
        if (flags and 8 != 0) readNoteEffects()
        return GpNote(string, fret.coerceIn(0, 99), type == 2, type == 3)
    }

    private fun readChord(): String {
        val newFormat = bool()
        if (!newFormat) {
            val name = intByteSizeString()
            val firstFret = i32()
            if (firstFret != 0) repeat(6) { i32() }
            return name
        }
        if (major == 3) {
            skip(1 + 3); repeat(5) { i32() }; bool()
            val name = byteSizeString(22)
            repeat(3) { i32() }; i32(); repeat(6) { i32() }
            i32(); repeat(6) { i32() }
            skip(7 + 1)
            return name
        }
        skip(1 + 3); u8(); u8(); u8(); i32(); i32(); bool()
        val name = byteSizeString(22)
        u8(); u8(); u8(); i32(); repeat(7) { i32() }
        u8(); skip(15)
        skip(7 + 1); skip(7); bool()
        return name
    }

    private fun readBend() {
        i8(); i32()
        val n = i32()
        if (n !in 0..100) throw GuitarProException("Bend invalide")
        repeat(n) { i32(); i32(); bool() }
    }

    private fun readBeatEffects() {
        if (major == 3) {
            val f1 = u8()
            if (f1 and 32 != 0) { val slap = u8(); i32().also { if (slap == 0) Unit } }
            if (f1 and 64 != 0) { i8(); i8() }
            return
        }
        val f1 = i8()
        val f2 = i8()
        if (f1 and 32 != 0) i8()
        if (f2 and 4 != 0) readBend()
        if (f1 and 64 != 0) { i8(); i8() }
        if (f2 and 2 != 0) i8()
    }

    private fun readMixTable() {
        i8() // instrument
        if (isV5) { i32(); i32(); i32(); if (isV500) { i16(); skip(1) } else i32(); if (isV500) skip(1) }
        val values = IntArray(6) { i8() }
        if (isV5) intByteSizeString()
        val tempo = i32()
        for (v in values) if (v >= 0) i8()
        if (tempo >= 0) { i8(); if (isV5 && !isV500) bool() }
        if (major >= 4) i8()
        if (isV5) { i8(); if (!isV500) { intByteSizeString(); intByteSizeString() } }
    }

    private fun readNoteEffects() {
        if (major == 3) {
            val f = u8()
            if (f and 1 != 0) readBend()
            if (f and 16 != 0) { i8(); u8(); u8(); i8() }
            return
        }
        val f1 = i8()
        val f2 = i8()
        if (f1 and 1 != 0) readBend()
        if (f1 and 16 != 0) { if (isV5) { u8(); u8(); u8(); u8(); u8() } else { i8(); u8(); u8(); i8() } }
        if (f2 and 4 != 0) i8()
        if (f2 and 8 != 0) { if (isV5) u8() else i8() }
        if (f2 and 16 != 0) {
            val h = i8()
            if (isV5) when (h) { 2 -> { u8(); i8(); u8() }; 3 -> u8() }
        }
        if (f2 and 32 != 0) { i8(); i8() }
    }

    companion object {
        fun read(bytes: ByteArray): GpSong = try {
            GuitarProReader(bytes).read()
        } catch (e: GuitarProException) {
            throw e
        } catch (e: RuntimeException) {
            throw GuitarProException("Fichier Guitar Pro illisible (${e.javaClass.simpleName})")
        }
    }
}
