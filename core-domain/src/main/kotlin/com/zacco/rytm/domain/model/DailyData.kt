package com.zacco.rytm.domain.model

import java.time.Instant
import java.time.LocalDate

enum class BodyArea { SHOULDERS, ELBOW, BACK, THIGHS, KNEES, CALVES, ANKLES }

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
    /**
     * Zapasowe tętno spoczynkowe z dnia (najspokojniejsze 30 min poza treningami), gdy w nocy zegarek się ładuje.
     * Wyższe niż nocne, więc porównujemy je tylko z innymi wartościami dziennymi.
     */
    val dayRestingHr: Int? = null,
)

data class NutritionDay(val date: LocalDate, val kcal: Int, val proteinG: Double, val fatG: Double, val carbsG: Double)

data class BodyMetrics(
    val date: LocalDate,
    val weightKg: Double?,
    val bodyFatPct: Double?,
    /** Pełny skład ciała z wagi (Xiaomi S400), gdy jest. */
    val composition: BodyComposition? = null,
)

/** Pomiary z wagi z analizą składu ciała. Wszystko opcjonalne, bo każda waga podaje co innego. */
data class BodyComposition(
    val muscleKg: Double? = null,
    val musclePct: Double? = null,
    val skeletalMuscleKg: Double? = null,
    val waterKg: Double? = null,
    val waterPct: Double? = null,
    val fatKg: Double? = null,
    val boneKg: Double? = null,
    val proteinKg: Double? = null,
    val proteinPct: Double? = null,
    val visceralFat: Double? = null,
    val bmrKcal: Int? = null,
    val leanMassKg: Double? = null,
    val waistHipRatio: Double? = null,
    val bmi: Double? = null,
    val bodyScore: Int? = null,
    val heartRate: Int? = null,
) {
    val isEmpty: Boolean get() = this == BodyComposition()
}

/**
 * Notatka dnia: „dziś wolne” (plan przesuwa trening na inne dni, bez kary) i alkohol wypity tego dnia
 * (liczony do gotowości następnego dnia). Fitatu nie wysyła alkoholu do Health Connect, więc to wpis ręczny.
 */
data class DayNote(
    val date: LocalDate,
    val dayOff: Boolean = false,
    /** Porcje standardowe: piwo 0,5 l, lampka wina albo 50 ml wódki. */
    val alcoholDrinks: Int = 0,
)
