package com.example.lifelog.domain

data class DaySettings(
    val wakeMinutes: Int = 7 * 60,
    val endMinutes: Int = 23 * 60 + 30,
    val intervalMinutes: Int = 30,
    val setupComplete: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    val exactAlarmAvailable: Boolean = false,
    val audioRetentionDays: Int = 7
)
