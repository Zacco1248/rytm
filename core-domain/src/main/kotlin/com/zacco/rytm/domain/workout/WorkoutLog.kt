package com.zacco.rytm.domain.workout

import com.zacco.rytm.domain.model.Activity
import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.model.Source
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Jedna zrobiona seria. [weightKg] = null przy ćwiczeniach z masą ciała; przy „(sekundy)” [reps] to sekundy. */
data class SetLog(
    val workoutId: String,
    val exercise: String,
    /** Numer serii w ćwiczeniu, od 1. */
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double?,
    /** Powtórzenia w zapasie (0 = do upadku); null, gdy nie podano. */
    val rir: Int?,
    val date: LocalDate,
)

/** Zapisany trening z dziennikiem serii (z zegarka albo z telefonu). */
data class WorkoutLog(
    val id: String,
    val title: String,
    val type: ActivityType,
    val start: Instant,
    val end: Instant,
    val sets: List<SetLog>,
    val rpe: Int?,
    val avgHr: Int?,
    val kcal: Int?,
    val source: Source,
) {
    val durationMin: Int get() = Duration.between(start, end).toMinutes().toInt().coerceAtLeast(1)

    /** Objętość: suma powtórzeń × ciężar (tylko serie z ciężarem). */
    val volumeKg: Double get() = sets.sumOf { s -> s.weightKg?.let { it * s.reps } ?: 0.0 }

    /** Trening liczy się do obciążenia i kcal jak każda inna aktywność. */
    fun toActivity(): Activity = Activity(
        id = id,
        type = type,
        start = start,
        durationMin = durationMin,
        rpe = rpe,
        avgHr = avgHr,
        kcal = kcal,
        source = source,
        notes = title,
    )
}
