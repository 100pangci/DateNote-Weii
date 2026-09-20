package com.datenote.app.domain

import com.datenote.app.data.local.ScheduleStepEntity
import com.datenote.app.domain.model.ScheduleStatus
import com.datenote.app.domain.model.progressSummary
import com.datenote.app.domain.model.statusAfterStepChange
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleProgressTest {
    @Test fun progressIsCalculatedWithoutPersistingPercentage() {
        val steps = listOf(
            ScheduleStepEntity(scheduleId = 1, title = "买材料", position = 0, isCompleted = true),
            ScheduleStepEntity(scheduleId = 1, title = "制作", position = 1),
            ScheduleStepEntity(scheduleId = 1, title = "打包", position = 2),
        )
        assertEquals(1, steps.progressSummary().completedCount)
        assertEquals(3, steps.progressSummary().totalCount)
        assertEquals(1f / 3f, steps.progressSummary().fraction)
    }

    @Test fun zeroStepsNeverDividesByZero() {
        val progress = emptyList<ScheduleStepEntity>().progressSummary()
        assertEquals(0, progress.totalCount)
        assertEquals(0f, progress.fraction)
    }

    @Test fun stepStatusMovesThroughTodoInProgressAndCompleted() {
        val first = ScheduleStepEntity(scheduleId = 1, title = "第一步", position = 0, isCompleted = true)
        val second = first.copy(id = 2, title = "第二步", position = 1, isCompleted = false)
        assertEquals(ScheduleStatus.IN_PROGRESS, statusAfterStepChange(ScheduleStatus.TODO, listOf(first, second)))
        assertEquals(ScheduleStatus.COMPLETED, statusAfterStepChange(ScheduleStatus.IN_PROGRESS, listOf(first, second.copy(isCompleted = true))))
        assertEquals(ScheduleStatus.IN_PROGRESS, statusAfterStepChange(ScheduleStatus.COMPLETED, listOf(first.copy(isCompleted = false), second.copy(isCompleted = true))))
    }
}
