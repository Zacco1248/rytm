package com.zacco.rytm.domain.coach

import com.zacco.rytm.domain.profile.Goal
import kotlin.test.Test
import kotlin.test.assertEquals

class WeightPaceTest {
    private fun pace(kg: Double, goal: Goal) = CoachEngine.paceOf(kg, goal, 80.0)

    @Test
    fun recompWantsStableWeight() {
        assertEquals(WeightPace.OK, pace(-0.3, Goal.RECOMP))
        assertEquals(WeightPace.GAINING, pace(0.3, Goal.RECOMP))
        assertEquals(WeightPace.TOO_FAST, pace(-0.6, Goal.RECOMP))
    }

    @Test
    fun fatLossWantsSteadyDrop() {
        assertEquals(WeightPace.OK, pace(-0.6, Goal.FAT_LOSS))
        assertEquals(WeightPace.STALLED, pace(0.0, Goal.FAT_LOSS))
        assertEquals(WeightPace.TOO_FAST, pace(-0.9, Goal.FAT_LOSS))
        assertEquals(WeightPace.GAINING, pace(0.2, Goal.FAT_LOSS))
    }

    @Test
    fun muscleGainWantsSlowRise() {
        assertEquals(WeightPace.OK, pace(0.25, Goal.MUSCLE_GAIN))
        assertEquals(WeightPace.STALLED, pace(0.0, Goal.MUSCLE_GAIN))
        assertEquals(WeightPace.TOO_FAST_UP, pace(0.5, Goal.MUSCLE_GAIN))
        assertEquals(WeightPace.TOO_FAST, pace(-0.3, Goal.MUSCLE_GAIN))
    }
}
