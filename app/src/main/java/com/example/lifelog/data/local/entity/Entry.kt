package com.example.lifelog.data.local.entity
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity
data class Entry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val startTime: Long,
    val endTime: Long,
    val text: String?,
    val status: String
)