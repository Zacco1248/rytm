package com.zacco.rytm.ai.evening

import kotlinx.serialization.Serializable

/**
 * Zagregowane dane dnia dla wieczornej analizy (sekcja 5). Bez surowych próbek tętna.
 * Plan na jutro i pora snu pochodzą z silnika reguł: LLM je tłumaczy, nie zmienia.
 */
@Serializable
data class EveningInput(
    val date: String,
    val weekday: String,
    val plannedToday: String?,
    val activities: List<ActivityLine>,
    val loadToday: Int,
    val acwr: Double?,
    val readinessToday: Int?,
    val readinessFlags: List<String>,
    val sleepLastNightMin: Int?,
    val steps: Int?,
    val kcal: Int?,
    val kcalTarget: Int,
    val proteinG: Int?,
    val proteinTarget: Int,
    val energy: Int?,
    val mood: Int?,
    val soreAreas: List<String>,
    val habitsDone: List<String>,
    val habitsMissed: List<String>,
    val reflection: String?,
    val tomorrowPlan: String,
    val tomorrowIntensity: String,
    val tomorrowWarnings: List<String>,
    val suggestedBedtime: String,
    val calibrating: Boolean,
)

@Serializable
data class ActivityLine(
    val type: String,
    val minutes: Int,
    val rpe: Int?,
    val load: Int?,
    /** Opis „innej aktywności” wpisany przez użytkownika, np. „remont”. */
    val note: String? = null,
)
