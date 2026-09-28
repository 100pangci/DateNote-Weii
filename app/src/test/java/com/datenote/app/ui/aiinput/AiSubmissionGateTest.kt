package com.datenote.app.ui.aiinput

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AiSubmissionGateTest {
    @Test fun batchConfirmationCannotStartWhileAnotherBatchIsSaving() {
        val gate = SubmissionGate()
        assertTrue(gate.tryStart())
        assertFalse(gate.tryStart())
        gate.finish()
        assertTrue(gate.tryStart())
    }

    @Test fun aiDraftSerializationRetainsStableStepIdsAndUnsubmittedValues() {
        val draft = EditableAiDraft(
            id = 0,
            title = "临时安排",
            startDate = "2026-10-01",
            endDate = "2026-10-03",
            time = "",
            category = "",
            note = "仍未保存",
            reminder = "1440",
            reminderEnabled = true,
            steps = listOf(EditableAiStep(-2, "刚新增的步骤", false)),
            confidence = 0.8,
            uncertainties = emptyList(),
        )

        val restored = Json.decodeFromString<EditableAiDraft>(Json.encodeToString(draft))

        assertTrue(restored == draft)
        assertTrue(restored.steps.single().stableId == -2L)
    }
}
