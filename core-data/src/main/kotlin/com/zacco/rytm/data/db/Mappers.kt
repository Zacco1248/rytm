package com.zacco.rytm.data.db

import com.zacco.rytm.domain.habits.HabitLog
import com.zacco.rytm.domain.model.Activity
import com.zacco.rytm.domain.model.BodyMetrics
import com.zacco.rytm.domain.model.DailyCheckIn
import com.zacco.rytm.domain.model.DailyMetrics
import com.zacco.rytm.domain.model.NutritionDay
import com.zacco.rytm.domain.model.Source
import com.zacco.rytm.domain.time.RytmTime
import java.time.Instant

fun ActivityEntity.toDomain() = Activity(
    id = id, type = type, start = start, durationMin = durationMin, rpe = rpe, avgHr = avgHr, kcal = kcal,
    source = source, notes = notes ?: title, loadedAreas = loadedAreas,
)

/** Źródło do deduplikacji: wpisy ręczne mają pierwszeństwo przed wszystkim z Health Connect. */
fun ActivityEntity.originForDedup(): String = if (source == Source.MANUAL) "com.zacco.rytm" else origin.orEmpty()

fun Activity.toEntity(origin: String?, title: String? = null) = ActivityEntity(
    id = id, type = type, start = start, date = start.atZone(RytmTime.zone).toLocalDate(), durationMin = durationMin,
    rpe = rpe, avgHr = avgHr, kcal = kcal, source = source, origin = origin, title = title, notes = notes,
    loadedAreas = loadedAreas,
)

fun DailyMetricsEntity.toDomain() = DailyMetrics(date, sleepMin, sleepStart, sleepEnd, nightRestingHr, steps, totalKcal)

fun NutritionDayEntity.toDomain() = NutritionDay(date, kcal, proteinG, fatG, carbsG)

fun WeighInEntity.toDomain() = BodyMetrics(date, weightKg, bodyFatPct)

fun CheckInEntity.toDomain() = DailyCheckIn(date, energy, mood, soreAreas, hrvRmssd)

fun DailyCheckIn.toEntity(now: Instant = Instant.now()) = CheckInEntity(date, energy, mood, soreAreas, hrvRmssd, now)

fun HabitLogEntity.toDomain() = HabitLog(habitId, date, value)
