package com.datenote.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [ScheduleEntity::class, ScheduleStepEntity::class], version = 2, exportSchema = false)
@TypeConverters(RoomConverters::class)
abstract class DateNoteDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        fun create(context: Context): DateNoteDatabase = Room.databaseBuilder(
            context,
            DateNoteDatabase::class.java,
            "date_note.db",
        ).build()
    }
}
