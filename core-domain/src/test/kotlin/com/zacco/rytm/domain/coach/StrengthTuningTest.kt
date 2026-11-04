package com.zacco.rytm.domain.coach

import com.zacco.rytm.domain.goals.GoalKind
import com.zacco.rytm.domain.goals.Goals
import com.zacco.rytm.domain.goals.UserGoal
import com.zacco.rytm.domain.model.DayKind
import com.zacco.rytm.domain.model.Intensity
import com.zacco.rytm.domain.model.PlannedDay
import com.zacco.rytm.domain.model.PlannedExercise
import com.zacco.rytm.domain.model.StrengthFocus
import com.zacco.rytm.domain.model.StrengthWorkout
import com.zacco.rytm.domain.planner.Equipment
import com.zacco.rytm.domain.planner.ExerciseLibrary
import com.zacco.rytm.domain.workout.SetLog
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StrengthTuningTest {
    private val today = LocalDate.of(2026, 11, 9)

    private fun day(focus: StrengthFocus, vararg names: String) = PlannedDay(
        today, DayKind.STRENGTH, "Siła", Intensity.MODERATE, focus = focus,
        workout = StrengthWorkout("Siła", names.map { PlannedExercise(it, 3, 10..15, null, null, 60) }),
    )

    @Test
    fun `reps goal adds the exercise to a fitting session`() {
        val g = UserGoal("g", GoalKind.REPS, "Podciąganie na drążku", 10.0, createdOn = today.toString())
        val p = Goals.progress(g, today, emptyList(), emptyList())
        val upper = StrengthTuning.withGoals(day(StrengthFocus.UPPER, "Pompki"), listOf(p))
        assertTrue(upper.workout!!.exercises.any { it.name.startsWith("Podciąganie") })
        val lower = StrengthTuning.withGoals(day(StrengthFocus.LOWER, "Przysiad"), listOf(p))
        assertFalse(lower.workout!!.exercises.any { it.name.startsWith("Podciąganie") })
    }

    @Test
    fun `goal exercise already in session gets a set more`() {
        val g = UserGoal("g", GoalKind.LIFT, "Przysiad goblet", 14.0, createdOn = today.toString())
        val d = StrengthTuning.withGoals(day(StrengthFocus.LOWER, "Przysiad goblet"), listOf(Goals.progress(g, today, emptyList(), emptyList())))
        assertEquals(4, d.workout!!.exercises.single().sets)
    }

    @Test
    fun `three sessions without progress swap to a variant`() {
        fun s(back: Long, kg: Double) = SetLog("w$back", "Przysiad goblet", 1, 12, kg, 2, today.minusDays(back))
        val stuck = listOf(s(28, 10.0), s(21, 10.0), s(14, 10.0), s(7, 9.0))
        assertTrue(StrengthTuning.plateaued(stuck, "Przysiad goblet", today))
        val d = StrengthTuning.swapPlateaus(day(StrengthFocus.LOWER, "Przysiad goblet"), stuck, today)
        assertEquals("Przysiad goblet z pauzą", d.workout!!.exercises.single().name)
        val growing = listOf(s(28, 8.0), s(21, 9.0), s(14, 10.0), s(7, 11.0))
        assertFalse(StrengthTuning.plateaued(growing, "Przysiad goblet", today))
    }

    @Test
    fun `accepted week plan replaces strength day without weights`() {
        val plan = WeekPlan(
            today.toString(),
            listOf(PlanDay(today.toString(), "Plecy i chwyt", listOf(OverrideExercise("Podciąganie na drążku", 4, 3, 6, weightKg = 20.0, restSec = 120)))),
            "Pod cel podciągnięć", accepted = true,
        )
        val d = assertNotNull(plan.applyTo(day(StrengthFocus.UPPER, "Pompki")))
        assertEquals("Plecy i chwyt", d.title)
        assertEquals(null, d.workout!!.exercises.single().weightKg)
        assertEquals(null, plan.copy(accepted = false).applyTo(day(StrengthFocus.UPPER, "Pompki")))
        val s = CoachEngine().today(CoachData(today = today, weekPlans = listOf(plan)))
        if (s.plan.kind == DayKind.STRENGTH) assertEquals("Plecy i chwyt", s.plan.title)
    }

    @Test
    fun `allowed names follow the equipment`() {
        val home = ExerciseLibrary(Equipment()).allowedNames()
        assertTrue(home.none { "sztang" in it.lowercase() }, home.toString())
        assertTrue(home.any { "hantl" in it.lowercase() })
    }
}
