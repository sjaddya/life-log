package com.example.lifelog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class IntervalGeneratorTest {
    private val date = LocalDate.of(2026, 4, 30)

    @Test
    fun defaultSettingsCreateThirtyThreeIntervals() {
        val entries = IntervalGenerator.generateForDate(DaySettings(setupComplete = true), date)

        assertEquals(33, entries.size)
        assertTrue(entries.all { it.date == "2026-04-30" })
        assertTrue(entries.all { it.status == EntryStatus.Pending })
    }

    @Test
    fun customSixtyMinuteIntervalUsesWakeAndEndTimes() {
        val settings = DaySettings(
            wakeMinutes = 8 * 60,
            endMinutes = 12 * 60,
            intervalMinutes = 60,
            setupComplete = true
        )

        val entries = IntervalGenerator.generateForDate(settings, date)

        assertEquals(4, entries.size)
        assertEquals("8:00 AM", IntervalGenerator.formatClock(entries.first().startTime))
        assertEquals("12:00 PM", IntervalGenerator.formatClock(entries.last().endTime))
    }

    @Test
    fun intervalIdsAreStableForSameDateAndSettings() {
        val first = IntervalGenerator.generateForDate(DaySettings(setupComplete = true), date)
        val second = IntervalGenerator.generateForDate(DaySettings(setupComplete = true), date)

        assertEquals(first.map { it.id }, second.map { it.id })
    }
}
