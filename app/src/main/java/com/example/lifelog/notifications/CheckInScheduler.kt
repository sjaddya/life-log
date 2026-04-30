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

        entries
            .filter { it.startTime > System.currentTimeMillis() }
            .forEach { entry ->
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    entry.startTime,
                    pendingIntent(entry.id)
                )
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
