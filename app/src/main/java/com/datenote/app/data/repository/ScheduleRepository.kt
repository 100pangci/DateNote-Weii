package com.datenote.app.data.repository

import androidx.room.withTransaction
import com.datenote.app.data.local.DateNoteDatabase
import com.datenote.app.data.local.ScheduleDao
import com.datenote.app.data.local.ScheduleEntity
import com.datenote.app.data.local.ScheduleStepEntity
import com.datenote.app.data.local.ScheduleTypeDao
import com.datenote.app.data.local.ScheduleTypeEntity
import com.datenote.app.data.local.ScheduleTypeStepEntity
import com.datenote.app.data.local.ScheduleTypeWithSteps
import com.datenote.app.data.local.ScheduleWithSteps
import com.datenote.app.domain.model.ScheduleStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class ScheduleRepository(private val database: DateNoteDatabase) {
    private val dao: ScheduleDao get() = database.scheduleDao()
    private val typeDao: ScheduleTypeDao get() = database.scheduleTypeDao()

    fun observeForDate(epochDay: Long): Flow<List<ScheduleEntity>> = dao.observeForDate(epochDay)

    fun observeForMonth(startEpochDay: Long, endEpochDay: Long): Flow<List<ScheduleEntity>> =
        dao.observeForMonth(startEpochDay, endEpochDay)

    fun observeForDateWithSteps(epochDay: Long): Flow<List<ScheduleWithSteps>> = dao.observeForDateWithSteps(epochDay)

    fun observeForMonthWithSteps(startEpochDay: Long, endEpochDay: Long): Flow<List<ScheduleWithSteps>> =
        dao.observeForMonthWithSteps(startEpochDay, endEpochDay)

    fun observeAll(): Flow<List<ScheduleEntity>> = dao.observeAll()

    fun observeAllWithSteps(): Flow<List<ScheduleWithSteps>> = dao.observeAllWithSteps()

    fun observeScheduleTypes(): Flow<List<ScheduleTypeWithSteps>> = typeDao.observeAll()

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

    suspend fun getScheduleType(id: Long): ScheduleTypeWithSteps? =
        withContext(Dispatchers.IO) { typeDao.getWithSteps(id) }

    suspend fun getScheduleTypeByName(name: String): ScheduleTypeWithSteps? =
        withContext(Dispatchers.IO) { typeDao.getByName(name.trim()) }

    suspend fun saveScheduleType(
        id: Long,
        name: String,
        stepTitles: List<String>,
        createdAt: Long? = null,
        position: Int? = null,
    ): Long = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "type name must not be empty" }
        require(cleanName.codePointCount(0, cleanName.length) <= 20) { "type name is too long" }
        val cleanSteps = stepTitles.map(String::trim)
        require(cleanSteps.all(String::isNotEmpty)) { "type step title must not be empty" }
        val now = System.currentTimeMillis()
        val existing = if (id == 0L) null else typeDao.getWithSteps(id)
        val duplicate = typeDao.getByName(cleanName)
        require(duplicate == null || duplicate.type.id == id) { "type name already exists" }
        val type = ScheduleTypeEntity(
            id = id,
            name = cleanName,
            position = position ?: existing?.type?.position ?: typeDao.maxPosition() + 1,
            createdAt = createdAt ?: existing?.type?.createdAt ?: now,
            updatedAt = now,
        )
        typeDao.saveWithSteps(
            type,
            cleanSteps.mapIndexed { index, title ->
                ScheduleTypeStepEntity(typeId = id, title = title, position = index)
            },
        )
    }

    suspend fun deleteScheduleType(id: Long) = withContext(Dispatchers.IO) {
        typeDao.deleteById(id)
    }

    /**
     * Restores both schedules and reusable templates in one Room transaction. Imported
     * templates never overwrite a local template when appending.
     */
    suspend fun restoreAllWithSteps(
        schedules: List<ScheduleWithSteps>,
        types: List<ScheduleTypeWithSteps>,
        replace: Boolean,
    ) = withContext(Dispatchers.IO) {
        database.withTransaction {
            if (replace) {
                dao.deleteAll()
                typeDao.deleteAll()
            }
            schedules.forEach { item ->
                dao.saveWithSteps(
                    item.schedule.copy(id = 0L),
                    item.orderedSteps.map { it.copy(id = 0L, scheduleId = 0L) },
                )
            }
            val restoredTypeNames = mutableSetOf<String>()
            types.forEach { item ->
                val name = item.type.name.trim()
                if (name.isEmpty() || name.codePointCount(0, name.length) > 20) return@forEach
                if (!restoredTypeNames.add(name)) return@forEach
                if (!replace && typeDao.getByName(name) != null) return@forEach
                typeDao.saveWithSteps(
                    item.type.copy(id = 0L, name = name),
                    item.orderedSteps.map { it.copy(id = 0L, typeId = 0L, title = it.title.trim()) }
                        .filter { it.title.isNotEmpty() },
                )
            }
        }
    }
}
