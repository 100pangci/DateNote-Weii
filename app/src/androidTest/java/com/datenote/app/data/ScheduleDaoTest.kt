package com.datenote.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.datenote.app.data.local.DateNoteDatabase
import com.datenote.app.data.local.ScheduleEntity
import com.datenote.app.data.local.ScheduleStepEntity
import com.datenote.app.domain.model.ScheduleStatus
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleDaoTest {
    private lateinit var database: DateNoteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DateNoteDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() { database.close() }

    @Test
    fun insertUpdateDeleteAndSearch() = runBlocking {
        val dao = database.scheduleDao()
        val id = dao.insert(ScheduleEntity(title = "联系小优", note = "确认时间", startEpochDay = LocalDate.now().toEpochDay()))
        assertEquals("联系小优", dao.getById(id)?.title)
        assertEquals(1, dao.observeSearch("确认").first().size)
        dao.update(dao.getById(id)!!.copy(status = ScheduleStatus.COMPLETED))
        assertEquals(ScheduleStatus.COMPLETED, dao.getById(id)?.status)
        dao.deleteById(id)
        assertEquals(0, dao.count())
    }

    @Test
    fun batchInsertMonthQueryAndReplaceAreSupported() = runBlocking {
        val dao = database.scheduleDao()
        val today = LocalDate.now().toEpochDay()
        dao.insertAll(listOf(
            ScheduleEntity(title = "今天", startEpochDay = today),
            ScheduleEntity(title = "明天", startEpochDay = today + 1),
        ))
        assertEquals(2, dao.observeForMonth(today, today + 31).first().size)
        val ids = dao.replaceAll(listOf(ScheduleEntity(title = "替换后", startEpochDay = today + 2)))
        assertEquals(1, ids.size)
        assertTrue(dao.observeAll().first().single().title == "替换后")
    }

    @Test
    fun monthRangeQueryIncludesScheduleStartingInPreviousMonth() = runBlocking {
        val dao = database.scheduleDao()
        dao.insert(ScheduleEntity(
            title = "跨月制作",
            startEpochDay = LocalDate.of(2026, 9, 28).toEpochDay(),
            endEpochDay = LocalDate.of(2026, 10, 3).toEpochDay(),
        ))
        assertEquals(1, dao.observeForMonthWithSteps(
            LocalDate.of(2026, 10, 1).toEpochDay(),
            LocalDate.of(2026, 10, 31).toEpochDay(),
        ).first().size)
    }

    @Test
    fun stepsAreSavedInTransactionAndCascadeWhenScheduleIsDeleted() = runBlocking {
        val dao = database.scheduleDao()
        val schedule = ScheduleEntity(title = "小优的服装", startEpochDay = 10, endEpochDay = 17)
        val id = dao.saveWithSteps(schedule, listOf(
            ScheduleStepEntity(scheduleId = 0, title = "买材料", position = 0),
            ScheduleStepEntity(scheduleId = 0, title = "制作", position = 1),
        ))
        val loaded = dao.getWithSteps(id)!!
        assertEquals(listOf("买材料", "制作"), loaded.orderedSteps.map { it.title })
        val stepId = loaded.orderedSteps.first().id
        assertEquals(ScheduleStatus.IN_PROGRESS, dao.setStepCompleted(id, stepId, true)?.status)
        dao.deleteById(id)
        assertTrue(dao.getSteps(id).isEmpty())
    }

    @Test
    fun editingStepsPreservesStableIdsAndSupportsRenameDeleteAndReorder() = runBlocking {
        val dao = database.scheduleDao()
        val schedule = ScheduleEntity(title = "制作", startEpochDay = 1)
        val id = dao.saveWithSteps(schedule, listOf(
            ScheduleStepEntity(scheduleId = 0, title = "买材料", position = 0),
            ScheduleStepEntity(scheduleId = 0, title = "制作", position = 1),
        ))
        val original = dao.getSteps(id)
        val keptId = original[1].id
        dao.saveWithSteps(dao.getById(id)!!, listOf(
            original[1].copy(title = "制作完成", position = 0),
            ScheduleStepEntity(scheduleId = id, title = "打包", position = 1),
        ))
        val updated = dao.getSteps(id)
        assertEquals(listOf("制作完成", "打包"), updated.map { it.title })
        assertEquals(keptId, updated.first().id)
        assertTrue(original.first().id !in updated.map { it.id })
    }
}
