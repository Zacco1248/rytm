package com.zacco.rytm.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ActivityEntity::class,
        DailyMetricsEntity::class,
        NutritionDayEntity::class,
        WeighInEntity::class,
        CheckInEntity::class,
        HabitLogEntity::class,
        CancellationEntity::class,
        LlmUsageEntity::class,
        SyncStateEntity::class,
    ],
    version = 1,
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
    abstract fun llmUsage(): LlmUsageDao
    abstract fun syncState(): SyncStateDao

    companion object {
        fun create(context: Context): RytmDatabase =
            Room.databaseBuilder(context.applicationContext, RytmDatabase::class.java, "rytm.db").build()
    }
}
