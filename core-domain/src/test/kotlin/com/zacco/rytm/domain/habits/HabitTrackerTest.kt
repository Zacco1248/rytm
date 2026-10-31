package com.zacco.rytm.domain.habits

import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HabitTrackerTest {

    private val today = LocalDate.of(2026, 11, 5) // czwartek
    private val mobility = DefaultHabits.all.first { it.id == "mobility-10" }

    @Test
    fun `minutes habit needs target`() {
        assertFalse(HabitTracker.isDone(mobility, 5))
        assertTrue(HabitTracker.isDone(mobility, 10))
    }

    @Test
    fun `daily streak ignores today until done`() {
        val logs = (1L..4L).map { HabitLog(mobility.id, today.minusDays(it), 10) }

        assertEquals(4, HabitTracker.streak(mobility, logs, today))
        assertEquals(5, HabitTracker.streak(mobility, logs + HabitLog(mobility.id, today, 12), today))
    }

    @Test
    fun `missed day breaks the streak`() {
        val logs = listOf(1L, 2L, 4L).map { HabitLog(mobility.id, today.minusDays(it), 10) }
        assertEquals(2, HabitTracker.streak(mobility, logs, today))
    }

    @Test
    fun `specific days skip unscheduled days in streak`() {
        val habit = Habit("gym", "Siłownia", HabitSchedule.SpecificDays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)), HabitKind.YES_NO)
        val logs = listOf(today, today.minusDays(3), today.minusDays(7)).map { HabitLog("gym", it, 1) }

        assertEquals(3, HabitTracker.streak(habit, logs, today))
        assertFalse(HabitTracker.isDueOn(habit, today.plusDays(1), logs))
    }

    @Test
    fun `times per week stops being due once weekly target is met`() {
        val habit = Habit("run", "Bieg", HabitSchedule.TimesPerWeek(2), HabitKind.YES_NO)
        val logs = listOf(HabitLog("run", today.minusDays(2), 1), HabitLog("run", today.minusDays(1), 1))

        assertFalse(HabitTracker.isDueOn(habit, today, logs))
        assertEquals(HabitWeekStatus(2, 2), HabitTracker.weekStatus(habit, today, logs))
    }

    @Test
    fun `general activity is done from steps`() {
        val activity = DefaultHabits.all.first { it.id == "activity-steps" }
        val log = HabitTracker.autoLog(activity, today, steps = 6_400)!!

        assertTrue(HabitTracker.isDone(activity, log.value))
        assertFalse(HabitTracker.isDone(activity, HabitTracker.autoLog(activity, today, 5_200)!!.value))
        assertEquals(null, HabitTracker.autoLog(mobility, today, 9_000))
    }
}
