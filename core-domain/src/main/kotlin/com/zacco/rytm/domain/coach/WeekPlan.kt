package com.zacco.rytm.domain.coach

import com.zacco.rytm.domain.model.DayKind
import com.zacco.rytm.domain.model.PlannedDay
import com.zacco.rytm.domain.model.StrengthWorkout
import kotlinx.serialization.Serializable

/** Sesja siłowa z planu tygodnia od trenera AI: ćwiczenia i objętość, bez ciężaru (ciężar liczy progresja). */
@Serializable
data class PlanDay(
    val date: String,
    val title: String,
    val exercises: List<OverrideExercise>,
    val note: String = "",
)

/** Propozycja planu siłowego na tydzień; obowiązuje dopiero po zatwierdzeniu. */
@Serializable
data class WeekPlan(
    /** Poniedziałek tygodnia (ISO). */
    val weekStart: String,
    val days: List<PlanDay>,
    /** Dlaczego tak, 1–3 zdania. */
    val reason: String,
    val createdAt: Long = System.currentTimeMillis(),
    val accepted: Boolean = false,
    val costUsd: Double = 0.0,
) {
    /** Dzień siłowy z reguł zastąpiony sesją z planu (ciężar z progresji, bez wpisanych kilogramów). */
    fun applyTo(day: PlannedDay): PlannedDay? {
        if (!accepted || day.kind != DayKind.STRENGTH) return null
        val d = days.firstOrNull { it.date == day.date.toString() } ?: return null
        if (d.exercises.isEmpty()) return null
        val list = d.exercises.map { it.copy(weightKg = null).toPlanned() }
        return day.copy(
            title = d.title,
            workout = StrengthWorkout(d.title, list),
            rationale = listOf("Plan tygodnia od trenera" + (d.note.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ".")) + day.rationale.take(1),
        )
    }
}
