package com.example.lifelog.data

import com.example.lifelog.data.local.AppDatabase
import com.example.lifelog.data.local.entity.Entry
import com.example.lifelog.domain.DaySettings
import com.example.lifelog.domain.EntrySource
import com.example.lifelog.domain.EntryStatus
import com.example.lifelog.domain.IntervalGenerator
import kotlinx.coroutines.flow.Flow

class TimeAuditRepository(
    private val db: AppDatabase,
    private val settingsRepository: SettingsRepository
) {
    val settings = settingsRepository.settings

    fun todayEntries(): Flow<List<Entry>> = db.entryDao().entriesForDate(IntervalGenerator.todayKey())

    suspend fun todayEntriesOnce(): List<Entry> =
        db.entryDao().entriesForDateOnce(IntervalGenerator.todayKey())

    suspend fun getEntry(id: String): Entry? = db.entryDao().getById(id)

    suspend fun saveSetup(wakeMinutes: Int, endMinutes: Int, intervalMinutes: Int) {
        settingsRepository.saveDaySetup(wakeMinutes, endMinutes, intervalMinutes)
        if (todayEntriesOnce().isEmpty()) {
            generateToday(settingsRepository.settings.value)
        }
    }

    suspend fun ensureTodayExists() {
        if (settings.value.setupComplete && todayEntriesOnce().isEmpty()) {
            generateToday(settings.value)
        }
        markPastPendingMissed()
    }

    suspend fun saveText(entryId: String, text: String) {
        val entry = getEntry(entryId) ?: return
        val status = if (entry.status == EntryStatus.Pending) EntryStatus.Completed else EntryStatus.Backfilled
        db.entryDao().update(
            entry.copy(
                text = text.trim(),
                status = status,
                source = EntrySource.Manual,
                filledAt = System.currentTimeMillis(),
                isEdited = entry.filledAt != null
            )
        )
    }

    suspend fun attachAudio(entryId: String, audioPath: String) {
        val entry = getEntry(entryId) ?: return
        val status = if (entry.status == EntryStatus.Pending) EntryStatus.Completed else EntryStatus.Backfilled
        db.entryDao().update(
            entry.copy(
                audioPath = audioPath,
                status = status,
                source = EntrySource.VoiceRecording,
                filledAt = System.currentTimeMillis(),
                isEdited = entry.filledAt != null
            )
        )
    }

    suspend fun skip(entryId: String) {
        val entry = getEntry(entryId) ?: return
        db.entryDao().update(entry.copy(status = EntryStatus.Skipped, filledAt = System.currentTimeMillis()))
    }

    suspend fun remindLater(entryId: String) {
        val entry = getEntry(entryId) ?: return
        db.entryDao().update(entry.copy(status = EntryStatus.Pending))
    }

    fun updateSystemState(notificationGranted: Boolean, exactAlarmAvailable: Boolean) {
        settingsRepository.updateSystemState(notificationGranted, exactAlarmAvailable)
    }

    private suspend fun generateToday(settings: DaySettings) {
        db.entryDao().insertAll(IntervalGenerator.generateForDate(settings))
    }

    private suspend fun markPastPendingMissed() {
        db.entryDao().updateStatusBefore(
            date = IntervalGenerator.todayKey(),
            oldStatus = EntryStatus.Pending,
            newStatus = EntryStatus.Missed,
            before = System.currentTimeMillis()
        )
    }
}
