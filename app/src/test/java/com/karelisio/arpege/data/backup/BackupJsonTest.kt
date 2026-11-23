package com.karelisio.arpege.data.backup

import com.karelisio.arpege.data.db.ExerciseStat
import com.karelisio.arpege.data.db.FavoriteChord
import com.karelisio.arpege.data.db.PracticeSession
import com.karelisio.arpege.data.db.TabFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** org.json est fourni par Android : Robolectric en donne l'implémentation réelle. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupJsonTest {
    @Test fun roundTripPreservesEverything() {
        val b = Backup(
            sessions = listOf(PracticeSession(startEpochMs = 1_700_000_000_000, durationSec = 754, instrument = "PIANO", activity = "SING", note = "Gammes « lentes »", auto = false)),
            favorites = listOf(FavoriteChord(name = "C#m7b5", instrument = "GUITAR", addedAt = 42)),
            stats = listOf(ExerciseStat("sing", "i7+", 3, 5, 99)),
            tabs = listOf(TabFile(title = "Été", artist = "Moi", format = "GUITAR_PRO", content = "R1BYAAE=", sourceName = "ete.gp5", importedAt = 7, transpose = -2, scrollSpeed = 1.5f)),
            settings = mapOf(
                "a4" to 442f, "weekly_goal" to 200, "vibrate_downbeat" to true, "notation" to "LATIN",
                "big" to 123_456_789_012L, "ratio" to 0.25, "tags" to setOf("a", "b"),
            ),
            exportedAt = 1234, appVersion = "0.2.0",
        )
        val back = BackupJson.decode(BackupJson.encode(b))
        assertEquals(b, back)
        assertEquals(442f, back.settings["a4"])
        assertEquals(200, back.settings["weekly_goal"])
    }

    @Test fun rejectsForeignFiles() {
        assertThrows(BackupFormatException::class.java) { BackupJson.decode("pas du json") }
        assertThrows(BackupFormatException::class.java) { BackupJson.decode("""{"app":"autre"}""") }
        assertThrows(BackupFormatException::class.java) { BackupJson.decode("""{"app":"arpege","format":99}""") }
        assertThrows(BackupFormatException::class.java) { BackupJson.decode("""{"app":"arpege","format":1,"sessions":[{"start":1}]}""") }
        assertEquals(Backup(), BackupJson.decode("""{"app":"arpege","format":1}"""))
    }
}
