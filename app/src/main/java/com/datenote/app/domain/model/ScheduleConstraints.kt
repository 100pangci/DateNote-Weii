package com.datenote.app.domain.model

import com.datenote.app.data.local.ScheduleEntity
import java.time.LocalDate

const val MAX_REMINDER_LEAD_MINUTES = 43_200L
private val MIN_SUPPORTED_SCHEDULE_DAY = LocalDate.of(1900, 1, 1).toEpochDay()
private val MAX_SUPPORTED_SCHEDULE_DAY = LocalDate.of(2100, 12, 31).toEpochDay()

fun isSupportedScheduleDateRange(startEpochDay: Long, endEpochDay: Long): Boolean =
    startEpochDay <= endEpochDay &&
        startEpochDay >= MIN_SUPPORTED_SCHEDULE_DAY &&
        endEpochDay <= MAX_SUPPORTED_SCHEDULE_DAY &&
        runCatching {
            LocalDate.ofEpochDay(startEpochDay)
            LocalDate.ofEpochDay(endEpochDay)
        }.isSuccess

/** Checks constraints shared by backup restoration and reminder date calculations. */
fun ScheduleEntity.hasValidDateAndReminderRange(): Boolean {
    if (!isSupportedScheduleDateRange(startEpochDay, endEpochDay)) return false
    if (minuteOfDay != null && minuteOfDay !in 0..1439) return false
    if (remindBeforeMinutes != null && remindBeforeMinutes !in 0..MAX_REMINDER_LEAD_MINUTES) return false
    return true
}
