package com.datenote.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.datenote.app.domain.model.ScheduleStatus
import com.datenote.app.domain.model.ScheduleDateLike

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val note: String = "",
    override val scheduledEpochDay: Long,
    val minuteOfDay: Int? = null,
    val category: String? = null,
    override val status: ScheduleStatus = ScheduleStatus.TODO,
    val colorArgb: Long? = null,
    val remindBeforeMinutes: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
) : ScheduleDateLike
