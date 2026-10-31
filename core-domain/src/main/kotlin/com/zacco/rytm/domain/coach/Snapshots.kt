package com.zacco.rytm.domain.coach

import com.zacco.rytm.domain.habits.Habit
import com.zacco.rytm.domain.load.LoadSummary
import com.zacco.rytm.domain.model.Activity
import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.model.DailyCheckIn
import com.zacco.rytm.domain.model.NutritionDay
import com.zacco.rytm.domain.model.PlannedDay
import com.zacco.rytm.domain.nutrition.NutritionWeekReport
import com.zacco.rytm.domain.profile.NutritionTargets
import com.zacco.rytm.domain.readiness.Readiness
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Ekran „Dziś”. */
data class TodaySnapshot(
    val date: LocalDate,
    val readiness: Readiness,
    /** Ile dni baseline'u tętna już mamy (do chipa „Kalibracja X/14”). */
    val baselineDays: Int,
    val checkIn: DailyCheckIn?,
    val plan: PlannedDay,
    val load: LoadSummary,
    val bedtime: LocalTime,
    val nutrition: NutritionDay?,
    val nutritionTargets: NutritionTargets,
    val habits: List<HabitStatus>,
    val steps: Int?,
    val lastSync: Instant?,
)

data class HabitStatus(val habit: Habit, val value: Int?, val done: Boolean, val streak: Int)

/** Ekran „Tydzień”. */
data class WeekSnapshot(
    val monday: LocalDate,
    val today: LocalDate,
    val days: List<WeekDay>,
    val load: LoadSummary,
    val strengthDone: Int,
    val strengthRequired: Int,
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
    val weekReport: NutritionWeekReport,
)

enum class WeightPace { UNKNOWN, TOO_FAST, OK, GAINING }
