package com.datenote.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `schedule_types` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `position` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_schedule_types_name` ON `schedule_types` (`name`)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `schedule_type_steps` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `typeId` INTEGER NOT NULL,
                    `title` TEXT NOT NULL,
                    `position` INTEGER NOT NULL,
                    FOREIGN KEY(`typeId`) REFERENCES `schedule_types`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_schedule_type_steps_typeId` ON `schedule_type_steps` (`typeId`)            ",
            )
        }
    }
}
