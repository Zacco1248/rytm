package com.zacco.rytm.domain.workout

import com.zacco.rytm.domain.model.PlannedExercise
import com.zacco.rytm.domain.planner.Equipment
import com.zacco.rytm.domain.planner.EquipmentKind
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgressionTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val eq = Equipment(EquipmentKind.DUMBBELLS, pairMaxKg = 8.0, singleMaxKg = 14.0)
    private val curl = PlannedExercise("Uginanie ramion z hantlami", 3, 12..15, 8.0, "3-0-1", 60)
    private val pushups = PlannedExercise("Pompki", 3, 8..15, null, "2-0-1", 60)

    private fun sets(ex: String, date: LocalDate, kg: Double?, vararg reps: Int, rir: Int? = null) =
        reps.mapIndexed { i, r -> SetLog("w", ex, i + 1, r, kg, rir, date) }

    @Test
    fun `first time starts at about 70 percent of the equipment max`() {
        val p = Progression(eq, today).next(curl, emptyList())
        assertEquals(5.0, p.weightKg)
        assertTrue(p.note!!.startsWith("Pierwszy raz"))
    }

    @Test
    fun `all sets at the top of the range add one step`() {
        val h = sets(curl.name, today.minusDays(3), 6.0, 15, 15, 15)
        val p = Progression(eq, today).next(curl, h)
        assertEquals(7.0, p.weightKg)
        assertEquals("Ostatnio 3 × 15 na 6 kg: dziś 7 kg", p.note)
    }

    @Test
    fun `sets inside the range keep the weight`() {
        val h = sets(curl.name, today.minusDays(3), 6.0, 15, 13, 12)
        val p = Progression(eq, today).next(curl, h)
        assertEquals(6.0, p.weightKg)
        assertEquals(12..15, p.reps)
    }

    @Test
    fun `most sets below the range drop one step`() {
        val h = sets(curl.name, today.minusDays(3), 7.0, 12, 10, 9)
        assertEquals(6.0, Progression(eq, today).next(curl, h).weightKg)
    }

    @Test
    fun `top of range at failure does not add weight`() {
        val h = sets(curl.name, today.minusDays(3), 6.0, 15, 15, 15, rir = 0)
        assertEquals(6.0, Progression(eq, today).next(curl, h).weightKg)
    }

    @Test
    fun `at equipment max progression moves to tempo and reps`() {
        val h = sets(curl.name, today.minusDays(3), 8.0, 15, 15, 15)
        val p = Progression(eq, today).next(curl, h)
        assertEquals(8.0, p.weightKg)
        assertEquals("4-0-1", p.tempo)
        assertEquals(12..18, p.reps)
    }

    @Test
    fun `long break lowers the weight by about 10 percent`() {
        val h = sets(curl.name, today.minusDays(30), 8.0, 15, 15, 15)
        assertEquals(7.0, Progression(eq, today).next(curl, h).weightKg)
    }

    @Test
    fun `only the latest session counts`() {
        val h = sets(curl.name, today.minusDays(10), 5.0, 15, 15, 15) + sets(curl.name, today.minusDays(3), 6.0, 13, 12, 12)
        assertEquals(6.0, Progression(eq, today).next(curl, h).weightKg)
    }

    @Test
    fun `sets logged today are ignored`() {
        val h = sets(curl.name, today, 6.0, 15, 15, 15)
        assertTrue(Progression(eq, today).next(curl, h).note!!.startsWith("Pierwszy raz"))
    }

    @Test
    fun `bodyweight range grows with what was done`() {
        val h = sets(pushups.name, today.minusDays(2), null, 15, 16, 15)
        val p = Progression(eq, today).next(pushups, h)
        assertNull(p.weightKg)
        assertEquals(16..18, p.reps)
    }

    @Test
    fun `gym weights use 2_5 kg steps`() {
        val gym = Equipment(EquipmentKind.GYM)
        val bench = PlannedExercise("Wyciskanie sztangi leżąc", 3, 6..10, null, null, 120)
        val h = sets(bench.name, today.minusDays(3), 60.0, 10, 10, 10)
        assertEquals(62.5, Progression(gym, today).next(bench, h).weightKg)
    }

    @Test
    fun `slower tempo adds a second down`() {
        assertEquals("4-1-1", Progression.slower("3-1-1"))
        assertEquals("3-1-1", Progression.slower(null))
        assertEquals("5-1-1", Progression.slower("5-1-1"))
    }

    @Test
    fun `summary shows uneven reps`() {
        assertEquals("15, 14, 12 na 6,5 kg", Progression.summary(sets("x", today, 6.5, 15, 14, 12)))
    }
}
