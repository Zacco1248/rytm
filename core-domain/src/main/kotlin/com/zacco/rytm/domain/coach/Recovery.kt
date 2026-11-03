package com.zacco.rytm.domain.coach

import com.zacco.rytm.domain.exercise.ExerciseGuide
import com.zacco.rytm.domain.exercise.Muscle
import com.zacco.rytm.domain.load.LoadSummary
import com.zacco.rytm.domain.model.Activity
import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.model.BodyArea
import com.zacco.rytm.domain.model.DayKind
import com.zacco.rytm.domain.model.Intensity
import com.zacco.rytm.domain.model.PlannedDay
import com.zacco.rytm.domain.model.PlannedExercise
import com.zacco.rytm.domain.time.RytmTime
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.floor

/** Dlaczego ten tydzień jest lżejszy. */
enum class DeloadReason { HIGH_ACWR, LONG_BLOCK }

data class Deload(val reason: DeloadReason, val text: String)

/**
 * Regeneracja: lżejszy tydzień (deload), gdy obciążenie rośnie za szybko albo po długim bloku ciężkich tygodni,
 * i zamiana ćwiczeń na bezpieczne, gdy w check-inie coś boli.
 */
object Recovery {
    /** ACWR od tej wartości (przy wiarygodnych danych) oznacza zbyt szybki wzrost obciążenia. */
    const val ACWR_DELOAD = 1.4
    /** Co tyle tygodni pracy przychodzi tydzień lżej (6. tydzień cyklu). */
    const val BLOCK_WEEKS = 5
    private const val MIN_STRENGTH_PER_WEEK = 2
    private const val WEIGHT_SHARE = 0.9

    /**
     * Lżejszy tydzień: od razu przy wysokim ACWR, a poza tym co 6. tydzień od startu planu ([planStart]),
     * jeśli co najmniej 4 z 5 poprzednich tygodni były pełne (min. 2 sesje siłowe).
     */
    fun deload(today: LocalDate, load: LoadSummary, activities: List<Activity>, planStart: LocalDate?): Deload? {
        val acwr = load.acwr
        if (load.reliable && acwr != null && acwr >= ACWR_DELOAD) {
            return Deload(
                DeloadReason.HIGH_ACWR,
                "Obciążenie rośnie za szybko (ACWR ${"%.2f".format(java.util.Locale.ROOT, acwr).replace('.', ',')}): ten tydzień lżej, mniej serii i ok. 10% mniejszy ciężar.",
            )
        }
        val monday = today.with(DayOfWeek.MONDAY)
        val start = planStart?.with(DayOfWeek.MONDAY) ?: return null
        val week = java.time.temporal.ChronoUnit.WEEKS.between(start, monday)
        if (week <= 0 || week % (BLOCK_WEEKS + 1) != BLOCK_WEEKS.toLong()) return null
        val full = (1..BLOCK_WEEKS).count { w ->
            val from = monday.minusWeeks(w.toLong())
            val to = from.plusDays(6)
            activities.count { a ->
                (a.type == ActivityType.STRENGTH || a.type == ActivityType.PERSONAL_TRAINING) &&
                    a.start.atZone(RytmTime.zone).toLocalDate() in from..to
            } >= MIN_STRENGTH_PER_WEEK
        }
        if (full >= BLOCK_WEEKS - 1) {
            return Deload(DeloadReason.LONG_BLOCK, "Po $BLOCK_WEEKS mocnych tygodniach: tydzień lżej, żeby ciało nadrobiło regenerację.")
        }
        return null
    }

    /** Lżejsza sesja siłowa: o serię mniej (min. 2), ciężar ok. 90%, intensywność najwyżej umiarkowana. */
    fun applyDeload(day: PlannedDay, d: Deload, stepKg: Double): PlannedDay {
        if (day.kind != DayKind.STRENGTH) return day
        val w = day.workout ?: return day
        return day.copy(
            intensity = if (day.intensity == Intensity.HIGH) Intensity.MODERATE else day.intensity,
            workout = w.copy(
                exercises = w.exercises.map { e ->
                    e.copy(
                        sets = (e.sets - 1).coerceAtLeast(minOf(2, e.sets)),
                        weightKg = e.weightKg?.let { kg -> maxOf(stepKg, floor(kg * WEIGHT_SHARE / stepKg + 1e-6) * stepKg) },
                        note = "Tydzień lżej" + (e.note?.let { ": $it" } ?: ""),
                    )
                },
            ),
            rationale = listOf(d.text) + day.rationale,
        )
    }

    /** Które partie oszczędzać, gdy boli dany obszar. */
    fun musclesFor(area: BodyArea): Set<Muscle> = when (area) {
        BodyArea.SHOULDERS -> setOf(Muscle.SHOULDERS, Muscle.CHEST)
        BodyArea.CHEST -> setOf(Muscle.CHEST)
        BodyArea.ARMS, BodyArea.ELBOW -> setOf(Muscle.BICEPS, Muscle.TRICEPS)
        BodyArea.ABS -> setOf(Muscle.ABS)
        BodyArea.BACK -> setOf(Muscle.BACK)
        BodyArea.THIGHS -> setOf(Muscle.LEGS, Muscle.GLUTES)
        BodyArea.KNEES -> setOf(Muscle.LEGS)
        BodyArea.CALVES, BodyArea.ANKLES -> setOf(Muscle.CALVES, Muscle.LEGS)
    }

    /** Bezpieczne zamienniki z masą ciała, od najbardziej uniwersalnych. */
    private val SAFE = listOf("Most biodrowy", "Dead bug", "Bird dog", "Deska", "Superman", "Band pull-apart", "Wspięcia na palce na jednej nodze")

    /**
     * Gdy w check-inie coś boli: ćwiczenia na te partie zamieniamy na bezpieczne (bez tych partii) albo pomijamy.
     * Przy ostrym bólu i tak lepiej odpocząć; mówi o tym uzasadnienie.
     */
    fun avoidSore(day: PlannedDay, sore: Set<BodyArea>): PlannedDay {
        if (sore.isEmpty() || day.kind != DayKind.STRENGTH) return day
        val w = day.workout ?: return day
        val avoid = sore.flatMap { musclesFor(it) }.toSet()
        val used = w.exercises.map { ExerciseGuide.key(it.name) }.toMutableSet()
        val swapped = mutableListOf<String>()
        val out = w.exercises.mapNotNull { e ->
            if (ExerciseGuide.muscles(e.name).none { it in avoid }) return@mapNotNull e
            val alt = SAFE.firstOrNull { s -> ExerciseGuide.key(s) !in used && ExerciseGuide.muscles(s).none { it in avoid } }
            swapped += e.name + (alt?.let { " → $it" } ?: " (pominięte)")
            alt?.let { a ->
                used += ExerciseGuide.key(a)
                PlannedExercise(a, e.sets.coerceAtMost(3), 10..15, null, null, 45, note = "Zamiast: ${e.name}")
            }
        }
        if (swapped.isEmpty()) return day
        val text = "Boli: ${sore.joinToString { areaName(it) }}. Zamiany: ${swapped.joinToString()}. Przy ostrym bólu odpuść trening i skonsultuj się z fizjoterapeutą."
        return day.copy(workout = w.copy(exercises = out), rationale = listOf(text) + day.rationale)
    }

    fun areaName(a: BodyArea): String = when (a) {
        BodyArea.SHOULDERS -> "barki"
        BodyArea.CHEST -> "klatka"
        BodyArea.ARMS -> "ramiona"
        BodyArea.ELBOW -> "łokieć"
        BodyArea.ABS -> "brzuch"
        BodyArea.BACK -> "plecy"
        BodyArea.THIGHS -> "uda"
        BodyArea.KNEES -> "kolana"
        BodyArea.CALVES -> "łydki"
        BodyArea.ANKLES -> "kostki"
    }
}
