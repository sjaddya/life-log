package com.example.lifelog.notifications

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.lifelog.data.local.entity.Entry
import com.example.lifelog.domain.EntryStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CheckInSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val scheduler = CheckInScheduler(context)

    private fun entry(id: String, endTime: Long) = Entry(
        id = id,
        date = "2026-05-18",
        startTime = endTime - 1_000L,
        endTime = endTime,
        status = EntryStatus.Pending,
        createdAt = 0L
    )

    @Test
    fun scheduleToday_armsFutureEntriesOnly() {
        val now = System.currentTimeMillis()
        scheduler.scheduleToday(
            listOf(entry("future", now + 60_000), entry("past", now - 60_000))
        )
        // Only the future entry should produce an alarm.
        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
    }

    @Test
    fun scheduleToday_vacationMode_armsNothing() {
        val now = System.currentTimeMillis()
        scheduler.scheduleToday(
            listOf(entry("future", now + 60_000)),
            vacationMode = true
        )
        assertEquals(0, shadowOf(alarmManager).scheduledAlarms.size)
    }

    @Test
    fun scheduleNextRollover_armsOneAlarm() {
        scheduler.scheduleNextRollover()
        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
    }
}
