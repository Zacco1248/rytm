package com.zacco.rytm.domain.coach

import com.zacco.rytm.domain.profile.Goal

import com.zacco.rytm.domain.habits.Habit
import com.zacco.rytm.domain.load.LoadSummary
import com.zacco.rytm.domain.model.Activity
import com.zacco.rytm.domain.model.BodyMetrics
import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.model.DailyCheckIn
import com.zacco.rytm.domain.model.DailyMetrics
import com.zacco.rytm.domain.model.NutritionDay
import com.zacco.rytm.domain.model.PlannedDay
import com.zacco.rytm.domain.nutrition.NutritionWeekReport
import com.zacco.rytm.domain.profile.NutritionTargets
import com.zacco.rytm.domain.readiness.Readiness
import com.zacco.rytm.domain.workout.ExerciseProgress
import com.zacco.rytm.domain.workout.SetLog
import java.time.Instant
import com.zacco.rytm.domain.readiness.CalibrationStatus
import java.time.LocalDate
import java.time.LocalTime

/** Ekran „Dziś”. */
data class TodaySnapshot(
    val date: LocalDate,
    val readiness: Readiness,
    /** Ile dni baseline'u tętna już mamy (do chipa „Kalibracja X/14”). */
    val baselineDays: Int,
    /** Co weszło do kalibracji, a czego brakowało (po dotknięciu chipa). */
    val calibration: CalibrationStatus? = null,
    val checkIn: DailyCheckIn?,
    /** Ostatnia noc (sen, tętno spoczynkowe) i kroki z dziś. */
    val night: DailyMetrics?,
    /** Średnie z poprzednich 7 dni, do porównania „52 (śr. 54)”. */
    val restingHrAvg: Double?,
    /** Tętno spoczynkowe dnia: z nocy, a gdy jej brak, zapasowe z dnia ([restingHrFromDay]). */
    val restingHr: Int?,
    val restingHrFromDay: Boolean,
    val hrvAvg: Double?,
    val plan: PlannedDay,
    val tomorrow: PlannedDay,
    val load: LoadSummary,
    val bedtime: LocalTime,
    val nutrition: NutritionDay?,
    /** Cel dnia z doliczonym treningiem ([trainingKcal]). */
    val nutritionTargets: NutritionTargets,
    /** Ile kcal z treningów doliczono do celu ([trainingKcalFactor] × kcal z zegarka). */
    val trainingKcal: Int,
    val trainingKcalFactor: Double = 0.75,
    val dayOff: Boolean,
    /** Porcje alkoholu: wczoraj (liczone do dzisiejszej gotowości) i dziś. */
    val alcoholYesterday: Int,
    val alcoholToday: Int,
    val habits: List<HabitStatus>,
    /** Wszystkie nawyki z tygodniem (zakładka Nawyki), także te już zaliczone w tym tygodniu. */
    val habitsAll: List<HabitStatus> = emptyList(),
    val steps: Int?,
    val lastSync: Instant?,
    /** Serie zapisane dziś (trening z zegarka albo z telefonu). */
    val setsToday: List<SetLog> = emptyList(),
    /** Wszystkie aktywności dnia (z Health Connect i ręczne), także spacery spoza planu. */
    val activitiesToday: List<Activity> = emptyList(),
    /** Aktywności, którym kcal oszacował Rytm (z RPE i czasu), bo zegarek ich nie podał. */
    val estimatedKcalIds: Set<String> = emptySet(),
    /** Plan dnia zmieniony w rozmowie z trenerem. */
    val planChanged: Boolean = false,
    /** Ten tydzień lżej (deload) i dlaczego. */
    val deload: Deload? = null,
) {
    /** Kcal z aktywności dnia (bez doliczania mnożnika). */
    val activityKcal: Int get() = activitiesToday.sumOf { it.kcal ?: 0 }
}

data class HabitStatus(
    val habit: Habit,
    val value: Int?,
    val done: Boolean,
    val streak: Int,
    /** Bieżący tydzień od poniedziałku: true zrobione, false nie, null dzień przyszły albo poza planem nawyku. */
    val week: List<Boolean?> = emptyList(),
    val weekDone: Int = 0,
    val weekRequired: Int = 0,
)

/** Ekran „Tydzień”. */
data class WeekSnapshot(
    val monday: LocalDate,
    val today: LocalDate,
    val days: List<WeekDay>,
    val load: LoadSummary,
    val strengthDone: Int,
    val strengthRequired: Int,
    val deload: Deload? = null,
)

data class WeekDay(
    val plan: PlannedDay,
    val activities: List<Activity>,
    /** Suma sRPE wykonanych aktywności (z szacunkiem z tętna). */
    val actualLoad: Int,
    /** Dla dni przyszłych: przybliżone obciążenie z planu. */
    val plannedLoad: Int,
    /** Obciążenie dnia w podziale na rodzaj, do słupków. */
    val loadByType: Map<ActivityType, Int>,
    val cancelled: Boolean,
    val dayOff: Boolean = false,
    /** Dzień minął, był w planie trening, a żadnej aktywności nie ma. Tylko informacja, bez kary. */
    val missed: Boolean = false,
) {
    val date: LocalDate get() = plan.date
}

/** Ekran „Sylwetka”: ostatnie 4 tygodnie. */
data class BodySnapshot(
    val from: LocalDate,
    val to: LocalDate,
    /** Pojedyncze ważenia (blade kropki na wykresie). */
    val weighIns: List<Pair<LocalDate, Double>>,
    /** Średnia 7-dniowa dla każdego dnia, w którym da się ją policzyć. */
    val weightAvg7: List<Pair<LocalDate, Double>>,
    val currentAvg7: Double?,
    /** Tempo z różnicy średnich 7-dniowych z ostatnich 2 tygodni (kg/tydz.). */
    val kgPerWeek: Double?,
    val pace: WeightPace,
    val protein: List<Pair<LocalDate, Double>>,
    val proteinTargetG: Int,
    val proteinDaysInTarget: Int,
    val avgKcal: Int?,
    val latestBodyFatPct: Double?,
    /** Ostatni pełny pomiar składu ciała (np. ze zrzutu Xiaomi Home). */
    val latestComposition: BodyMetrics?,
    val weekReport: NutritionWeekReport,
    /** Ostatnie sesje ćwiczeń z dziennika serii. */
    val strength: List<ExerciseProgress> = emptyList(),
    /** Dni z wpisem żywienia i celem tego dnia (z doliczonym treningiem), do karty „Makro”. */
    val macros: List<MacroDay> = emptyList(),
    /** Cel osoby: od niego zależy ocena tempa i opisy na ekranie. */
    val goal: Goal = Goal.RECOMP,
    /** Korekta celu kcal z pętli jedzenie–waga i kiedy następny przegląd. */
    val kcalAdjust: Int = 0,
    val kcalNextReview: java.time.LocalDate? = null,
    val goals: List<com.zacco.rytm.domain.goals.GoalProgress> = emptyList(),
)

data class MacroDay(val nutrition: NutritionDay, val target: NutritionTargets) {
    val date: LocalDate get() = nutrition.date

    fun actual(m: Macro): Double = when (m) {
        Macro.KCAL -> nutrition.kcal.toDouble()
        Macro.PROTEIN -> nutrition.proteinG
        Macro.FAT -> nutrition.fatG
        Macro.CARBS -> nutrition.carbsG
    }

    fun target(m: Macro): Int = when (m) {
        Macro.KCAL -> target.kcal
        Macro.PROTEIN -> target.proteinG
        Macro.FAT -> target.fatG
        Macro.CARBS -> target.carbsG
    }

    /** W celu: białko co najmniej 90% celu, reszta w ±10%. */
    fun inTarget(m: Macro): Boolean {
        val t = target(m).takeIf { it > 0 } ?: return false
        val r = actual(m) / t
        return if (m == Macro.PROTEIN) r >= 0.9 else r in 0.9..1.1
    }
}

enum class Macro { KCAL, PROTEIN, FAT, CARBS }

/** TOO_FAST = za szybko w dół, TOO_FAST_UP = za szybko w górę (masa), STALLED = stoi, choć cel wymaga zmiany. */
enum class WeightPace { UNKNOWN, TOO_FAST, OK, GAINING, STALLED, TOO_FAST_UP }
