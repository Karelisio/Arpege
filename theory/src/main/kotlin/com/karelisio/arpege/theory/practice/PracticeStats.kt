package com.karelisio.arpege.theory.practice

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Séance minimale pour les calculs (indépendante de Room). */
data class SessionLite(val startMs: Long, val durationSec: Int, val instrument: String, val activity: String)

/** Statistiques du journal de pratique. */
class PracticeStats(sessions: List<SessionLite>, private val zone: ZoneId = ZoneId.systemDefault()) {
    private val list = sessions

    fun day(s: SessionLite): LocalDate = Instant.ofEpochMilli(s.startMs).atZone(zone).toLocalDate()

    /** Minutes par jour. */
    val dailyMinutes: Map<LocalDate, Int> by lazy {
        list.groupBy { day(it) }.mapValues { (_, v) -> v.sumOf { it.durationSec } / 60 }
    }

    /** Minutes de la semaine (lundi → dimanche) contenant [today]. */
    fun weekMinutes(today: LocalDate): Int {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return list.filter { val d = day(it); !d.isBefore(monday) && !d.isAfter(today) }.sumOf { it.durationSec } / 60
    }

    /**
     * Série de jours consécutifs avec pratique, se terminant aujourd'hui
     * (ou hier, si l'on n'a pas encore pratiqué aujourd'hui).
     */
    fun streak(today: LocalDate): Int {
        val days = list.filter { it.durationSec > 0 }.map { day(it) }.toSet()
        var d = if (today in days) today else today.minusDays(1)
        var n = 0
        while (d in days) { n++; d = d.minusDays(1) }
        return n
    }

    fun bestStreak(): Int {
        val days = list.map { day(it) }.toSortedSet()
        var best = 0; var cur = 0; var prev: LocalDate? = null
        for (d in days) {
            cur = if (prev != null && prev.plusDays(1) == d) cur + 1 else 1
            best = maxOf(best, cur); prev = d
        }
        return best
    }

    fun minutesBy(selector: (SessionLite) -> String): List<Pair<String, Int>> =
        list.groupBy(selector).map { (k, v) -> k to v.sumOf { it.durationSec } / 60 }.sortedByDescending { it.second }

    /** Minutes des 7 derniers jours (le plus ancien en premier). */
    fun lastDays(today: LocalDate, n: Int = 7): List<Pair<LocalDate, Int>> =
        (n - 1 downTo 0).map { today.minusDays(it.toLong()) }.map { it to (dailyMinutes[it] ?: 0) }

    val totalMinutes: Int get() = list.sumOf { it.durationSec } / 60

    companion object {
        /** Niveau d'intensité 0..4 pour la heatmap. */
        fun level(minutes: Int): Int = when {
            minutes <= 0 -> 0
            minutes < 15 -> 1
            minutes < 30 -> 2
            minutes < 60 -> 3
            else -> 4
        }
    }
}
