package com.zacco.rytm.domain.profile

import com.zacco.rytm.domain.planner.Equipment
import com.zacco.rytm.domain.planner.PlannerConfig
import java.time.LocalTime
import kotlin.math.roundToInt

/** Ustawienia silnika wyliczone z [PersonalProfile]. Wartości domyślne (z sekcji 13) służą testom. */
data class UserProfile(
    val weightKg: Double = 80.0,
    val heightCm: Int = 187,
    val nutrition: NutritionTargets = NutritionTargets(),
    val sleep: SleepTarget = SleepTarget(),
    /** Tętno maksymalne do szacowania RPE z tętna, gdy brak oceny po treningu. Bez wieku i testu: 190. */
    val maxHr: Int = 190,
    /** Jaką część kcal z treningu (z zegarka) doliczać do celu dnia. Tak samo jak w Fitatu: 0,75. */
    val trainingKcalFactor: Double = 0.75,
    val planner: PlannerConfig = PlannerConfig(),
    val equipment: Equipment = Equipment(),
    val goal: Goal = Goal.RECOMP,
    val goalNote: String = "",
    val name: String = "",
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

    /**
     * Cel dnia z doliczonym treningiem. Jak w Fitatu: dodatkowe kcal rozkładamy na makro w tych samych proporcjach,
     * więc białko, tłuszcz i węgle rosną o ten sam procent co kcal.
     */
    fun withTraining(extraKcal: Int): NutritionTargets {
        if (extraKcal <= 0 || kcal <= 0) return this
        val f = (kcal + extraKcal).toDouble() / kcal
        return copy(
            kcal = kcal + extraKcal,
            proteinG = (proteinG * f).roundToInt(),
            fatG = (fatG * f).roundToInt(),
            carbsG = (carbsG * f).roundToInt(),
        )
    }
}

data class SleepTarget(
    val onsetFrom: LocalTime = LocalTime.MIDNIGHT,
    val onsetTo: LocalTime = LocalTime.of(1, 0),
    val minDurationMin: Int = 7 * 60,
    val maxDurationMin: Int = 8 * 60,
)
