package com.zacco.rytm.domain.heart

import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DayRestingHrTest {

    private val dayStart = Instant.parse("2026-11-01T23:00:00Z")
    private val dayEnd = dayStart.plus(Duration.ofDays(1))
    private fun minute(m: Long, bpm: Int) = HrSample(dayStart.plus(Duration.ofMinutes(m)), bpm)

    @Test
    fun `lowest calm half hour outside workouts and recovery`() {
        // 08:00–09:00 spokojnie 64, 12:00–13:00 trening i zaraz po nim 58 (w czasie regeneracji), wieczór 61.
        val calm = (480L until 540L).map { minute(it, 64) }
        val workout = (720L until 780L).map { minute(it, 140) }
        val afterWorkout = (780L until 820L).map { minute(it, 58) }
        val evening = (1200L until 1260L).map { minute(it, 61) }
        val workouts = listOf(dayStart.plus(Duration.ofMinutes(720)) to dayStart.plus(Duration.ofMinutes(780)))

        assertEquals(61, DayRestingHr.compute(calm + workout + afterWorkout + evening, dayStart, dayEnd, workouts))
    }

    @Test
    fun `sparse samples give no value`() {
        val sparse = (0L until 600L step 10).map { minute(it, 55) }
        assertNull(DayRestingHr.compute(sparse, dayStart, dayEnd, emptyList()))
    }
}
