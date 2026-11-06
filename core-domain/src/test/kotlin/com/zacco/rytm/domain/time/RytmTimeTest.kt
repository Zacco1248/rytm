package com.zacco.rytm.domain.time

import java.time.LocalDate
import java.time.ZonedDateTime
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RytmTimeTest {
    private fun at(h: Int, m: Int) = ZonedDateTime.of(2026, 11, 7, h, m, 0, 0, RytmTime.zone)

    @Test
    fun `dzień kończy się o 4 rano`() {
        assertEquals(LocalDate.of(2026, 11, 6), RytmTime.appDay(at(0, 30)))
        assertEquals(LocalDate.of(2026, 11, 6), RytmTime.appDay(at(3, 59)))
        assertEquals(LocalDate.of(2026, 11, 7), RytmTime.appDay(at(4, 0)))
        assertEquals(LocalDate.of(2026, 11, 7), RytmTime.appDay(at(23, 59)))
    }
}
