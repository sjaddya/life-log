package com.example.lifelog.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.lifelog.data.local.entity.Entry
import java.time.LocalDate
import java.time.ZoneId

class CheckInScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExactAlarms(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }

    fun scheduleToday(entries: List<Entry>) {
        if (!canScheduleExactAlarms()) return

        val now = System.currentTimeMillis()
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
                }
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

    private fun nextRolloverTimeMillis(): Long {
        val zone = ZoneId.systemDefault()
        return LocalDate.now(zone)
            .plusDays(1)
            .atTime(0, 1)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }

    private fun pendingIntent(entryId: String): PendingIntent {
        val intent = Intent(context, CheckInReceiver::class.java)
            .setAction(CheckInReceiver.ACTION_CHECK_IN)
            .putExtra(CheckInReceiver.EXTRA_ENTRY_ID, entryId)
        return PendingIntent.getBroadcast(
            context,
            entryId.hashCode(),
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
    }
}
