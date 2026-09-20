package com.datenote.app.data.repository

import com.datenote.app.data.local.ScheduleDao
import com.datenote.app.data.local.ScheduleEntity
import com.datenote.app.data.local.ScheduleStepEntity
import com.datenote.app.data.local.ScheduleWithSteps
import com.datenote.app.domain.model.ScheduleStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScheduleRepository(private val dao: ScheduleDao) {
    fun observeForDate(epochDay: Long): Flow<List<ScheduleEntity>> = dao.observeForDate(epochDay)

    fun observeForMonth(startEpochDay: Long, endEpochDay: Long): Flow<List<ScheduleEntity>> =
        dao.observeForMonth(startEpochDay, endEpochDay)

    fun observeForDateWithSteps(epochDay: Long): Flow<List<ScheduleWithSteps>> = dao.observeForDateWithSteps(epochDay)

    fun observeForMonthWithSteps(startEpochDay: Long, endEpochDay: Long): Flow<List<ScheduleWithSteps>> =
        dao.observeForMonthWithSteps(startEpochDay, endEpochDay)

    fun observeAll(): Flow<List<ScheduleEntity>> = dao.observeAll()

    fun observeAllWithSteps(): Flow<List<ScheduleWithSteps>> = dao.observeAllWithSteps()

    fun observeIncomplete(): Flow<List<ScheduleEntity>> = dao.observeIncomplete()

    fun observeSearch(query: String): Flow<List<ScheduleEntity>> = dao.observeSearch(query)

    suspend fun getById(id: Long): ScheduleEntity? = withContext(Dispatchers.IO) { dao.getById(id) }

    suspend fun getWithSteps(id: Long): ScheduleWithSteps? = withContext(Dispatchers.IO) { dao.getWithSteps(id) }

    suspend fun insert(schedule: ScheduleEntity): Long = withContext(Dispatchers.IO) { dao.insert(schedule) }

    suspend fun saveWithSteps(schedule: ScheduleEntity, steps: List<ScheduleStepEntity>): Long =
        withContext(Dispatchers.IO) { dao.saveWithSteps(schedule, steps) }

    suspend fun insertAll(schedules: List<ScheduleEntity>): List<Long> =
        withContext(Dispatchers.IO) { dao.insertAll(schedules) }

    suspend fun update(schedule: ScheduleEntity) = withContext(Dispatchers.IO) { dao.update(schedule) }

    suspend fun delete(schedule: ScheduleEntity) = withContext(Dispatchers.IO) { dao.delete(schedule) }

    suspend fun deleteById(id: Long) = withContext(Dispatchers.IO) { dao.deleteById(id) }

    suspend fun deleteAll() = withContext(Dispatchers.IO) { dao.deleteAll() }

    suspend fun replaceAll(schedules: List<ScheduleEntity>) = withContext(Dispatchers.IO) { dao.replaceAll(schedules) }

    suspend fun replaceAllWithSteps(schedules: List<ScheduleWithSteps>) =
        withContext(Dispatchers.IO) { dao.replaceAllWithSteps(schedules) }

    suspend fun setStepCompleted(scheduleId: Long, stepId: Long, completed: Boolean): ScheduleEntity? =
        withContext(Dispatchers.IO) { dao.setStepCompleted(scheduleId, stepId, completed) }

    suspend fun setStatus(scheduleId: Long, status: ScheduleStatus, completeSteps: Boolean = false): ScheduleEntity? =
        withContext(Dispatchers.IO) { dao.setStatus(scheduleId, status, completeSteps) }

    suspend fun count(): Int = withContext(Dispatchers.IO) { dao.count() }
}
