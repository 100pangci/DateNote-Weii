package com.datenote.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.datenote.app.data.local.DatabaseMigrations
import com.datenote.app.data.local.DateNoteDatabase
import com.datenote.app.data.local.ScheduleTypeEntity
import com.datenote.app.data.local.ScheduleTypeStepEntity
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DateNoteMigrationTest {
    private lateinit var helper: MigrationTestHelper
    private val databaseName = "migration-test.db"

    @Before
    fun setUp() {
        helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            DateNoteDatabase::class.java,
        )
    }

    @After
    fun tearDown() {
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(databaseName)
    }

    @Test
    @Throws(IOException::class)
    fun migration2To3KeepsSchedulesAndSupportsTemplateCrud() = runBlocking {
        helper.createDatabase(databaseName, 2).apply {
            execSQL(
                """
                CREATE TABLE `schedules` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `note` TEXT NOT NULL,
                    `startEpochDay` INTEGER NOT NULL,
                    `endEpochDay` INTEGER NOT NULL,
                    `minuteOfDay` INTEGER,
                    `category` TEXT,
                    `status` TEXT NOT NULL,
                    `colorArgb` INTEGER,
                    `remindBeforeMinutes` INTEGER,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `completedAt` INTEGER
                )
                """.trimIndent(),
            )
            execSQL(
                """
                CREATE TABLE `schedule_steps` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `scheduleId` INTEGER NOT NULL,
                    `title` TEXT NOT NULL,
                    `position` INTEGER NOT NULL,
                    `isCompleted` INTEGER NOT NULL,
                    `completedAt` INTEGER,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`scheduleId`) REFERENCES `schedules`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            execSQL("CREATE INDEX `index_schedule_steps_scheduleId` ON `schedule_steps` (`scheduleId`)")
            execSQL("INSERT INTO schedules (id, title, note, startEpochDay, endEpochDay, status, createdAt, updatedAt) VALUES (1, '旧排期', '保留', 1, 1, 'TODO', 1, 1)")
            execSQL("INSERT INTO schedule_steps (id, scheduleId, title, position, isCompleted, createdAt, updatedAt) VALUES (1, 1, '旧步骤', 0, 0, 1, 1)")
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 3, true, DatabaseMigrations.MIGRATION_2_3).close()
        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DateNoteDatabase::class.java,
            databaseName,
        ).addMigrations(DatabaseMigrations.MIGRATION_2_3).build()
        try {
            val schedule = database.scheduleDao().getWithSteps(1)!!
            assertEquals("旧排期", schedule.schedule.title)
            assertEquals(listOf("旧步骤"), schedule.orderedSteps.map { it.title })

            val typeId = database.scheduleTypeDao().saveWithSteps(
                ScheduleTypeEntity(name = "手作"),
                listOf(ScheduleTypeStepEntity(typeId = 0, title = "买毛", position = 0)),
            )
            assertEquals(listOf("买毛"), database.scheduleTypeDao().getWithSteps(typeId)!!.orderedSteps.map { it.title })
            database.scheduleTypeDao().saveWithSteps(
                ScheduleTypeEntity(id = typeId, name = "手作改"),
                listOf(ScheduleTypeStepEntity(typeId = typeId, title = "包装", position = 0)),
            )
            assertEquals("手作改", database.scheduleTypeDao().getWithSteps(typeId)!!.type.name)
            database.scheduleTypeDao().deleteById(typeId)
            assertTrue(database.scheduleTypeDao().getWithSteps(typeId) == null)
        } finally {
            database.close()
        }
    }
}
