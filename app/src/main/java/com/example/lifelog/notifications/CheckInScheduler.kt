package com.example.lifelog.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.lifelog.data.local.entity.Entry

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
}
