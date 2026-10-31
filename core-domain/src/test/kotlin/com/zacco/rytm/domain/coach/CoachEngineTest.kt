package com.zacco.rytm.domain.coach

import com.zacco.rytm.domain.habits.HabitLog
import com.zacco.rytm.domain.model.Activity
import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.model.BodyMetrics
import com.zacco.rytm.domain.model.DailyMetrics
import com.zacco.rytm.domain.model.DayKind
import com.zacco.rytm.domain.model.NutritionDay
import com.zacco.rytm.domain.model.Source
import com.zacco.rytm.domain.time.RytmTime
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoachEngineTest {

    private val engine = CoachEngine()
    private val wednesday = LocalDate.of(2026, 11, 4)

    private fun at(date: LocalDate, hour: Int) = date.atTime(LocalTime.of(hour, 0)).atZone(RytmTime.zone).toInstant()

    private fun activity(id: String, date: LocalDate, type: ActivityType, min: Int, rpe: Int?, hr: Int? = null) =
        Activity(id, type, at(date, 19), min, rpe, hr, null, Source.HEALTH_CONNECT)

    @Test
    fun `dziś - kroki odhaczają nawyk aktywności, plan to squash`() {
        val data = CoachData(
            today = wednesday,
            metrics = listOf(DailyMetrics(wednesday, 450, at(wednesday.minusDays(1), 23), at(wednesday, 7), 55, 6400, null)),
            habitLogs = listOf(HabitLog("no-screen-2330", wednesday, 1)),
        )
        val today = engine.today(data)
        assertEquals(DayKind.FIXED_SPORT, today.plan.kind)
        val steps = today.habits.first { it.habit.id == "activity-steps" }
        assertTrue(steps.done)
        assertEquals(6400, steps.value)
        assertTrue(today.habits.first { it.habit.id == "no-screen-2330" }.done)
        assertFalse(today.habits.first { it.habit.id == "mobility-10" }.done)
        assertTrue(today.readiness.calibrating)
        assertEquals(0, today.baselineDays)
    }

    @Test
    fun `tydzień - obciążenie z RPE albo z tętna, siła liczona`() {
        val monday = wednesday.minusDays(2)
        val data = CoachData(
            today = wednesday,
            activities = listOf(
                activity("m", monday, ActivityType.STRENGTH, 60, rpe = 6),
                // bez RPE: szacunek z tętna
                activity("t", monday.plusDays(1), ActivityType.VOLLEYBALL, 90, rpe = null, hr = 150),
            ),
        )
        val week = engine.week(data)
        assertEquals(7, week.days.size)
        assertEquals(360, week.days[0].actualLoad)
        assertTrue(week.days[1].actualLoad > 0)
        assertEquals(1, week.strengthDone)
        assertTrue(week.days[2].plannedLoad > 0)
        assertEquals(0, week.days[0].plannedLoad)
    }

    @Test
    fun `sylwetka - średnia 7-dniowa, tempo i białko w celu`() {
        val weighIns = (0L until 28L).map { i ->
            val d = wednesday.minusDays(27 - i)
            BodyMetrics(d, 81.0 - i * 0.04, null)
        }
        val nutrition = (0L until 7L).map { i ->
            NutritionDay(wednesday.minusDays(i), 2250, if (i % 2 == 0L) 165.0 else 140.0, 70.0, 250.0)
        }
        val body = engine.body(CoachData(today = wednesday, weighIns = weighIns, nutrition = nutrition))
        assertEquals(28, body.weighIns.size)
        assertEquals(WeightPace.OK, body.pace)
        assertEquals(-0.28, body.kgPerWeek!!, 0.01)
        assertEquals(4, body.proteinDaysInTarget)
        assertEquals(2250, body.avgKcal)
    }
}
