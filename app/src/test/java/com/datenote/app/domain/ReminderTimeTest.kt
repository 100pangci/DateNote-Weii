package com.datenote.app.domain

import com.datenote.app.data.local.ScheduleEntity
import com.datenote.app.reminder.reminderDateTime
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderTimeTest {
    @Test fun dateOnlyReminderUsesDefaultClock() {
        val schedule = ScheduleEntity(title = "test", scheduledEpochDay = LocalDate.of(2026, 10, 20).toEpochDay(), remindBeforeMinutes = 1440)
        assertEquals("2026-10-19T09:00", reminderDateTime(schedule, 540).toString())
    }

    @Test fun timedReminderSubtractsMinutesWithoutMillisecondMath() {
        val schedule = ScheduleEntity(title = "test", scheduledEpochDay = LocalDate.of(2026, 1, 1).toEpochDay(), minuteOfDay = 30, remindBeforeMinutes = 45)
        assertEquals("2025-12-31T23:45", reminderDateTime(schedule, 540).toString())
    }

    @Test fun noReminderReturnsNull() {
        val schedule = ScheduleEntity(title = "test", scheduledEpochDay = 0)
        assertNull(reminderDateTime(schedule, 540))
    }
}
