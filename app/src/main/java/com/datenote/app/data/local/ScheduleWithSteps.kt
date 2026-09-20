package com.datenote.app.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class ScheduleWithSteps(
    @Embedded
    val schedule: ScheduleEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "scheduleId",
    )
    val steps: List<ScheduleStepEntity>,
) {
    val orderedSteps: List<ScheduleStepEntity>
        get() = steps.sortedWith(compareBy<ScheduleStepEntity> { it.position }.thenBy { it.id })
}
