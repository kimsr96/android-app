package com.example.data.dao

import androidx.room.*
import com.example.data.model.MeetingNote
import kotlinx.coroutines.flow.Flow

@Dao
interface MeetingNoteDao {
    @Query("SELECT * FROM meeting_notes ORDER BY dateMillis DESC")
    fun getAllNotes(): Flow<List<MeetingNote>>

    @Query("SELECT * FROM meeting_notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Int): MeetingNote?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: MeetingNote): Long

    @Update
    suspend fun updateNote(note: MeetingNote)

    @Delete
    suspend fun deleteNote(note: MeetingNote)

    @Query("DELETE FROM meeting_notes WHERE id = :id")
    suspend fun deleteNoteById(id: Int)
}
