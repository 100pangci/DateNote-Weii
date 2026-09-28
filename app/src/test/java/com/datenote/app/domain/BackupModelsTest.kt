package com.datenote.app.domain

import com.datenote.app.data.backup.BackupSchedule
import com.datenote.app.data.backup.BackupDocument
import com.datenote.app.data.backup.toBackupData
import com.datenote.app.data.backup.toScheduleWithSteps
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupModelsTest {
    @Test fun oldSingleDateBackupImportsAsSingleDayWithoutSteps() {
        val date = LocalDate.of(2026, 10, 20).toEpochDay()
        val restored = BackupSchedule(title = "发美肌", scheduledEpochDay = date).toScheduleWithSteps()!!
        assertEquals(date, restored.schedule.startEpochDay)
        assertEquals(date, restored.schedule.endEpochDay)
        assertTrue(restored.steps.isEmpty())
    }

    @Test fun invalidEpochDayAndReverseDateRangeAreRejected() {
        assertNull(BackupSchedule(title = "bad", startEpochDay = Long.MAX_VALUE).toScheduleWithSteps())
        assertNull(BackupSchedule(title = "bad", startEpochDay = 20, endEpochDay = 19).toScheduleWithSteps())
        assertNull(BackupSchedule(title = "bad", startEpochDay = LocalDate.of(2101, 1, 1).toEpochDay()).toScheduleWithSteps())
    }

    @Test fun reminderLeadMustBeWithinEditorSupportedRange() {
        val date = LocalDate.of(2026, 10, 20).toEpochDay()
        assertNull(BackupSchedule(title = "bad", startEpochDay = date, remindBeforeMinutes = -1).toScheduleWithSteps())
        assertNull(BackupSchedule(title = "bad", startEpochDay = date, remindBeforeMinutes = 43_201).toScheduleWithSteps())
        assertEquals(43_200L, BackupSchedule(title = "valid", startEpochDay = date, remindBeforeMinutes = 43_200).toScheduleWithSteps()!!.schedule.remindBeforeMinutes)
    }

    @Test fun documentWithInvalidScheduleIsRejectedAsAWholeBeforeRestore() {
        val document = BackupDocument(
            exportedAt = 0,
            schedules = listOf(
                BackupSchedule(title = "valid", startEpochDay = 1),
                BackupSchedule(title = "invalid", startEpochDay = 1, remindBeforeMinutes = 43_201),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) { document.toBackupData() }
    }
}
