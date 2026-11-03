package com.zacco.rytm.domain.workout

import com.zacco.rytm.domain.model.PlannedExercise
import com.zacco.rytm.domain.model.StrengthWorkout
import com.zacco.rytm.domain.planner.Equipment
import com.zacco.rytm.domain.planner.EquipmentKind
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.floor

/**
 * Dobiera dzisiejszy ciężar i powtórzenia z dziennika serii (podwójna progresja).
 *
 * Kolejność przy małym ciężarze w domu: najpierw powtórzenia w zakresie, potem +1 krok ciężaru,
 * a gdy sprzęt się kończy: wolniejsze opuszczanie i wyższy zakres powtórzeń.
 * Po przerwie ponad 3 tygodnie ciężar spada o ok. 10%. Pierwszy raz: ok. 70% maksimum sprzętu i 2–3 powtórzenia zapasu.
 */
class Progression(private val eq: Equipment, private val today: LocalDate) {

    fun apply(workout: StrengthWorkout, history: List<SetLog>): StrengthWorkout {
        val byExercise = history.groupBy { it.exercise }
        return workout.copy(exercises = workout.exercises.map { next(it, byExercise[it.name].orEmpty()) })
    }

    fun next(p: PlannedExercise, history: List<SetLog>): PlannedExercise {
        val own = history.filter { it.exercise == p.name && it.date < today }
        val lastDate = own.maxOfOrNull { it.date } ?: return firstTime(p)
        val last = own.filter { it.date == lastDate }.sortedBy { it.setIndex }
        val lastW = last.mapNotNull { it.weightKg }.maxOrNull()
        val summary = summary(last)
        val cap = p.weightKg

        if (ChronoUnit.DAYS.between(lastDate, today) > LONG_BREAK_DAYS) {
            val w = lastW?.let { atLeastMin(roundDown(it * 0.9)) }
            return p.copy(weightKg = w, note = "Przerwa ponad 3 tygodnie (ostatnio $summary): spokojny powrót" + (w?.let { ", ${kg(it)} kg" } ?: ""))
        }

        val reps = last.map { it.reps }
        val allTop = reps.all { it >= p.reps.last }
        val toFailure = last.any { it.rir == 0 }
        val failed = reps.count { it < p.reps.first } * 2 > reps.size

        if (lastW == null) {
            // Masa ciała: zakres rośnie razem z tym, co już zrobiłeś.
            return if (allTop) {
                val low = maxOf(p.reps.first, reps.min() + 1)
                val high = maxOf(p.reps.last, reps.max() + 2)
                p.copy(reps = low..high, note = "Ostatnio $summary: dziś celuj w $low–$high")
            } else {
                p.copy(note = "Ostatnio $summary: celuj w +1 powtórzenie")
            }
        }

        return when {
            failed -> {
                val w = atLeastMin(lastW - eq.stepKg)
                p.copy(weightKg = w, note = "Ostatnio $summary, za ciężko: dziś ${kg(w)} kg")
            }
            allTop && !toFailure -> {
                val up = lastW + eq.stepKg
                if (cap != null && up > cap + EPS) {
                    // Sprzęt się skończył: trudniej przez tempo i więcej powtórzeń.
                    val high = maxOf(p.reps.last, reps.max()) + 3
                    p.copy(
                        weightKg = lastW,
                        reps = p.reps.first..high,
                        tempo = slower(p.tempo),
                        note = "Ostatnio $summary na maks. ciężarze: wolniej w dół (${slower(p.tempo)}) i do $high powtórzeń",
                    )
                } else {
                    p.copy(weightKg = up, note = "Ostatnio $summary: dziś ${kg(up)} kg")
                }
            }
            else -> p.copy(weightKg = lastW, note = "Ostatnio $summary: ten sam ciężar, celuj w +1 powtórzenie")
        }
    }

    private fun firstTime(p: PlannedExercise): PlannedExercise {
        val cap = p.weightKg
        return when {
            cap != null -> {
                val w = atLeastMin(roundDown(cap * START_SHARE))
                p.copy(weightKg = w, note = "Pierwszy raz: ok. ${kg(w)} kg, zostaw 2–3 powtórzenia zapasu")
            }
            eq.kind == EquipmentKind.GYM -> p.copy(note = "Pierwszy raz: dobierz ciężar na 2–3 powtórzenia zapasu")
            else -> p
        }
    }

    private fun roundDown(kg: Double): Double = floor(kg / eq.stepKg + EPS) * eq.stepKg

    private fun atLeastMin(kg: Double): Double = maxOf(eq.minKg, kg)

    companion object {
        private const val LONG_BREAK_DAYS = 21
        private const val START_SHARE = 0.7
        private const val EPS = 1e-6

        /** Tempo „3-1-1” → „4-1-1”; bez tempa: 3 s w dół. */
        fun slower(tempo: String?): String {
            val parts = tempo?.split("-")
            val down = parts?.firstOrNull()?.toIntOrNull() ?: return "3-1-1"
            return (listOf((down + 1).coerceAtMost(5).toString()) + parts.drop(1)).joinToString("-")
        }

        /** „6”, „6,5”. */
        fun kg(v: Double): String =
            if (v % 1.0 == 0.0) v.toLong().toString() else "%.1f".format(java.util.Locale.ROOT, v).replace('.', ',')

        /** „3 × 15 na 6 kg” albo „15, 14, 12 na 6 kg”. */
        fun summary(sets: List<SetLog>): String {
            if (sets.isEmpty()) return ""
            val reps = sets.map { it.reps }
            val r = if (reps.distinct().size == 1) "${reps.size} × ${reps.first()}" else reps.joinToString(", ")
            val w = sets.mapNotNull { it.weightKg }.maxOrNull()
            return if (w == null) r else "$r na ${kg(w)} kg"
        }
    }
}
