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
    @Query("SELECT * FROM schedules WHERE scheduledEpochDay = :epochDay ORDER BY minuteOfDay IS NULL, minuteOfDay, id")
    fun observeForDate(epochDay: Long): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE scheduledEpochDay BETWEEN :startEpochDay AND :endEpochDay ORDER BY scheduledEpochDay, minuteOfDay IS NULL, minuteOfDay, id")
    fun observeForMonth(startEpochDay: Long, endEpochDay: Long): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules ORDER BY scheduledEpochDay, minuteOfDay IS NULL, minuteOfDay, id")
    fun observeAll(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE status != :completed ORDER BY scheduledEpochDay, minuteOfDay IS NULL, minuteOfDay, id")
    fun observeIncomplete(completed: ScheduleStatus = ScheduleStatus.COMPLETED): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE title LIKE '%' || :query || '%' OR note LIKE '%' || :query || '%' ORDER BY scheduledEpochDay, id")
    fun observeSearch(query: String): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(schedule: ScheduleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(schedules: List<ScheduleEntity>): List<Long>

    @Update
    suspend fun update(schedule: ScheduleEntity)

    @Delete
    suspend fun delete(schedule: ScheduleEntity)

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM schedules")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(schedules: List<ScheduleEntity>): List<Long> {
        deleteAll()
        return insertAll(schedules)
    }

    @Query("SELECT COUNT(*) FROM schedules")
    suspend fun count(): Int
}
