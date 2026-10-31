package com.zacco.rytm.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
abstract class ActivityDao {
    @Query("SELECT * FROM activity WHERE date BETWEEN :from AND :to ORDER BY start")
    abstract fun observe(from: LocalDate, to: LocalDate): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activity WHERE id = :id")
    abstract suspend fun get(id: String): ActivityEntity?

    @Upsert
    abstract suspend fun upsert(activity: ActivityEntity)

    @Query("DELETE FROM activity WHERE id = :id")
    abstract suspend fun delete(id: String)

    @Query("SELECT id FROM activity WHERE date = :date AND source = 'HEALTH_CONNECT'")
    abstract suspend fun healthConnectIdsOn(date: LocalDate): List<String>

    @Query("UPDATE activity SET rpe = :rpe WHERE id = :id")
    abstract suspend fun setRpe(id: String, rpe: Int?)

    /** Dane z Health Connect nadpisują pomiary, ale nie ręczne pola użytkownika. */
    @Transaction
    open suspend fun upsertFromHealthConnect(fresh: ActivityEntity) {
        val old = get(fresh.id)
        upsert(
            if (old == null) fresh else fresh.copy(
                rpe = old.rpe ?: fresh.rpe,
                notes = old.notes ?: fresh.notes,
                loadedAreas = old.loadedAreas.ifEmpty { fresh.loadedAreas },
            ),
        )
    }
}

@Dao
interface DailyMetricsDao {
    @Query("SELECT * FROM daily_metrics WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observe(from: LocalDate, to: LocalDate): Flow<List<DailyMetricsEntity>>

    @Upsert
    suspend fun upsert(metrics: DailyMetricsEntity)
}

@Dao
interface NutritionDao {
    @Query("SELECT * FROM nutrition_day WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observe(from: LocalDate, to: LocalDate): Flow<List<NutritionDayEntity>>

    @Query("SELECT * FROM nutrition_day WHERE date = :date")
    suspend fun get(date: LocalDate): NutritionDayEntity?

    @Upsert
    suspend fun upsert(day: NutritionDayEntity)

    @Query("DELETE FROM nutrition_day WHERE date = :date")
    suspend fun delete(date: LocalDate)
}

@Dao
interface WeighInDao {
    @Query("SELECT * FROM weigh_in WHERE date BETWEEN :from AND :to ORDER BY time")
    fun observe(from: LocalDate, to: LocalDate): Flow<List<WeighInEntity>>

    @Upsert
    suspend fun upsert(items: List<WeighInEntity>)

    @Query("DELETE FROM weigh_in WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface CheckInDao {
    @Query("SELECT * FROM check_in WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observe(from: LocalDate, to: LocalDate): Flow<List<CheckInEntity>>

    @Upsert
    suspend fun upsert(checkIn: CheckInEntity)
}

@Dao
interface HabitLogDao {
    @Query("SELECT * FROM habit_log WHERE date BETWEEN :from AND :to")
    fun observe(from: LocalDate, to: LocalDate): Flow<List<HabitLogEntity>>

    @Upsert
    suspend fun upsert(log: HabitLogEntity)

    @Query("DELETE FROM habit_log WHERE habitId = :habitId AND date = :date")
    suspend fun delete(habitId: String, date: LocalDate)
}

@Dao
interface CancellationDao {
    @Query("SELECT * FROM cancellation WHERE date BETWEEN :from AND :to")
    fun observe(from: LocalDate, to: LocalDate): Flow<List<CancellationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(c: CancellationEntity)

    @Query("DELETE FROM cancellation WHERE date = :date")
    suspend fun delete(date: LocalDate)
}

@Dao
interface LlmUsageDao {
    @Query("SELECT COALESCE(SUM(costUsd), 0) FROM llm_usage WHERE month = :month")
    suspend fun spentUsd(month: String): Double

    @Insert
    suspend fun insert(usage: LlmUsageEntity)
}

@Dao
interface SyncStateDao {
    @Query("SELECT value FROM sync_state WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM sync_state WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Upsert
    suspend fun put(state: SyncStateEntity)

    @Query("DELETE FROM sync_state WHERE `key` = :key")
    suspend fun delete(key: String)
}
