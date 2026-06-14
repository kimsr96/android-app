package com.example.data.repository

import com.example.data.dao.MeetingNoteDao
import com.example.data.model.MeetingNote
import kotlinx.coroutines.flow.Flow

class MeetingNoteRepository(private val meetingNoteDao: MeetingNoteDao) {
    val allNotes: Flow<List<MeetingNote>> = meetingNoteDao.getAllNotes()

    suspend fun getNoteById(id: Int): MeetingNote? {
        return meetingNoteDao.getNoteById(id)
    }

    suspend fun insertNote(note: MeetingNote): Long {
        return meetingNoteDao.insertNote(note)
    }

    suspend fun updateNote(note: MeetingNote) {
        meetingNoteDao.updateNote(note)
    }

    suspend fun deleteNote(note: MeetingNote) {
        meetingNoteDao.deleteNote(note)
    }

    suspend fun deleteNoteById(id: Int) {
        meetingNoteDao.deleteNoteById(id)
    }
}
