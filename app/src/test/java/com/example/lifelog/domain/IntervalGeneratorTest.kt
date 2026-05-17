package com.example.lifelog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

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

    @Test
    fun springForwardSkipsSlotsInsideTheDstGap() {
        // 2026-03-08 in Los Angeles: 2:00 wall-clock skips to 3:00. The
        // 2:00-2:30 and 2:30-3:00 wall-clock intervals do not exist.
        val la = ZoneId.of("America/Los_Angeles")
        val settings = DaySettings(
            wakeMinutes = 1 * 60,
            endMinutes = 4 * 60,
            intervalMinutes = 30,
            setupComplete = true
        )

        val entries = IntervalGenerator.generateForDate(
            settings,
            LocalDate.of(2026, 3, 8),
            la
        )

        // 6 wall-clock intervals minus the 2 that live inside the gap = 4.
        assertEquals(4, entries.size)
        assertTrue(entries.all { it.endTime > it.startTime })
        // Slots remain strictly monotonic by startTime (no overlaps after DST).
        val sorted = entries.sortedBy { it.startTime }
        assertEquals(entries.map { it.id }, sorted.map { it.id })
    }

    @Test
    fun fallBackEmitsMonotonicSlotsAcrossTheRepeatedHour() {
        // 2026-11-01 in Los Angeles: 2:00 PDT falls back to 1:00 PST. The
        // 1:00-2:00 wall-clock hour exists in real time twice; the generator
        // emits one slot per wall-clock interval but extends the 1:30-2:00
        // slot through the DST overlap so the schedule stays monotonic.
        val la = ZoneId.of("America/Los_Angeles")
        val settings = DaySettings(
            wakeMinutes = 0,
            endMinutes = 3 * 60,
            intervalMinutes = 30,
            setupComplete = true
        )

        val entries = IntervalGenerator.generateForDate(
            settings,
            LocalDate.of(2026, 11, 1),
            la
        )

        assertEquals(6, entries.size)
        assertTrue(entries.all { it.endTime > it.startTime })
        // Strictly monotonic — no slot starts before the previous one ends.
        entries.zipWithNext().forEach { (a, b) ->
            assertTrue(
                "expected ${a.endTime} <= ${b.startTime}",
                a.endTime <= b.startTime
            )
        }
    }
}
