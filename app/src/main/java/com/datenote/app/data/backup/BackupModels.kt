package com.datenote.app.data.backup

import com.datenote.app.data.local.ScheduleEntity
import com.datenote.app.domain.model.ScheduleStatus
import kotlinx.serialization.Serializable

@Serializable
data class BackupDocument(
    val schemaVersion: Int = 1,
    val exportedAt: Long,
    val schedules: List<BackupSchedule>,
)

@Serializable
data class BackupSchedule(
    val title: String,
    val note: String = "",
    val scheduledEpochDay: Long,
    val minuteOfDay: Int? = null,
    val category: String? = null,
    val status: String = ScheduleStatus.TODO.name,
    val colorArgb: Long? = null,
    val remindBeforeMinutes: Long? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val completedAt: Long? = null,
)

fun ScheduleEntity.toBackup(): BackupSchedule = BackupSchedule(
    title = title,
    note = note,
    scheduledEpochDay = scheduledEpochDay,
    minuteOfDay = minuteOfDay,
    category = category,
    status = status.name,
    colorArgb = colorArgb,
    remindBeforeMinutes = remindBeforeMinutes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
)

fun BackupSchedule.toEntity(): ScheduleEntity? {
    if (title.isBlank()) return null
    val safeStatus = runCatching { ScheduleStatus.valueOf(status) }.getOrDefault(ScheduleStatus.TODO)
    val safeMinute = minuteOfDay?.takeIf { it in 0..1439 }
    return ScheduleEntity(
        title = title.trim(),
        note = note,
        scheduledEpochDay = scheduledEpochDay,
        minuteOfDay = safeMinute,
        category = category,
        status = safeStatus,
        colorArgb = colorArgb,
        remindBeforeMinutes = remindBeforeMinutes?.takeIf { it >= 0 },
        createdAt = createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
        updatedAt = updatedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
        completedAt = completedAt,
    )
}
