package com.example.svommeapp.data

import androidx.room.Embedded
import androidx.room.Relation

data class SessionWithLaps(
    @Embedded val session: SessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val laps: List<LapEntity>
)
