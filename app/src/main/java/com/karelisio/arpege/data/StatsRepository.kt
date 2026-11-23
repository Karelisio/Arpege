package com.karelisio.arpege.data

import com.karelisio.arpege.data.db.ExerciseStat
import kotlinx.coroutines.launch

/** Statistiques des exercices (réussites / tentatives par élément). */
class StatsRepository(private val c: AppContainer) {
    fun record(exercise: String, item: String, correct: Boolean) {
        c.scope.launch {
            val dao = c.db.stats()
            val cur = dao.get(exercise, item) ?: ExerciseStat(exercise, item)
            dao.upsert(cur.copy(correct = cur.correct + if (correct) 1 else 0, attempts = cur.attempts + 1, lastSeen = System.currentTimeMillis()))
        }
    }

    /** Conserve le meilleur score (stocké dans « correct »). */
    fun recordBest(exercise: String, item: String, score: Int) {
        c.scope.launch {
            val dao = c.db.stats()
            val cur = dao.get(exercise, item)
            if (cur == null || score > cur.correct) dao.upsert(ExerciseStat(exercise, item, score, (cur?.attempts ?: 0) + 1, System.currentTimeMillis()))
        }
    }

    fun reset(exercise: String) { c.scope.launch { c.db.stats().reset(exercise) } }
}
