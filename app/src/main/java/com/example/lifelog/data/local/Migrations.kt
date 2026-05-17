package com.example.lifelog.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Central registry of Room migrations. Add a new MIGRATION_N_(N+1) here
// whenever AppDatabase.version is bumped, and reference it from
// Room.databaseBuilder(...).addMigrations(...) in MainActivity.

val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Placeholder — kept wired into the builder so the path is in place
        // when the schema is actually bumped to version 3. No schema change
        // yet, so the body is intentionally empty.
    }
}
