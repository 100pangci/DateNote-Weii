package com.datenote.app.data.local

import androidx.room.TypeConverter
import com.datenote.app.domain.model.ScheduleStatus

class RoomConverters {
    @TypeConverter
    fun statusToString(value: ScheduleStatus): String = value.name

    @TypeConverter
    fun stringToStatus(value: String): ScheduleStatus =
        runCatching { ScheduleStatus.valueOf(value) }.getOrDefault(ScheduleStatus.TODO)
}
