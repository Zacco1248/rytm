package com.zacco.rytm.domain.workout

import com.zacco.rytm.domain.nutrition.StrengthTrend
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class StrengthHistoryTest {
    private val today = LocalDate.of(2026, 11, 20)

    private fun s(ex: String, daysAgo: Long, kg: Double?, vararg reps: Int) =
        reps.mapIndexed { i, r -> SetLog("w$daysAgo", ex, i + 1, r, kg, null, today.minusDays(daysAgo)) }

    @Test
    fun `latest shows last and previous session per exercise`() {
        val sets = s("Przysiad", 10, 10.0, 12, 12) + s("Przysiad", 3, 10.0, 15, 14) + s("Pompki", 5, null, 10)
        val list = StrengthHistory.latest(sets)
        assertEquals("Przysiad", list.first().exercise)
        assertEquals("15, 14 na 10 kg", list.first().last)
        assertEquals("2 × 12 na 10 kg", list.first().previous)
        assertEquals(true, list.first().improved)
        assertEquals(null, list[1].improved)
    }

    @Test
    fun `trend up when most exercises got stronger`() {
        val sets = s("A", 30, 6.0, 12) + s("A", 5, 7.0, 12) + s("B", 25, 10.0, 12) + s("B", 3, 11.0, 12)
        assertEquals(StrengthTrend.UP, StrengthHistory.trend(sets, today))
    }

    @Test
    fun `trend down when weights dropped`() {
        val sets = s("A", 30, 8.0, 12) + s("A", 5, 6.0, 12) + s("B", 25, 12.0, 12) + s("B", 3, 10.0, 12)
        assertEquals(StrengthTrend.DOWN, StrengthHistory.trend(sets, today))
    }

    @Test
    fun `trend unknown with too little data`() {
        assertEquals(StrengthTrend.UNKNOWN, StrengthHistory.trend(s("A", 30, 6.0, 12) + s("A", 5, 7.0, 12), today))
    }
}
