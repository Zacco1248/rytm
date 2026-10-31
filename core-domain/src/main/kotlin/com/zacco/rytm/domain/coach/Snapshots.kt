package com.zacco.rytm.domain.coach

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
    /** Ile kcal z treningów doliczono do celu (0,75 × kcal z zegarka). */
    val trainingKcal: Int,
    val dayOff: Boolean,
    /** Porcje alkoholu: wczoraj (liczone do dzisiejszej gotowości) i dziś. */
    val alcoholYesterday: Int,
    val alcoholToday: Int,
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
)

enum class WeightPace { UNKNOWN, TOO_FAST, OK, GAINING }
