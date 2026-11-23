package com.karelisio.arpege.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Séance de pratique (auto si un outil est utilisé plus d'une minute, ou manuelle). */
@Entity(tableName = "practice_session", indices = [Index("startEpochMs")])
data class PracticeSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startEpochMs: Long,
    val durationSec: Int,
    val instrument: String,
    val activity: String,
    val note: String = "",
    val auto: Boolean = true,
)

@Entity(tableName = "favorite_chord", indices = [Index(value = ["name", "instrument"], unique = true)])
data class FavoriteChord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val instrument: String,
    val addedAt: Long = System.currentTimeMillis(),
)

/** Statistiques par élément d'exercice (ex. "ear_interval" / "P5"). */
@Entity(tableName = "exercise_stat", primaryKeys = ["exercise", "item"])
data class ExerciseStat(
    val exercise: String,
    val item: String,
    val correct: Int = 0,
    val attempts: Int = 0,
    val lastSeen: Long = 0,
)

@Entity(tableName = "tab_file")
data class TabFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String = "",
    val format: String,
    val content: String,
    val sourceName: String,
    val importedAt: Long = System.currentTimeMillis(),
    val transpose: Int = 0,
    val scrollSpeed: Float = 1f,
)
