package com.zacco.rytm.domain.nutrition

import com.zacco.rytm.domain.model.BodyMetrics
import com.zacco.rytm.domain.model.NutritionDay
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** Wynik przeglądu celu kcal co 2 tygodnie. [change] = 0: bez zmiany (z powodem w [reason]). */
data class KcalReview(
    val change: Int,
    /** Łączna korekta po zmianie (względem celu z wzoru albo z Fitatu). */
    val newAdjust: Int,
    val kgPerWeek: Double,
    val targetKgPerWeek: Double,
    val reason: String,
)

/**
 * Pętla jedzenie–waga: co 2 tygodnie porównuje tempo zmiany wagi (średnie 7-dniowe) z celem i koryguje cel kcal.
 * 1 kg tkanki ≈ 7700 kcal, więc 0,1 kg/tydz. różnicy to ok. 110 kcal dziennie; zmieniamy o połowę tego,
 * najwyżej o 150 kcal naraz i 500 kcal łącznie. Gdy jedzenie mocno odbiega od celu, najpierw trzeba się go trzymać.
 */
object KcalAdjuster {
    const val PERIOD_DAYS = 14L
    private const val KCAL_PER_KG_WEEK_DAILY = 7700.0 / 7
    private const val DAMPING = 0.5
    private const val STEP_MAX = 150
    private const val TOTAL_MAX = 500
    private const val TOLERANCE_KG = 0.1
    private const val MIN_WEIGH_INS = 6
    private const val MIN_FOOD_DAYS = 8
    private const val ADHERENCE = 0.15

    fun isDue(today: LocalDate, lastReview: LocalDate?): Boolean =
        lastReview != null && ChronoUnit.DAYS.between(lastReview, today) >= PERIOD_DAYS

    /** Null: za mało danych (brak przeglądu). */
    fun review(
        today: LocalDate,
        targetKcal: Int,
        currentAdjust: Int,
        targetKgPerWeek: Double,
        weighIns: List<BodyMetrics>,
        nutrition: List<NutritionDay>,
    ): KcalReview? {
        val from = today.minusDays(PERIOD_DAYS - 1)
        val recent = weighIns.filter { it.weightKg != null && it.date in from..today }
        if (recent.size < MIN_WEIGH_INS) return null
        val now = NutritionAnalyzer.weightAvg7(weighIns, today) ?: return null
        val before = NutritionAnalyzer.weightAvg7(weighIns, today.minusDays(PERIOD_DAYS)) ?: return null
        val kgPerWeek = (now - before) / (PERIOD_DAYS / 7.0)
        val food = nutrition.filter { it.date in from..today.minusDays(1) }
        if (food.size < MIN_FOOD_DAYS) return null
        val eaten = food.map { it.kcal }.average()
        fun none(reason: String) = KcalReview(0, currentAdjust, kgPerWeek, targetKgPerWeek, reason)

        if (abs(eaten - targetKcal) > targetKcal * ADHERENCE) {
            return none("Średnio jesz ${eaten.roundToInt()} kcal przy celu $targetKcal, więc najpierw trzymaj się celu; korekta za 2 tygodnie.")
        }
        val deviation = kgPerWeek - targetKgPerWeek
        if (abs(deviation) < TOLERANCE_KG) return none("Tempo ${fmt(kgPerWeek)} kg/tydz. zgadza się z celem ${fmt(targetKgPerWeek)}: cel kcal bez zmian.")
        val raw = -deviation * KCAL_PER_KG_WEEK_DAILY * DAMPING
        val step = (raw / 10).roundToInt() * 10
        val change = step.coerceIn(-STEP_MAX, STEP_MAX)
        val newAdjust = (currentAdjust + change).coerceIn(-TOTAL_MAX, TOTAL_MAX)
        val real = newAdjust - currentAdjust
        if (real == 0) return none("Korekta doszła do limitu ±$TOTAL_MAX kcal; sprawdź cel w profilu.")
        val dir = if (real > 0) "w górę" else "w dół"
        return KcalReview(
            real, newAdjust, kgPerWeek, targetKgPerWeek,
            "Waga zmienia się o ${fmt(kgPerWeek)} kg/tydz., a cel to ${fmt(targetKgPerWeek)}: cel kcal $dir o ${abs(real)}.",
        )
    }

    private fun fmt(v: Double): String {
        val s = "%+.2f".format(java.util.Locale.ROOT, v).replace('.', ',')
        return if (s == "-0,00" || s == "+0,00") "0" else s
    }
}
