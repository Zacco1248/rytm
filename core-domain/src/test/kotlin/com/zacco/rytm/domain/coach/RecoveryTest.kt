package com.zacco.rytm.domain.coach

import com.zacco.rytm.domain.exercise.ExerciseGuide
import com.zacco.rytm.domain.exercise.Muscle
import com.zacco.rytm.domain.load.AcwrZone
import com.zacco.rytm.domain.load.LoadSummary
import com.zacco.rytm.domain.model.Activity
import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.model.BodyArea
import com.zacco.rytm.domain.model.DayKind
import com.zacco.rytm.domain.model.Intensity
import com.zacco.rytm.domain.model.PlannedDay
import com.zacco.rytm.domain.model.PlannedExercise
import com.zacco.rytm.domain.model.Source
import com.zacco.rytm.domain.model.StrengthWorkout
import com.zacco.rytm.domain.time.RytmTime
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecoveryTest {
    private val monday = LocalDate.of(2026, 12, 7)
    private fun load(acwr: Double?, reliable: Boolean = true) = LoadSummary(monday, 0, 0, 0, 0, acwr, AcwrZone.SAFE, reliable)
    private fun strength(d: LocalDate) = Activity(d.toString(), ActivityType.STRENGTH, d.atTime(18, 0).atZone(RytmTime.zone).toInstant(), 45, 7, null, null, Source.MANUAL)

    private val day = PlannedDay(
        monday, DayKind.STRENGTH, "Góra", Intensity.HIGH,
        workout = StrengthWorkout(
            "Góra",
            listOf(
                PlannedExercise("Wyciskanie hantli nad głowę", 3, 10..12, 8.0, null, 60),
                PlannedExercise("Wiosłowanie hantlą jednorącz", 3, 10..12, 10.0, null, 60),
                PlannedExercise("Przysiad goblet", 3, 10..12, 12.0, null, 60),
            ),
        ),
    )

    @Test
    fun `high reliable acwr triggers a lighter week`() {
        assertEquals(DeloadReason.HIGH_ACWR, Recovery.deload(monday, load(1.5), emptyList(), null)?.reason)
        assertNull(Recovery.deload(monday, load(1.5, reliable = false), emptyList(), null))
        assertNull(Recovery.deload(monday, load(1.1), emptyList(), null))
    }

    @Test
    fun `every sixth week after five full weeks is lighter`() {
        val start = monday.minusWeeks(5)
        val acts = (1..5).flatMap { w -> listOf(strength(monday.minusWeeks(w.toLong())), strength(monday.minusWeeks(w.toLong()).plusDays(2))) }
        assertEquals(DeloadReason.LONG_BLOCK, Recovery.deload(monday, load(1.0), acts, start)?.reason)
        assertNull(Recovery.deload(monday.plusWeeks(1), load(1.0), acts, start))
        assertNull(Recovery.deload(monday, load(1.0), acts.take(4), start))
    }

    @Test
    fun `deload cuts a set, about ten percent of weight and caps intensity`() {
        val d = Recovery.applyDeload(day, Deload(DeloadReason.HIGH_ACWR, "x"), 1.0)
        assertEquals(Intensity.MODERATE, d.intensity)
        assertEquals(2, d.workout!!.exercises[0].sets)
        assertEquals(7.0, d.workout!!.exercises[0].weightKg)
        assertEquals(9.0, d.workout!!.exercises[1].weightKg)
        assertEquals("x", d.rationale.first())
    }

    @Test
    fun `sore shoulders swap pressing for a safe exercise`() {
        val d = Recovery.avoidSore(day, setOf(BodyArea.SHOULDERS))
        val names = d.workout!!.exercises.map { it.name }
        assertTrue("Wyciskanie hantli nad głowę" !in names)
        assertTrue("Przysiad goblet" in names)
        assertTrue(d.workout!!.exercises.none { e -> ExerciseGuide.muscles(e.name).any { it == Muscle.SHOULDERS || it == Muscle.CHEST } })
        assertTrue(d.rationale.first().contains("barki"))
        assertEquals(day, Recovery.avoidSore(day, emptySet()))
    }

    @Test
    fun `engine applies soreness from todays check-in`() {
        val data = CoachData(today = monday, checkIns = listOf(com.zacco.rytm.domain.model.DailyCheckIn(monday, 3, 3, setOf(BodyArea.KNEES), null)))
        val s = CoachEngine().today(data)
        val w = s.plan.workout
        if (s.plan.kind == DayKind.STRENGTH && w != null) {
            assertTrue(w.exercises.none { Muscle.LEGS in ExerciseGuide.muscles(it.name) })
        }
        assertNotNull(s)
    }
}
