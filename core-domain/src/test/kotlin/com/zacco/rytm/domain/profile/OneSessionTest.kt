package com.zacco.rytm.domain.profile

import com.zacco.rytm.domain.coach.CoachData
import com.zacco.rytm.domain.coach.CoachEngine
import com.zacco.rytm.domain.model.DayKind
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class OneSessionTest {
    @Test
    fun newProfileDoesNotMakeUpDaysBeforeItExisted() {
        val today = LocalDate.of(2026, 11, 3)
        for (days in listOf(listOf(1), listOf(1, 2), listOf(1, 3, 5), listOf(2, 4))) {
            val p = PersonalProfile(
                activities = listOf(ActivityChoice.STRENGTH_HOME, ActivityChoice.WALK, ActivityChoice.SPORT),
                strengthPerWeek = 1,
                strengthDays = days,
                startedOn = today.toString(),
            )
            val week = CoachEngine().week(CoachData(today = today, profile = p.toUserProfile(today), habits = p.habits()))
            assertEquals(1, week.days.count { it.plan.kind == DayKind.STRENGTH }, "dni $days")
        }
    }
}
