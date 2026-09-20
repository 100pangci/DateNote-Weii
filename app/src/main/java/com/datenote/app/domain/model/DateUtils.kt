package com.datenote.app.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

fun monthGrid(month: YearMonth): List<LocalDate> {
    val first = month.atDay(1)
    val firstMonday = first.minusDays((first.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
    return (0L until 42L).map { firstMonday.plusDays(it) }
}

fun daysBetween(today: LocalDate, target: LocalDate): Long =
    target.toEpochDay() - today.toEpochDay()

fun inclusiveDays(startEpochDay: Long, endEpochDay: Long): Long =
    (endEpochDay - startEpochDay + 1).coerceAtLeast(0)

fun isOverdue(schedule: ScheduleDateLike, today: LocalDate): Boolean =
    schedule.status != ScheduleStatus.COMPLETED && schedule.endEpochDay < today.toEpochDay()

interface ScheduleDateLike {
    val startEpochDay: Long
    val endEpochDay: Long
    val status: ScheduleStatus
}
