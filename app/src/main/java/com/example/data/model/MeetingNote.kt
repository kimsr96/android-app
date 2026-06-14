package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meeting_notes")
data class MeetingNote(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val dateMillis: Long = System.currentTimeMillis(),
    val durationSeconds: Int = 0,
    val category: String = "General",
    val aiSummary: String? = null,
    val aiActionItems: String? = null,
    val aiKeywords: String? = null
)
