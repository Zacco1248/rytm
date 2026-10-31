package com.zacco.rytm.domain.model

import java.time.Instant
import java.time.LocalDate

enum class BodyArea { SHOULDERS, ELBOW, KNEES, BACK, CALVES, ANKLES }

data class DailyCheckIn(
    val date: LocalDate,
    /** 1–5 */
    val energy: Int,
    /** 1–5 */
    val mood: Int,
    val soreAreas: Set<BodyArea> = emptySet(),
    val hrvRmssd: Double? = null,
) {
    init {
        require(energy in 1..5 && mood in 1..5) { "energy i mood w skali 1–5" }
    }
}

data class DailyMetrics(
    val date: LocalDate,
    val sleepMin: Int?,
    val sleepStart: Instant?,
    val sleepEnd: Instant?,
    val nightRestingHr: Int?,
    val steps: Int?,
    val totalKcal: Int?,
)

data class NutritionDay(val date: LocalDate, val kcal: Int, val proteinG: Double, val fatG: Double, val carbsG: Double)

data class BodyMetrics(val date: LocalDate, val weightKg: Double?, val bodyFatPct: Double?)
