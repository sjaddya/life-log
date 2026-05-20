package com.example.lifelog.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.lifelog.data.SettingsRepository
import com.example.lifelog.data.TimeAuditRepository
import com.example.lifelog.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Keeps the check-in schedule alive without the user opening the app.
// Triggered by:
//  - the daily rollover alarm (ACTION_DAY_ROLLOVER) just after midnight,
//  - device boot (BOOT_COMPLETED) — exact alarms are cleared on reboot,
//  - clock / timezone changes (TIME_SET, TIMEZONE_CHANGED).
// In every case it ensures today's entries exist, (re)schedules today's
// check-in alarms, and re-arms the next rollover.
class SchedulingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = TimeAuditRepository(
                    AppDatabase.getInstance(appContext),
                    SettingsRepository(appContext)
                )
                val scheduler = CheckInScheduler(appContext)
                repository.ensureTodayExists()
                scheduler.scheduleToday(repository.todayEntriesOnce())
                scheduler.scheduleNextRollover()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DAY_ROLLOVER = "com.example.lifelog.action.DAY_ROLLOVER"
    }
}
