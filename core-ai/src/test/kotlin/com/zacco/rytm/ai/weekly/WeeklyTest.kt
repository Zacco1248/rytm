package com.zacco.rytm.ai.weekly

import com.zacco.rytm.ai.morning.MorningInput
import com.zacco.rytm.ai.morning.RuleBasedMorningNote
import com.zacco.rytm.domain.coach.CoachData
import com.zacco.rytm.domain.coach.CoachEngine
import com.zacco.rytm.domain.model.Activity
import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.model.DailyMetrics
import com.zacco.rytm.domain.model.Source
import com.zacco.rytm.domain.time.RytmTime
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeeklyTest {
    private val sunday = LocalDate.of(2026, 11, 8)

    private fun data(): CoachData {
        val metrics = (0L..20L).map { back ->
            val d = sunday.minusDays(back)
            DailyMetrics(d, if (d.dayOfWeek.value in listOf(3, 5)) 360 else 450, null, null, 52, 7000, null)
        }
        val acts = listOf(2L, 4L).map { back ->
            val d = sunday.minusDays(back)
            Activity("a$back", ActivityType.VOLLEYBALL, d.atTime(19, 0).atZone(RytmTime.zone).toInstant(), 90, 7, null, 600, Source.HEALTH_CONNECT)
        } + Activity("s1", ActivityType.STRENGTH, sunday.minusDays(6).atTime(18, 0).atZone(RytmTime.zone).toInstant(), 45, 6, null, 250, Source.MANUAL)
        return CoachData(today = sunday, metrics = metrics, activities = acts)
    }

    @Test
    fun buildsWeekWithSevenDaysAndFallback() {
        val input = WeeklyInputs.from(data(), sunday, listOf("Ciężki tydzień w pracy"))
        assertEquals(7, input.days.size)
        assertEquals(sunday.toString(), input.weekEnd)
        assertEquals(1, input.strengthSessions)
        assertEquals(2, input.days.count { it.hadMatch })
        assertEquals(7, input.nextWeekPlan.size)
        val report = RuleBasedWeeklyReport.build(input).validate()
        assertTrue(report.suggestions.isNotEmpty())
        assertTrue(report.correlations.any { it.contains("dniach ze sportem") }, report.correlations.toString())
    }

    @Test
    fun morningFallbackMentionsReadiness() {
        val d = data()
        val s = CoachEngine().today(d)
        val note = RuleBasedMorningNote.build(MorningInput.from(d, s)).validate()
        assertTrue(note.headline.contains("${s.readiness.score}"))
    }
}
