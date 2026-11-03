package com.zacco.rytm.domain.planner

import com.zacco.rytm.domain.model.ActivityType
import java.time.DayOfWeek
import com.zacco.rytm.domain.model.StrengthFocus
import java.time.LocalDate

/** Tydzień użytkownika (z profilu) i progi reguł (sekcja 3.2). Wartości domyślne to tydzień z coach-projekt.md, sekcja 3.1. */
data class PlannerConfig(
    val fixedSports: Map<DayOfWeek, ActivityType> = mapOf(
        DayOfWeek.TUESDAY to ActivityType.VOLLEYBALL,
        DayOfWeek.WEDNESDAY to ActivityType.SQUASH,
        DayOfWeek.THURSDAY to ActivityType.VOLLEYBALL,
    ),
    /** Nazwy stałych sportów do planu (np. „Piłka nożna”); bez nazwy: nazwa typu. */
    val sportNames: Map<DayOfWeek, String> = emptyMap(),
    val personalTrainingDay: DayOfWeek? = DayOfWeek.FRIDAY,
    /** Grupowe treningi personalne do końca listopada 2026. */
    val personalTrainingUntil: LocalDate? = LocalDate.of(2026, 11, 30),
    /** Dni, w które można trenować siłowo, w kolejności preferencji. */
    val strengthDays: List<DayOfWeek> = listOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY),
    /** Ile sesji siłowych planować (trening personalny osobno). */
    val strengthPerWeek: Int = 2,
    /** Dzień odpoczynku z mobilnością i spacerem. */
    val restDay: DayOfWeek? = DayOfWeek.SUNDAY,
    val minStrengthSessionsPerWeek: Int = 2,
    /** „Inna aktywność” (np. remont) od tylu minut lub od [heavyOtherActivityLoad] sRPE liczy się jak ciężki trening. */
    val heavyOtherActivityMinutes: Int = 120,
    val heavyOtherActivityLoad: Int = 400,
    /** Gotowość poniżej tej wartości: zamiast treningu pełny odpoczynek. */
    val restBelowReadiness: Int = 35,
) {
    fun hasPersonalTraining(date: LocalDate): Boolean =
        personalTrainingDay != null && date.dayOfWeek == personalTrainingDay &&
            (personalTrainingUntil == null || !date.isAfter(personalTrainingUntil))

    /**
     * Dni siły w tygodniu z [anyDayOfWeek]: pierwsze [strengthPerWeek] dni z listy, pomijając stałe sporty
     * i dzień treningu personalnego, z naciskiem dobranym do liczby sesji.
     */
    fun strengthPlan(anyDayOfWeek: LocalDate): Map<DayOfWeek, StrengthFocus> {
        val monday = anyDayOfWeek.with(DayOfWeek.MONDAY)
        val picked = strengthDays.filter { d ->
            d !in fixedSports && !hasPersonalTraining(monday.plusDays((d.value - 1).toLong()))
        }.take(strengthPerWeek)
        val ordered = picked.sortedBy { it.value }
        return ordered.zip(focusesFor(ordered.size)).toMap()
    }

    companion object {
        /** Podział sesji: 1–2 dni całe ciało, 3 dni góra/dół/całe, 4 i więcej góra/dół na zmianę. */
        fun focusesFor(n: Int): List<StrengthFocus> = when (n) {
            0 -> emptyList()
            1, 2 -> List(n) { StrengthFocus.FULL_BODY }
            3 -> listOf(StrengthFocus.UPPER, StrengthFocus.LOWER, StrengthFocus.FULL_BODY)
            else -> List(n) { if (it % 2 == 0) StrengthFocus.UPPER else StrengthFocus.LOWER }
        }
    }
}
