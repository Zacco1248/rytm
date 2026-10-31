package com.zacco.rytm.domain.planner

import com.zacco.rytm.domain.model.ActivityType
import java.time.DayOfWeek
import java.time.LocalDate

/** Stały tydzień użytkownika (sekcja 2) i progi reguł (sekcja 3.2). */
data class PlannerConfig(
    val fixedSports: Map<DayOfWeek, ActivityType> = mapOf(
        DayOfWeek.TUESDAY to ActivityType.VOLLEYBALL,
        DayOfWeek.WEDNESDAY to ActivityType.SQUASH,
        DayOfWeek.THURSDAY to ActivityType.VOLLEYBALL,
    ),
    val personalTrainingDay: DayOfWeek = DayOfWeek.FRIDAY,
    /** Grupowe treningi personalne do końca listopada 2026. */
    val personalTrainingUntil: LocalDate? = LocalDate.of(2026, 11, 30),
    val minStrengthSessionsPerWeek: Int = 2,
    /** Remont od tylu minut (lub od [heavyRenovationLoad] sRPE) liczy się jak ciężki trening. */
    val heavyRenovationMinutes: Int = 120,
    val heavyRenovationLoad: Int = 400,
    /** Gotowość poniżej tej wartości: zamiast treningu pełny odpoczynek. */
    val restBelowReadiness: Int = 35,
) {
    fun hasPersonalTraining(date: LocalDate): Boolean =
        date.dayOfWeek == personalTrainingDay && personalTrainingUntil?.let { !date.isAfter(it) } == true
}
