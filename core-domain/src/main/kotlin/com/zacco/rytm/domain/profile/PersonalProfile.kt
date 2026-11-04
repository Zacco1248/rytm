package com.zacco.rytm.domain.profile

import com.zacco.rytm.domain.exercise.Muscle
import com.zacco.rytm.domain.habits.DefaultHabits
import com.zacco.rytm.domain.habits.Habit
import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.planner.CardioKind
import com.zacco.rytm.domain.planner.Equipment
import com.zacco.rytm.domain.planner.EquipmentKind
import com.zacco.rytm.domain.planner.MobilityPrefs
import com.zacco.rytm.domain.planner.PlannerConfig
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

enum class Sex { MALE, FEMALE }

/** Ruch poza treningami (praca, dojazdy, spacery). Treningi doliczamy osobno z zegarka. */
enum class ActivityLevel { SEDENTARY, LIGHT, MODERATE, ACTIVE }

enum class Goal { RECOMP, FAT_LOSS, MUSCLE_GAIN, STRENGTH, ENDURANCE, HEALTH }

/** Co osoba chce robić (krok 1 kreatora). Plan układa się wokół tego wyboru. */
enum class ActivityChoice { STRENGTH_HOME, GYM, WALK, RUN, BIKE, SWIM, SPORT, YOGA }

/** Stały sport w tygodniu, np. siatkówka we wtorki. */
@Serializable
data class FixedSportEntry(
    /** 1 = poniedziałek … 7 = niedziela. */
    val day: Int,
    val name: String,
    val type: ActivityType = ActivityType.OTHER,
)

@Serializable
data class PersonalTrainingEntry(val day: Int, val until: String? = null)

/**
 * Profil wpisywany w kreatorze i na ekranie „Profil”. Każda osoba ma swój, zapisany lokalnie na telefonie.
 * Z niego liczymy [UserProfile] (cele, plan, sprzęt), zamiast trzymać dane jednej osoby w kodzie.
 */
@Serializable
data class PersonalProfile(
    val name: String = "",
    val sex: Sex = Sex.MALE,
    val birthYear: Int? = null,
    val heightCm: Int = 175,
    val weightKg: Double = 75.0,
    val activity: ActivityLevel = ActivityLevel.SEDENTARY,
    val goal: Goal = Goal.HEALTH,
    /** Cel własnymi słowami, np. „schudnąć 5 kg do wakacji i przebiec 10 km”. Trafia do AI i na ekran. */
    val goalNote: String = "",
    val equipment: EquipmentKind = EquipmentKind.NONE,
    val dumbbellPairMaxKg: Double = 8.0,
    val dumbbellSingleMaxKg: Double = 14.0,
    val bands: Boolean = false,
    val sessionMinutes: Int = 45,
    /** Skok ciężaru hantli/sztangi; null: 1 kg w domu, 2,5 kg na siłowni. */
    val weightStepKg: Double? = null,
    /** Dni, w które możesz trenować siłowo (1–7), w kolejności preferencji. Nacisk sesji dobiera Rytm. */
    val strengthDays: List<Int> = listOf(1, 3, 5),
    /** Ile sesji siłowych w tygodniu (bez treningu personalnego). */
    val strengthPerWeek: Int = 2,
    val fixedSports: List<FixedSportEntry> = emptyList(),
    val personalTraining: PersonalTrainingEntry? = null,
    val restDay: Int? = 7,
    val stepsTarget: Int = 8000,
    /** Pora zaśnięcia: od–do, w minutach od północy (może przekraczać 24 h, np. 24:30 = 1470). */
    val sleepOnsetFromMin: Int = 23 * 60,
    val sleepOnsetToMin: Int = 24 * 60,
    val sleepHoursMin: Int = 7,
    val sleepHoursMax: Int = 9,
    /** Własne cele (np. z Fitatu albo od dietetyka). Null: liczone z profilu. */
    val customTargets: CustomTargets? = null,
    /** Stare pole (zawsze 0,75); zostaje tylko po to, żeby wczytać zapisane profile. */
    val trainingKcalFactor: Double = 0.75,
    /** Własny mnożnik kcal z treningu; null: zależnie od celu ([defaultKcalFactor]). */
    val kcalFactorOverride: Double? = null,
    /** Wybrane aktywności; null: stary profil, czyli siła ze sprzętem z profilu. */
    val activities: List<ActivityChoice>? = null,
    /** Bieg i marsz na bieżni zamiast na zewnątrz. */
    val treadmill: Boolean = false,
    /** Partie, na których chcesz się skupić (brzuch, biceps…). */
    val focus: List<Muscle> = emptyList(),
    /** Wyłączone domyślne ćwiczenia mobilności (nazwy). */
    val mobilityOff: List<String> = emptyList(),
    /** Własne ćwiczenia mobilności, np. „Gołąb (pigeon) na stronę”. */
    val mobilityCustom: List<String> = emptyList(),
    /** Tętno maksymalne z testu; bez niego szacunek z wieku. */
    val maxHr: Int? = null,
    /** Hantle w domu; null: stary profil, wtedy z [equipment]. */
    val ownsDumbbells: Boolean? = null,
    val pullUpBar: Boolean = false,
    val indoorBike: Boolean = false,
    /** Dzień założenia profilu w kreatorze (ISO); od niego liczy się plan. */
    val startedOn: String? = null,
    /** Korekta celu kcal z pętli jedzenie–waga (co 2 tygodnie), dodawana do celu z wzoru albo z Fitatu. */
    val kcalAdjust: Int = 0,
    /** Ostatni przegląd celu kcal (ISO); null: od [startedOn]. */
    val kcalReviewedOn: String? = null,
    /** Cele z kamieniami milowymi (waga, powtórzenia, ciężar). */
    val goals: List<com.zacco.rytm.domain.goals.UserGoal> = emptyList(),
    /** Własna lista nawyków; null: domyślne trzy. Pusta lista: wszystkie usunięte. */
    val habitList: List<com.zacco.rytm.domain.habits.HabitSpec>? = null,
) {
    val hasDumbbells: Boolean get() = ownsDumbbells ?: (equipment == EquipmentKind.DUMBBELLS)
    val hasGym: Boolean get() = equipment == EquipmentKind.GYM
    val hasBands: Boolean get() = bands || equipment == EquipmentKind.BANDS

    /** Lista „co masz” → główny rodzaj sprzętu do planu: siłownia, potem hantle, potem gumy. */
    fun withGear(dumbbells: Boolean = hasDumbbells, bands: Boolean = hasBands, gym: Boolean = hasGym): PersonalProfile = copy(
        ownsDumbbells = dumbbells,
        bands = bands,
        equipment = when {
            gym -> EquipmentKind.GYM
            dumbbells -> EquipmentKind.DUMBBELLS
            bands -> EquipmentKind.BANDS
            else -> EquipmentKind.NONE
        },
    )

    fun age(today: LocalDate): Int? = birthYear?.let { today.year - it }

    /**
     * Jaką część kcal treningu z zegarka doliczać do celu. Zegarki zawyżają spalanie o ok. 20–30%,
     * więc przy redukcji bierzemy połowę, przy masie całość. Przy celach z Fitatu zostaje 0,75 jak w Fitatu.
     */
    val kcalFactor: Double get() = kcalFactorOverride ?: defaultKcalFactor(goal, customTargets != null)

    fun toUserProfile(today: LocalDate): UserProfile = UserProfile(
        weightKg = weightKg,
        heightCm = heightCm,
        nutrition = (customTargets?.toTargets() ?: NutritionCalculator.targets(this, today)).adjusted(kcalAdjust),
        kcalAdjust = kcalAdjust,
        goals = goals,
        kcalReviewedOn = (kcalReviewedOn ?: startedOn)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        sleep = SleepTarget(
            onsetFrom = timeOf(sleepOnsetFromMin),
            onsetTo = timeOf(sleepOnsetToMin),
            minDurationMin = sleepHoursMin * 60,
            maxDurationMin = sleepHoursMax * 60,
        ),
        // Wzór Tanaki: 208 − 0,7 × wiek.
        maxHr = maxHr ?: age(today)?.let { (208 - 0.7 * it).roundToInt() } ?: 190,
        trainingKcalFactor = kcalFactor,
        planner = plannerConfig(),
        equipment = Equipment(equipment, dumbbellPairMaxKg, dumbbellSingleMaxKg, bands || equipment == EquipmentKind.BANDS, sessionMinutes, weightStepKg, pullUpBar),
        goal = goal,
        goalNote = goalNote,
        name = name,
        focus = focus.toSet(),
        mobility = MobilityPrefs(mobilityOff, mobilityCustom),
    )

    fun habits(): List<Habit> = habitList?.map { it.toHabit(stepsTarget) } ?: DefaultHabits.withSteps(stepsTarget)

    /** Lista do edycji: zapisana albo domyślna. */
    fun habitSpecs(): List<com.zacco.rytm.domain.habits.HabitSpec> =
        habitList ?: DefaultHabits.all.map(com.zacco.rytm.domain.habits.HabitSpec::of)

    /** Siła jest w planie, gdy wybrano ją w kreatorze albo profil jest sprzed wyboru aktywności. */
    val wantsStrength: Boolean
        get() = activities == null || ActivityChoice.STRENGTH_HOME in activities || ActivityChoice.GYM in activities

    /** Rodzaje cardio w kolejności ważności (bieg, rower, pływanie, marsz). */
    val cardioKinds: List<CardioKind>
        get() = listOfNotNull(
            CardioKind.RUN.takeIf { activities?.contains(ActivityChoice.RUN) == true },
            CardioKind.BIKE.takeIf { activities?.contains(ActivityChoice.BIKE) == true },
            CardioKind.SWIM.takeIf { activities?.contains(ActivityChoice.SWIM) == true },
            CardioKind.WALK.takeIf { activities?.contains(ActivityChoice.WALK) == true },
        )

    /**
     * Podział [strengthPerWeek] (wszystkie treningi z Rytmem) na siłę i cardio. Bez siły wszystko to cardio;
     * przy obu: cel „kondycja” daje 1 siłę i resztę cardio, w innym wypadku 1 cardio od 3 sesji (2 od 5).
     * Sam marsz przy sile nie zabiera sesji, bo spacery i tak są codziennie.
     */
    fun sessionSplit(): Pair<Int, Int> {
        val total = strengthPerWeek.coerceAtLeast(0)
        val kinds = cardioKinds
        if (!wantsStrength) return 0 to if (kinds.isEmpty()) 0 else total
        if (kinds.isEmpty() || kinds == listOf(CardioKind.WALK) && goal != Goal.ENDURANCE) return total to 0
        val cardio = when {
            goal == Goal.ENDURANCE -> (total - 1).coerceAtLeast(if (total > 0) 1 else 0)
            total >= 5 -> 2
            total >= 3 -> 1
            else -> 0
        }
        return (total - cardio) to cardio
    }

    fun plannerConfig(): PlannerConfig {
        val days = strengthDays.distinct().mapNotNull { runCatching { DayOfWeek.of(it) }.getOrNull() }
        val (strength, cardio) = sessionSplit()
        return PlannerConfig(
            fixedSports = fixedSports.associate { DayOfWeek.of(it.day) to it.type },
            sportNames = fixedSports.associate { DayOfWeek.of(it.day) to it.name },
            personalTrainingDay = personalTraining?.let { DayOfWeek.of(it.day) },
            personalTrainingUntil = personalTraining?.until?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            strengthDays = days,
            strengthPerWeek = strength,
            restDay = restDay?.let { DayOfWeek.of(it) },
            minStrengthSessionsPerWeek = if (wantsStrength) minStrength(strength) else 0,
            cardioKinds = cardioKinds,
            cardioPerWeek = cardio,
            cardioMinutes = sessionMinutes,
            treadmill = treadmill,
            indoorBike = indoorBike,
            planStart = startedOn?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        )
    }

    private fun minStrength(sessions: Int): Int {
        val available = minOf(sessions, strengthDays.distinct().size) + (if (personalTraining != null) 1 else 0)
        val wanted = when (goal) {
            Goal.STRENGTH, Goal.MUSCLE_GAIN -> 3
            Goal.ENDURANCE -> 1
            else -> 2
        }
        return minOf(wanted, available).coerceAtLeast(if (available > 0) 1 else 0)
    }

    companion object {
        fun defaultKcalFactor(goal: Goal, customTargets: Boolean): Double = when {
            customTargets -> 0.75
            goal == Goal.FAT_LOSS -> 0.5
            goal == Goal.MUSCLE_GAIN -> 1.0
            goal == Goal.ENDURANCE || goal == Goal.STRENGTH -> 0.9
            else -> 0.75
        }

        private fun timeOf(min: Int): LocalTime = LocalTime.of((min / 60) % 24, min % 60)
    }
}

@Serializable
data class CustomTargets(val kcal: Int, val proteinG: Int, val fatG: Int, val carbsG: Int) {
    fun toTargets() = NutritionTargets(kcal, proteinG, fatG, carbsG)
}

/**
 * Cele żywieniowe z profilu: BMR wzorem Mifflina-St Jeora × ruch poza treningami, korekta pod cel,
 * białko w g/kg zależnie od celu, tłuszcz min. 25% kcal, reszta to węglowodany. Treningi doliczamy osobno.
 */
object NutritionCalculator {

    fun bmr(p: PersonalProfile, today: LocalDate): Double {
        val age = p.age(today) ?: 35
        val base = 10 * p.weightKg + 6.25 * p.heightCm - 5 * age
        return if (p.sex == Sex.MALE) base + 5 else base - 161
    }

    fun activityFactor(level: ActivityLevel): Double = when (level) {
        ActivityLevel.SEDENTARY -> 1.25
        ActivityLevel.LIGHT -> 1.4
        ActivityLevel.MODERATE -> 1.55
        ActivityLevel.ACTIVE -> 1.7
    }

    fun targets(p: PersonalProfile, today: LocalDate): NutritionTargets {
        val maintenance = bmr(p, today) * activityFactor(p.activity)
        val kcal = when (p.goal) {
            Goal.FAT_LOSS -> maintenance - minOf(500.0, maintenance * 0.2)
            Goal.RECOMP -> maintenance * 0.9
            Goal.MUSCLE_GAIN -> maintenance * 1.1
            else -> maintenance
        }.roundTo(10)
        val proteinPerKg = when (p.goal) {
            Goal.FAT_LOSS, Goal.RECOMP -> 2.0
            Goal.MUSCLE_GAIN, Goal.STRENGTH -> 1.8
            Goal.ENDURANCE -> 1.5
            Goal.HEALTH -> 1.4
        }
        val protein = (p.weightKg * proteinPerKg).roundToInt()
        val fat = maxOf(p.weightKg * 0.8, kcal * 0.25 / 9).roundToInt()
        val carbs = ((kcal - protein * 4 - fat * 9) / 4.0).roundToInt().coerceAtLeast(50)
        val weekly = when (p.goal) {
            Goal.FAT_LOSS -> -0.005 * p.weightKg
            Goal.RECOMP -> -0.25
            Goal.MUSCLE_GAIN -> 0.25
            else -> 0.0
        }
        return NutritionTargets(kcal, protein, fat, carbs, weeklyWeightChangeKg = weekly)
    }

    private fun Double.roundTo(step: Int): Int = ((this / step).roundToInt()) * step
}

object ProfilePresets {
    /**
     * Profil użytkownika, który używał Rytmu przed personalizacją (dane z sekcji 13 coach-projekt.md i odpowiedzi
     * z 2026-10-31). Wgrywany automatycznie przy aktualizacji, gdy w bazie są już dane, a profilu jeszcze nie ma.
     */
    val existingInstall = PersonalProfile(
        name = "Zacco",
        sex = Sex.MALE,
        heightCm = 187,
        weightKg = 80.0,
        activity = ActivityLevel.SEDENTARY,
        goal = Goal.RECOMP,
        equipment = EquipmentKind.DUMBBELLS,
        dumbbellPairMaxKg = 8.0,
        dumbbellSingleMaxKg = 14.0,
        sessionMinutes = 45,
        // Piątek zajmuje trening personalny do końca listopada, więc wtedy siła wypada w sobotę.
        strengthDays = listOf(1, 5, 6),
        strengthPerWeek = 2,
        fixedSports = listOf(
            FixedSportEntry(2, "Siatkówka", ActivityType.VOLLEYBALL),
            FixedSportEntry(3, "Squash", ActivityType.SQUASH),
            FixedSportEntry(4, "Siatkówka", ActivityType.VOLLEYBALL),
        ),
        personalTraining = PersonalTrainingEntry(5, "2026-11-30"),
        restDay = 7,
        stepsTarget = 6000,
        sleepOnsetFromMin = 24 * 60,
        sleepOnsetToMin = 25 * 60,
        sleepHoursMin = 7,
        sleepHoursMax = 8,
        customTargets = CustomTargets(2300, 161, 72, 253),
        maxHr = 190,
    )
}
