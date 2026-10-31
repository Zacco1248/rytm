package com.zacco.rytm.domain.ingest

import com.zacco.rytm.domain.model.Activity
import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.model.Source
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IngestTest {

    private val samsung = "com.sec.android.app.shealth"
    private val other = "com.example.tracker"

    @Test
    fun `noc przypisana do dnia końca i sklejona z kawałków`() {
        val sessions = listOf(
            // 00:10–03:00 i 03:30–07:40 czasu warszawskiego (UTC+1 w listopadzie)
            SleepSession(Instant.parse("2026-11-02T23:10:00Z"), Instant.parse("2026-11-03T02:00:00Z"), null, samsung),
            SleepSession(Instant.parse("2026-11-03T02:30:00Z"), Instant.parse("2026-11-03T06:40:00Z"), 240, samsung),
            // drzemka po południu nie jest nocą
            SleepSession(Instant.parse("2026-11-03T15:00:00Z"), Instant.parse("2026-11-03T15:40:00Z"), null, samsung),
        )
        val night = Ingest.nightFor(LocalDate.of(2026, 11, 3), sessions)!!
        assertEquals(170 + 240, night.sleepMin)
        assertEquals(Instant.parse("2026-11-02T23:10:00Z"), night.start)
        assertEquals(Instant.parse("2026-11-03T06:40:00Z"), night.end)
    }

    @Test
    fun `noc bierze tylko najbardziej zaufane źródło`() {
        val sessions = listOf(
            SleepSession(Instant.parse("2026-11-02T23:00:00Z"), Instant.parse("2026-11-03T06:00:00Z"), null, other),
            SleepSession(Instant.parse("2026-11-02T23:30:00Z"), Instant.parse("2026-11-03T06:30:00Z"), 400, samsung),
        )
        assertEquals(400, Ingest.nightFor(LocalDate.of(2026, 11, 3), sessions)!!.sleepMin)
        assertNull(Ingest.nightFor(LocalDate.of(2026, 11, 4), sessions))
    }

    @Test
    fun `duplikat treningu z innego źródła znika, a RPE zostaje`() {
        val fromSamsung = activity("s", "2026-11-03T18:00:00Z", 90, rpe = null)
        val fromOther = activity("o", "2026-11-03T18:05:00Z", 80, rpe = 7)
        val separate = activity("x", "2026-11-03T21:00:00Z", 30, rpe = null)
        val result = Ingest.dedupActivities(listOf(fromOther to other, fromSamsung to samsung, separate to other))
        assertEquals(listOf("s", "x"), result.map { it.id })
        assertEquals(7, result.first().rpe)
    }

    private fun activity(id: String, start: String, min: Int, rpe: Int?) =
        Activity(id, ActivityType.VOLLEYBALL, Instant.parse(start), min, rpe, null, null, Source.HEALTH_CONNECT)
}
