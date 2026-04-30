package com.example.lifelog.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.lifelog.data.local.dao.EntryDao
import com.example.lifelog.data.local.entity.Entry

@Database(entities = [Entry::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
}
