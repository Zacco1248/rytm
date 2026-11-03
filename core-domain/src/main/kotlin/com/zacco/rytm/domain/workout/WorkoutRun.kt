package com.zacco.rytm.domain.workout

import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.model.Source
import com.zacco.rytm.domain.model.StrengthWorkout
import com.zacco.rytm.domain.time.RytmTime
import kotlinx.serialization.Serializable
import java.time.Instant

/** Ćwiczenie w trakcie treningu. Proste typy, bo stan zapisujemy jako JSON (proces na zegarku bywa zabijany). */
@Serializable
data class RunExercise(
    val name: String,
    val sets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val weightKg: Double? = null,
    val tempo: String? = null,
    val restSec: Int = 60,
    val note: String? = null,
)

@Serializable
data class RunSet(
    val exercise: String,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double? = null,
    val rir: Int? = null,
    val atMs: Long,
)

/**
 * Prowadzenie treningu: które ćwiczenie i seria teraz, przerwa, zapisane serie.
 * Czysta logika, wspólna dla zegarka i telefonu; czas zawsze podawany z zewnątrz.
 */
@Serializable
data class WorkoutRun(
    val id: String,
    val title: String,
    /** Nazwa [ActivityType]. */
    val type: String = ActivityType.STRENGTH.name,
    val startedAtMs: Long,
    val exercises: List<RunExercise>,
    val stepKg: Double = 1.0,
    val exerciseIdx: Int = 0,
    /** Numer następnej serii w bieżącym ćwiczeniu, od 1. */
    val setNo: Int = 1,
    val sets: List<RunSet> = emptyList(),
    /** Koniec przerwy (epoch ms); null, gdy nie ma przerwy. */
    val restUntilMs: Long? = null,
    val skipped: List<String> = emptyList(),
    /** Górna granica ciężaru sprzętu (np. najcięższy hantel); null: bez limitu (siłownia). */
    val maxKg: Double? = null,
    /** Podpowiedź po ostatniej ocenionej serii (autoregulacja). */
    val advice: SetAdvice? = null,
) {
    val current: RunExercise? get() = exercises.getOrNull(exerciseIdx)
    val isFinished: Boolean get() = current == null

    fun isResting(nowMs: Long): Boolean = restUntilMs != null && nowMs < restUntilMs

    fun restLeftSec(nowMs: Long): Int = restUntilMs?.let { ((it - nowMs + 999) / 1000).toInt().coerceAtLeast(0) } ?: 0

    /** Podpowiedź na następną serię: to, co wpisałeś w poprzedniej serii tego ćwiczenia, a na start cel z planu. */
    fun suggestedReps(): Int {
        val ex = current ?: return 0
        currentAdvice()?.reps?.let { return it }
        return sets.lastOrNull { it.exercise == ex.name }?.reps ?: ex.repsMax
    }

    fun suggestedWeight(): Double? {
        val ex = current ?: return null
        val last = sets.lastOrNull { it.exercise == ex.name }
        currentAdvice()?.takeIf { last?.weightKg != null }?.weightKg?.let { return it }
        return last?.weightKg ?: ex.weightKg
    }

    /** Podpowiedź na następną serię bieżącego ćwiczenia (nie ta po ostatniej serii poprzedniego). */
    fun currentAdvice(): SetAdvice? = advice?.takeIf { a -> a.exercise == current?.name && !a.offerExtraSet && setNo > 1 }

    /** Podpowiedź do pokazania w przerwie: także „dołóż serię” po ostatniej serii poprzedniego ćwiczenia. */
    fun visibleAdvice(): SetAdvice? = advice?.takeIf { a ->
        (a.offerExtraSet && exercises.getOrNull(exerciseIdx - 1)?.name == a.exercise && setNo == 1) || currentAdvice() != null
    }

    /** Zapisuje serię; po ostatniej serii ćwiczenia przechodzi do następnego. Przerwa po każdej serii poza ostatnią w treningu. */
    fun log(reps: Int, weightKg: Double?, rir: Int?, nowMs: Long): WorkoutRun {
        val ex = current ?: return this
        val done = RunSet(ex.name, setNo, reps.coerceAtLeast(0), weightKg, rir, nowMs)
        val logged = sets + done
        val lastOfExercise = setNo >= ex.sets
        val advice = Autoregulation.afterSet(ex, done, sets, stepKg, maxKg, lastOfExercise)
        val nextIdx = if (lastOfExercise) exerciseIdx + 1 else exerciseIdx
        val finished = nextIdx >= exercises.size
        return copy(
            sets = logged,
            exerciseIdx = nextIdx,
            setNo = if (lastOfExercise) 1 else setNo + 1,
            restUntilMs = if (finished || ex.restSec <= 0) null else nowMs + ex.restSec * 1000L,
            advice = advice,
        )
    }

    /** „Dołóż serię” po lekkiej ostatniej serii: wraca do poprzedniego ćwiczenia na jedną serię więcej. */
    fun extraSet(): WorkoutRun {
        val a = advice?.takeIf { it.offerExtraSet } ?: return this
        val idx = exerciseIdx - 1
        val prev = exercises.getOrNull(idx)?.takeIf { it.name == a.exercise } ?: return this
        return copy(
            exercises = exercises.mapIndexed { i, e -> if (i == idx) e.copy(sets = prev.sets + 1) else e },
            exerciseIdx = idx,
            setNo = prev.sets + 1,
            advice = a.copy(offerExtraSet = false, reps = sets.lastOrNull { it.exercise == a.exercise }?.reps),
        )
    }

    /** Kończy bieżące ćwiczenie przed czasem (np. dwie serie do upadku); nie liczy się jako pominięte. */
    fun endExercise(): WorkoutRun {
        val ex = current ?: return this
        val done = sets.count { it.exercise == ex.name }
        return copy(
            exercises = exercises.mapIndexed { i, e -> if (i == exerciseIdx) e.copy(sets = done.coerceAtLeast(1)) else e },
            exerciseIdx = exerciseIdx + 1,
            setNo = 1,
            advice = null,
        )
    }

    /** Jeszcze jedna seria tego samego ćwiczenia (np. gdy poszło dobrze). */
    fun addSet(): WorkoutRun {
        val ex = current ?: return this
        return copy(exercises = exercises.mapIndexed { i, e -> if (i == exerciseIdx) e.copy(sets = ex.sets + 1) else e })
    }

    fun skipRest(): WorkoutRun = copy(restUntilMs = null)

    fun addRest(sec: Int, nowMs: Long): WorkoutRun = copy(restUntilMs = maxOf(restUntilMs ?: nowMs, nowMs) + sec * 1000L)

    /** Pomija resztę serii bieżącego ćwiczenia (brak czasu, boli, brak sprzętu). */
    fun skipExercise(): WorkoutRun {
        val ex = current ?: return this
        return copy(exerciseIdx = exerciseIdx + 1, setNo = 1, restUntilMs = null, skipped = skipped + ex.name, advice = null)
    }

    /** Gotowy wpis do dziennika. Serie z 0 powtórzeń pomijamy. */
    fun toLog(endMs: Long, rpe: Int?, avgHr: Int?, kcal: Int?, source: Source): WorkoutLog {
        val start = Instant.ofEpochMilli(startedAtMs)
        val date = start.atZone(RytmTime.zone).toLocalDate()
        return WorkoutLog(
            id = id,
            title = title,
            type = ActivityType.entries.firstOrNull { it.name == type } ?: ActivityType.STRENGTH,
            start = start,
            end = Instant.ofEpochMilli(maxOf(endMs, startedAtMs + 60_000)),
            sets = sets.filter { it.reps > 0 }.map { SetLog(id, it.exercise, it.setIndex, it.reps, it.weightKg, it.rir, date) },
            rpe = rpe?.coerceIn(1, 10),
            avgHr = avgHr,
            kcal = kcal,
            source = source,
        )
    }

    companion object {
        fun start(id: String, workout: StrengthWorkout, type: ActivityType, stepKg: Double, nowMs: Long, maxKg: Double? = null) = WorkoutRun(
            id = id,
            maxKg = maxKg,
            title = workout.name,
            type = type.name,
            startedAtMs = nowMs,
            stepKg = stepKg,
            exercises = workout.exercises.map {
                RunExercise(it.name, it.sets, it.reps.first, it.reps.last, it.weightKg, it.tempo, it.restSec, it.note)
            },
        )
    }
}
