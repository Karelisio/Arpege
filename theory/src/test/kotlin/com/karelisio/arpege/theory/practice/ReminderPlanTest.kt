package com.karelisio.arpege.theory.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderPlanTest {
    private val zone = ZoneId.of("Europe/Paris")
    private fun at(s: String) = ZonedDateTime.of(LocalDateTime.parse(s), zone)

    @Test fun nextIsTodayOrTomorrow() {
        assertEquals(at("2026-11-23T19:00"), ReminderPlan.next(at("2026-11-23T08:15"), 19 * 60))
        assertEquals(at("2026-11-24T19:00"), ReminderPlan.next(at("2026-11-23T19:00"), 19 * 60))
        assertEquals(at("2026-11-24T07:30"), ReminderPlan.next(at("2026-11-23T21:00"), 7 * 60 + 30))
        assertEquals("07:05", ReminderPlan.label(7 * 60 + 5))
    }

    @Test fun handlesDaylightSavingGap() {
        // 29/03/2026 : 02:30 n'existe pas à Paris → décalé à 03:30.
        val next = ReminderPlan.next(at("2026-03-29T01:00"), 2 * 60 + 30)
        assertEquals(3, next.hour)
        assertEquals(LocalDate.parse("2026-03-29"), next.toLocalDate())
    }

    @Test fun practicedOnChecksTheDay() {
        val ms = at("2026-11-23T10:00").toInstant().toEpochMilli()
        val stats = PracticeStats(listOf(SessionLite(ms, 300, "GUITAR", "TUNER")), zone)
        assertTrue(stats.practicedOn(LocalDate.parse("2026-11-23")))
        assertFalse(stats.practicedOn(LocalDate.parse("2026-11-24")))
    }
}
