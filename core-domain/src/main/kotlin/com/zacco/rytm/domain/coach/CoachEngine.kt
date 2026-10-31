package com.zacco.rytm.domain.coach

import com.zacco.rytm.domain.habits.HabitAutoSource
import com.zacco.rytm.domain.habits.HabitLog
import com.zacco.rytm.domain.habits.HabitTracker
import com.zacco.rytm.domain.load.HrProfile
import com.zacco.rytm.domain.load.LoadCalculator
import com.zacco.rytm.domain.load.LoadSummary
import com.zacco.rytm.domain.model.Activity
import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.model.DayKind
import com.zacco.rytm.domain.model.Intensity
import com.zacco.rytm.domain.model.PlannedDay
import com.zacco.rytm.domain.nutrition.NutritionAnalyzer
import com.zacco.rytm.domain.nutrition.StrengthTrend
import com.zacco.rytm.domain.planner.ExerciseLibrary
import com.zacco.rytm.domain.planner.PlanningContext
import com.zacco.rytm.domain.planner.WeekPlanner
import com.zacco.rytm.domain.readiness.Readiness
import com.zacco.rytm.domain.readiness.ReadinessCalculator
import com.zacco.rytm.domain.readiness.ReadinessConfig
import com.zacco.rytm.domain.readiness.ReadinessInput
import com.zacco.rytm.domain.sleep.BedtimeAdvisor
import com.zacco.rytm.domain.time.RytmTime
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Składa ekrany z surowych danych: gotowość, obciążenie, plan, nawyki, sylwetka.
 * Czysta logika, więc ViewModel tylko mapuje wynik na UI.
 */
class CoachEngine(
    private val readinessConfig: ReadinessConfig = ReadinessConfig(),
) {
    /** Plan i ćwiczenia z profilu osoby (stałe sporty, dni siły, sprzęt, cel). */
    private fun plannerFor(data: CoachData) =
        WeekPlanner(data.profile.planner, ExerciseLibrary(data.profile.equipment, data.profile.goal))

    fun today(data: CoachData): TodaySnapshot {
        val load = load(data)
        val readiness = readiness(data, load)
        val context = context(data, readiness, load)
        val plan = plannerFor(data).planDay(data.today, context)
        val tomorrow = plannerFor(data).planDay(data.today.plusDays(1), context)
        val heavyToday = load.today >= HEAVY_DAY_MIN_LOAD && load.today >= load.chronicDailyAvg * HEAVY_DAY_FACTOR
        val night = data.metrics.firstOrNull { it.date == data.today }
        val steps = night?.steps
        val week = data.today.minusDays(BASELINE_DAYS)..data.today.minusDays(1)
        val previous = data.metrics.filter { it.date in week }
        val fromDay = night?.nightRestingHr == null && night?.dayRestingHr != null
        val trainingKcal = trainingKcal(data, data.today)

        return TodaySnapshot(
            date = data.today,
            readiness = readiness,
            baselineDays = restingHrHistory(data).size.coerceAtMost(CALIBRATION_DAYS),
            checkIn = data.checkIns.firstOrNull { it.date == data.today },
            night = night,
            restingHrAvg = previous.mapNotNull { if (fromDay) it.dayRestingHr else it.nightRestingHr }
                .takeIf { it.isNotEmpty() }?.average(),
            restingHr = night?.nightRestingHr ?: night?.dayRestingHr,
            restingHrFromDay = fromDay,
            hrvAvg = data.checkIns.filter { it.date in week }.mapNotNull { it.hrvRmssd }.takeIf { it.isNotEmpty() }?.average(),
            plan = plan,
            tomorrow = tomorrow,
            load = load,
            bedtime = BedtimeAdvisor.suggest(data.profile.sleep, readiness.level, tomorrow.intensity, heavyToday),
            nutrition = data.nutrition.firstOrNull { it.date == data.today },
            nutritionTargets = data.profile.nutrition.withTraining(trainingKcal),
            trainingKcal = trainingKcal,
            dayOff = data.dayNotes.any { it.date == data.today && it.dayOff },
            alcoholYesterday = alcoholOn(data, data.today.minusDays(1)),
            alcoholToday = alcoholOn(data, data.today),
            habits = habits(data, steps),
            steps = steps,
            lastSync = data.lastSync,
        )
    }

    fun week(data: CoachData): WeekSnapshot {
        val load = load(data)
        val readiness = readiness(data, load)
        val context = context(data, readiness, load)
        val monday = data.today.with(DayOfWeek.MONDAY)
        val plan = plannerFor(data).planWeek(data.today, context)
        val hr = hrProfile(data)

        val days = plan.map { day ->
            val acts = data.activities.filter { it.localDate() == day.date }.sortedBy { it.start }
            val byType = acts.groupBy { it.type }
                .mapValues { (_, list) -> list.sumOf { LoadCalculator.effectiveLoad(it, hr) ?: 0 } }
                .filterValues { it > 0 }
            WeekDay(
                plan = day,
                activities = acts,
                actualLoad = byType.values.sum(),
                plannedLoad = if (day.date >= data.today) estimatePlannedLoad(day) else 0,
                loadByType = byType,
                cancelled = day.date in data.cancelled,
                dayOff = data.dayNotes.any { it.date == day.date && it.dayOff },
                missed = day.date < data.today && acts.isEmpty() && day.kind in PLANNED_TRAINING &&
                    day.date !in data.cancelled,
            )
        }
        val strengthDone = data.activities.count {
            it.localDate() in monday..monday.plusDays(6) &&
                (it.type == ActivityType.STRENGTH || it.type == ActivityType.PERSONAL_TRAINING)
        }
        return WeekSnapshot(monday, data.today, days, load, strengthDone, data.profile.planner.minStrengthSessionsPerWeek)
    }

    fun body(data: CoachData, days: Long = 28): BodySnapshot {
        val from = data.today.minusDays(days - 1)
        val range = from..data.today
        val weighIns = data.weighIns.filter { it.date in range && it.weightKg != null }
            .sortedBy { it.date }
            .map { it.date to it.weightKg!! }
        val avg = generateSequence(from) { it.plusDays(1) }.takeWhile { it <= data.today }
            .mapNotNull { d -> NutritionAnalyzer.weightAvg7(data.weighIns, d)?.let { d to it } }
            .toList()
        val current = NutritionAnalyzer.weightAvg7(data.weighIns, data.today)
        val twoWeeksAgo = NutritionAnalyzer.weightAvg7(data.weighIns, data.today.minusDays(14))
        val kgPerWeek = if (current != null && twoWeeksAgo != null) (current - twoWeeksAgo) / 2 else null

        val nutrition = data.nutrition.filter { it.date in range }.sortedBy { it.date }
        val target = data.profile.nutrition.proteinG
        return BodySnapshot(
            from = from,
            to = data.today,
            weighIns = weighIns,
            weightAvg7 = avg,
            currentAvg7 = current,
            kgPerWeek = kgPerWeek,
            pace = paceOf(kgPerWeek),
            protein = nutrition.map { it.date to it.proteinG },
            proteinTargetG = target,
            proteinDaysInTarget = nutrition.count { n ->
                n.proteinG >= data.profile.nutrition.withTraining(trainingKcal(data, n.date)).proteinG * PROTEIN_IN_TARGET
            },
            avgKcal = nutrition.takeIf { it.isNotEmpty() }?.map { it.kcal }?.average()?.toInt(),
            latestBodyFatPct = data.weighIns.filter { it.bodyFatPct != null }.maxByOrNull { it.date }?.bodyFatPct,
            latestComposition = data.weighIns.filter { it.composition?.isEmpty == false }.maxByOrNull { it.date },
            weekReport = NutritionAnalyzer.weekly(
                weekEnd = data.today,
                nutrition = data.nutrition,
                body = data.weighIns,
                // Trend siły przyjdzie z dziennika serii (faza 2).
                strengthTrend = StrengthTrend.UNKNOWN,
                targets = data.profile.nutrition,
            ),
        )
    }

    // --- składanie ---

    fun load(data: CoachData): LoadSummary =
        LoadCalculator.summary(LoadCalculator.dailyLoads(data.activities, hrProfile(data)), data.today)

    fun readiness(data: CoachData, load: LoadSummary = load(data)): Readiness {
        val night = data.metrics.firstOrNull { it.date == data.today }
        val previous = data.metrics.filter { it.date < data.today && it.date >= data.today.minusDays(BASELINE_DAYS) }
        val checkIn = data.checkIns.firstOrNull { it.date == data.today }
        val hrvHistory = data.checkIns
            .filter { it.date < data.today && it.date >= data.today.minusDays(BASELINE_DAYS) }
            .mapNotNull { it.hrvRmssd }
        val input = ReadinessInput(
            date = data.today,
            sleepMin = night?.sleepMin,
            sleepStart = night?.sleepStart,
            previousSleepStarts = previous.mapNotNull { it.sleepStart },
            nightRestingHr = night?.nightRestingHr,
            restingHrBaseline = previous.mapNotNull { it.nightRestingHr },
            dayRestingHr = night?.dayRestingHr,
            dayRestingHrBaseline = previous.mapNotNull { it.dayRestingHr },
            alcoholDrinks = alcoholOn(data, data.today.minusDays(1)),
            hrvRmssd = checkIn?.hrvRmssd,
            hrvBaseline = hrvHistory,
            checkIn = checkIn,
            load = load,
        )
        return ReadinessCalculator.compute(input, readinessConfig)
    }

    private fun context(data: CoachData, readiness: Readiness, load: LoadSummary) = PlanningContext(
        today = data.today,
        readiness = readiness,
        load = load,
        activities = data.activities.filter { it.localDate() >= data.today.with(DayOfWeek.MONDAY).minusWeeks(1) },
        cancelled = data.cancelled,
        daysOff = data.dayNotes.filter { it.dayOff }.map { it.date }.toSet(),
    )

    /** Kcal z treningów dnia doliczane do celu: [UserProfile.trainingKcalFactor] × kcal z zegarka, bez spacerów. */
    fun trainingKcal(data: CoachData, date: LocalDate): Int =
        (data.activities.filter { it.localDate() == date && it.type != ActivityType.WALK }.sumOf { it.kcal ?: 0 } *
            data.profile.trainingKcalFactor).roundToInt()

    private fun alcoholOn(data: CoachData, date: LocalDate): Int =
        data.dayNotes.filter { it.date == date }.sumOf { it.alcoholDrinks }

    private fun habits(data: CoachData, steps: Int?): List<HabitStatus> {
        // Wpisy automatyczne (kroki) wyliczamy z metryk, żeby nie dublować ich w bazie.
        val autoLogs = data.habits.filter { it.autoSource == HabitAutoSource.STEPS }.flatMap { habit ->
            data.metrics.mapNotNull { m -> HabitTracker.autoLog(habit, m.date, m.steps) }
        }
        val logs: List<HabitLog> = data.habitLogs + autoLogs
        return data.habits.filter { HabitTracker.isDueOn(it, data.today, logs) }.map { habit ->
            val value = if (habit.autoSource == HabitAutoSource.STEPS) steps else
                logs.filter { it.habitId == habit.id && it.date == data.today }.maxOfOrNull { it.value }
            HabitStatus(habit, value, HabitTracker.isDone(habit, value), HabitTracker.streak(habit, logs, data.today))
        }
    }

    private fun restingHrHistory(data: CoachData): List<Int> =
        data.metrics.filter { it.date < data.today && it.date >= data.today.minusDays(CALIBRATION_DAYS.toLong()) }
            .mapNotNull { it.nightRestingHr ?: it.dayRestingHr }

    private fun hrProfile(data: CoachData): HrProfile {
        val rest = data.metrics.mapNotNull { it.nightRestingHr }.sorted()
            .let { if (it.isEmpty()) DEFAULT_RESTING_HR else it[it.size / 2] }
        return HrProfile(maxHr = data.profile.maxHr, restingHr = rest.coerceAtMost(data.profile.maxHr - 1))
    }

    private fun Activity.localDate(): LocalDate = start.atZone(RytmTime.zone).toLocalDate()

    companion object {
        const val CALIBRATION_DAYS = 14
        private const val BASELINE_DAYS = 7L
        private const val DEFAULT_RESTING_HR = 60
        private const val HEAVY_DAY_MIN_LOAD = 300
        private const val HEAVY_DAY_FACTOR = 1.5
        private const val PROTEIN_IN_TARGET = 0.95
        private val PLANNED_TRAINING = setOf(DayKind.STRENGTH, DayKind.PERSONAL_TRAINING, DayKind.FIXED_SPORT)

        /** Przybliżone sRPE dnia z planu: typowy czas × typowe RPE dla intensywności. */
        fun estimatePlannedLoad(day: PlannedDay): Int {
            val rpe = when (day.intensity) {
                Intensity.LOW -> 3
                Intensity.MODERATE -> 5
                Intensity.HIGH -> 7
            }
            val minutes = when (day.kind) {
                DayKind.FIXED_SPORT -> 90
                DayKind.PERSONAL_TRAINING, DayKind.STRENGTH -> 60
                DayKind.MOBILITY -> 25
                DayKind.FLEX -> 45
                DayKind.REST -> 0
            }
            return rpe * minutes
        }

        fun paceOf(kgPerWeek: Double?): WeightPace = when {
            kgPerWeek == null -> WeightPace.UNKNOWN
            kgPerWeek < -0.5 -> WeightPace.TOO_FAST
            kgPerWeek > 0.2 -> WeightPace.GAINING
            else -> WeightPace.OK
        }
    }
}
