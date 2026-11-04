package com.zacco.rytm.domain.coach

import com.zacco.rytm.domain.exercise.ExerciseGuide
import com.zacco.rytm.domain.exercise.Muscle
import com.zacco.rytm.domain.goals.GoalKind
import com.zacco.rytm.domain.goals.GoalProgress
import com.zacco.rytm.domain.model.DayKind
import com.zacco.rytm.domain.model.PlannedDay
import com.zacco.rytm.domain.model.PlannedExercise
import com.zacco.rytm.domain.model.StrengthFocus
import com.zacco.rytm.domain.planner.ExerciseLibrary
import com.zacco.rytm.domain.workout.SetLog
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Dostrojenie sesji siłowej z reguł, przed progresją ciężaru:
 * akcent pod cele (ćwiczenie z celu w sesji, krótsze przerwy przy redukcji)
 * i wymiana ćwiczenia, które od 3 tygodni nie idzie do przodu.
 */
object StrengthTuning {
    const val PLATEAU_SESSIONS = 3
    private const val PLATEAU_MIN_DAYS = 14L
    private const val PLATEAU_WINDOW_DAYS = 42L
    private const val MAX_SETS = 5
    private val LOWER = setOf(Muscle.GLUTES, Muscle.LEGS, Muscle.CALVES)

    fun apply(day: PlannedDay, goals: List<GoalProgress>, sets: List<SetLog>, today: LocalDate): PlannedDay =
        swapPlateaus(withGoals(day, goals), sets, today)

    fun withGoals(day: PlannedDay, goals: List<GoalProgress>): PlannedDay {
        if (day.kind != DayKind.STRENGTH) return day
        val w = day.workout ?: return day
        val open = goals.filter { !it.done }
        if (open.isEmpty()) return day
        var list = w.exercises
        val notes = mutableListOf<String>()
        for (g in open) {
            val ex = g.goal.exercise?.takeIf { g.goal.kind != GoalKind.WEIGHT } ?: continue
            val key = ExerciseGuide.key(ex)
            val i = list.indexOfFirst { ExerciseGuide.key(it.name) == key }
            if (i >= 0) {
                val e = list[i]
                if (e.sets < MAX_SETS) {
                    list = list.toMutableList().also { it[i] = e.copy(sets = e.sets + 1, note = joinNote("Cel: ${g.goal.title}", e.note)) }
                    notes += "seria więcej w: ${e.name}"
                }
            } else if (fits(ex, day.focus)) {
                val reps = if (g.goal.kind == GoalKind.REPS) 3..maxOf(6, g.goal.target.toInt()) else 6..10
                list = list + PlannedExercise(ExerciseGuide.find(ex)?.name ?: ex, 3, reps, null, null, 90, note = "Cel: ${g.goal.title}")
                notes += "dodane: $ex"
            }
        }
        // Redukcja wagi: krótsze przerwy (więcej pracy w tym samym czasie), siła bez zmian.
        val cut = open.firstOrNull { it.goal.kind == GoalKind.WEIGHT && it.start != null && it.goal.target < it.start }
        if (cut != null) {
            list = list.map { it.copy(restSec = maxOf(45, it.restSec - 15)) }
            notes += "przerwy o 15 s krótsze"
        }
        if (notes.isEmpty()) return day
        return day.copy(workout = w.copy(exercises = list), rationale = day.rationale + "Pod Twoje cele: ${notes.joinToString()}.")
    }

    fun swapPlateaus(day: PlannedDay, sets: List<SetLog>, today: LocalDate): PlannedDay {
        if (day.kind != DayKind.STRENGTH) return day
        val w = day.workout ?: return day
        val used = w.exercises.map { ExerciseGuide.key(it.name) }.toMutableSet()
        val swapped = mutableListOf<String>()
        val list = w.exercises.map { e ->
            if (!plateaued(sets, e.name, today)) return@map e
            val alt = ExerciseLibrary.alternative(e.name)?.takeIf { ExerciseGuide.key(it) !in used } ?: return@map e
            used += ExerciseGuide.key(alt)
            swapped += "${e.name} → $alt"
            e.copy(name = alt, note = "Zastój w: ${e.name}, dziś wariant")
        }
        if (swapped.isEmpty()) return day
        return day.copy(workout = w.copy(exercises = list), rationale = day.rationale + "Bez postępu od $PLATEAU_SESSIONS sesji: ${swapped.joinToString()}.")
    }

    /**
     * Zastój: ostatnie [PLATEAU_SESSIONS] sesje ćwiczenia (w ciągu 6 tygodni, rozciągnięte na co najmniej 2 tygodnie)
     * nie przebiły najlepszego wyniku sprzed nich.
     */
    fun plateaued(sets: List<SetLog>, exercise: String, today: LocalDate): Boolean {
        val key = ExerciseGuide.key(exercise)
        val sessions = sets.filter { ExerciseGuide.key(it.exercise) == key && it.date < today && it.date >= today.minusDays(PLATEAU_WINDOW_DAYS) }
            .groupBy { it.date }.toSortedMap().values.toList()
        if (sessions.size < PLATEAU_SESSIONS + 1) return false
        val recent = sessions.takeLast(PLATEAU_SESSIONS)
        val before = sessions.dropLast(PLATEAU_SESSIONS)
        if (ChronoUnit.DAYS.between(recent.first().first().date, recent.last().first().date) < PLATEAU_MIN_DAYS) return false
        val bestBefore = before.maxOf { score(it) }
        return recent.all { score(it) <= bestBefore * 1.001 }
    }

    /** Najlepsza seria (Epley przy ciężarze, powtórzenia przy masie ciała) plus odrobina za objętość. */
    private fun score(session: List<SetLog>): Double {
        fun one(s: SetLog) = s.weightKg?.let { it * (1 + s.reps / 30.0) } ?: s.reps.toDouble()
        return session.maxOf { one(it) } + session.sumOf { one(it) } * 0.01
    }

    private fun fits(exercise: String, focus: StrengthFocus?): Boolean {
        val m = ExerciseGuide.muscles(exercise)
        if (m.isEmpty()) return focus == StrengthFocus.FULL_BODY || focus == null
        return when (focus) {
            StrengthFocus.UPPER -> m.none { it in LOWER }
            StrengthFocus.LOWER -> m.any { it in LOWER }
            else -> true
        }
    }

    private fun joinNote(a: String, b: String?) = if (b.isNullOrBlank()) a else "$a. $b"
}
