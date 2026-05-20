package com.example.lifelog.domain

data class DaySettings(
    val wakeMinutes: Int = 7 * 60,
    val endMinutes: Int = 23 * 60 + 30,
    val intervalMinutes: Int = 30,
    val setupComplete: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    val exactAlarmAvailable: Boolean = false,
    val audioRetentionDays: Int = 7,
    // When several past slots are unfilled, "Open current check-in" picks the
    // oldest unfilled slot (true) or the most recent one (false).
    val fillOldestFirst: Boolean = true
)
