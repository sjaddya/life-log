package com.example.lifelog.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(indices = [Index(value = ["date"]), Index(value = ["status"])])
data class Entry(
    @PrimaryKey val id: String,
    val date: String,
    val startTime: Long,
    val endTime: Long,
    val text: String? = null,
    val audioPath: String? = null,
    val status: String,
    val source: String? = null,
    val createdAt: Long,
    val filledAt: Long? = null,
    val isEdited: Boolean = false
)
