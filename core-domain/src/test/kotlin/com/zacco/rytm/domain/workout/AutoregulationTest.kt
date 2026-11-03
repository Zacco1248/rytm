package com.zacco.rytm.domain.workout

import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.model.PlannedExercise
import com.zacco.rytm.domain.model.StrengthWorkout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoregulationTest {
    private val workout = StrengthWorkout(
        "Góra",
        listOf(
            PlannedExercise("Wyciskanie", 3, 10..12, 6.0, null, 60),
            PlannedExercise("Pompki", 3, 8..15, null, null, 60),
        ),
    )
    private val t0 = 1_800_000_000_000L
    private fun run(maxKg: Double? = null) = WorkoutRun.start("w1", workout, ActivityType.STRENGTH, 1.0, t0, maxKg)

    @Test
    fun `easy set raises the weight for the next set`() {
        val r = run().log(12, 6.0, Effort.EASY.rir, t0)
        assertEquals(7.0, r.suggestedWeight())
        assertEquals(10, r.suggestedReps())
        assertTrue(r.visibleAdvice()!!.message.contains("7 kg"))
    }

    @Test
    fun `easy set at equipment max asks for more reps instead`() {
        val r = run(maxKg = 6.0).log(12, 6.0, Effort.EASY.rir, t0)
        assertEquals(6.0, r.suggestedWeight())
        assertEquals(14, r.suggestedReps())
    }

    @Test
    fun `failure below the range lowers the weight`() {
        val r = run().log(8, 6.0, Effort.MAX.rir, t0)
        assertEquals(5.0, r.suggestedWeight())
        assertEquals(10, r.suggestedReps())
    }

    @Test
    fun `good set keeps everything`() {
        val r = run().log(11, 6.0, Effort.GOOD.rir, t0)
        assertEquals(6.0, r.suggestedWeight())
        assertEquals(11, r.suggestedReps())
    }

    @Test
    fun `no effort means no advice`() {
        val r = run().log(11, 6.0, null, t0)
        assertNull(r.advice)
        assertEquals(6.0, r.suggestedWeight())
    }

    @Test
    fun `two failures in a row offer ending the exercise`() {
        val r = run().log(7, 6.0, 0, t0).log(6, 5.0, 0, t0 + 1)
        assertTrue(r.visibleAdvice()!!.offerEndExercise)
        val ended = r.endExercise()
        assertEquals("Pompki", ended.current!!.name)
        assertEquals(2, ended.exercises[0].sets)
        assertTrue(ended.skipped.isEmpty())
    }

    @Test
    fun `easy last set offers an extra set of the same exercise`() {
        var r = run()
        repeat(3) { r = r.log(12, 6.0, Effort.EASY.rir, t0 + it) }
        assertEquals("Pompki", r.current!!.name)
        val a = assertNotNull(r.visibleAdvice())
        assertTrue(a.offerExtraSet)
        // Podpowiedź z poprzedniego ćwiczenia nie zmienia celu pompek.
        assertEquals(15, r.suggestedReps())
        val extra = r.extraSet()
        assertEquals("Wyciskanie", extra.current!!.name)
        assertEquals(4, extra.setNo)
        assertEquals(4, extra.exercises[0].sets)
        val after = extra.log(12, 6.0, null, t0 + 10)
        assertEquals("Pompki", after.current!!.name)
        assertFalse(after.extraSet() != after)
    }

    @Test
    fun `bodyweight easy set raises the rep target`() {
        val r = run().log(12, 6.0, 2, t0).log(12, 6.0, 2, t0).log(12, 6.0, 2, t0).log(15, null, Effort.EASY.rir, t0)
        assertEquals(17, r.suggestedReps())
        assertNull(r.suggestedWeight())
    }
}
