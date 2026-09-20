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

fun isOverdue(schedule: ScheduleDateLike, today: LocalDate): Boolean =
    schedule.status != ScheduleStatus.COMPLETED && schedule.scheduledEpochDay < today.toEpochDay()

interface ScheduleDateLike {
    val scheduledEpochDay: Long
    val status: ScheduleStatus
}
