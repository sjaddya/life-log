package com.example.lifelog.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.lifelog.data.local.dao.EntryDao
import com.example.lifelog.data.local.entity.Entry

@Database(entities = [Entry::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        // Single shared instance. MainActivity, the scheduling receiver, and
        // any future background worker must all go through this — two Room
        // instances on the same file fight over invalidation tracking.
        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "lifelog-db"
            )
                .addMigrations(MIGRATION_2_3)
                .build()
    }
}
