package com.datenote.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleTypeDao {
    @Transaction
    @Query("SELECT * FROM schedule_types ORDER BY position, id")
    fun observeAll(): Flow<List<ScheduleTypeWithSteps>>

    @Transaction
    @Query("SELECT * FROM schedule_types WHERE id = :id LIMIT 1")
    suspend fun getWithSteps(id: Long): ScheduleTypeWithSteps?

    @Transaction
    @Query("SELECT * FROM schedule_types WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): ScheduleTypeWithSteps?

    @Query("SELECT COALESCE(MAX(position), -1) FROM schedule_types")
    suspend fun maxPosition(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(type: ScheduleTypeEntity): Long

    @Update
    suspend fun update(type: ScheduleTypeEntity)

    @Delete
    suspend fun delete(type: ScheduleTypeEntity)

    @Transaction
    @Query("DELETE FROM schedule_types WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM schedule_types")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSteps(steps: List<ScheduleTypeStepEntity>)

    @Query("DELETE FROM schedule_type_steps WHERE typeId = :typeId")
    suspend fun deleteSteps(typeId: Long)

    @Transaction
    suspend fun saveWithSteps(type: ScheduleTypeEntity, steps: List<ScheduleTypeStepEntity>): Long {
        val typeId = if (type.id == 0L) insert(type) else {
            update(type)
            type.id
        }
        deleteSteps(typeId)
        insertSteps(steps.mapIndexed { index, step ->
            step.copy(id = 0L, typeId = typeId, position = index)
        })
        return typeId
    }
}
