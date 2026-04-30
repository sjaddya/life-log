package com.example.lifelog.data

import android.content.Context
import com.example.lifelog.domain.DaySettings
import com.example.lifelog.domain.SupportedIntervals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("time_audit_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())

    val settings: StateFlow<DaySettings> = _settings.asStateFlow()

    fun saveDaySetup(wakeMinutes: Int, endMinutes: Int, intervalMinutes: Int) {
        val safeInterval = if (intervalMinutes in SupportedIntervals) intervalMinutes else 30
        prefs.edit()
            .putInt(KEY_WAKE, wakeMinutes)
            .putInt(KEY_END, endMinutes)
            .putInt(KEY_INTERVAL, safeInterval)
            .putBoolean(KEY_SETUP_COMPLETE, true)
            .apply()
        _settings.value = read()
    }

    fun updateSystemState(notificationGranted: Boolean, exactAlarmAvailable: Boolean) {
        prefs.edit()
            .putBoolean(KEY_NOTIFICATIONS_GRANTED, notificationGranted)
            .putBoolean(KEY_EXACT_ALARM_AVAILABLE, exactAlarmAvailable)
            .apply()
        _settings.value = read()
    }

    private fun read(): DaySettings = DaySettings(
        wakeMinutes = prefs.getInt(KEY_WAKE, 7 * 60),
        endMinutes = prefs.getInt(KEY_END, 23 * 60 + 30),
        intervalMinutes = prefs.getInt(KEY_INTERVAL, 30),
        setupComplete = prefs.getBoolean(KEY_SETUP_COMPLETE, false),
        notificationPermissionGranted = prefs.getBoolean(KEY_NOTIFICATIONS_GRANTED, false),
        exactAlarmAvailable = prefs.getBoolean(KEY_EXACT_ALARM_AVAILABLE, false),
        audioRetentionDays = prefs.getInt(KEY_AUDIO_RETENTION_DAYS, 7)
    )

    companion object {
        private const val KEY_WAKE = "wake_minutes"
        private const val KEY_END = "end_minutes"
        private const val KEY_INTERVAL = "interval_minutes"
        private const val KEY_SETUP_COMPLETE = "setup_complete"
        private const val KEY_NOTIFICATIONS_GRANTED = "notifications_granted"
        private const val KEY_EXACT_ALARM_AVAILABLE = "exact_alarm_available"
        private const val KEY_AUDIO_RETENTION_DAYS = "audio_retention_days"
    }
}
