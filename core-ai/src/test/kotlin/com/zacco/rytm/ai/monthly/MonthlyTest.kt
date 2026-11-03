package com.zacco.rytm.ai.monthly

import com.zacco.rytm.domain.coach.CoachData
import com.zacco.rytm.domain.model.Activity
import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.model.BodyMetrics
import com.zacco.rytm.domain.model.DailyMetrics
import com.zacco.rytm.domain.model.Source
import com.zacco.rytm.domain.time.RytmTime
import com.zacco.rytm.domain.workout.SetLog
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonthlyTest {
    private val end = LocalDate.of(2026, 10, 31)

    private fun data(days: Long = 84): CoachData {
        val metrics = (0 until days).map { back ->
            DailyMetrics(end.minusDays(back), 400, null, null, 55, 7000, null)
        }
        val weighIns = (0 until days step 3).map { back -> BodyMetrics(end.minusDays(back), 82.0 - (days - back) * 0.02, null) }
        val acts = (0 until days step 4).map { back ->
            Activity("s$back", ActivityType.STRENGTH, end.minusDays(back).atTime(18, 0).atZone(RytmTime.zone).toInstant(), 45, 7, null, 250, Source.MANUAL)
        }
        val sets = listOf(
            SetLog("a", "Wiosłowanie", 1, 10, 10.0, null, end.minusDays(80)),
            SetLog("b", "Wiosłowanie", 1, 10, 12.0, null, end.minusDays(5)),
        )
        return CoachData(today = end, metrics = metrics, weighIns = weighIns, activities = acts, sets = sets)
    }

    @Test
    fun buildsTwelveWeeksWithStrengthProgress() {
        val input = MonthlyInputs.from(data(), end)
        assertEquals(12, input.weeks.size)
        assertEquals("Wiosłowanie: 10 kg×10 → 12 kg×10 (2 sesji)", input.strengthProgress.single())
        assertTrue(input.weeks.all { it.avgSleepMin == 400 })
    }

    @Test
    fun skipsWeeksBeforeFirstData() {
        val input = MonthlyInputs.from(data(days = 20), end)
        assertEquals(3, input.weeks.size)
    }

    @Test
    fun ruleFallbackIsValidAndFlagsShortSleep() {
        val report = RuleBasedMonthlyReport.build(MonthlyInputs.from(data(), end)).validate()
        assertTrue(report.blockers.any { it.startsWith("Sen") })
        assertTrue(report.trends.any { it.startsWith("Waga") })
    }
}
