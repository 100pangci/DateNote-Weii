package com.datenote.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.datenote.app.data.local.DateNoteDatabase
import com.datenote.app.data.local.ScheduleEntity
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
        val id = dao.insert(ScheduleEntity(title = "联系小优", note = "确认时间", scheduledEpochDay = LocalDate.now().toEpochDay()))
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
            ScheduleEntity(title = "今天", scheduledEpochDay = today),
            ScheduleEntity(title = "明天", scheduledEpochDay = today + 1),
        ))
        assertEquals(2, dao.observeForMonth(today, today + 31).first().size)
        val ids = dao.replaceAll(listOf(ScheduleEntity(title = "替换后", scheduledEpochDay = today + 2)))
        assertEquals(1, ids.size)
        assertTrue(dao.observeAll().first().single().title == "替换后")
    }
}
