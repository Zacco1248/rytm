package com.zacco.rytm.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.model.BodyArea
import com.zacco.rytm.domain.model.LoadedArea
import com.zacco.rytm.domain.model.Source
import java.time.Instant
import java.time.LocalDate

/**
 * Trening albo inna aktywność. Dla rekordów z Health Connect [id] = id rekordu HC, więc ponowna synchronizacja
 * nadpisuje dane z zegarka, a pola wpisane ręcznie (RPE, notatka, „co obciążyło”) zostają (ActivityDao.upsertFromHealthConnect).
 * Duplikaty z różnych źródeł zostają w bazie; odfiltrowuje je Ingest.dedupActivities przy odczycie.
 */
@Entity(tableName = "activity", indices = [Index("date")])
data class ActivityEntity(
    @PrimaryKey val id: String,
    val type: ActivityType,
    val start: Instant,
    /** Dzień w strefie Warszawa, do szybkich zapytań po zakresie. */
    val date: LocalDate,
    val durationMin: Int,
    val rpe: Int?,
    val avgHr: Int?,
    val kcal: Int?,
    val source: Source,
    val origin: String?,
    val title: String?,
    val notes: String?,
    val loadedAreas: Set<LoadedArea>,
)

/** Dzień: noc zakończona tego dnia plus kroki i kalorie z tego dnia. */
@Entity(tableName = "daily_metrics")
data class DailyMetricsEntity(
    @PrimaryKey val date: LocalDate,
    val sleepMin: Int?,
    val sleepStart: Instant?,
    val sleepEnd: Instant?,
    val nightRestingHr: Int?,
    val steps: Int?,
    val totalKcal: Int?,
    val updatedAt: Instant,
)

@Entity(tableName = "nutrition_day")
data class NutritionDayEntity(
    @PrimaryKey val date: LocalDate,
    val kcal: Int,
    val proteinG: Double,
    val fatG: Double,
    val carbsG: Double,
    val updatedAt: Instant,
)

/** Pojedynczy pomiar z wagi (waga albo tkanka tłuszczowa; HC trzyma je jako osobne rekordy). */
@Entity(tableName = "weigh_in", indices = [Index("date")])
data class WeighInEntity(
    @PrimaryKey val id: String,
    val time: Instant,
    val date: LocalDate,
    val weightKg: Double?,
    val bodyFatPct: Double?,
    val origin: String,
)

@Entity(tableName = "check_in")
data class CheckInEntity(
    @PrimaryKey val date: LocalDate,
    val energy: Int,
    val mood: Int,
    val soreAreas: Set<BodyArea>,
    val hrvRmssd: Double?,
    val createdAt: Instant,
)

@Entity(tableName = "habit_log", primaryKeys = ["habitId", "date"])
data class HabitLogEntity(
    val habitId: String,
    val date: LocalDate,
    val value: Int,
)

/** „Mecz odwołany” / „Personalny odwołany” na dany dzień. */
@Entity(tableName = "cancellation")
data class CancellationEntity(
    @PrimaryKey val date: LocalDate,
    val createdAt: Instant,
)

@Entity(tableName = "llm_usage", indices = [Index("month")])
data class LlmUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** RRRR-MM */
    val month: String,
    val model: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val costUsd: Double,
    val at: Instant,
)

/** Token Changes API i czas ostatniej synchronizacji. */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val key: String,
    val value: String,
)
