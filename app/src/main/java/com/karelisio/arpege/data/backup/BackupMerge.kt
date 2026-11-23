package com.karelisio.arpege.data.backup

import com.karelisio.arpege.data.db.ExerciseStat
import com.karelisio.arpege.data.db.FavoriteChord
import com.karelisio.arpege.data.db.PracticeSession
import com.karelisio.arpege.data.db.TabFile

/** Ce qu'un import ajoutera ou mettra à jour (les identifiants Room sont remis à zéro). */
data class MergePlan(
    val sessions: List<PracticeSession>,
    val favorites: List<FavoriteChord>,
    val stats: List<ExerciseStat>,
    val tabs: List<TabFile>,
) {
    val isEmpty: Boolean get() = sessions.isEmpty() && favorites.isEmpty() && stats.isEmpty() && tabs.isEmpty()
}

/**
 * Fusion sans doublons d'une sauvegarde dans les données existantes ; idempotente
 * (réimporter le même fichier n'ajoute rien).
 * - séances : même début, durée, activité et instrument = doublon ;
 * - favoris : même accord et instrument ;
 * - stats : par (exercice, élément), on garde l'enregistrement qui compte le plus de tentatives ;
 * - tablatures : même format et même contenu.
 */
object BackupMerge {
    private fun PracticeSession.key() = listOf(startEpochMs, durationSec, activity, instrument)
    private fun FavoriteChord.key() = name to instrument
    private fun TabFile.key() = format to content

    fun plan(existing: Backup, incoming: Backup): MergePlan {
        val sessionKeys = existing.sessions.mapTo(HashSet()) { it.key() }
        val sessions = incoming.sessions
            .filter { it.durationSec > 0 && sessionKeys.add(it.key()) }
            .map { it.copy(id = 0) }

        val favoriteKeys = existing.favorites.mapTo(HashSet()) { it.key() }
        val favorites = incoming.favorites.filter { favoriteKeys.add(it.key()) }.map { it.copy(id = 0) }

        val currentStats = existing.stats.associateBy { it.exercise to it.item }.toMutableMap()
        val stats = mutableListOf<ExerciseStat>()
        for (s in incoming.stats.sortedByDescending { it.attempts }) {
            val cur = currentStats[s.exercise to s.item]
            if (cur == null || s.attempts > cur.attempts) {
                val merged = s.copy(lastSeen = maxOf(s.lastSeen, cur?.lastSeen ?: 0))
                currentStats[s.exercise to s.item] = merged
                stats += merged
            }
        }

        val tabKeys = existing.tabs.mapTo(HashSet()) { it.key() }
        val tabs = incoming.tabs.filter { tabKeys.add(it.key()) }.map { it.copy(id = 0) }

        return MergePlan(sessions, favorites, stats, tabs)
    }
}
