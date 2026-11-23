package com.karelisio.arpege.theory.practice

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PracticeStatsTest {
    private val zone = ZoneId.of("Europe/Paris")
    private fun at(date: String, hour: Int = 18) = LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun s(date: String, min: Int, ins: String = "GUITAR", act: String = "TUNER") = SessionLite(at(date), min * 60, ins, act)

    @Test fun streakCountsConsecutiveDays() {
        val st = PracticeStats(listOf(s("2026-11-20", 10), s("2026-11-21", 5), s("2026-11-22", 20), s("2026-11-18", 30)), zone)
        assertEquals(3, st.streak(LocalDate.parse("2026-11-22")))
        // Pas encore pratiqué aujourd'hui : la série d'hier compte toujours.
        assertEquals(3, st.streak(LocalDate.parse("2026-11-23")))
        assertEquals(0, st.streak(LocalDate.parse("2026-11-25")))
        assertEquals(3, st.bestStreak())
    }

    @Test fun weekStartsOnMonday() {
        // 23/11/2026 est un lundi.
        val st = PracticeStats(listOf(s("2026-11-22", 40), s("2026-11-23", 15), s("2026-11-25", 20)), zone)
        assertEquals(35, st.weekMinutes(LocalDate.parse("2026-11-25")))
        assertEquals(40, st.weekMinutes(LocalDate.parse("2026-11-22")))
    }

    @Test fun groupsAndLevels() {
        val st = PracticeStats(listOf(s("2026-11-22", 40, "PIANO", "EAR"), s("2026-11-22", 20, "GUITAR", "EAR"), s("2026-11-21", 5, "GUITAR", "TUNER")), zone)
        assertEquals(listOf("EAR" to 60, "TUNER" to 5), st.minutesBy { it.activity })
        assertEquals(60, st.dailyMinutes[LocalDate.parse("2026-11-22")])
        assertEquals(listOf(5, 60), st.lastDays(LocalDate.parse("2026-11-22"), 2).map { it.second })
        assertEquals(4, PracticeStats.level(60))
        assertEquals(0, PracticeStats.level(0))
    }
}
