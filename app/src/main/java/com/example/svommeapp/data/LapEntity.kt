package com.example.svommeapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lap")
data class LapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val durationMs: Long,
    val source: String
)
