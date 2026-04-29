package com.example.lifelog.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lifelog.data.local.AppDatabase
import com.example.lifelog.data.local.entity.Entry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class MainViewModel(private val db: AppDatabase) : ViewModel() {

    val entries: Flow<List<Entry>> = db.entryDao().getAll()

    fun save(text: String) {
        viewModelScope.launch {
            val entry = Entry(
                startTime = System.currentTimeMillis(),
                endTime = System.currentTimeMillis(),
                text = text,
                status = "completed"
            )
            db.entryDao().insert(entry)
        }
    }
}