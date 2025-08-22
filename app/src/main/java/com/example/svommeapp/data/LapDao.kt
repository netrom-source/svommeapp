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

    @Query("DELETE FROM lap WHERE sessionId = :sessionId")
    suspend fun deleteLapsBySession(sessionId: Long)

    @Query("DELETE FROM session WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)

    @Query("DELETE FROM lap")
    suspend fun deleteAllLaps()

    @Query("DELETE FROM session")
    suspend fun deleteAllSessions()

    @Transaction
    suspend fun deleteSessionWithLaps(sessionId: Long) {
        deleteLapsBySession(sessionId)
        deleteSessionById(sessionId)
    }

    @Transaction
    suspend fun deleteAllSessionsWithLaps() {
        deleteAllLaps()
        deleteAllSessions()
    }
}
