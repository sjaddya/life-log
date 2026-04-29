package com.example.lifelog.data.local.dao

import com.example.lifelog.data.local.entity.Entry
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Insert
    suspend fun insert(entry: Entry)

//    @Query("SELECT * FROM Entry")
//    suspend fun getAll(): List<Entry>

    @Query("SELECT * FROM Entry ORDER BY startTime DESC")
    fun getAll(): Flow<List<Entry>>
}