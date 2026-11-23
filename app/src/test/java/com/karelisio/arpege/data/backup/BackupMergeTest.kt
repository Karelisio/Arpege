package com.karelisio.arpege.data.backup

import com.karelisio.arpege.data.db.ExerciseStat
import com.karelisio.arpege.data.db.FavoriteChord
import com.karelisio.arpege.data.db.PracticeSession
import com.karelisio.arpege.data.db.TabFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMergeTest {
    private fun session(start: Long, sec: Int = 600, activity: String = "TUNER", id: Long = 0) =
        PracticeSession(id = id, startEpochMs = start, durationSec = sec, instrument = "GUITAR", activity = activity)

    private val existing = Backup(
        sessions = listOf(session(1_000, id = 1), session(2_000, id = 2)),
        favorites = listOf(FavoriteChord(id = 1, name = "Am7", instrument = "GUITAR")),
        stats = listOf(ExerciseStat("ear_intervals", "i7", correct = 8, attempts = 10, lastSeen = 500)),
        tabs = listOf(TabFile(id = 1, title = "Jeux interdits", format = "ASCII", content = "e|--0--|", sourceName = "a.txt")),
    )

    @Test fun onlyNewItemsAreAdded() {
        val incoming = Backup(
            sessions = listOf(session(1_000, id = 40), session(3_000, id = 41), session(3_000, id = 42), session(4_000, sec = 0)),
            favorites = listOf(FavoriteChord(id = 9, name = "Am7", instrument = "GUITAR"), FavoriteChord(id = 10, name = "Am7", instrument = "UKULELE")),
            tabs = listOf(
                TabFile(id = 7, title = "Autre titre", format = "ASCII", content = "e|--0--|", sourceName = "b.txt"),
                TabFile(id = 8, title = "Nouvelle", format = "CHORDPRO", content = "[C]la", sourceName = "c.cho"),
            ),
        )
        val plan = BackupMerge.plan(existing, incoming)
        assertEquals(listOf(3_000L), plan.sessions.map { it.startEpochMs })
        assertEquals(listOf("UKULELE"), plan.favorites.map { it.instrument })
        assertEquals(listOf("Nouvelle"), plan.tabs.map { it.title })
        // Identifiants remis à zéro : Room en attribue de nouveaux.
        assertTrue((plan.sessions.map { it.id } + plan.favorites.map { it.id } + plan.tabs.map { it.id }).all { it == 0L })
    }

    @Test fun statsKeepTheRecordWithMoreAttempts() {
        val incoming = Backup(
            stats = listOf(
                ExerciseStat("ear_intervals", "i7", correct = 3, attempts = 4, lastSeen = 900), // moins de tentatives : ignoré
                ExerciseStat("ear_intervals", "i5", correct = 1, attempts = 2),
                ExerciseStat("sing", "i7+", correct = 5, attempts = 6, lastSeen = 100),
                ExerciseStat("sing", "i7+", correct = 9, attempts = 12, lastSeen = 50),
            ),
        )
        val plan = BackupMerge.plan(existing, incoming)
        assertEquals(setOf("i5", "i7+"), plan.stats.map { it.item }.toSet())
        val sing = plan.stats.single { it.exercise == "sing" }
        assertEquals(12, sing.attempts)
        val more = BackupMerge.plan(existing, Backup(stats = listOf(ExerciseStat("ear_intervals", "i7", 15, 20, 100))))
        assertEquals(20, more.stats.single().attempts)
        assertEquals(500L, more.stats.single().lastSeen)
    }

    @Test fun importingTwiceAddsNothing() {
        val incoming = Backup(
            sessions = listOf(session(5_000), session(6_000, activity = "SING")),
            favorites = listOf(FavoriteChord(name = "C", instrument = "PIANO")),
            stats = listOf(ExerciseStat("sing", "u", 2, 3)),
            tabs = listOf(TabFile(title = "T", format = "ASCII", content = "x", sourceName = "t.txt")),
        )
        val first = BackupMerge.plan(existing, incoming)
        assertEquals(2, first.sessions.size)
        val after = Backup(
            sessions = existing.sessions + first.sessions,
            favorites = existing.favorites + first.favorites,
            stats = existing.stats + first.stats,
            tabs = existing.tabs + first.tabs,
        )
        assertTrue(BackupMerge.plan(after, incoming).isEmpty)
        assertTrue(BackupMerge.plan(existing, existing).isEmpty)
    }
}
