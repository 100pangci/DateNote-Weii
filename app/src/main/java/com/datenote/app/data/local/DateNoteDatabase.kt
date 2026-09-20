package com.datenote.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ScheduleEntity::class,
        ScheduleStepEntity::class,
        ScheduleTypeEntity::class,
        ScheduleTypeStepEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(RoomConverters::class)
abstract class DateNoteDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
    abstract fun scheduleTypeDao(): ScheduleTypeDao

    companion object {
        fun create(context: Context): DateNoteDatabase = Room.databaseBuilder(
            context,
            DateNoteDatabase::class.java,
            "date_note.db",
        ).addMigrations(DatabaseMigrations.MIGRATION_2_3).build()
    }
}
