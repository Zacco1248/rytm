package com.zacco.rytm.domain.exercise

import com.zacco.rytm.domain.model.DayKind
import com.zacco.rytm.domain.model.Intensity
import com.zacco.rytm.domain.model.PlannedDay
import com.zacco.rytm.domain.model.StrengthFocus
import com.zacco.rytm.domain.planner.Equipment
import com.zacco.rytm.domain.planner.EquipmentKind
import com.zacco.rytm.domain.planner.ExerciseLibrary
import com.zacco.rytm.domain.profile.Goal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExerciseGuideTest {
    private val date = LocalDate.of(2026, 11, 3)

    @Test
    fun everyPlannedExerciseHasDescription() {
        val missing = mutableSetOf<String>()
        // Dwa kolejne bloki 4-tygodniowe (warianty ćwiczeń) i dom z drążkiem albo bez.
        for (kind in EquipmentKind.entries) for (goal in Goal.entries) for (bar in listOf(false, true)) for (d in listOf(date, date.plusWeeks(4))) {
            val lib = ExerciseLibrary(Equipment(kind = kind, bands = true, pullUpBar = bar), goal, focus = Muscle.entries.toSet())
            val days = StrengthFocus.entries.flatMap { f -> Intensity.entries.map { PlannedDay(d, DayKind.STRENGTH, "Siła", it, f) } } +
                PlannedDay(date, DayKind.MOBILITY, "Mobilność 30 min", Intensity.LOW) +
                PlannedDay(date, DayKind.FLEX, "Interwały 20 min", Intensity.HIGH)
            days.mapNotNull { lib.workoutFor(it) }.flatMap { it.exercises }
                .filter { ExerciseGuide.find(it.name) == null }
                .forEach { missing += it.name }
        }
        assertEquals(emptySet(), missing)
    }

    @Test
    fun blocksRotateVariantsAndBarAddsPullUps() {
        val lib = ExerciseLibrary(Equipment(kind = EquipmentKind.DUMBBELLS, pullUpBar = true))
        val day = { d: LocalDate -> PlannedDay(d, DayKind.STRENGTH, "Siła", Intensity.MODERATE, StrengthFocus.UPPER) }
        val a = lib.workoutFor(day(date))!!.exercises.map { it.name }
        val b = lib.workoutFor(day(date.plusWeeks(4)))!!.exercises.map { it.name }
        assertTrue(a != b, "kolejny blok ma inne warianty")
        assertEquals(a, lib.workoutFor(day(date.plusWeeks(8)))!!.exercises.map { it.name })
        assertTrue(a.any { it.startsWith("Podciąganie") })
        val noBar = ExerciseLibrary(Equipment(kind = EquipmentKind.DUMBBELLS)).workoutFor(day(date))!!.exercises
        assertTrue(noBar.none { it.name.startsWith("Podciąganie") })
    }

    @Test
    fun findsByNameWithoutParenthesis() {
        val info = assertNotNull(ExerciseGuide.find("Wykroki w tył (na nogę)"))
        assertTrue(Muscle.GLUTES in info.muscles)
        assertTrue(info.videoUrl.startsWith("https://www.youtube.com/results?search_query="))
        assertTrue(Muscle.ABS in ExerciseGuide.muscles("Deska (sekundy)"))
    }
}
