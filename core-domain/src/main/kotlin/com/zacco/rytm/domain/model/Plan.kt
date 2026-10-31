package com.zacco.rytm.domain.model

import java.time.LocalDate

enum class DayKind { FIXED_SPORT, STRENGTH, PERSONAL_TRAINING, MOBILITY, REST, FLEX }

enum class Intensity { LOW, MODERATE, HIGH }

/** Na co kładzie nacisk sesja siłowa; potrzebne do reguł (np. „bez ciężkich nóg przed meczem”). */
enum class StrengthFocus { UPPER, LOWER, FULL_BODY }

data class PlannedDay(
    val date: LocalDate,
    val kind: DayKind,
    val title: String,
    val intensity: Intensity,
    val focus: StrengthFocus? = null,
    val workout: StrengthWorkout? = null,
    /** Krótkie uzasadnienia po polsku, w kolejności ważności. */
    val rationale: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
)

data class StrengthWorkout(val name: String, val exercises: List<PlannedExercise>)

data class PlannedExercise(
    val name: String,
    val sets: Int,
    val reps: IntRange,
    val weightKg: Double?,
    val tempo: String?,
    val restSec: Int,
)

data class LoggedSet(val exercise: String, val reps: Int, val weightKg: Double, val rir: Int?)
