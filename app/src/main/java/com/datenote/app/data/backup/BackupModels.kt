package com.datenote.app.data.backup

import com.datenote.app.data.local.ScheduleEntity
import com.datenote.app.data.local.ScheduleStepEntity
import com.datenote.app.data.local.ScheduleWithSteps
import com.datenote.app.domain.model.ScheduleStatus
import kotlinx.serialization.Serializable

@Serializable
data class BackupDocument(
    val schemaVersion: Int = 2,
    val exportedAt: Long,
    val schedules: List<BackupSchedule>,
)

@Serializable
data class BackupStep(
    val title: String,
    val position: Int,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class BackupSchedule(
    val title: String,
    val note: String = "",
    val startEpochDay: Long? = null,
    val endEpochDay: Long? = null,
    // Nullable legacy field keeps schemaVersion 1 files importable without exporting it again.
    val scheduledEpochDay: Long? = null,
    val minuteOfDay: Int? = null,
    val category: String? = null,
    val status: String = ScheduleStatus.TODO.name,
    val colorArgb: Long? = null,
    val remindBeforeMinutes: Long? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val completedAt: Long? = null,
    val steps: List<BackupStep> = emptyList(),
)

fun ScheduleWithSteps.toBackup(): BackupSchedule = BackupSchedule(
    title = schedule.title,
    note = schedule.note,
    startEpochDay = schedule.startEpochDay,
    endEpochDay = schedule.endEpochDay,
    minuteOfDay = schedule.minuteOfDay,
    category = schedule.category,
    status = schedule.status.name,
    colorArgb = schedule.colorArgb,
    remindBeforeMinutes = schedule.remindBeforeMinutes,
    createdAt = schedule.createdAt,
    updatedAt = schedule.updatedAt,
    completedAt = schedule.completedAt,
    steps = orderedSteps.map { step ->
        BackupStep(
            title = step.title,
            position = step.position,
            isCompleted = step.isCompleted,
            completedAt = step.completedAt,
            createdAt = step.createdAt,
            updatedAt = step.updatedAt,
        )
    },
)

fun ScheduleEntity.toBackup(): BackupSchedule = ScheduleWithSteps(this, emptyList()).toBackup()

fun BackupSchedule.toScheduleWithSteps(): ScheduleWithSteps? {
    val cleanTitle = title.trim().takeIf { it.isNotEmpty() } ?: return null
    val start = startEpochDay ?: scheduledEpochDay ?: return null
    val end = (endEpochDay ?: scheduledEpochDay ?: start).coerceAtLeast(start)
    val safeStatus = runCatching { ScheduleStatus.valueOf(status) }.getOrDefault(ScheduleStatus.TODO)
    val safeMinute = minuteOfDay?.takeIf { it in 0..1439 }
    val schedule = ScheduleEntity(
        title = cleanTitle,
        note = note.trim(),
        startEpochDay = start,
        endEpochDay = end,
        minuteOfDay = safeMinute,
        category = category?.trim()?.takeIf { it.isNotEmpty() },
        status = safeStatus,
        colorArgb = colorArgb,
        remindBeforeMinutes = remindBeforeMinutes?.takeIf { it >= 0 },
        createdAt = createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
        updatedAt = updatedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
        completedAt = completedAt,
    )
    val safeSteps = steps.mapNotNull { step ->
        step.title.trim().takeIf { it.isNotEmpty() }?.let { title ->
            ScheduleStepEntity(
                scheduleId = 0,
                title = title,
                position = step.position,
                isCompleted = step.isCompleted,
                completedAt = if (step.isCompleted) step.completedAt ?: System.currentTimeMillis() else null,
                createdAt = step.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                updatedAt = step.updatedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
            )
        }
    }.sortedWith(compareBy<ScheduleStepEntity> { it.position }.thenBy { it.id })
        .mapIndexed { index, step -> step.copy(position = index) }
    return ScheduleWithSteps(schedule, safeSteps)
}

fun BackupSchedule.toEntity(): ScheduleEntity? = toScheduleWithSteps()?.schedule
