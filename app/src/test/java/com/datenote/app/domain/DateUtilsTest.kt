package com.datenote.app.domain

import com.datenote.app.domain.model.ScheduleStatus
import com.datenote.app.data.local.ScheduleEntity
import com.datenote.app.domain.model.daysBetween
import com.datenote.app.domain.model.inclusiveDays
import com.datenote.app.domain.model.isOverdue
import com.datenote.app.domain.model.monthGrid
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DateUtilsTest {
    @Test fun monthGridStartsOnMondayAndHasSixRows() {
        val grid = monthGrid(YearMonth.of(2026, 9))
        assertEquals(42, grid.size)
        assertEquals(1, grid.first().dayOfWeek.value)
        assertEquals(2026, grid.first().year)
        assertEquals(8, grid.first().monthValue)
    }

    @Test fun epochDaysCalculateCalendarDaysAcrossYear() {
        assertEquals(1L, daysBetween(LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 1)))
        assertEquals(-2L, daysBetween(LocalDate.of(2026, 1, 3), LocalDate.of(2026, 1, 1)))
    }

    @Test fun overdueDoesNotIncludeCompletedItems() {
        val todo = TestSchedule(startEpochDay = 1, status = ScheduleStatus.TODO)
        val complete = TestSchedule(startEpochDay = 1, status = ScheduleStatus.COMPLETED)
        val today = LocalDate.ofEpochDay(2)
        assertTrue(isOverdue(todo, today))
        assertFalse(isOverdue(complete, today))
    }

    @Test fun inclusiveDaysIncludesBothEndsAcrossMonthAndYear() {
        assertEquals(8L, inclusiveDays(LocalDate.of(2026, 10, 10).toEpochDay(), LocalDate.of(2026, 10, 17).toEpochDay()))
        assertEquals(2L, inclusiveDays(LocalDate.of(2025, 12, 31).toEpochDay(), LocalDate.of(2026, 1, 1).toEpochDay()))
    }

    @Test fun rangeIsOverdueOnlyAfterItsEnd() {
        val range = RangeSchedule(
            startEpochDay = LocalDate.of(2026, 9, 10).toEpochDay(),
            endEpochDay = LocalDate.of(2026, 9, 17).toEpochDay(),
            status = ScheduleStatus.TODO,
        )
        assertFalse(isOverdue(range, LocalDate.of(2026, 9, 17)))
        assertTrue(isOverdue(range, LocalDate.of(2026, 9, 18)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun endDateBeforeStartDateCannotBeConstructed() {
        ScheduleEntity(title = "invalid", startEpochDay = 10, endEpochDay = 9)
    }

    private data class TestSchedule(
        override val startEpochDay: Long,
        override val endEpochDay: Long = startEpochDay,
        override val status: ScheduleStatus,
    ) : com.datenote.app.domain.model.ScheduleDateLike

    private data class RangeSchedule(
        override val startEpochDay: Long,
        override val endEpochDay: Long,
        override val status: ScheduleStatus,
    ) : com.datenote.app.domain.model.ScheduleDateLike
}
