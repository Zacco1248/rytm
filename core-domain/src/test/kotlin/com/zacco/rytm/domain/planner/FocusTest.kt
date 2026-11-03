package com.zacco.rytm.domain.planner

import com.zacco.rytm.domain.exercise.Muscle
import com.zacco.rytm.domain.model.DayKind
import com.zacco.rytm.domain.model.Intensity
import com.zacco.rytm.domain.model.PlannedDay
import com.zacco.rytm.domain.model.StrengthFocus
import com.zacco.rytm.domain.profile.CustomTargets
import com.zacco.rytm.domain.profile.Goal
import com.zacco.rytm.domain.profile.PersonalProfile
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FocusTest {
    private val date = LocalDate.of(2026, 11, 3)
    private fun day(f: StrengthFocus) = PlannedDay(date, DayKind.STRENGTH, "Siła", Intensity.MODERATE, f)
    private val eq = Equipment(EquipmentKind.DUMBBELLS)

    @Test
    fun focusAddsAccessoryOnMatchingSession() {
        val plain = ExerciseLibrary(eq).workoutFor(day(StrengthFocus.UPPER))!!.exercises
        val focused = ExerciseLibrary(eq, focus = setOf(Muscle.BICEPS, Muscle.ABS)).workoutFor(day(StrengthFocus.UPPER))!!.exercises
        assertTrue(focused.any { it.name == "Uginanie ramion z hantlami" && it.note == "Priorytet: biceps" })
        assertTrue(focused.size >= plain.size + 1)
    }

    @Test
    fun lowerSessionIgnoresUpperFocus() {
        val plain = ExerciseLibrary(eq).workoutFor(day(StrengthFocus.LOWER))!!.exercises
        val focused = ExerciseLibrary(eq, focus = setOf(Muscle.BICEPS)).workoutFor(day(StrengthFocus.LOWER))!!.exercises
        assertEquals(plain, focused)
    }

    @Test
    fun shortSessionGetsOneExtraAtMost() {
        val short = eq.copy(sessionMinutes = 30)
        val plain = ExerciseLibrary(short).workoutFor(day(StrengthFocus.FULL_BODY))!!.exercises
        val focused = ExerciseLibrary(short, focus = setOf(Muscle.BICEPS, Muscle.TRICEPS, Muscle.CALVES)).workoutFor(day(StrengthFocus.FULL_BODY))!!.exercises
        assertTrue(focused.size - plain.size <= 1)
    }

    @Test
    fun kcalFactorDependsOnGoal() {
        assertEquals(0.5, PersonalProfile(goal = Goal.FAT_LOSS).kcalFactor)
        assertEquals(1.0, PersonalProfile(goal = Goal.MUSCLE_GAIN).kcalFactor)
        assertEquals(0.75, PersonalProfile(goal = Goal.FAT_LOSS, customTargets = CustomTargets(2300, 161, 72, 253)).kcalFactor)
        assertEquals(0.6, PersonalProfile(goal = Goal.MUSCLE_GAIN, kcalFactorOverride = 0.6).kcalFactor)
    }
}
