package com.zacco.rytm.ai.evening

import com.zacco.rytm.domain.coach.CoachData
import com.zacco.rytm.domain.coach.TodaySnapshot
import com.zacco.rytm.domain.time.RytmTime
import com.zacco.rytm.domain.workout.Progression
import java.time.format.TextStyle
import java.util.Locale

/** Składa [EveningInput] z danych dnia. Tylko agregaty, bez surowych próbek. */
object EveningInputs {

    fun from(data: CoachData, today: TodaySnapshot, reflection: String? = null): EveningInput {
        val activities = data.activities.filter { it.start.atZone(RytmTime.zone).toLocalDate() == data.today }
        val strength = today.setsToday.groupBy { it.exercise }.map { (name, sets) -> "$name: " + Progression.summary(sets.sortedBy { it.setIndex }) }
        return EveningInput(
            date = data.today.toString(),
            weekday = data.today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("pl")),
            plannedToday = today.plan.title,
            activities = activities.map { ActivityLine(it.type.name, it.durationMin, it.rpe, it.load, it.notes) },
            loadToday = today.load.today,
            acwr = today.load.acwr,
            readinessToday = today.readiness.score,
            readinessFlags = today.readiness.flags.map { it.name },
            sleepLastNightMin = today.night?.sleepMin,
            steps = today.steps,
            kcal = today.nutrition?.kcal,
            kcalTarget = today.nutritionTargets.kcal,
            proteinG = today.nutrition?.proteinG?.toInt(),
            proteinTarget = today.nutritionTargets.proteinG,
            energy = today.checkIn?.energy,
            mood = today.checkIn?.mood,
            soreAreas = today.checkIn?.soreAreas.orEmpty().map { it.name },
            habitsDone = today.habits.filter { it.done }.map { it.habit.name },
            habitsMissed = today.habits.filterNot { it.done }.map { it.habit.name },
            reflection = reflection?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_REFLECTION),
            tomorrowPlan = today.tomorrow.title,
            tomorrowIntensity = today.tomorrow.intensity.name,
            tomorrowWarnings = today.tomorrow.warnings,
            suggestedBedtime = "%02d:%02d".format(Locale.ROOT, today.bedtime.hour, today.bedtime.minute),
            calibrating = today.readiness.calibrating,
            goal = data.profile.goal.name,
            goalNote = data.profile.goalNote.takeIf { it.isNotBlank() },
            strengthToday = strength,
            dayOff = today.dayOff,
            alcoholDrinksToday = today.alcoholToday,
        )
    }

    private const val MAX_REFLECTION = 500
}
