package com.zacco.rytm.domain.coach

import com.zacco.rytm.domain.model.Activity
import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.model.DailyMetrics
import com.zacco.rytm.domain.model.NutritionDay
import com.zacco.rytm.domain.model.Source
import com.zacco.rytm.domain.readiness.ReadinessFlag
import com.zacco.rytm.domain.readiness.ReadinessLevel
import com.zacco.rytm.domain.time.RytmTime
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NudgesTest {
    private val day = LocalDate.of(2026, 11, 4)
    private val all = NudgeKind.entries.toSet()

    private fun metrics(d: LocalDate, steps: Int?) = DailyMetrics(d, 420, null, null, 55, steps, null)

    private fun snapshot(data: CoachData) = CoachEngine().today(data)

    @Test
    fun `short night gives a morning nudge, but not at night or twice`() {
        val data = CoachData(today = day)
        val s = snapshot(data).let { it.copy(readiness = it.readiness.copy(flags = listOf(ReadinessFlag.SHORT_SLEEP))) }
        assertEquals(NudgeKind.SLEEP, Nudges.pick(data, s, LocalTime.of(8, 0), all, emptySet())?.kind)
        assertNull(Nudges.pick(data, s, LocalTime.of(6, 30), all, emptySet()))
        assertNull(Nudges.pick(data, s, LocalTime.of(8, 0), all, setOf(NudgeKind.SLEEP)))
        assertNull(Nudges.pick(data, s, LocalTime.of(8, 0), all - NudgeKind.SLEEP, emptySet()))
    }

    @Test
    fun `daily limit stops further nudges`() {
        val data = CoachData(today = day)
        val s = snapshot(data).let { it.copy(readiness = it.readiness.copy(level = ReadinessLevel.LOW)) }
        assertNull(Nudges.pick(data, s, LocalTime.of(8, 0), all, setOf(NudgeKind.MOVE, NudgeKind.PROTEIN)))
    }

    @Test
    fun `three quiet days with few steps suggest a walk`() {
        val old = Activity("a", ActivityType.CARDIO, day.minusDays(6).atTime(18, 0).atZone(RytmTime.zone).toInstant(), 30, 6, null, null, Source.MANUAL)
        val data = CoachData(today = day, activities = listOf(old), metrics = listOf(metrics(day, 1200)))
        val s = snapshot(data)
        assertEquals(NudgeKind.MOVE, Nudges.pick(data, s, LocalTime.of(16, 0), setOf(NudgeKind.MOVE), emptySet())?.kind)
        val walked = data.copy(metrics = listOf(metrics(day, 8000)))
        assertNull(Nudges.pick(walked, snapshot(walked), LocalTime.of(16, 0), setOf(NudgeKind.MOVE), emptySet()))
        val fresh = CoachData(today = day, metrics = listOf(metrics(day, 1200)))
        assertNull(Nudges.pick(fresh, snapshot(fresh), LocalTime.of(16, 0), setOf(NudgeKind.MOVE), emptySet()))
    }

    @Test
    fun `low protein in the evening names what is missing`() {
        val data = CoachData(today = day, nutrition = listOf(NutritionDay(day, 1500, 80.0, 50.0, 150.0)))
        val s = snapshot(data)
        val n = Nudges.pick(data, s, LocalTime.of(20, 0), setOf(NudgeKind.PROTEIN), emptySet())
        assertEquals(NudgeKind.PROTEIN, n?.kind)
        assertNull(Nudges.pick(data, s, LocalTime.of(14, 0), setOf(NudgeKind.PROTEIN), emptySet()))
        val none = CoachData(today = day)
        assertNull(Nudges.pick(none, snapshot(none), LocalTime.of(20, 0), setOf(NudgeKind.PROTEIN), emptySet()))
    }
}
