package com.example.svommeapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LapDao {
    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Insert
    suspend fun insertLap(lap: LapEntity)

    @Transaction
    @Query("SELECT * FROM session ORDER BY startedAt DESC")
    fun getSessionsWithLaps(): Flow<List<SessionWithLaps>>
}
