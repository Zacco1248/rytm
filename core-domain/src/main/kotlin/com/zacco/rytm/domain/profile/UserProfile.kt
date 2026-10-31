package com.zacco.rytm.domain.profile

import java.time.LocalTime

/** Ustawienia użytkownika. Domyślne wartości z odpowiedzi w sekcji 13 (2026-10-31). */
data class UserProfile(
    val weightKg: Double = 80.0,
    val heightCm: Int = 187,
    val nutrition: NutritionTargets = NutritionTargets(),
    val sleep: SleepTarget = SleepTarget(),
)

/** Cele z Fitatu pod rekompozycję. */
data class NutritionTargets(
    val kcal: Int = 2300,
    val proteinG: Int = 161,
    val fatG: Int = 72,
    val carbsG: Int = 253,
    /** Docelowe tempo zmiany wagi (kg/tydzień); lekki deficyt przy rekompozycji. */
    val weeklyWeightChangeKg: Double = -0.25,
) {
    fun proteinPerKg(weightKg: Double): Double = proteinG / weightKg
}

data class SleepTarget(
    val onsetFrom: LocalTime = LocalTime.MIDNIGHT,
    val onsetTo: LocalTime = LocalTime.of(1, 0),
    val minDurationMin: Int = 7 * 60,
    val maxDurationMin: Int = 8 * 60,
)
