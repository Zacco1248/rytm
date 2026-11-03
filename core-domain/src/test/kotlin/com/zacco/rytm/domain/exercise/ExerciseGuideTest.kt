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
        for (kind in EquipmentKind.entries) for (goal in Goal.entries) {
            val lib = ExerciseLibrary(Equipment(kind = kind, bands = true), goal)
            val days = StrengthFocus.entries.flatMap { f -> Intensity.entries.map { PlannedDay(date, DayKind.STRENGTH, "Siła", it, f) } } +
                PlannedDay(date, DayKind.MOBILITY, "Mobilność 30 min", Intensity.LOW) +
                PlannedDay(date, DayKind.FLEX, "Interwały 20 min", Intensity.HIGH)
            days.mapNotNull { lib.workoutFor(it) }.flatMap { it.exercises }
                .filter { ExerciseGuide.find(it.name) == null }
                .forEach { missing += it.name }
        }
        assertEquals(emptySet(), missing)
    }

    @Test
    fun findsByNameWithoutParenthesis() {
        val info = assertNotNull(ExerciseGuide.find("Wykroki w tył (na nogę)"))
        assertTrue(Muscle.GLUTES in info.muscles)
        assertTrue(info.videoUrl.startsWith("https://www.youtube.com/results?search_query="))
        assertTrue(Muscle.ABS in ExerciseGuide.muscles("Deska (sekundy)"))
    }
}
