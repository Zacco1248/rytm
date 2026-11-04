package com.zacco.rytm.domain.nutrition

import com.zacco.rytm.domain.model.BodyMetrics
import com.zacco.rytm.domain.model.NutritionDay
import com.zacco.rytm.domain.profile.NutritionTargets
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KcalAdjusterTest {
    private val today = LocalDate.of(2026, 12, 20)

    /** Ważenia codziennie przez 4 tygodnie, zmiana o [perWeek] kg tygodniowo. */
    private fun weights(perWeek: Double) = (0L until 28L).map { d ->
        BodyMetrics(today.minusDays(27 - d), 80.0 + perWeek * d / 7.0, null)
    }

    private fun food(kcal: Int, days: Long = 14) = (1L..days).map { NutritionDay(today.minusDays(it), kcal, 160.0, 70.0, 250.0) }

    @Test
    fun `weight standing still on a cut lowers the target`() {
        val r = KcalAdjuster.review(today, 2300, 0, -0.25, weights(0.0), food(2300))!!
        assertTrue(r.change < 0)
        assertTrue(r.change >= -150)
        assertEquals(r.change, r.newAdjust)
    }

    @Test
    fun `losing too fast raises the target, on pace keeps it`() {
        val fast = KcalAdjuster.review(today, 2300, 0, -0.25, weights(-0.8), food(2300))!!
        assertEquals(150, fast.change)
        val onPace = KcalAdjuster.review(today, 2300, 0, -0.25, weights(-0.25), food(2300))!!
        assertEquals(0, onPace.change)
    }

    @Test
    fun `eating far from target means no correction`() {
        val r = KcalAdjuster.review(today, 2300, 0, -0.25, weights(0.0), food(2900))!!
        assertEquals(0, r.change)
        assertTrue(r.reason.contains("trzymaj się celu"))
    }

    @Test
    fun `too little data gives no review and the total is capped`() {
        assertNull(KcalAdjuster.review(today, 2300, 0, -0.25, weights(0.0).takeLast(3), food(2300)))
        assertNull(KcalAdjuster.review(today, 2300, 0, -0.25, weights(0.0), food(2300, days = 4)))
        val capped = KcalAdjuster.review(today, 2300, -450, -0.25, weights(0.5), food(2300))!!
        assertEquals(-500, capped.newAdjust)
    }

    @Test
    fun `adjustment moves carbs and keeps protein`() {
        val t = NutritionTargets(2300, 161, 72, 253).adjusted(-120)
        assertEquals(2180, t.kcal)
        assertEquals(161, t.proteinG)
        assertEquals(223, t.carbsG)
        assertTrue(KcalAdjuster.isDue(today, today.minusDays(14)))
        assertTrue(!KcalAdjuster.isDue(today, today.minusDays(5)))
    }
}
