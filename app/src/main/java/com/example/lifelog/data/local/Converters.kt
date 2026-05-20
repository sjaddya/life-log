package com.example.lifelog.data.local

import androidx.room.TypeConverter
import com.example.lifelog.domain.EntrySource
import com.example.lifelog.domain.EntryStatus

// Room type converters mapping the status / source enums to their raw string
// form. The column stays TEXT, so the stored representation — and the exported
// schema — are identical to when these were string constants.
//
// `status` is a NOT NULL column, so its converters are non-null. `source` is
// nullable — its converters must accept/return null, otherwise Room passes a
// null cursor value into a non-null Kotlin parameter and the intrinsic
// null-check throws.
class Converters {
    @TypeConverter
    fun statusToRaw(status: EntryStatus): String = status.raw

    @TypeConverter
    fun statusFromRaw(raw: String): EntryStatus = EntryStatus.fromRaw(raw)

    @TypeConverter
    fun sourceToRaw(source: EntrySource?): String? = source?.raw

    @TypeConverter
    fun sourceFromRaw(raw: String?): EntrySource? = raw?.let { EntrySource.fromRaw(it) }
}
