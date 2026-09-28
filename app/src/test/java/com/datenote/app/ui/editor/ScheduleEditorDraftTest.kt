package com.datenote.app.ui.editor

import com.datenote.app.data.local.ScheduleStepEntity
import com.datenote.app.domain.model.ScheduleStatus
import com.datenote.app.ui.components.moveItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleEditorDraftTest {
    @Test fun emptyStepListNeverRequestsFocusAndNewEmptyLastStepDoes() {
        assertFalse(emptyDraft().shouldFocusLastEmptyStep())
        val withStep = emptyDraft().copy(steps = listOf(step(-1, "")))
        assertTrue(withStep.shouldFocusLastEmptyStep())
    }

    @Test fun transientStepIdIsIndependentOfEditedEntityAndMovesWithItsRow() {
        val first = step(-1, "第一步")
        val second = step(-2, "第二步")
        val renamed = first.copy(step = first.step.copy(title = "已改名"))
        assertEquals(first.rowId, renamed.rowId)
        val reordered = listOf(renamed, second).moveItem(0, 1)
        assertEquals(listOf(-2L, -1L), reordered.map { it.rowId })
    }

    @Test fun saveSubmissionGateRejectsRapidRepeatAndAllowsRetryAfterCompletion() {
        val gate = SubmissionGate()
        assertTrue(gate.tryStart())
        assertFalse(gate.tryStart())
        gate.finish()
        assertTrue(gate.tryStart())
    }

    @Test fun editorDraftRoundTripsThroughSavedStateWithStableStepKeys() {
        val draft = emptyDraft().copy(
            title = "未保存标题",
            steps = listOf(step(-4, "未保存步骤")),
        )

        val restored = Json.decodeFromString<SavedEditorDraft>(
            Json.encodeToString(draft.toSavedSnapshot(scheduleId = 0L)),
        ).toEditorDraft()

        assertEquals(draft, restored)
    }

    private fun emptyDraft() = ScheduleEditorDraft(
        title = "",
        note = "",
        category = "",
        startEpochDay = 0,
        endEpochDay = 0,
        minuteOfDay = null,
        status = ScheduleStatus.TODO,
        steps = emptyList(),
        reminderEnabled = true,
        reminderText = "1440",
    )

    private fun step(id: Long, title: String) = EditorStepDraft(
        rowId = id,
        step = ScheduleStepEntity(scheduleId = 0, title = title, position = 0),
    )
}
