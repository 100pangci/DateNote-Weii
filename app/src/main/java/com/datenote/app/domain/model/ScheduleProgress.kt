package com.datenote.app.domain.model

import com.datenote.app.data.local.ScheduleStepEntity
import com.datenote.app.data.local.ScheduleWithSteps

data class ProgressSummary(
    val completedCount: Int,
    val totalCount: Int,
) {
    val hasSteps: Boolean get() = totalCount > 0
    val fraction: Float get() = if (totalCount == 0) 0f else completedCount.toFloat() / totalCount
}

fun List<ScheduleStepEntity>.progressSummary(): ProgressSummary = ProgressSummary(
    completedCount = count { it.isCompleted },
    totalCount = size,
)

val ScheduleWithSteps.progress: ProgressSummary
    get() = orderedSteps.progressSummary()

fun statusAfterStepChange(
    currentStatus: ScheduleStatus,
    steps: List<ScheduleStepEntity>,
): ScheduleStatus {
    if (steps.isEmpty()) return currentStatus
    val completed = steps.count { it.isCompleted }
    return when {
        completed == steps.size -> ScheduleStatus.COMPLETED
        completed > 0 -> ScheduleStatus.IN_PROGRESS
        currentStatus == ScheduleStatus.COMPLETED -> ScheduleStatus.IN_PROGRESS
        else -> currentStatus
    }
}
