package com.zacco.rytm.domain.habits

import java.time.DayOfWeek
import java.time.LocalDate

sealed interface HabitSchedule {
    data object Daily : HabitSchedule
    data class TimesPerWeek(val times: Int) : HabitSchedule
    data class SpecificDays(val days: Set<DayOfWeek>) : HabitSchedule
}

enum class HabitKind { YES_NO, COUNT, MINUTES }

data class Habit(
    val id: String,
    val name: String,
    val schedule: HabitSchedule,
    val kind: HabitKind,
    /** Cel dla COUNT/MINUTES; dla YES_NO pomijany. */
    val target: Int? = null,
)

data class HabitLog(val habitId: String, val date: LocalDate, val value: Int)

data class HabitWeekStatus(val done: Int, val required: Int) {
    val completed: Boolean get() = done >= required
}

object DefaultHabits {
    /** Startowe nawyki (sekcja 13). Sen i zmęczenie mierzy check-in i zegarek, więc nie są nawykami do odhaczania. */
    val all = listOf(
        Habit("no-screen-2330", "Bez ekranu po 23:30", HabitSchedule.Daily, HabitKind.YES_NO),
        Habit("mobility-10", "10 min mobilności", HabitSchedule.Daily, HabitKind.MINUTES, target = 10),
        Habit("activity-30", "Ogólna aktywność", HabitSchedule.Daily, HabitKind.MINUTES, target = 30),
    )
}

object HabitTracker {

    fun isDone(habit: Habit, value: Int?): Boolean = when {
        value == null -> false
        habit.kind == HabitKind.YES_NO -> value >= 1
        else -> value >= (habit.target ?: 1)
    }

    fun isScheduledOn(habit: Habit, date: LocalDate): Boolean = when (val s = habit.schedule) {
        HabitSchedule.Daily -> true
        is HabitSchedule.SpecificDays -> date.dayOfWeek in s.days
        is HabitSchedule.TimesPerWeek -> true
    }

    /** Czy pokazać nawyk na liście „na dziś”. Dla X razy w tygodniu: dopóki tygodniowy cel nie jest spełniony. */
    fun isDueOn(habit: Habit, date: LocalDate, logs: List<HabitLog>): Boolean = when (val s = habit.schedule) {
        is HabitSchedule.TimesPerWeek -> {
            val doneToday = isDone(habit, valueOn(habit, date, logs))
            doneToday || weekStatus(habit, date, logs).done < s.times
        }
        else -> isScheduledOn(habit, date)
    }

    fun weekStatus(habit: Habit, anyDayOfWeek: LocalDate, logs: List<HabitLog>): HabitWeekStatus {
        val monday = anyDayOfWeek.with(DayOfWeek.MONDAY)
        val week = (0L until 7L).map { monday.plusDays(it) }
        val done = week.count { isDone(habit, valueOn(habit, it, logs)) }
        val required = when (val s = habit.schedule) {
            HabitSchedule.Daily -> 7
            is HabitSchedule.TimesPerWeek -> s.times
            is HabitSchedule.SpecificDays -> s.days.size
        }
        return HabitWeekStatus(done, required)
    }

    /**
     * Seria: kolejne zaplanowane dni (lub tygodnie dla X razy w tygodniu) z wykonanym nawykiem, licząc wstecz od [today].
     * Niewykonany jeszcze dzisiejszy dzień nie przerywa serii.
     */
    fun streak(habit: Habit, logs: List<HabitLog>, today: LocalDate): Int {
        val own = logs.filter { it.habitId == habit.id }
        if (habit.schedule is HabitSchedule.TimesPerWeek) {
            var weeks = 0
            var monday = today.with(DayOfWeek.MONDAY)
            if (!weekStatus(habit, monday, own).completed) monday = monday.minusWeeks(1)
            while (weekStatus(habit, monday, own).completed && weeks < MAX_STREAK) {
                weeks++
                monday = monday.minusWeeks(1)
            }
            return weeks
        }
        var count = 0
        var day = today
        if (!isDone(habit, valueOn(habit, day, own))) day = day.minusDays(1)
        var checked = 0
        while (checked < MAX_STREAK) {
            if (isScheduledOn(habit, day)) {
                if (!isDone(habit, valueOn(habit, day, own))) break
                count++
            }
            day = day.minusDays(1)
            checked++
        }
        return count
    }

    private fun valueOn(habit: Habit, date: LocalDate, logs: List<HabitLog>): Int? =
        logs.filter { it.habitId == habit.id && it.date == date }.maxOfOrNull { it.value }

    private const val MAX_STREAK = 3650
}
