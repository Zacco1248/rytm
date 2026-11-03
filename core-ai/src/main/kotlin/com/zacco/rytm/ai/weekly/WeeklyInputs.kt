package com.zacco.rytm.ai.weekly

import com.zacco.rytm.domain.coach.CoachData
import com.zacco.rytm.domain.coach.CoachEngine
import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.habits.HabitAutoSource
import com.zacco.rytm.domain.nutrition.NutritionAnalyzer
import com.zacco.rytm.domain.time.RytmTime
import com.zacco.rytm.domain.workout.StrengthHistory
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Składa [WeeklyInput] z danych: 7 dni kończących się [weekEnd] i poprzedni tydzień do porównania. */
object WeeklyInputs {
    private val pl = Locale.forLanguageTag("pl")

    fun from(data: CoachData, weekEnd: LocalDate, reflections: List<String> = emptyList()): WeeklyInput {
        val engine = CoachEngine()
        val dates = (6L downTo 0L).map { weekEnd.minusDays(it) }
        val snaps = dates.map { d -> engine.today(data.copy(today = d)) }
        val week = engine.week(data.copy(today = weekEnd))
        val nextWeek = engine.week(data.copy(today = weekEnd.plusDays(1)))

        fun acts(d: LocalDate) = data.activities.filter { it.start.atZone(RytmTime.zone).toLocalDate() == d }
        val days = snaps.map { s ->
            val a = acts(s.date)
            WeekDayLine(
                date = s.date.toString(),
                weekday = s.date.dayOfWeek.getDisplayName(TextStyle.SHORT, pl),
                planned = s.plan.title,
                activities = a.map { x ->
                    listOfNotNull(
                        x.type.name + (x.notes?.takeIf { n -> n.isNotBlank() }?.let { n -> " ($n)" } ?: ""),
                        "${x.durationMin} min",
                        x.rpe?.let { "RPE $it" },
                        x.kcal?.let { "$it kcal" },
                    ).joinToString(" ")
                },
                load = s.load.today,
                readiness = s.readiness.score,
                sleepMin = s.night?.sleepMin,
                restingHr = s.restingHr,
                hrv = s.checkIn?.hrvRmssd?.roundToInt(),
                steps = s.steps,
                kcal = s.nutrition?.kcal,
                proteinG = s.nutrition?.proteinG?.roundToInt(),
                energy = s.checkIn?.energy,
                mood = s.checkIn?.mood,
                sore = s.checkIn?.soreAreas.orEmpty().map { it.name },
                habitsDone = s.habits.count { it.done },
                habitsTotal = s.habits.size,
                alcohol = s.alcoholToday,
                dayOff = s.dayOff,
                hadMatch = a.any { it.isMatch },
            )
        }
        val prevFrom = weekEnd.minusDays(13)
        val prevTo = weekEnd.minusDays(7)
        val prevSleep = data.metrics.filter { it.date in prevFrom..prevTo }.mapNotNull { it.sleepMin }
        val loadPrev = (7L..13L).sumOf { back -> engine.today(data.copy(today = weekEnd.minusDays(back))).load.today }
        val nutrition = NutritionAnalyzer.weekly(
            weekEnd = weekEnd,
            nutrition = data.nutrition,
            body = data.weighIns,
            strengthTrend = StrengthHistory.trend(data.sets, weekEnd),
            targets = data.profile.nutrition,
        )
        val last = snaps.last()
        val inWeek = dates.toSet()
        val habitLines = last.habits.map { h ->
            val done = snaps.count { s -> s.habits.any { it.habit.id == h.habit.id && it.done } }
            "${h.habit.name}: $done/7"
        }
        val strength = StrengthHistory.latest(data.sets).filter { it.lastDate in inWeek }.map { e ->
            e.exercise + ": " + e.last + when (e.improved) {
                true -> " (↑)"
                false -> " (=)"
                null -> ""
            }
        }
        val steps = days.mapNotNull { it.steps }
        val weightNow = NutritionAnalyzer.weightAvg7(data.weighIns, weekEnd)
        val weightBefore = NutritionAnalyzer.weightAvg7(data.weighIns, weekEnd.minusDays(7))
        return WeeklyInput(
            weekStart = dates.first().toString(),
            weekEnd = weekEnd.toString(),
            goal = data.profile.goal.name,
            goalNote = data.profile.goalNote.takeIf { it.isNotBlank() },
            days = days,
            loadWeek = days.sumOf { it.load },
            loadPrevWeek = loadPrev,
            acwr = last.load.acwr,
            strengthSessions = data.activities.count {
                it.start.atZone(RytmTime.zone).toLocalDate() in inWeek &&
                    (it.type == ActivityType.STRENGTH || it.type == ActivityType.PERSONAL_TRAINING)
            },
            strengthRequired = week.strengthRequired,
            cardioSessions = data.activities.count {
                it.start.atZone(RytmTime.zone).toLocalDate() in inWeek && it.type == ActivityType.CARDIO
            },
            avgSleepMin = days.mapNotNull { it.sleepMin }.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            avgSleepPrevWeekMin = prevSleep.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            avgSteps = steps.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            stepsTarget = last.habits.firstOrNull { it.habit.autoSource == HabitAutoSource.STEPS }?.habit?.target ?: 0,
            kcalTarget = data.profile.nutrition.kcal,
            proteinTarget = data.profile.nutrition.proteinG,
            avgKcal = nutrition.avgKcal,
            avgProteinG = nutrition.avgProteinG,
            proteinDaysBelowTarget = nutrition.proteinDaysBelowTarget,
            nutritionDaysLogged = nutrition.daysLogged,
            weightAvg7 = weightNow,
            weightChangeKg = if (weightNow != null && weightBefore != null) weightNow - weightBefore else null,
            strengthTrend = StrengthHistory.trend(data.sets, weekEnd).name,
            strengthLatest = strength,
            habits = habitLines,
            reflections = reflections.map { it.take(300) }.take(7),
            nutritionFlags = nutrition.suggestions.map { it.name },
            nextWeekPlan = nextWeek.days.map { it.date.dayOfWeek.getDisplayName(TextStyle.SHORT, pl) + ": " + it.plan.title },
        )
    }
}
