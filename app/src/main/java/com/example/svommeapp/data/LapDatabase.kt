package com.example.svommeapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SessionEntity::class, LapEntity::class], version = 1)
abstract class LapDatabase : RoomDatabase() {
    abstract fun lapDao(): LapDao

    companion object {
        @Volatile private var INSTANCE: LapDatabase? = null

        fun get(context: Context): LapDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                LapDatabase::class.java,
                "laps.db"
            ).build().also { INSTANCE = it }
        }
    }
}
