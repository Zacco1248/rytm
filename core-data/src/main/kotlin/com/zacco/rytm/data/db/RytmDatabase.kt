package com.zacco.rytm.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ActivityEntity::class,
        DailyMetricsEntity::class,
        NutritionDayEntity::class,
        WeighInEntity::class,
        CheckInEntity::class,
        HabitLogEntity::class,
        CancellationEntity::class,
        DayNoteEntity::class,
        WorkoutSetEntity::class,
        LlmUsageEntity::class,
        SyncStateEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class RytmDatabase : RoomDatabase() {
    abstract fun activities(): ActivityDao
    abstract fun dailyMetrics(): DailyMetricsDao
    abstract fun nutrition(): NutritionDao
    abstract fun weighIns(): WeighInDao
    abstract fun checkIns(): CheckInDao
    abstract fun habitLogs(): HabitLogDao
    abstract fun cancellations(): CancellationDao
    abstract fun dayNotes(): DayNoteDao
    abstract fun workoutSets(): WorkoutSetDao
    abstract fun llmUsage(): LlmUsageDao
    abstract fun syncState(): SyncStateDao

    companion object {
        fun create(context: Context): RytmDatabase =
            Room.databaseBuilder(context.applicationContext, RytmDatabase::class.java, "rytm.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()

        /** Ręczne wpisy (check-in, żywienie, ważenia) muszą przetrwać aktualizację, więc migracja zamiast czyszczenia bazy. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `daily_metrics` ADD COLUMN `dayRestingHr` INTEGER")
                db.execSQL("ALTER TABLE `weigh_in` ADD COLUMN `composition` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `day_note` (`date` TEXT NOT NULL, `dayOff` INTEGER NOT NULL, " +
                        "`alcoholDrinks` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`date`))",
                )
                // Nowe tętno z dnia liczy się przy imporcie, więc wymuszamy ponowny pełny import (ręczne wpisy zostają).
                db.execSQL("DELETE FROM `sync_state` WHERE `key` = 'hc_changes_token'")
            }
        }
    
        /** Dziennik serii z treningów (faza 3). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workout_set` (`workoutId` TEXT NOT NULL, `exercise` TEXT NOT NULL, " +
                        "`setIndex` INTEGER NOT NULL, `reps` INTEGER NOT NULL, `weightKg` REAL, `rir` INTEGER, " +
                        "`date` TEXT NOT NULL, PRIMARY KEY(`workoutId`, `exercise`, `setIndex`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_set_date` ON `workout_set` (`date`)")
            }
        }

        /** Sen wpisany ręcznie. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `daily_metrics` ADD COLUMN `sleepManual` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
