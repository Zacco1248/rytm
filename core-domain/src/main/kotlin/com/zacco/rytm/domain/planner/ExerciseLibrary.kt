package com.zacco.rytm.domain.planner

import com.zacco.rytm.domain.exercise.ExerciseGuide
import com.zacco.rytm.domain.exercise.Muscle
import com.zacco.rytm.domain.model.DayKind
import com.zacco.rytm.domain.model.Intensity
import com.zacco.rytm.domain.model.PlannedDay
import com.zacco.rytm.domain.model.PlannedExercise
import com.zacco.rytm.domain.model.StrengthFocus
import com.zacco.rytm.domain.model.StrengthWorkout
import com.zacco.rytm.domain.profile.Goal

enum class EquipmentKind { NONE, BANDS, DUMBBELLS, GYM }

/** Własna sesja mobilności: wyłączone domyślne ćwiczenia i dopisane własne (nazwy). */
data class MobilityPrefs(val off: List<String> = emptyList(), val custom: List<String> = emptyList())

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
    /** Najmniejsza zmiana ciężaru; null: 1 kg przy hantlach, 2,5 kg na siłowni. */
    val stepKgOverride: Double? = null,
    /** Drążek w domu: podciąganie i unoszenie kolan w zwisie zamiast zastępczych ćwiczeń. */
    val pullUpBar: Boolean = false,
) {
    val stepKg: Double get() = stepKgOverride ?: if (kind == EquipmentKind.GYM) 2.5 else 1.0

    /** Najlżejsze, co da się ustawić (regulowane hantle zaczynają się od 2 kg). */
    val minKg: Double get() = if (kind == EquipmentKind.GYM) 2.5 else 2.0
}

/** Konkretne ćwiczenia do sesji z planu, dobrane do sprzętu i celu. Ciężar jest tu górną granicą sprzętu; dzisiejszy dobiera [com.zacco.rytm.domain.workout.Progression] z dziennika serii. */
class ExerciseLibrary(
    private val eq: Equipment = Equipment(),
    private val goal: Goal = Goal.RECOMP,
    private val focus: Set<Muscle> = emptySet(),
    private val mobility: MobilityPrefs = MobilityPrefs(),
) {

    fun workoutFor(day: PlannedDay): StrengthWorkout? = when (day.kind) {
        DayKind.STRENGTH -> strength(day.focus ?: StrengthFocus.FULL_BODY, day.intensity, block(day.date))
        DayKind.MOBILITY -> mobility()
        DayKind.FLEX -> if (day.title.startsWith("Interwały")) intervals() else null
        DayKind.REST -> if (day.title.contains("mobilność", ignoreCase = true)) mobility() else null
        DayKind.CARDIO -> null
        else -> null
    }

    private fun strength(focus: StrengthFocus, intensity: Intensity, block: Int): StrengthWorkout {
        var exercises = forGoal(variant(withBar(base(focus), focus), block))
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
        exercises = withFocus(exercises, focus)
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

    /**
     * Blok treningowy (mezocykl) 4 tygodni. Ćwiczenia zostają te same przez cały blok, żeby dało się
     * progresować ciężar i powtórzenia, a w kolejnym bloku zmieniają się warianty (inny bodziec, mniej nudy).
     */
    private fun block(date: java.time.LocalDate): Int =
        ((date.with(java.time.DayOfWeek.MONDAY).toEpochDay() / 7) / BLOCK_WEEKS % 2).toInt()

    /** W co drugim bloku warianty głównych ćwiczeń (ten sam wzorzec ruchu, inny bodziec). */
    private fun variant(list: List<PlannedExercise>, block: Int): List<PlannedExercise> =
        if (block == 0) list else list.map { e -> VARIANTS[ExerciseGuide.key(e.name)]?.let { v -> e.copy(name = v) } ?: e }

    /** Drążek w domu: pionowe przyciąganie zamiast zastępczego wiosłowania, zwis zamiast deski lub dead buga. */
    private fun withBar(list: List<PlannedExercise>, focus: StrengthFocus): List<PlannedExercise> {
        if (!eq.pullUpBar || eq.kind == EquipmentKind.GYM) return list
        val pull = ex("Podciąganie na drążku (albo negatywy 3–5 s)", 3, 4..8, null, "2-1-3", 120)
        val hang = ex("Unoszenie kolan w zwisie", 3, 8..12, null, "2-1-2", 60)
        var out = list.map { e ->
            when (ExerciseGuide.key(e.name)) {
                "wiosłowanie odwrócone pod stołem" -> pull
                "deska", "dead bug", "brzuszki", "superman" -> hang
                else -> e
            }
        }.distinctBy { ExerciseGuide.key(it.name) }
        if (focus != StrengthFocus.LOWER && out.none { it.name == pull.name }) out = out.take(1) + pull + out.drop(1)
        return out
    }

    /**
     * Priorytetowe partie z profilu: gdy pasują do sesji (góra, dół, całe ciało), dostają serię więcej
     * w ćwiczeniu, które już je trenuje, albo dodatkowe ćwiczenie na koniec (1 przy krótkich sesjach, max 2).
     */
    private fun withFocus(list: List<PlannedExercise>, session: StrengthFocus): List<PlannedExercise> {
        val fits = focus.filter { m ->
            when (session) {
                StrengthFocus.UPPER -> m !in LOWER_MUSCLES
                StrengthFocus.LOWER -> m in LOWER_MUSCLES || m == Muscle.ABS
                StrengthFocus.FULL_BODY -> true
            }
        }
        if (fits.isEmpty()) return list
        val limit = if (eq.sessionMinutes <= 30) 1 else 2
        var out = list
        var added = 0
        for (m in fits) {
            if (added >= limit) break
            val accessory = accessoryFor(m)
            val i = out.indexOfFirst { ExerciseGuide.key(it.name) == ExerciseGuide.key(accessory.name) }
            out = if (i >= 0) {
                out.toMutableList().also { it[i] = it[i].copy(sets = it[i].sets + 1, note = it[i].note ?: "Priorytet: ${label(m)}") }
            } else {
                out + accessory.copy(note = "Priorytet: ${label(m)}")
            }
            added++
        }
        return out
    }

    private fun accessoryFor(m: Muscle): PlannedExercise {
        val gym = eq.kind == EquipmentKind.GYM
        val db = eq.kind == EquipmentKind.DUMBBELLS
        val bands = eq.kind == EquipmentKind.BANDS || eq.bands
        val bar = eq.pullUpBar && !gym
        return when (m) {
            Muscle.ABS -> if (bar) ex("Unoszenie kolan w zwisie", 3, 8..12, null, "2-1-2", 60) else if (gym) ex("Unoszenie nóg w zwisie", 3, 8..12, null, null, 60) else ex("Dead bug (na stronę)", 3, 8..12, null, "3-1-1", 45)
            Muscle.BICEPS -> when {
                gym -> ex("Uginanie ramion ze sztangielkami", 3, 8..12, null, null, 60)
                db -> ex("Uginanie ramion z hantlami", 3, 10..15, eq.pairMaxKg, "2-0-3", 60)
                bands -> ex("Uginanie ramion z gumą", 3, 12..20, null, "2-0-3", 45)
                bar -> ex("Podciąganie podchwytem (albo negatywy)", 3, 4..8, null, "2-1-3", 90)
                else -> ex("Wiosłowanie odwrócone pod stołem", 3, 8..12, null, "2-1-2", 60)
            }
            Muscle.TRICEPS -> ex("Dipy na krześle", 3, 8..15, null, "3-0-1", 60)
            Muscle.CHEST -> if (gym) ex("Wyciskanie sztangi leżąc", 3, 8..10, null, null, 120) else ex("Pompki", 3, 8..15, null, "3-0-1", 60)
            Muscle.SHOULDERS -> when {
                gym -> ex("Wyciskanie hantli nad głowę siedząc", 3, 8..12, null, null, 90)
                db -> ex("Wyciskanie hantli nad głowę stojąc", 3, 10..15, eq.pairMaxKg, "3-0-1", 60)
                bands -> ex("Wyciskanie gumą nad głowę", 3, 12..20, null, null, 45)
                else -> ex("Pompki w pozycji piki (na barki)", 3, 6..12, null, "3-0-1", 60)
            }
            Muscle.BACK -> when {
                bar -> ex("Podciąganie na drążku (albo negatywy 3–5 s)", 3, 4..8, null, "2-1-3", 120)
                gym -> ex("Ściąganie drążka do klatki (albo podciąganie)", 3, 8..12, null, null, 90)
                db -> ex("Wiosłowanie jednorącz w opadzie (na stronę)", 3, 10..15, eq.singleMaxKg, "2-1-2", 60)
                bands -> ex("Wiosłowanie gumą", 3, 12..20, null, "2-1-2", 45)
                else -> ex("Wiosłowanie odwrócone pod stołem", 3, 8..12, null, "2-1-2", 60)
            }
            Muscle.GLUTES -> if (db) ex("Unoszenie bioder z hantlem (hip thrust)", 3, 12..15, eq.singleMaxKg, "2-1-2", 60)
                else ex("Most biodrowy na jednej nodze (na nogę)", 3, 10..15, null, "2-1-2", 45)
            Muscle.LEGS -> if (gym) ex("Wypychanie na suwnicy", 3, 10..12, null, null, 90)
                else ex("Przysiad bułgarski na krześle (na nogę)", 3, 8..12, if (db) eq.pairMaxKg else null, "3-1-1", 60)
            Muscle.CALVES -> if (gym) ex("Wspięcia na palce na maszynie", 3, 12..15, null, null, 45)
                else ex("Wspięcia na palce na jednej nodze", 3, 12..20, null, "2-1-2", 30)
        }
    }

    private fun label(m: Muscle) = when (m) {
        Muscle.ABS -> "brzuch"
        Muscle.CHEST -> "klatka"
        Muscle.BACK -> "plecy"
        Muscle.SHOULDERS -> "barki"
        Muscle.BICEPS -> "biceps"
        Muscle.TRICEPS -> "triceps"
        Muscle.GLUTES -> "pośladki"
        Muscle.LEGS -> "nogi"
        Muscle.CALVES -> "łydki"
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

    /** Sesja mobilności: domyślne ćwiczenia bez wyłączonych w profilu, plus własne (2 serie po ok. 45 s). */
    private fun mobility(): StrengthWorkout {
        val off = mobility.off.map(ExerciseGuide::key).toSet()
        val list = MOBILITY_DEFAULT.filter { ExerciseGuide.key(it.name) !in off } +
            mobility.custom.filter { it.isNotBlank() }.map { ex(it.trim(), 2, 45..60, null, null, 0) }
        return StrengthWorkout("Mobilność", list.ifEmpty { MOBILITY_DEFAULT })
    }

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

    companion object {
        private val LOWER_MUSCLES = setOf(Muscle.GLUTES, Muscle.LEGS, Muscle.CALVES)
        private const val BLOCK_WEEKS = 4

        /** Warianty na drugi blok: klucz ćwiczenia → nazwa wariantu. */
        private val VARIANTS: Map<String, String> = mapOf(
            "przysiad" to "Przysiad z pauzą",
            "przysiad goblet" to "Przysiad goblet z pauzą",
            "przysiad ze sztangą" to "Przysiad przedni ze sztangą",
            "wykroki w tył" to "Wykroki chodzone (na nogę)",
            "martwy ciąg rumuński z hantlami" to "Martwy ciąg rumuński na jednej nodze (na nogę)",
            "martwy ciąg rumuński" to "Hip thrust ze sztangą",
            "wypychanie na suwnicy" to "Uginanie nóg na maszynie",
            "wyciskanie hantli leżąc na podłodze" to "Wyciskanie hantli jednorącz na podłodze (na stronę)",
            "wyciskanie sztangi leżąc" to "Wyciskanie hantli na ławce skośnej",
            "wyciskanie hantli nad głowę stojąc" to "Wyciskanie Arnolda",
            "wiosłowanie jednorącz w opadzie" to "Wiosłowanie oburącz w opadzie",
            "wiosłowanie sztangą w opadzie" to "Wiosłowanie na wyciągu siedząc",
            "uginanie ramion z hantlami" to "Uginanie młotkowe",
            "face pull gumą" to "Band pull-apart",
            "dipy na krześle" to "Pompki diamentowe",
            "deska" to "Deska boczna (sekundy na stronę)",
            "brzuszki" to "Odwrotne brzuszki",
            "dead bug" to "Bird dog (na stronę)",
        )

        /** Domyślne ćwiczenia sesji mobilności (do włączania i wyłączania w profilu). */
        val MOBILITY_DEFAULT: List<PlannedExercise> = listOf(
            PlannedExercise("Kot–krowa", 2, 10..10, null, null, 0),
            PlannedExercise("Rotacje odcinka piersiowego w klęku (na stronę)", 2, 8..10, null, null, 0),
            PlannedExercise("Rozciąganie zginaczy bioder w wykroku (sekundy na stronę)", 2, 45..60, null, null, 0),
            PlannedExercise("Głęboki przysiad z podparciem (sekundy)", 2, 45..60, null, null, 0),
            PlannedExercise("Rozciąganie łydek przy ścianie (sekundy na stronę)", 2, 45..45, null, null, 0),
            PlannedExercise("Krążenia barków z kijem lub ręcznikiem", 2, 10..12, null, null, 0),
        )
    }

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
