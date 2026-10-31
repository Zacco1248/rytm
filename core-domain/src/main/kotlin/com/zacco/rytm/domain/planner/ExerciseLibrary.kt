package com.zacco.rytm.domain.planner

import com.zacco.rytm.domain.model.DayKind
import com.zacco.rytm.domain.model.Intensity
import com.zacco.rytm.domain.model.PlannedDay
import com.zacco.rytm.domain.model.PlannedExercise
import com.zacco.rytm.domain.model.StrengthFocus
import com.zacco.rytm.domain.model.StrengthWorkout
import com.zacco.rytm.domain.profile.Goal

enum class EquipmentKind { NONE, BANDS, DUMBBELLS, GYM }

/**
 * Sprzęt z profilu. Przy lekkich hantlach (np. para 2–8 kg, jeden do 14 kg) trudność dają powtórzenia (12–15),
 * wolne opuszczanie (tempo 3-1-1) i ćwiczenia na jedną nogę lub rękę.
 */
data class Equipment(
    val kind: EquipmentKind = EquipmentKind.DUMBBELLS,
    /** Maks. na hantel, gdy używasz dwóch. */
    val pairMaxKg: Double = 8.0,
    /** Maks. jednego hantla. */
    val singleMaxKg: Double = 14.0,
    val bands: Boolean = false,
    /** Ile minut zwykle jest na sesję; krótszy czas to mniej ćwiczeń. */
    val sessionMinutes: Int = 45,
)

/** Konkretne ćwiczenia do sesji z planu, dobrane do sprzętu i celu. Progresja ciężaru przyjdzie z dziennikiem serii (faza 3). */
class ExerciseLibrary(
    private val eq: Equipment = Equipment(),
    private val goal: Goal = Goal.RECOMP,
) {

    fun workoutFor(day: PlannedDay): StrengthWorkout? = when (day.kind) {
        DayKind.STRENGTH -> strength(day.focus ?: StrengthFocus.FULL_BODY, day.intensity)
        DayKind.MOBILITY -> mobility()
        DayKind.FLEX -> if (day.title.startsWith("Interwały")) intervals() else null
        DayKind.REST -> if (day.title.contains("mobilność", ignoreCase = true)) mobility() else null
        else -> null
    }

    private fun strength(focus: StrengthFocus, intensity: Intensity): StrengthWorkout {
        var exercises = forGoal(base(focus))
        exercises = when (intensity) {
            // Lżej: 2 serie i bez ostatniego ćwiczenia.
            Intensity.LOW -> exercises.dropLast(1).map { it.copy(sets = 2) }
            Intensity.MODERATE -> exercises
            // Mocniej: seria więcej w dwóch pierwszych ćwiczeniach.
            Intensity.HIGH -> exercises.mapIndexed { i, e -> if (i < 2) e.copy(sets = e.sets + 1) else e }
        }
        exercises = when {
            eq.sessionMinutes <= 20 -> exercises.take(3).map { it.copy(sets = minOf(it.sets, 2)) }
            eq.sessionMinutes <= 30 -> exercises.take(4)
            else -> exercises
        }
        val name = when (focus) {
            StrengthFocus.UPPER -> "Góra + core"
            StrengthFocus.LOWER -> "Dół + core"
            StrengthFocus.FULL_BODY -> "Całe ciało"
        }
        val kit = when (eq.kind) {
            EquipmentKind.NONE -> " · bez sprzętu"
            EquipmentKind.BANDS -> " · gumy"
            EquipmentKind.DUMBBELLS -> " · hantle"
            EquipmentKind.GYM -> " · siłownia (ciężar na 2 powtórzenia zapasu)"
        }
        return StrengthWorkout(name + kit, exercises)
    }

    /** Na siłowni zakres powtórzeń zależy od celu; w domu ciężar jest mały, więc zostają wyższe zakresy. */
    private fun forGoal(list: List<PlannedExercise>): List<PlannedExercise> = when {
        eq.kind == EquipmentKind.GYM && goal == Goal.STRENGTH ->
            list.mapIndexed { i, e -> if (i < 3) e.copy(sets = 4, reps = 4..6, restSec = 150) else e }
        eq.kind == EquipmentKind.GYM && goal == Goal.MUSCLE_GAIN ->
            list.map { e -> if (e.reps.last <= 15) e.copy(reps = 8..12, restSec = maxOf(e.restSec, 90)) else e }
        goal == Goal.ENDURANCE ->
            list.map { e -> e.copy(sets = minOf(e.sets, 3), reps = (e.reps.first + 3)..(e.reps.last + 5), restSec = minOf(e.restSec, 45)) }
        else -> list
    }

    private fun base(focus: StrengthFocus): List<PlannedExercise> = when (eq.kind) {
        EquipmentKind.NONE -> when (focus) {
            StrengthFocus.UPPER -> listOf(
                ex("Pompki", 3, 8..15, null, "2-0-1", 60),
                ex("Wiosłowanie odwrócone pod stołem", 3, 8..12, null, "2-1-1", 60),
                ex("Pompki w pozycji piki (na barki)", 3, 6..10, null, "2-0-1", 60),
                ex("Dipy na krześle", 3, 8..12, null, "2-0-1", 60),
                ex("Superman", 3, 12..15, null, null, 45),
                ex("Deska (sekundy)", 3, 30..60, null, null, 45),
            )
            StrengthFocus.LOWER -> listOf(
                ex("Przysiad", 4, 15..20, null, "3-1-1", 60),
                ex("Przysiad bułgarski na krześle (na nogę)", 3, 8..12, null, "2-1-1", 60),
                ex("Most biodrowy na jednej nodze (na nogę)", 3, 10..15, null, "2-1-1", 45),
                ex("Wykroki w tył (na nogę)", 3, 10..12, null, "2-0-1", 45),
                ex("Wspięcia na palce na jednej nodze", 3, 15..20, null, "2-1-1", 45),
                ex("Dead bug (na stronę)", 3, 8..10, null, null, 45),
            )
            StrengthFocus.FULL_BODY -> listOf(
                ex("Przysiad", 3, 15..20, null, "3-1-1", 60),
                ex("Pompki", 3, 8..15, null, "2-0-1", 60),
                ex("Wiosłowanie odwrócone pod stołem", 3, 8..12, null, "2-1-1", 60),
                ex("Wykroki w tył (na nogę)", 3, 10..12, null, "2-0-1", 45),
                ex("Most biodrowy", 3, 15..20, null, "2-1-1", 45),
                ex("Brzuszki", 3, 15..25, null, null, 45),
            )
        }
        EquipmentKind.BANDS -> when (focus) {
            StrengthFocus.UPPER -> listOf(
                ex("Pompki", 3, 8..15, null, "2-0-1", 60),
                ex("Wiosłowanie gumą", 3, 12..15, null, "2-1-1", 60),
                ex("Wyciskanie gumą nad głowę", 3, 10..15, null, "2-0-1", 60),
                ex("Face pull gumą", 3, 15..20, null, "2-1-1", 45),
                ex("Uginanie ramion z gumą", 3, 12..15, null, "3-0-1", 45),
                ex("Pallof press (na stronę)", 3, 10..12, null, null, 45),
            )
            StrengthFocus.LOWER -> listOf(
                ex("Przysiad z gumą", 4, 15..20, null, "3-1-1", 60),
                ex("Martwy ciąg rumuński z gumą", 3, 12..15, null, "3-1-1", 60),
                ex("Przysiad bułgarski (na nogę)", 3, 8..12, null, "2-1-1", 60),
                ex("Most biodrowy z gumą nad kolanami", 3, 15..20, null, "2-1-1", 45),
                ex("Chód boczny z gumą (kroki na stronę)", 3, 12..15, null, null, 45),
                ex("Dead bug (na stronę)", 3, 8..10, null, null, 45),
            )
            StrengthFocus.FULL_BODY -> listOf(
                ex("Przysiad z gumą", 3, 15..20, null, "3-1-1", 60),
                ex("Pompki", 3, 8..15, null, "2-0-1", 60),
                ex("Wiosłowanie gumą", 3, 12..15, null, "2-1-1", 60),
                ex("Martwy ciąg rumuński z gumą", 3, 12..15, null, "3-1-1", 60),
                ex("Wyciskanie gumą nad głowę", 3, 10..15, null, "2-0-1", 45),
                ex("Deska (sekundy)", 3, 30..60, null, null, 45),
            )
        }
        EquipmentKind.DUMBBELLS -> when (focus) {
            StrengthFocus.UPPER -> listOf(
                ex("Wyciskanie hantli leżąc na podłodze", 3, 12..15, eq.pairMaxKg, "3-1-1", 75),
                ex("Wiosłowanie jednorącz w opadzie (na stronę)", 3, 10..12, eq.singleMaxKg, "2-1-1", 75),
                ex("Wyciskanie hantli nad głowę stojąc", 3, 10..12, eq.pairMaxKg, "2-0-1", 75),
                ex("Pompki", 3, 8..15, null, "2-0-1", 60),
                ex("Uginanie ramion z hantlami", 3, 12..15, eq.pairMaxKg, "3-0-1", 60),
                ex("Deska (sekundy)", 3, 40..60, null, null, 45),
            )
            StrengthFocus.LOWER -> listOf(
                ex("Przysiad goblet", 4, 12..15, eq.singleMaxKg, "3-1-1", 90),
                ex("Przysiad bułgarski (na nogę)", 3, 8..12, eq.pairMaxKg, "2-1-1", 75),
                ex("Martwy ciąg rumuński z hantlami", 3, 12..15, eq.pairMaxKg, "3-1-1", 75),
                ex("Unoszenie bioder z hantlem (hip thrust)", 3, 12..15, eq.singleMaxKg, "2-1-1", 60),
                ex("Wspięcia na palce na jednej nodze", 3, 15..20, eq.pairMaxKg, "2-1-1", 45),
                ex("Dead bug (na stronę)", 3, 8..10, null, null, 45),
            )
            StrengthFocus.FULL_BODY -> listOf(
                ex("Przysiad goblet", 3, 12..15, eq.singleMaxKg, "3-1-1", 75),
                ex("Pompki", 3, 8..15, null, "2-0-1", 60),
                ex("Wiosłowanie jednorącz w opadzie (na stronę)", 3, 10..12, eq.singleMaxKg, "2-1-1", 60),
                ex("Wykroki w tył (na nogę)", 3, 10..12, eq.pairMaxKg, "2-0-1", 60),
                ex("Wyciskanie hantli nad głowę stojąc", 3, 10..12, eq.pairMaxKg, "2-0-1", 60),
                ex("Brzuszki", 3, 20..25, null, null, 45),
            )
        }
        EquipmentKind.GYM -> when (focus) {
            StrengthFocus.UPPER -> listOf(
                ex("Wyciskanie sztangi leżąc", 3, 6..10, null, "2-1-1", 120),
                ex("Wiosłowanie sztangą w opadzie", 3, 8..10, null, "2-1-1", 120),
                ex("Wyciskanie hantli nad głowę siedząc", 3, 8..12, null, "2-0-1", 90),
                ex("Ściąganie drążka do klatki (albo podciąganie)", 3, 8..12, null, "2-1-1", 90),
                ex("Uginanie ramion ze sztangielkami", 3, 10..12, null, "3-0-1", 60),
                ex("Deska (sekundy)", 3, 40..60, null, null, 45),
            )
            StrengthFocus.LOWER -> listOf(
                ex("Przysiad ze sztangą", 3, 6..10, null, "3-1-1", 150),
                ex("Martwy ciąg rumuński", 3, 8..10, null, "3-1-1", 120),
                ex("Wypychanie na suwnicy", 3, 10..12, null, "2-1-1", 90),
                ex("Wykroki z hantlami (na nogę)", 3, 8..12, null, "2-0-1", 90),
                ex("Wspięcia na palce na maszynie", 3, 12..15, null, "2-1-1", 60),
                ex("Unoszenie nóg w zwisie", 3, 10..12, null, null, 60),
            )
            StrengthFocus.FULL_BODY -> listOf(
                ex("Przysiad ze sztangą", 3, 6..10, null, "3-1-1", 150),
                ex("Wyciskanie sztangi leżąc", 3, 6..10, null, "2-1-1", 120),
                ex("Wiosłowanie hantlą jednorącz (na stronę)", 3, 8..12, null, "2-1-1", 90),
                ex("Martwy ciąg rumuński", 3, 8..10, null, "3-1-1", 120),
                ex("Wyciskanie hantli nad głowę", 3, 8..12, null, "2-0-1", 90),
                ex("Deska (sekundy)", 3, 40..60, null, null, 45),
            )
        }
    }

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
    private fun intervals() = if (eq.kind != EquipmentKind.DUMBBELLS) bodyweightIntervals() else StrengthWorkout(
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

    private fun bodyweightIntervals() = StrengthWorkout(
        "Obwód (40 s pracy, 20 s przerwy, 4–5 rund)",
        listOf(
            ex("Przysiad z wyskokiem", 5, 10..15, null, null, 20),
            ex("Pompki", 5, 8..12, null, null, 20),
            ex("Wykroki naprzemienne", 5, 12..16, null, null, 20),
            ex("Burpees", 5, 8..10, null, null, 20),
            ex("Mountain climbers", 5, 20..30, null, null, 60),
        ),
    )
}
