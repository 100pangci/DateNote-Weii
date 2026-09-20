package com.datenote.app.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class ScheduleTypeWithSteps(
    @Embedded
    val type: ScheduleTypeEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "typeId",
    )
    val steps: List<ScheduleTypeStepEntity>,
) {
    val orderedSteps: List<ScheduleTypeStepEntity>
        get() = steps.sortedWith(compareBy<ScheduleTypeStepEntity> { it.position }.thenBy { it.id })

    fun toScheduleSteps(scheduleId: Long): List<ScheduleStepEntity> = orderedSteps.mapIndexed { index, step ->
        ScheduleStepEntity(
            scheduleId = scheduleId,
            title = step.title,
            position = index,
            isCompleted = false,
            completedAt = null,
        )
    }
}
