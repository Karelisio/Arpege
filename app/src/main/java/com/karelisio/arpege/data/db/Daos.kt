package com.karelisio.arpege.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PracticeDao {
    @Insert suspend fun insert(s: PracticeSession): Long
    @Delete suspend fun delete(s: PracticeSession)
    @Query("SELECT * FROM practice_session ORDER BY startEpochMs DESC")
    fun all(): Flow<List<PracticeSession>>
    @Query("SELECT * FROM practice_session WHERE startEpochMs >= :from ORDER BY startEpochMs DESC")
    suspend fun since(from: Long): List<PracticeSession>
}

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun add(f: FavoriteChord)
    @Query("DELETE FROM favorite_chord WHERE name = :name AND instrument = :instrument")
    suspend fun remove(name: String, instrument: String)
    @Query("SELECT * FROM favorite_chord ORDER BY addedAt DESC")
    fun all(): Flow<List<FavoriteChord>>
}

@Dao
interface StatDao {
    @Upsert suspend fun upsert(s: ExerciseStat)
    @Query("SELECT * FROM exercise_stat WHERE exercise = :exercise")
    fun forExercise(exercise: String): Flow<List<ExerciseStat>>
    @Query("SELECT * FROM exercise_stat WHERE exercise = :exercise AND item = :item")
    suspend fun get(exercise: String, item: String): ExerciseStat?
    @Query("DELETE FROM exercise_stat WHERE exercise = :exercise")
    suspend fun reset(exercise: String)
}

@Dao
interface TabDao {
    @Insert suspend fun insert(t: TabFile): Long
    @Delete suspend fun delete(t: TabFile)
    @Query("UPDATE tab_file SET transpose = :transpose, scrollSpeed = :speed WHERE id = :id")
    suspend fun updatePrefs(id: Long, transpose: Int, speed: Float)
    @Query("SELECT * FROM tab_file ORDER BY importedAt DESC")
    fun all(): Flow<List<TabFile>>
    @Query("SELECT * FROM tab_file WHERE id = :id")
    suspend fun get(id: Long): TabFile?
}
