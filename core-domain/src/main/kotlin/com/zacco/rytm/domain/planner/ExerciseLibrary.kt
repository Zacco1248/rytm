package com.zacco.rytm.domain.planner

import com.zacco.rytm.domain.model.DayKind
import com.zacco.rytm.domain.model.Intensity
import com.zacco.rytm.domain.model.PlannedDay
import com.zacco.rytm.domain.model.PlannedExercise
import com.zacco.rytm.domain.model.StrengthFocus
import com.zacco.rytm.domain.model.StrengthWorkout

/**
 * Sprzęt w domu (odpowiedź z 2026-10-31): para hantli regulowanych 2–8 kg albo jeden hantel do 14 kg; gumy opcjonalnie.
 * Ciężar jest mały, więc trudność dają powtórzenia (12–15), wolne opuszczanie (tempo 3-1-1) i ćwiczenia na jedną nogę lub rękę.
 */
data class Equipment(
    /** Maks. na hantel, gdy używasz dwóch. */
    val pairMaxKg: Double = 8.0,
    /** Maks. jednego hantla. */
    val singleMaxKg: Double = 14.0,
    val bands: Boolean = false,
)

/** Konkretne ćwiczenia do sesji z planu. Progresja ciężaru przyjdzie z dziennikiem serii (faza 3). */
class ExerciseLibrary(private val eq: Equipment = Equipment()) {

    fun workoutFor(day: PlannedDay): StrengthWorkout? = when (day.kind) {
        DayKind.STRENGTH -> strength(day.focus ?: StrengthFocus.FULL_BODY, day.intensity)
        DayKind.MOBILITY -> mobility()
        DayKind.FLEX -> if (day.title.startsWith("Interwały")) intervals() else null
        DayKind.REST -> if (day.title.contains("mobilność", ignoreCase = true)) mobility() else null
        else -> null
    }

    private fun strength(focus: StrengthFocus, intensity: Intensity): StrengthWorkout {
        val base = when (focus) {
            StrengthFocus.UPPER -> upper()
            StrengthFocus.LOWER -> lower()
            StrengthFocus.FULL_BODY -> fullBody()
        }
        val exercises = when (intensity) {
            // Lżej: 2 serie i bez ostatniego ćwiczenia.
            Intensity.LOW -> base.dropLast(1).map { it.copy(sets = 2) }
            Intensity.MODERATE -> base
            // Mocniej: seria więcej w dwóch pierwszych ćwiczeniach.
            Intensity.HIGH -> base.mapIndexed { i, e -> if (i < 2) e.copy(sets = e.sets + 1) else e }
        }
        val name = when (focus) {
            StrengthFocus.UPPER -> "Góra + core"
            StrengthFocus.LOWER -> "Dół + core"
            StrengthFocus.FULL_BODY -> "Całe ciało"
        }
        return StrengthWorkout(name, exercises)
    }

    private fun upper() = listOf(
        ex("Wyciskanie hantli leżąc na podłodze", 3, 12..15, eq.pairMaxKg, "3-1-1", 75),
        ex("Wiosłowanie jednorącz w opadzie (na stronę)", 3, 10..12, eq.singleMaxKg, "2-1-1", 75),
        ex("Wyciskanie hantli nad głowę stojąc", 3, 10..12, eq.pairMaxKg, "2-0-1", 75),
        ex("Pompki", 3, 8..15, null, "2-0-1", 60),
        ex("Uginanie ramion z hantlami", 3, 12..15, eq.pairMaxKg, "3-0-1", 60),
        ex("Deska (sekundy)", 3, 40..60, null, null, 45),
    )

    private fun lower() = listOf(
        ex("Przysiad goblet", 4, 12..15, eq.singleMaxKg, "3-1-1", 90),
        ex("Przysiad bułgarski (na nogę)", 3, 8..12, eq.pairMaxKg, "2-1-1", 75),
        ex("Martwy ciąg rumuński z hantlami", 3, 12..15, eq.pairMaxKg, "3-1-1", 75),
        ex("Unoszenie bioder z hantlem (hip thrust)", 3, 12..15, eq.singleMaxKg, "2-1-1", 60),
        ex("Wspięcia na palce na jednej nodze", 3, 15..20, eq.pairMaxKg, "2-1-1", 45),
        ex("Dead bug (na stronę)", 3, 8..10, null, null, 45),
    )

    private fun fullBody() = listOf(
        ex("Przysiad goblet", 3, 12..15, eq.singleMaxKg, "3-1-1", 75),
        ex("Pompki", 3, 8..15, null, "2-0-1", 60),
        ex("Wiosłowanie jednorącz w opadzie (na stronę)", 3, 10..12, eq.singleMaxKg, "2-1-1", 60),
        ex("Wykroki w tył (na nogę)", 3, 10..12, eq.pairMaxKg, "2-0-1", 60),
        ex("Wyciskanie hantli nad głowę stojąc", 3, 10..12, eq.pairMaxKg, "2-0-1", 60),
        ex("Brzuszki", 3, 20..25, null, null, 45),
    )

    private fun mobility() = StrengthWorkout(
        "Mobilność",
        listOf(
            ex("Kot–krowa", 2, 10..10, null, null, 0),
            ex("Rotacje odcinka piersiowego w klęku (na stronę)", 2, 8..10, null, null, 0),
            ex("Rozciąganie zginaczy bioder w wykroku (sekundy na stronę)", 2, 45..60, null, null, 0),
            ex("Głęboki przysiad z podparciem (sekundy)", 2, 45..60, null, null, 0),
            ex("Rozciąganie łydek przy ścianie (sekundy na stronę)", 2, 45..45, null, null, 0),
            ex("Krążenia barków z kijem lub ręcznikiem", 2, 10..12, null, null, 0),
        ),
    )

    /** Zamiennik przy niskim tygodniowym obciążeniu: obwód 4–5 rund, 40 s pracy / 20 s przerwy. */
    private fun intervals() = StrengthWorkout(
        "Obwód z hantlami (40 s pracy, 20 s przerwy, 4–5 rund)",
        listOf(
            ex("Thruster z hantlami", 5, 10..15, eq.pairMaxKg, null, 20),
            ex("Wiosłowanie oburącz w opadzie", 5, 10..15, eq.pairMaxKg, null, 20),
            ex("Wykroki naprzemienne", 5, 10..15, eq.pairMaxKg, null, 20),
            ex("Wymachy hantlem (swing)", 5, 12..15, eq.singleMaxKg, null, 20),
            ex("Mountain climbers", 5, 20..30, null, null, 60),
        ),
    )

    private fun ex(name: String, sets: Int, reps: IntRange, kg: Double?, tempo: String?, rest: Int) =
        PlannedExercise(name, sets, reps, kg, tempo, rest)
}
