package com.karelisio.arpege.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PracticeSession::class, FavoriteChord::class, ExerciseStat::class, TabFile::class],
    version = 1,
    exportSchema = true,
)
abstract class ArpegeDatabase : RoomDatabase() {
    abstract fun practice(): PracticeDao
    abstract fun favorites(): FavoriteDao
    abstract fun stats(): StatDao
    abstract fun tabs(): TabDao

    companion object {
        fun build(context: Context): ArpegeDatabase =
            Room.databaseBuilder(context, ArpegeDatabase::class.java, "arpege.db").build()
    }
}
