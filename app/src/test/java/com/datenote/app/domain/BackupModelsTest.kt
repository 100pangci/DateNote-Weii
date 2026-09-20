package com.datenote.app.domain

import com.datenote.app.data.backup.BackupSchedule
import com.datenote.app.data.backup.toScheduleWithSteps
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupModelsTest {
    @Test fun oldSingleDateBackupImportsAsSingleDayWithoutSteps() {
        val date = LocalDate.of(2026, 10, 20).toEpochDay()
        val restored = BackupSchedule(title = "发美肌", scheduledEpochDay = date).toScheduleWithSteps()!!
        assertEquals(date, restored.schedule.startEpochDay)
        assertEquals(date, restored.schedule.endEpochDay)
        assertTrue(restored.steps.isEmpty())
    }
}
