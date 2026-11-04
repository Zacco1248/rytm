package com.zacco.rytm.ai.weekplan

import com.zacco.rytm.ai.weekly.WeeklyInputs
import com.zacco.rytm.domain.coach.CoachData
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeekPlanServiceTest {
    private val sunday = LocalDate.of(2026, 11, 8)

    @Test
    fun `input lists next week strength days and allowed exercises`() {
        val data = CoachData(today = sunday)
        val input = WeekPlanService.input(data, sunday, WeeklyInputs.from(data, sunday))
        assertTrue(input.strengthDays.isNotEmpty())
        assertTrue(input.strengthDays.all { LocalDate.parse(it.date) > sunday })
        assertTrue(input.allowedExercises.isNotEmpty())
    }

    @Test
    fun `validation keeps only known days and allowed exercises`() {
        val data = CoachData(today = sunday)
        val input = WeekPlanService.input(data, sunday, WeeklyInputs.from(data, sunday))
        val day = input.strengthDays.first().date
        val ok = input.allowedExercises.take(3)
        val reply = WeekPlanReply(
            listOf(
                WeekPlanReply.ReplyDay(day, "Góra", "Więcej pleców", ok.map { WeekPlanReply.ReplyExercise(it, 9, 12, 8, 999, null) } +
                    WeekPlanReply.ReplyExercise("Wyciskanie na suwnicy kosmicznej", 3, 8, 12, 90, null)),
                WeekPlanReply.ReplyDay("2026-12-01", "Obcy dzień", "", ok.map { WeekPlanReply.ReplyExercise(it, 3, 8, 12, 90, null) }),
            ),
            "Pod cel",
        )
        val plan = assertNotNull(WeekPlanService.validate(reply, input, sunday.plusDays(1)))
        assertEquals("2026-11-09", plan.weekStart)
        val d = plan.days.single()
        assertEquals(3, d.exercises.size)
        assertTrue(d.exercises.all { it.sets == 5 && it.repsMax >= it.repsMin && it.restSec == 240 && it.weightKg == null })
        assertNull(WeekPlanService.validate(WeekPlanReply(emptyList(), "nic"), input, sunday.plusDays(1)))
    }
}
