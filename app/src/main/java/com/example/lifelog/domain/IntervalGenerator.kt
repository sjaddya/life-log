package com.example.lifelog.domain

import com.example.lifelog.data.local.entity.Entry
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object IntervalGenerator {
    private val zone: ZoneId = ZoneId.systemDefault()

    fun todayKey(): String = LocalDate.now(zone).toString()

    fun generateForDate(settings: DaySettings, date: LocalDate = LocalDate.now(zone)): List<Entry> {
        val interval = settings.intervalMinutes.coerceAtLeast(1)
        val startMinute = settings.wakeMinutes.coerceIn(0, 24 * 60 - 1)
        val endMinute = settings.endMinutes.coerceIn(startMinute + interval, 24 * 60)
        val dateKey = date.toString()
        val createdAt = System.currentTimeMillis()
        val entries = mutableListOf<Entry>()
        var cursor = startMinute

        while (cursor < endMinute) {
            val next = (cursor + interval).coerceAtMost(endMinute)
            val startMillis = date.atMinuteOfDay(cursor).toMillis()
            val endMillis = date.atMinuteOfDay(next).toMillis()
            entries += Entry(
                id = "$dateKey-$startMillis-$endMillis",
                date = dateKey,
                startTime = startMillis,
                endTime = endMillis,
                status = EntryStatus.Pending,
                createdAt = createdAt
            )
            cursor = next
        }

        return entries
    }

    fun formatClock(millis: Long): String {
        val time = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), zone).toLocalTime()
        val hour = time.hour
        val minute = time.minute
        val suffix = if (hour >= 12) "PM" else "AM"
        val displayHour = when (val h = hour % 12) {
            0 -> 12
            else -> h
        }
        return "%d:%02d %s".format(displayHour, minute, suffix)
    }

    fun formatMinutes(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    private fun LocalDate.atMinuteOfDay(minutes: Int): LocalDateTime {
        val safeMinutes = minutes.coerceIn(0, 24 * 60)
        return if (safeMinutes == 24 * 60) {
            atTime(LocalTime.MAX)
        } else {
            atTime(safeMinutes / 60, safeMinutes % 60)
        }
    }

    private fun LocalDateTime.toMillis(): Long = atZone(zone).toInstant().toEpochMilli()
}
