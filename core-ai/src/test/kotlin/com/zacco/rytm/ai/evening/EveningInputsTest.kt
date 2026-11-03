package com.zacco.rytm.ai.evening

import com.zacco.rytm.domain.coach.CoachData
import com.zacco.rytm.domain.coach.CoachEngine
import com.zacco.rytm.domain.profile.Goal
import com.zacco.rytm.domain.profile.UserProfile
import com.zacco.rytm.domain.workout.SetLog
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EveningInputsTest {
    private val today = LocalDate.of(2026, 11, 3)

    @Test
    fun `input carries goal and today's sets`() {
        val data = CoachData(
            today = today,
            profile = UserProfile(goal = Goal.FAT_LOSS, goalNote = "minus 5 kg do lata"),
            sets = listOf(
                SetLog("w", "Przysiad goblet", 1, 15, 10.0, null, today),
                SetLog("w", "Przysiad goblet", 2, 14, 10.0, null, today),
            ),
        )
        val input = EveningInputs.from(data, CoachEngine().today(data), reflection = "  zmęczony  ")
        assertEquals("FAT_LOSS", input.goal)
        assertEquals("minus 5 kg do lata", input.goalNote)
        assertEquals(listOf("Przysiad goblet: 15, 14 na 10 kg"), input.strengthToday)
        assertEquals("zmęczony", input.reflection)
        assertEquals("wtorek", input.weekday)
    }

    @Test
    fun `blank goal note and reflection are left out`() {
        val data = CoachData(today = today)
        val input = EveningInputs.from(data, CoachEngine().today(data), reflection = " ")
        assertNull(input.goalNote)
        assertNull(input.reflection)
    }
}
