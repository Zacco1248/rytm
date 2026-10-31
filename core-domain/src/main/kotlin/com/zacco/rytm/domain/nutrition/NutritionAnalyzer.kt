package com.zacco.rytm.domain.nutrition

import com.zacco.rytm.domain.model.BodyMetrics
import com.zacco.rytm.domain.model.NutritionDay
import com.zacco.rytm.domain.profile.NutritionTargets
import java.time.LocalDate
import kotlin.math.abs

enum class StrengthTrend { UP, FLAT, DOWN, UNKNOWN }

enum class NutritionSuggestion {
    /** Waga spada za szybko, a siła spada: zwiększ kalorie. */
    INCREASE_CALORIES,
    /** Waga stoi 2–3 tygodnie, siła rośnie: typowa rekompozycja, bez zmian. */
    RECOMPOSITION_ON_TRACK,
    /** Białko poniżej celu przez większość tygodnia. */
    PROTEIN_LOW,
    /** Za mało dni z wpisami, wnioski niepewne. */
    INCOMPLETE_LOGGING,
}

data class NutritionWeekReport(
    val weekEnd: LocalDate,
    val daysLogged: Int,
    val avgKcal: Int?,
    val avgProteinG: Int?,
    val proteinDaysBelowTarget: Int,
    /** Średnia 7-dniowa wagi na koniec tygodnia; nigdy pojedynczy pomiar. */
    val weightAvg7: Double?,
    /** Zmiana średniej 7-dniowej tydzień do tygodnia (kg). */
    val weeklyWeightChangeKg: Double?,
    /** Ile kolejnych tygodni średnia wagi stoi w miejscu (±0,2 kg). */
    val weightStallWeeks: Int,
    val suggestions: List<NutritionSuggestion>,
)

/** Logika tygodniowa z sekcji 4. Rytm nie liczy kalorii sam: tylko czyta Fitatu i interpretuje. */
object NutritionAnalyzer {

    private const val STALL_KG = 0.2
    private const val PROTEIN_TOLERANCE = 0.9
    private const val MIN_LOGGED_DAYS = 4

    fun weekly(
        weekEnd: LocalDate,
        nutrition: List<NutritionDay>,
        body: List<BodyMetrics>,
        strengthTrend: StrengthTrend,
        targets: NutritionTargets = NutritionTargets(),
    ): NutritionWeekReport {
        val week = nutrition.filter { !it.date.isAfter(weekEnd) && it.date.isAfter(weekEnd.minusDays(7)) }
        val proteinBelow = week.count { it.proteinG < targets.proteinG * PROTEIN_TOLERANCE }

        val averages = (0..4).map { weightAvg7(body, weekEnd.minusWeeks(it.toLong())) }
        val change = averages[0]?.let { now -> averages[1]?.let { now - it } }
        val stallWeeks = averages.zipWithNext()
            .takeWhile { (a, b) -> a != null && b != null && abs(a - b) < STALL_KG }
            .count()

        val suggestions = buildList {
            if (week.size < MIN_LOGGED_DAYS) add(NutritionSuggestion.INCOMPLETE_LOGGING)
            if (week.isNotEmpty() && proteinBelow * 2 > week.size) add(NutritionSuggestion.PROTEIN_LOW)
            val tooFast = change != null && change < targets.weeklyWeightChangeKg * 2 && change < -0.5
            if (tooFast && strengthTrend == StrengthTrend.DOWN) add(NutritionSuggestion.INCREASE_CALORIES)
            if (stallWeeks >= 2 && strengthTrend == StrengthTrend.UP) add(NutritionSuggestion.RECOMPOSITION_ON_TRACK)
        }

        return NutritionWeekReport(
            weekEnd = weekEnd,
            daysLogged = week.size,
            avgKcal = week.takeIf { it.isNotEmpty() }?.map { it.kcal }?.average()?.toInt(),
            avgProteinG = week.takeIf { it.isNotEmpty() }?.map { it.proteinG }?.average()?.toInt(),
            proteinDaysBelowTarget = proteinBelow,
            weightAvg7 = averages[0],
            weeklyWeightChangeKg = change,
            weightStallWeeks = stallWeeks,
            suggestions = suggestions,
        )
    }

    /** Średnia krocząca 7 dni (pomiary z [date-6, date]); null, gdy w oknie nie ma pomiaru. */
    fun weightAvg7(body: List<BodyMetrics>, date: LocalDate): Double? =
        body.filter { it.weightKg != null && !it.date.isAfter(date) && it.date.isAfter(date.minusDays(7)) }
            .mapNotNull { it.weightKg }
            .takeIf { it.isNotEmpty() }
            ?.average()
}
