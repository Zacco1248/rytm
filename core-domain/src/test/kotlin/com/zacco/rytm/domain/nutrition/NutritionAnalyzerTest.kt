package com.zacco.rytm.domain.nutrition

import com.zacco.rytm.domain.model.BodyMetrics
import com.zacco.rytm.domain.model.NutritionDay
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NutritionAnalyzerTest {

    private val weekEnd = LocalDate.of(2026, 11, 29)

    private fun weights(days: Int, kgOf: (Int) -> Double) =
        (0 until days).map { BodyMetrics(weekEnd.minusDays(it.toLong()), kgOf(it), null) }

    private fun meals(protein: Double, days: Int = 7) =
        (0 until days).map { NutritionDay(weekEnd.minusDays(it.toLong()), 2300, protein, 72.0, 253.0) }

    @Test
    fun `seven day average never uses a single reading`() {
        val body = listOf(
            BodyMetrics(weekEnd, 81.0, null),
            BodyMetrics(weekEnd.minusDays(3), 79.0, null),
            BodyMetrics(weekEnd.minusDays(8), 70.0, null),
        )
        assertEquals(80.0, NutritionAnalyzer.weightAvg7(body, weekEnd))
    }

    @Test
    fun `stable weight with rising strength is recomposition`() {
        val r = NutritionAnalyzer.weekly(weekEnd, meals(165.0), weights(35) { 80.0 + (it % 2) * 0.1 }, StrengthTrend.UP)

        assertTrue(r.weightStallWeeks >= 2)
        assertEquals(listOf(NutritionSuggestion.RECOMPOSITION_ON_TRACK), r.suggestions)
    }

    @Test
    fun `fast weight loss with falling strength suggests more calories`() {
        // spadek ok. 1 kg/tydzień
        val r = NutritionAnalyzer.weekly(weekEnd, meals(165.0), weights(35) { 78.0 + it / 7.0 }, StrengthTrend.DOWN)

        assertTrue(r.weeklyWeightChangeKg!! < -0.9)
        assertTrue(NutritionSuggestion.INCREASE_CALORIES in r.suggestions)
    }

    @Test
    fun `low protein on most days is reported`() {
        val r = NutritionAnalyzer.weekly(weekEnd, meals(120.0), emptyList(), StrengthTrend.UNKNOWN)

        assertEquals(7, r.proteinDaysBelowTarget)
        assertTrue(NutritionSuggestion.PROTEIN_LOW in r.suggestions)
        assertFalse(NutritionSuggestion.INCOMPLETE_LOGGING in r.suggestions)
    }

    @Test
    fun `few logged days are flagged`() {
        val r = NutritionAnalyzer.weekly(weekEnd, meals(165.0, days = 2), emptyList(), StrengthTrend.UNKNOWN)
        assertTrue(NutritionSuggestion.INCOMPLETE_LOGGING in r.suggestions)
    }
}
