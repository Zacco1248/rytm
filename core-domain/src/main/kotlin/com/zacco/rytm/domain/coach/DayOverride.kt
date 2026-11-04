package com.zacco.rytm.domain.coach

import com.zacco.rytm.domain.model.DayKind
import com.zacco.rytm.domain.model.Intensity
import com.zacco.rytm.domain.model.PlannedDay
import com.zacco.rytm.domain.model.PlannedExercise
import com.zacco.rytm.domain.model.StrengthWorkout
import kotlinx.serialization.Serializable

/**
 * Plan dnia zmieniony w rozmowie z trenerem („mam 20 minut”, „boli bark”). Zastępuje plan z reguł tylko na ten dzień;
 * „Przywróć plan” go usuwa.
 */
@Serializable
data class DayOverride(
    /** yyyy-MM-dd */
    val date: String,
    val title: String,
    /** STRENGTH, MOBILITY albo REST. */
    val kind: String,
    /** LOW, MODERATE, HIGH */
    val intensity: String,
    val exercises: List<OverrideExercise> = emptyList(),
    /** Dlaczego tak, jednym zdaniem; trafia do uzasadnienia planu. */
    val reason: String,
    val createdAt: Long = 0,
) {
    fun applyTo(day: PlannedDay): PlannedDay {
        // Zaplanowany sport ma pierwszeństwo: trening od trenera go nie zastępuje (odpoczynek, np. przy bólu, tak).
        if (day.kind == DayKind.FIXED_SPORT && kind != "REST") {
            return day.copy(rationale = day.rationale + "Trener proponował: $title. Dziś pierwszeństwo ma zaplanowany sport.")
        }
        val k = when (kind) {
            "REST" -> DayKind.REST
            "MOBILITY" -> DayKind.MOBILITY
            else -> DayKind.STRENGTH
        }
        val workout = exercises.takeIf { it.isNotEmpty() && k != DayKind.REST }?.let { list ->
            StrengthWorkout(title, list.map { it.toPlanned() })
        }
        return day.copy(
            kind = k,
            title = title,
            intensity = Intensity.entries.firstOrNull { it.name == intensity } ?: Intensity.MODERATE,
            focus = if (k == DayKind.STRENGTH) day.focus else null,
            workout = workout,
            cardio = null,
            rationale = listOf("Zmienione w rozmowie z trenerem: $reason") + day.rationale.take(1).map { "Plan z reguł: ${day.title}." },
        )
    }
}

@Serializable
data class OverrideExercise(
    val name: String,
    val sets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val weightKg: Double? = null,
    val restSec: Int = 60,
    val note: String? = null,
) {
    fun toPlanned() = PlannedExercise(
        name = name,
        sets = sets.coerceIn(1, 8),
        reps = repsMin.coerceIn(1, 300)..maxOf(repsMin, repsMax).coerceIn(1, 300),
        weightKg = weightKg?.takeIf { it > 0 },
        tempo = null,
        restSec = restSec.coerceIn(0, 300),
        note = note,
    )
}
