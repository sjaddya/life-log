package com.example.lifelog.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.lifelog.data.local.entity.Entry
import java.time.LocalDate
import java.time.ZoneId

class CheckInScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun canScheduleExactAlarms(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }

    fun scheduleToday(entries: List<Entry>) {
        if (!canScheduleExactAlarms()) return

        // Cancel alarms armed in the previous pass first, so any id that has
        // dropped out of today's set (regen, DST reshape) leaves no orphan.
        cancelTrackedAlarms()

        val now = System.currentTimeMillis()
        val scheduled = mutableSetOf<String>()
        entries
            .filter { it.endTime > now }
            .forEach { entry ->
                // Exact-alarm permission can be revoked between canScheduleExactAlarms()
                // and this call; swallow so one bad slot doesn't crash the activity.
                runCatching {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        entry.endTime,
                        pendingIntent(entry.id)
                    )
                }.onSuccess { scheduled += entry.id }
            }
        prefs.edit().putStringSet(KEY_SCHEDULED_IDS, scheduled).apply()
    }

    // Re-prompt for an entry the user asked to be reminded about later.
    // Uses a salted request code so it doesn't collide with the slot's
    // original alarm PendingIntent.
    fun scheduleSnooze(entryId: String, delayMinutes: Long = SNOOZE_MINUTES) {
        if (!canScheduleExactAlarms()) return
        runCatching {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + delayMinutes * 60_000L,
                pendingIntent(entryId, SNOOZE_SALT)
            )
        }
    }

    // The user has filled / skipped this entry — drop its pending alarm, any
    // snooze alarm, and the posted notification so nothing stale fires later.
    fun dismiss(entryId: String) {
        runCatching { alarmManager.cancel(pendingIntent(entryId)) }
        runCatching { alarmManager.cancel(pendingIntent(entryId, SNOOZE_SALT)) }
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                ?.cancel(entryId.hashCode())
        }
    }

    // Arm a single alarm just after midnight so the next day's entries get
    // generated and scheduled even if the user never opens the app. The
    // receiver re-arms this each time it fires, so the chain is self-perpetuating.
    fun scheduleNextRollover() {
        if (!canScheduleExactAlarms()) return
        runCatching {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextRolloverTimeMillis(),
                rolloverPendingIntent()
            )
        }
    }

    private fun cancelTrackedAlarms() {
        val tracked = prefs.getStringSet(KEY_SCHEDULED_IDS, emptySet()).orEmpty()
        tracked.forEach { id -> runCatching { alarmManager.cancel(pendingIntent(id)) } }
    }

    private fun nextRolloverTimeMillis(): Long {
        val zone = ZoneId.systemDefault()
        return LocalDate.now(zone)
            .plusDays(1)
            .atTime(0, 1)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }

    private fun pendingIntent(entryId: String, salt: Int = 0): PendingIntent {
        val intent = Intent(context, CheckInReceiver::class.java)
            .setAction(CheckInReceiver.ACTION_CHECK_IN)
            .putExtra(CheckInReceiver.EXTRA_ENTRY_ID, entryId)
        return PendingIntent.getBroadcast(
            context,
            entryId.hashCode() xor salt,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun rolloverPendingIntent(): PendingIntent {
        val intent = Intent(context, SchedulingReceiver::class.java)
            .setAction(SchedulingReceiver.ACTION_DAY_ROLLOVER)
        return PendingIntent.getBroadcast(
            context,
            ROLLOVER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        // Fixed request code for the daily rollover alarm. Distinct from entry
        // alarms (which use entryId.hashCode()); even on a hash collision the
        // Intents differ by action so the PendingIntents stay separate.
        private const val ROLLOVER_REQUEST_CODE = 920_180

        // XOR salt giving the snooze alarm a distinct request code from the
        // entry's original slot alarm.
        private const val SNOOZE_SALT = 0x73_4E_5A
        private const val SNOOZE_MINUTES = 10L

        private const val PREFS = "checkin_scheduler"
        private const val KEY_SCHEDULED_IDS = "scheduled_entry_ids"
    }
}
