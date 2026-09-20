package com.datenote.app.domain

import com.datenote.app.domain.model.ScheduleStatus
import com.datenote.app.domain.model.daysBetween
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
        val todo = TestSchedule(1, ScheduleStatus.TODO)
        val complete = TestSchedule(1, ScheduleStatus.COMPLETED)
        val today = LocalDate.ofEpochDay(2)
        assertTrue(isOverdue(todo, today))
        assertFalse(isOverdue(complete, today))
    }

    private data class TestSchedule(
        override val scheduledEpochDay: Long,
        override val status: ScheduleStatus,
    ) : com.datenote.app.domain.model.ScheduleDateLike
}
