package com.example.lifelog.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lifelog.data.TimeAuditRepository
import com.example.lifelog.data.local.entity.Entry
import com.example.lifelog.domain.DaySettings
import com.example.lifelog.notifications.CheckInScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val settings: DaySettings = DaySettings(),
    val entries: List<Entry> = emptyList(),
    val activeEntry: Entry? = null
)

class MainViewModel(
    private val repository: TimeAuditRepository,
    private val scheduler: CheckInScheduler
) : ViewModel() {
    private val activeEntryId = MutableStateFlow<String?>(null)

    val entries: Flow<List<Entry>> = repository.todayEntries()

    val state: StateFlow<MainUiState> = combine(
        repository.settings,
        repository.todayEntries(),
        activeEntryId
    ) { settings, entries, activeId ->
        MainUiState(
            settings = settings,
            entries = entries,
            activeEntry = entries.firstOrNull { it.id == activeId }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        viewModelScope.launch {
            repository.ensureTodayExists()
            scheduler.scheduleToday(repository.todayEntriesOnce())
        }
    }

    fun refreshSystemState(notificationGranted: Boolean, exactAlarmAvailable: Boolean) {
        repository.updateSystemState(notificationGranted, exactAlarmAvailable)
    }

    fun selectEntry(entryId: String?) {
        activeEntryId.value = entryId
    }

    fun saveSetup(wakeMinutes: Int, endMinutes: Int, intervalMinutes: Int, afterSave: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveSetup(wakeMinutes, endMinutes, intervalMinutes)
            scheduler.scheduleToday(repository.todayEntriesOnce())
            afterSave()
        }
    }

    fun saveText(entryId: String, text: String, afterSave: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveText(entryId, text)
            afterSave()
        }
    }

    fun attachAudio(entryId: String, audioPath: String, afterSave: () -> Unit = {}) {
        viewModelScope.launch {
            repository.attachAudio(entryId, audioPath)
            afterSave()
        }
    }

    fun skip(entryId: String, afterSave: () -> Unit = {}) {
        viewModelScope.launch {
            repository.skip(entryId)
            afterSave()
        }
    }

    fun remindLater(entryId: String, afterSave: () -> Unit = {}) {
        viewModelScope.launch {
            repository.remindLater(entryId)
            afterSave()
        }
    }
}
