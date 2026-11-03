package com.zacco.rytm.ai.plan

import com.zacco.rytm.domain.exercise.Muscle
import com.zacco.rytm.domain.profile.ActivityChoice
import com.zacco.rytm.domain.profile.PersonalProfile
import kotlin.test.Test
import kotlin.test.assertEquals

class PlanTweakTest {
    @Test
    fun appliesOnlyValidFields() {
        val p = PersonalProfile(strengthDays = listOf(1, 3, 5), strengthPerWeek = 3, sessionMinutes = 45)
        val t = PlanTweak(
            activities = listOf("RUN", "NOPE"),
            trainingDays = listOf(1, 4, 9),
            sessionsPerWeek = 12,
            sessionMinutes = 35,
            focus = listOf("ABS"),
            explanation = "Mniej czasu w tygodniu.",
        )
        val r = t.applyTo(p)
        assertEquals(listOf(ActivityChoice.RUN), r.activities)
        assertEquals(listOf(1, 4), r.strengthDays)
        assertEquals(3, r.strengthPerWeek)
        assertEquals(30, r.sessionMinutes)
        assertEquals(listOf(Muscle.ABS), r.focus)
    }
}
