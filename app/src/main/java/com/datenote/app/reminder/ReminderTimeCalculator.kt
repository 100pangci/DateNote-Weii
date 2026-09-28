package com.datenote.app.reminder

import com.datenote.app.data.local.ScheduleEntity
import com.datenote.app.domain.model.hasValidDateAndReminderRange
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

fun reminderDateTime(schedule: ScheduleEntity, defaultReminderTimeMinutes: Int): LocalDateTime? {
    val minutes = schedule.remindBeforeMinutes ?: return null
    if (!schedule.hasValidDateAndReminderRange()) return null
    if (schedule.minuteOfDay == null && defaultReminderTimeMinutes !in 0..1439) return null
    return runCatching {
        val date = LocalDate.ofEpochDay(schedule.endEpochDay)
        val time = schedule.minuteOfDay?.let { LocalTime.of(it / 60, it % 60) }
            ?: LocalTime.of(defaultReminderTimeMinutes / 60, defaultReminderTimeMinutes % 60)
        LocalDateTime.of(date, time).minusMinutes(minutes)
    }.getOrNull()
}
