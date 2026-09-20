package com.datenote.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "schedule_type_steps",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["typeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("typeId")],
)
data class ScheduleTypeStepEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val typeId: Long,
    val title: String,
    val position: Int,
)
