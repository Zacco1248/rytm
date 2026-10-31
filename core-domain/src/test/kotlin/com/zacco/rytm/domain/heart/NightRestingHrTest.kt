package com.zacco.rytm.domain.heart

import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NightRestingHrTest {

    private val start = Instant.parse("2026-11-01T23:00:00Z")
    private fun at(min: Long, bpm: Int) = HrSample(start.plus(Duration.ofMinutes(min)), bpm)

    @Test
    fun `lowest 30 minute average during sleep`() {
        val samples = listOf(at(0, 60), at(10, 58), at(20, 57), at(30, 52), at(40, 50), at(50, 51), at(60, 56), at(70, 59))

        val rhr = NightRestingHr.compute(samples, start, start.plus(Duration.ofHours(7)))

        assertEquals(51, rhr) // (52 + 50 + 51) / 3
    }

    @Test
    fun `samples outside sleep window are ignored`() {
        val samples = listOf(at(-60, 40), at(-50, 40), at(-40, 40), at(0, 60), at(10, 60), at(20, 60))

        assertEquals(60, NightRestingHr.compute(samples, start, start.plus(Duration.ofHours(7))))
    }

    @Test
    fun `too sparse samples give null`() {
        val samples = listOf(at(0, 55), at(60, 54), at(120, 53))

        assertNull(NightRestingHr.compute(samples, start, start.plus(Duration.ofHours(7))))
    }
}
