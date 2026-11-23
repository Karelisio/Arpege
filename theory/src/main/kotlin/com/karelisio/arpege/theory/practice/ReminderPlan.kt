package com.karelisio.arpege.theory.practice

import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.Locale

/** Planification du rappel quotidien de pratique. */
object ReminderPlan {
    const val DEFAULT_MINUTE_OF_DAY = 19 * 60

    fun time(minuteOfDay: Int): LocalTime = LocalTime.of((minuteOfDay / 60).coerceIn(0, 23), (minuteOfDay % 60).coerceIn(0, 59))

    /**
     * Prochain rappel strictement après [now], à l'heure locale [minuteOfDay] (minutes depuis minuit).
     * Une heure inexistante (passage à l'heure d'été) est décalée vers l'avant par ZonedDateTime.
     */
    fun next(now: ZonedDateTime, minuteOfDay: Int): ZonedDateTime {
        val t = time(minuteOfDay)
        val today = ZonedDateTime.of(now.toLocalDate(), t, now.zone)
        return if (today.isAfter(now)) today else ZonedDateTime.of(now.toLocalDate().plusDays(1), t, now.zone)
    }

    /** « 19:05 ». */
    fun label(minuteOfDay: Int): String {
        val t = time(minuteOfDay)
        return String.format(Locale.ROOT, "%02d:%02d", t.hour, t.minute)
    }
}
