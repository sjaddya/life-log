package com.example.lifelog.data.local.dao

import com.example.lifelog.data.local.entity.Entry
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<Entry>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: Entry)

    @Update
    suspend fun update(entry: Entry)

    @Query("SELECT * FROM Entry ORDER BY startTime DESC")
    fun getAll(): Flow<List<Entry>>

    @Query("SELECT * FROM Entry WHERE date = :date ORDER BY startTime ASC")
    fun entriesForDate(date: String): Flow<List<Entry>>

    @Query("SELECT * FROM Entry WHERE date = :date ORDER BY startTime ASC")
    suspend fun entriesForDateOnce(date: String): List<Entry>

    @Query("SELECT * FROM Entry WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Entry?

    @Query("SELECT * FROM Entry WHERE date = :date AND status = :status ORDER BY startTime ASC")
    suspend fun entriesByStatus(date: String, status: String): List<Entry>

    @Query("UPDATE Entry SET status = :newStatus WHERE date = :date AND status = :oldStatus AND endTime < :before")
    suspend fun updateStatusBefore(date: String, oldStatus: String, newStatus: String, before: Long)
}
