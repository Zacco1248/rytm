package com.zacco.rytm.domain.coach

import com.zacco.rytm.domain.model.Activity
import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.model.DailyMetrics
import com.zacco.rytm.domain.model.DayKind
import com.zacco.rytm.domain.model.DayNote
import com.zacco.rytm.domain.model.Source
import com.zacco.rytm.domain.profile.NutritionTargets
import com.zacco.rytm.domain.readiness.ReadinessFlag
import com.zacco.rytm.domain.time.RytmTime
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Poprawki po teście fazy 1: trening w celach, dzień wolny, alkohol, tętno z dnia, pominięty trening. */
class AdjustmentsTest {

    private val engine = CoachEngine()
    private val monday = LocalDate.of(2026, 12, 7)

    private fun at(date: LocalDate, hour: Int) = date.atTime(LocalTime.of(hour, 0)).atZone(RytmTime.zone).toInstant()

    @Test
    fun `training adds 75 percent of watch kcal and scales macros like Fitatu`() {
        val t = NutritionTargets().withTraining(400)
        assertEquals(2700, t.kcal)
        assertEquals(189, t.proteinG) // 161 × 2700 / 2300
        assertEquals(85, t.fatG)
        assertEquals(297, t.carbsG)

        val data = CoachData(
            today = monday,
            activities = listOf(
                Activity("s", ActivityType.STRENGTH, at(monday, 18), 60, null, null, 400, Source.HEALTH_CONNECT),
                // spacer z psem nie jest treningiem doliczanym w Fitatu
                Activity("w", ActivityType.WALK, at(monday, 8), 40, null, null, 150, Source.HEALTH_CONNECT),
            ),
        )
        val today = engine.today(data)
        assertEquals(300, today.trainingKcal)
        assertEquals(2600, today.nutritionTargets.kcal)
    }

    @Test
    fun `day off becomes rest and missing strength moves to a later day`() {
        val data = CoachData(today = monday, dayNotes = listOf(DayNote(monday, dayOff = true)))
        val today = engine.today(data)
        assertEquals(DayKind.REST, today.plan.kind)
        assertTrue(today.dayOff)

        val week = engine.week(data)
        val strength = week.days.count { it.plan.kind == DayKind.STRENGTH || it.plan.kind == DayKind.PERSONAL_TRAINING }
        assertTrue(strength >= 2, "w tygodniu nadal 2 sesje siłowe")
        assertEquals(DayKind.STRENGTH, week.days.first { it.date.dayOfWeek == DayOfWeek.SATURDAY }.plan.kind)
    }

    @Test
    fun `missed training is only marked, not penalised`() {
        val wednesday = monday.plusDays(2)
        val week = engine.week(CoachData(today = wednesday))
        val mondayDay = week.days.first { it.date == monday }
        assertTrue(mondayDay.missed)
        assertFalse(week.days.first { it.date == wednesday }.missed)
    }

    @Test
    fun `alcohol yesterday lowers readiness`() {
        val sober = engine.today(CoachData(today = monday))
        val after = engine.today(CoachData(today = monday, dayNotes = listOf(DayNote(monday.minusDays(1), alcoholDrinks = 3))))
        assertEquals(sober.readiness.score - 12, after.readiness.score)
        assertTrue(ReadinessFlag.ALCOHOL in after.readiness.flags)
        assertEquals(3, after.alcoholYesterday)
    }

    @Test
    fun `day resting hr is used when the watch charged overnight`() {
        val metrics = (1L..7L).map { DailyMetrics(monday.minusDays(it), null, null, null, null, 6000, null, dayRestingHr = 62) } +
            DailyMetrics(monday, null, null, null, null, 2000, null, dayRestingHr = 70)
        val today = engine.today(CoachData(today = monday, metrics = metrics))
        assertEquals(70, today.restingHr)
        assertTrue(today.restingHrFromDay)
        assertTrue(ReadinessFlag.RESTING_HR_ELEVATED in today.readiness.flags)
    }

    @Test
    fun `strength day lists dumbbell exercises within the equipment`() {
        val plan = engine.today(CoachData(today = monday)).plan
        val w = kotlin.test.assertNotNull(plan.workout)
        assertTrue(w.exercises.size >= 5)
        assertTrue(w.exercises.all { (it.weightKg ?: 0.0) <= 14.0 })
    }
}
