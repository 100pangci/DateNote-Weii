package com.datenote.app.ui.editor

import com.datenote.app.data.local.ScheduleEntity
import com.datenote.app.data.local.ScheduleStepEntity
import com.datenote.app.domain.model.ScheduleStatus
import kotlinx.serialization.Serializable

data class EditorStepDraft(
    val rowId: Long,
    val step: ScheduleStepEntity,
)

data class ScheduleEditorDraft(
    val title: String,
    val note: String,
    val category: String,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val minuteOfDay: Int?,
    val status: ScheduleStatus,
    val steps: List<EditorStepDraft>,
    val reminderEnabled: Boolean,
    val reminderText: String,
)

internal fun ScheduleEditorDraft.shouldFocusLastEmptyStep(): Boolean =
    steps.lastOrNull()?.step?.title?.isEmpty() == true

internal fun ScheduleEditorDraft.toSavedSnapshot(scheduleId: Long): SavedEditorDraft = SavedEditorDraft(
    scheduleId = scheduleId,
    title = title,
    note = note,
    category = category,
    startEpochDay = startEpochDay,
    endEpochDay = endEpochDay,
    minuteOfDay = minuteOfDay,
    status = status.name,
    steps = steps.map { item ->
        SavedEditorStep(
            rowId = item.rowId,
            id = item.step.id,
            scheduleId = item.step.scheduleId,
            title = item.step.title,
            position = item.step.position,
            isCompleted = item.step.isCompleted,
            completedAt = item.step.completedAt,
            createdAt = item.step.createdAt,
            updatedAt = item.step.updatedAt,
        )
    },
    reminderEnabled = reminderEnabled,
    reminderText = reminderText,
)

internal fun SavedEditorDraft.toEditorDraft(): ScheduleEditorDraft = ScheduleEditorDraft(
    title = title,
    note = note,
    category = category,
    startEpochDay = startEpochDay,
    endEpochDay = endEpochDay,
    minuteOfDay = minuteOfDay,
    status = runCatching { ScheduleStatus.valueOf(status) }.getOrDefault(ScheduleStatus.TODO),
    steps = steps.map { item ->
        EditorStepDraft(
            rowId = item.rowId,
            step = ScheduleStepEntity(
                id = item.id,
                scheduleId = item.scheduleId,
                title = item.title,
                position = item.position,
                isCompleted = item.isCompleted,
                completedAt = item.completedAt,
                createdAt = item.createdAt,
                updatedAt = item.updatedAt,
            ),
        )
    },
    reminderEnabled = reminderEnabled,
    reminderText = reminderText,
)

internal fun ScheduleEditorDraft.toScheduleEntity(original: ScheduleEntity): ScheduleEntity = original.copy(
    title = title,
    note = note,
    category = category,
    startEpochDay = startEpochDay,
    endEpochDay = endEpochDay,
    minuteOfDay = minuteOfDay,
    status = status,
    remindBeforeMinutes = if (reminderEnabled) reminderText.trim().toLongOrNull() else null,
)

@Serializable
internal data class SavedEditorDraft(
    val scheduleId: Long,
    val title: String,
    val note: String,
    val category: String,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val minuteOfDay: Int?,
    val status: String,
    val steps: List<SavedEditorStep>,
    val reminderEnabled: Boolean,
    val reminderText: String,
)

@Serializable
internal data class SavedEditorStep(
    val rowId: Long,
    val id: Long,
    val scheduleId: Long,
    val title: String,
    val position: Int,
    val isCompleted: Boolean,
    val completedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)
