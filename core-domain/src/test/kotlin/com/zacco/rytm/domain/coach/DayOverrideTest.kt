package com.zacco.rytm.domain.coach

import com.zacco.rytm.domain.model.DayKind
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DayOverrideTest {
    private val day = LocalDate.of(2026, 11, 4)

    @Test
    fun `override replaces only its own day`() {
        val o = DayOverride(
            date = day.toString(), title = "Szybkie 20 minut", kind = "STRENGTH", intensity = "MODERATE",
            exercises = listOf(OverrideExercise("Przysiad", 2, 12, 15, 6.0, 45)), reason = "mało czasu",
        )
        val s = CoachEngine().today(CoachData(today = day, overrides = listOf(o)))
        assertEquals("Szybkie 20 minut", s.plan.title)
        assertEquals(DayKind.STRENGTH, s.plan.kind)
        assertEquals(6.0, s.plan.workout!!.exercises.single().weightKg)
        assertTrue(s.plan.rationale.first().contains("mało czasu"))
        assertTrue(s.tomorrow.title != "Szybkie 20 minut")
        assertTrue(s.planChanged)
    }

    @Test
    fun `rest override has no workout`() {
        val o = DayOverride(day.toString(), "Odpoczynek", "REST", "LOW", reason = "boli bark")
        val s = CoachEngine().today(CoachData(today = day, overrides = listOf(o)))
        assertEquals(DayKind.REST, s.plan.kind)
        assertNull(s.plan.workout)
        val w = CoachEngine().week(CoachData(today = day, overrides = listOf(o)))
        assertEquals("Odpoczynek", w.days.first { it.plan.date == day }.plan.title)
    }
}
