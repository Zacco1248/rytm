package com.zacco.rytm.domain.sleep

import com.zacco.rytm.domain.time.RytmTime
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ManualSleepTest {
    private val day = LocalDate.of(2026, 11, 4)

    @Test
    fun `parses common time formats`() {
        assertEquals(LocalTime.of(23, 45), ManualSleep.parseTime("23:45"))
        assertEquals(LocalTime.of(23, 45), ManualSleep.parseTime("2345"))
        assertEquals(LocalTime.of(7, 5), ManualSleep.parseTime("7:05"))
        assertEquals(LocalTime.of(7, 0), ManualSleep.parseTime("7"))
        assertNull(ManualSleep.parseTime("25:00"))
        assertNull(ManualSleep.parseTime(""))
    }

    @Test
    fun `onset after wake on the clock starts the evening before`() {
        val w = assertNotNull(ManualSleep.window(day, LocalTime.of(23, 30), LocalTime.of(7, 0)))
        assertEquals(day.minusDays(1), w.first.atZone(RytmTime.zone).toLocalDate())
        assertEquals(day, w.second.atZone(RytmTime.zone).toLocalDate())
        assertEquals(450, Duration.between(w.first, w.second).toMinutes())
    }

    @Test
    fun `onset after midnight stays on the wake day`() {
        val w = assertNotNull(ManualSleep.window(day, LocalTime.of(0, 40), LocalTime.of(8, 10)))
        assertEquals(day, w.first.atZone(RytmTime.zone).toLocalDate())
        assertEquals(450, Duration.between(w.first, w.second).toMinutes())
    }
}
