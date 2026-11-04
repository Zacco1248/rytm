package com.zacco.rytm.ai.weekly

import kotlinx.serialization.Serializable

/** Jeden dzień tygodnia w zagregowanej postaci (bez surowych próbek). */
@Serializable
data class WeekDayLine(
    val date: String,
    val weekday: String,
    val planned: String,
    /** Np. „STRENGTH 45 min RPE 7”, „OTHER (remont) 120 min”. */
    val activities: List<String>,
    val load: Int,
    val readiness: Int?,
    val sleepMin: Int?,
    val restingHr: Int?,
    val hrv: Int?,
    val steps: Int?,
    val kcal: Int?,
    val proteinG: Int?,
    val energy: Int?,
    val mood: Int?,
    val sore: List<String>,
    val habitsDone: Int,
    val habitsTotal: Int,
    val alcohol: Int,
    val dayOff: Boolean,
    val hadMatch: Boolean,
)

/** Dane tygodnia dla raportu niedzielnego: dni, sumy, porównanie z poprzednim tygodniem i sylwetka. */
@Serializable
data class WeeklyInput(
    val weekStart: String,
    val weekEnd: String,
    val goal: String,
    val goalNote: String?,
    val days: List<WeekDayLine>,
    val loadWeek: Int,
    val loadPrevWeek: Int,
    val acwr: Double?,
    val strengthSessions: Int,
    val strengthRequired: Int,
    val cardioSessions: Int,
    val avgSleepMin: Int?,
    val avgSleepPrevWeekMin: Int?,
    val avgSteps: Int?,
    val stepsTarget: Int,
    val kcalTarget: Int,
    val proteinTarget: Int,
    val avgKcal: Int?,
    val avgProteinG: Int?,
    val proteinDaysBelowTarget: Int,
    val nutritionDaysLogged: Int,
    val weightAvg7: Double?,
    val weightChangeKg: Double?,
    val strengthTrend: String,
    /** Np. „Przysiad goblet: 3 × 15 na 10 kg (↑)”. */
    val strengthLatest: List<String>,
    /** Wykonanie nawyków w tygodniu, np. „Bez ekranu po 23:30: 4/7”. */
    val habits: List<String>,
    /** Wieczorne refleksje użytkownika z tygodnia. */
    val reflections: List<String>,
    /** Sugestie z silnika żywienia (PROTEIN_LOW, INCREASE_CALORIES…). */
    val nutritionFlags: List<String>,
    val nextWeekPlan: List<String>,
    /** Lżejszy tydzień w planie na kolejny tydzień (powód), jeśli wypada. */
    val nextWeekDeload: String? = null,
    /** Cele użytkownika z postępem, np. „10 × Podciąganie: 60%, prognoza 12 stycznia”. */
    val goals: List<String> = emptyList(),
)
