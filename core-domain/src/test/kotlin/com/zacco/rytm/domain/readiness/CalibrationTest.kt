package com.zacco.rytm.domain.readiness

import com.zacco.rytm.domain.load.AcwrZone
import com.zacco.rytm.domain.load.LoadSummary
import com.zacco.rytm.domain.model.DailyMetrics
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalibrationTest {
    private val today = LocalDate.of(2026, 11, 3)
    private val load = LoadSummary(today, 0, 0, 0, 0, null, AcwrZone.UNKNOWN, reliable = false, historyDays = 10)

    private fun m(back: Long, sleep: Int? = 450, night: Int? = 52, day: Int? = null) =
        DailyMetrics(today.minusDays(back), sleep, null, null, night, 6000, null, day)

    @Test
    fun explainsEachMissingDay() {
        val metrics = listOf(m(1), m(2, night = null, day = 60), m(3, sleep = 400, night = null), m(5))
        val s = CalibrationStatus.of(today, metrics, emptyList(), load, needDays = 7)
        assertEquals(14, s.days.size)
        assertNull(s.days[0].gap)
        assertEquals(CalibrationGap.DAY_ONLY, s.days[1].gap)
        assertEquals(CalibrationGap.NO_HR, s.days[2].gap)
        assertEquals(CalibrationGap.NO_DATA, s.days[3].gap)
        assertEquals(3, s.counted)
        assertEquals(2, s.restingHrDays)
        assertFalse(s.restingHrReady)
        assertTrue(s.hrvReady, "HRV bez dzisiejszego pomiaru nie blokuje")
        assertEquals(10, s.loadHistoryDays)
    }

    @Test
    fun sevenNightsInFourteenDaysAreEnough() {
        val metrics = (1L..14L).filter { it % 2 == 0L }.map { m(it) } + m(1)
        val s = CalibrationStatus.of(today, metrics, emptyList(), load, needDays = 7)
        assertTrue(s.restingHrReady)
    }
}
