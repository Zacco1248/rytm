package com.zacco.rytm.domain.load

import com.zacco.rytm.domain.model.Activity
import com.zacco.rytm.domain.time.RytmTime
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Obciążenie treningowe (sekcja 3.3).
 * Ostre = suma sRPE z 7 dni, przewlekłe = średnia tygodniowa z 28 dni, ACWR = ostre / przewlekłe.
 */
object LoadCalculator {

    /** Dzienne sumy obciążenia. Aktywności bez RPE dostają szacunek z tętna, jeśli podano [hrProfile]. */
    fun dailyLoads(activities: List<Activity>, hrProfile: HrProfile? = null): Map<LocalDate, Int> =
        activities.groupBy { it.start.atZone(RytmTime.zone).toLocalDate() }
            .mapValues { (_, list) -> list.sumOf { effectiveLoad(it, hrProfile) ?: 0 } }

    fun effectiveLoad(activity: Activity, hrProfile: HrProfile? = null): Int? =
        activity.load ?: hrProfile?.let { profile ->
            activity.avgHr?.let { RpeEstimator.fromHeartRate(it, profile) * activity.durationMin }
        }

    fun summary(dailyLoads: Map<LocalDate, Int>, today: LocalDate): LoadSummary {
        fun sum(days: Long) = (0 until days).sumOf { dailyLoads[today.minusDays(it)] ?: 0 }
        val acute = sum(7)
        val chronicWeekly = sum(28) / 4.0
        val historyDays = dailyLoads.keys.filter { !it.isAfter(today) }.minOrNull()
            ?.let { java.time.temporal.ChronoUnit.DAYS.between(it, today).toInt() + 1 } ?: 0
        val acwr = if (chronicWeekly > 0) acute / chronicWeekly else null
        return LoadSummary(
            date = today,
            yesterday = dailyLoads[today.minusDays(1)] ?: 0,
            today = dailyLoads[today] ?: 0,
            acute7d = acute,
            chronicWeeklyAvg = chronicWeekly.roundToInt(),
            acwr = acwr,
            zone = AcwrZone.of(acwr),
            // ACWR ma sens dopiero przy pełnym oknie przewlekłym.
            reliable = historyDays >= 28,
        )
    }
}

data class LoadSummary(
    val date: LocalDate,
    val yesterday: Int,
    val today: Int,
    val acute7d: Int,
    val chronicWeeklyAvg: Int,
    val acwr: Double?,
    val zone: AcwrZone,
    val reliable: Boolean,
) {
    /** Średnie dzienne obciążenie przewlekłe, punkt odniesienia dla „ciężkiego dnia”. */
    val chronicDailyAvg: Double get() = chronicWeeklyAvg / 7.0
}

enum class AcwrZone {
    UNKNOWN, LOW, SAFE, ELEVATED, HIGH;

    companion object {
        fun of(acwr: Double?): AcwrZone = when {
            acwr == null -> UNKNOWN
            acwr < 0.8 -> LOW
            acwr <= 1.3 -> SAFE
            acwr <= 1.5 -> ELEVATED
            else -> HIGH
        }
    }
}

/** Tętno maksymalne i spoczynkowe do szacunków z tętna. */
data class HrProfile(val maxHr: Int, val restingHr: Int) {
    init {
        require(maxHr > restingHr) { "maxHr musi być większe od restingHr" }
    }
}

object RpeEstimator {
    /**
     * Fallback, gdy brak RPE: % rezerwy tętna (Karvonen) przeliczony liniowo na skalę 1–10.
     * 40% HRR ≈ RPE 3 (lekko), 90% HRR ≈ RPE 9–10.
     */
    fun fromHeartRate(avgHr: Int, profile: HrProfile): Int {
        val hrr = (avgHr - profile.restingHr).toDouble() / (profile.maxHr - profile.restingHr)
        return (hrr * 11 - 1.4).roundToInt().coerceIn(1, 10)
    }
}
