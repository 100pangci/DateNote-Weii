package com.datenote.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.datenote.app.domain.model.ScheduleStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules WHERE startEpochDay <= :epochDay AND endEpochDay >= :epochDay ORDER BY startEpochDay, minuteOfDay IS NULL, minuteOfDay, id")
    fun observeForDate(epochDay: Long): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE startEpochDay <= :endEpochDay AND endEpochDay >= :startEpochDay ORDER BY startEpochDay, minuteOfDay IS NULL, minuteOfDay, id")
    fun observeForMonth(startEpochDay: Long, endEpochDay: Long): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules ORDER BY startEpochDay, minuteOfDay IS NULL, minuteOfDay, id")
    fun observeAll(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE status != :completed ORDER BY endEpochDay, startEpochDay, minuteOfDay IS NULL, minuteOfDay, id")
    fun observeIncomplete(completed: ScheduleStatus = ScheduleStatus.COMPLETED): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE title LIKE '%' || :query || '%' OR note LIKE '%' || :query || '%' ORDER BY startEpochDay, id")
    fun observeSearch(query: String): Flow<List<ScheduleEntity>>

    @Transaction
    @Query("SELECT * FROM schedules WHERE startEpochDay <= :epochDay AND endEpochDay >= :epochDay ORDER BY startEpochDay, minuteOfDay IS NULL, minuteOfDay, id")
    fun observeForDateWithSteps(epochDay: Long): Flow<List<ScheduleWithSteps>>

    @Transaction
    @Query("SELECT * FROM schedules WHERE startEpochDay <= :endEpochDay AND endEpochDay >= :startEpochDay ORDER BY startEpochDay, minuteOfDay IS NULL, minuteOfDay, id")
    fun observeForMonthWithSteps(startEpochDay: Long, endEpochDay: Long): Flow<List<ScheduleWithSteps>>

    @Transaction
    @Query("SELECT * FROM schedules ORDER BY startEpochDay, minuteOfDay IS NULL, minuteOfDay, id")
    fun observeAllWithSteps(): Flow<List<ScheduleWithSteps>>

    @Query("SELECT * FROM schedules WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ScheduleEntity?

    @Transaction
    @Query("SELECT * FROM schedules WHERE id = :id LIMIT 1")
    suspend fun getWithSteps(id: Long): ScheduleWithSteps?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(schedule: ScheduleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(schedules: List<ScheduleEntity>): List<Long>

    @Update
    suspend fun update(schedule: ScheduleEntity)

    @Delete
    suspend fun delete(schedule: ScheduleEntity)

    @Query("SELECT * FROM schedule_steps WHERE scheduleId = :scheduleId ORDER BY position, id")
    suspend fun getSteps(scheduleId: Long): List<ScheduleStepEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStep(step: ScheduleStepEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSteps(steps: List<ScheduleStepEntity>): List<Long>

    @Update
    suspend fun updateStep(step: ScheduleStepEntity)

    @Query("DELETE FROM schedule_steps WHERE id = :id")
    suspend fun deleteStepById(id: Long)

    @Query("DELETE FROM schedule_steps WHERE scheduleId = :scheduleId")
    suspend fun deleteStepsForSchedule(scheduleId: Long)

    @Query("DELETE FROM schedule_steps WHERE scheduleId = :scheduleId AND id NOT IN (:keepIds)")
    suspend fun deleteStepsExcept(scheduleId: Long, keepIds: List<Long>)

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM schedules")
    suspend fun deleteAll()

    @Transaction
    suspend fun saveWithSteps(schedule: ScheduleEntity, steps: List<ScheduleStepEntity>): Long {
        val scheduleId = if (schedule.id == 0L) insert(schedule) else {
            update(schedule)
            schedule.id
        }
        val normalizedSteps = steps.mapIndexed { index, step ->
            step.copy(scheduleId = scheduleId, position = index)
        }
        val normalizedStatus = com.datenote.app.domain.model.statusAfterStepChange(schedule.status, normalizedSteps)
        val normalizedSchedule = if (normalizedSteps.isEmpty()) schedule.copy(
            id = scheduleId,
            completedAt = if (schedule.status == ScheduleStatus.COMPLETED) schedule.completedAt ?: System.currentTimeMillis() else null,
        ) else schedule.copy(
            id = scheduleId,
            status = normalizedStatus,
            completedAt = if (normalizedStatus == ScheduleStatus.COMPLETED) schedule.completedAt ?: System.currentTimeMillis() else null,
        )
        if (normalizedSchedule != schedule) update(normalizedSchedule)
        val existingIds = normalizedSteps.mapNotNull { it.id.takeIf { id -> id != 0L } }
        if (existingIds.isEmpty()) deleteStepsForSchedule(scheduleId)
        else deleteStepsExcept(scheduleId, existingIds)
        normalizedSteps.forEach { step ->
            if (step.id == 0L) insertStep(step) else updateStep(step)
        }
        return scheduleId
    }

    @Transaction
    suspend fun setStepCompleted(scheduleId: Long, stepId: Long, completed: Boolean): ScheduleEntity? {
        val current = getWithSteps(scheduleId) ?: return null
        val now = System.currentTimeMillis()
        val target = current.steps.firstOrNull { it.id == stepId } ?: return current.schedule
        updateStep(target.copy(isCompleted = completed, completedAt = if (completed) now else null, updatedAt = now))
        val updatedSteps = getSteps(scheduleId)
        val status = com.datenote.app.domain.model.statusAfterStepChange(current.schedule.status, updatedSteps)
        val updatedSchedule = current.schedule.copy(
            status = status,
            completedAt = if (status == ScheduleStatus.COMPLETED) now else null,
            updatedAt = now,
        )
        update(updatedSchedule)
        return updatedSchedule
    }

    @Transaction
    suspend fun setStatus(scheduleId: Long, status: ScheduleStatus, completeSteps: Boolean): ScheduleEntity? {
        val current = getWithSteps(scheduleId) ?: return null
        val now = System.currentTimeMillis()
        if (completeSteps) {
            current.steps.filterNot { it.isCompleted }.forEach {
                updateStep(it.copy(isCompleted = true, completedAt = now, updatedAt = now))
            }
        }
        val actualStatus = if (completeSteps) ScheduleStatus.COMPLETED else status
        return current.schedule.copy(
            status = actualStatus,
            completedAt = if (actualStatus == ScheduleStatus.COMPLETED) now else null,
            updatedAt = now,
        ).also { update(it) }
    }

    @Transaction
    suspend fun replaceAll(schedules: List<ScheduleEntity>): List<Long> {
        deleteAll()
        return insertAll(schedules)
    }

    @Transaction
    suspend fun replaceAllWithSteps(schedules: List<ScheduleWithSteps>): List<Long> {
        deleteAll()
        return schedules.map { saveWithSteps(it.schedule.copy(id = 0L), it.orderedSteps.map { step -> step.copy(id = 0L, scheduleId = 0L) }) }
    }

    @Query("SELECT COUNT(*) FROM schedules")
    suspend fun count(): Int
}
