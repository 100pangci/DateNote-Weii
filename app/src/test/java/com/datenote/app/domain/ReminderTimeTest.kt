package com.datenote.app.domain

import com.datenote.app.data.local.ScheduleEntity
import com.datenote.app.reminder.reminderDateTime
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderTimeTest {
    @Test fun dateOnlyReminderUsesDefaultClock() {
        val schedule = ScheduleEntity(title = "test", startEpochDay = LocalDate.of(2026, 10, 20).toEpochDay(), remindBeforeMinutes = 1440)
        assertEquals("2026-10-19T09:00", reminderDateTime(schedule, 540).toString())
    }

    @Test fun timedReminderSubtractsMinutesWithoutMillisecondMath() {
        val schedule = ScheduleEntity(title = "test", startEpochDay = LocalDate.of(2026, 1, 1).toEpochDay(), minuteOfDay = 30, remindBeforeMinutes = 45)
        assertEquals("2025-12-31T23:45", reminderDateTime(schedule, 540).toString())
    }

    @Test fun noReminderReturnsNull() {
        val schedule = ScheduleEntity(title = "test", startEpochDay = 0)
        assertNull(reminderDateTime(schedule, 540))
    }

    @Test fun dateOnlyScheduleUsesTheLatestDefaultClockValue() {
        val schedule = ScheduleEntity(
            title = "date only",
            startEpochDay = LocalDate.of(2026, 10, 20).toEpochDay(),
            remindBeforeMinutes = 60,
        )
        assertEquals("2026-10-20T08:00", reminderDateTime(schedule, 9 * 60).toString())
        assertEquals("2026-10-20T10:00", reminderDateTime(schedule, 11 * 60).toString())
    }

    @Test fun corruptStoredDateOrDefaultClockDoesNotCrashReminderCalculation() {
        val corrupt = ScheduleEntity(title = "corrupt", startEpochDay = Long.MAX_VALUE, remindBeforeMinutes = 60)
        val valid = ScheduleEntity(title = "valid", startEpochDay = 0, remindBeforeMinutes = 60)
        assertNull(reminderDateTime(corrupt, 540))
        assertNull(reminderDateTime(valid, 1440))
    }
}
