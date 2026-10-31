package com.zacco.rytm.domain.readiness

import com.zacco.rytm.domain.load.AcwrZone
import com.zacco.rytm.domain.load.LoadSummary
import com.zacco.rytm.domain.model.BodyArea
import com.zacco.rytm.domain.model.DailyCheckIn
import com.zacco.rytm.domain.time.RytmTime
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadinessCalculatorTest {

    private val date = LocalDate.of(2026, 11, 2)
    private fun onset(daysAgo: Long, time: LocalTime) =
        date.minusDays(daysAgo).atTime(time).atZone(RytmTime.zone).toInstant()

    private fun load(yesterday: Int, zone: AcwrZone = AcwrZone.SAFE) =
        LoadSummary(date, yesterday, 0, 2100, 2100, 1.0, zone, reliable = true)

    private fun input(
        sleepMin: Int? = 450,
        rhr: Int? = 52,
        hrv: Double? = 50.0,
        checkIn: DailyCheckIn? = DailyCheckIn(date, energy = 4, mood = 4),
        load: LoadSummary? = load(300),
        baselineDays: Int = 7,
    ) = ReadinessInput(
        date = date,
        sleepMin = sleepMin,
        sleepStart = onset(1, LocalTime.of(0, 15)),
        previousSleepStarts = (2L..8L).map { onset(it, LocalTime.of(0, 0)) },
        nightRestingHr = rhr,
        restingHrBaseline = List(baselineDays) { 52 },
        hrvRmssd = hrv,
        hrvBaseline = List(baselineDays) { 50.0 },
        checkIn = checkIn,
        load = load,
    )

    @Test
    fun `good day scores high`() {
        val r = ReadinessCalculator.compute(input())

        assertEquals(ReadinessLevel.HIGH, r.level)
        assertEquals(setOf(ReadinessComponent.entries.toSet()), setOf(r.components.keys))
        assertFalse(r.calibrating)
        assertTrue(r.flags.isEmpty())
    }

    @Test
    fun `bad night and elevated resting hr lower the score with flags`() {
        val good = ReadinessCalculator.compute(input())
        val bad = ReadinessCalculator.compute(input(sleepMin = 300, rhr = 60, hrv = 35.0))

        assertTrue(bad.score < good.score - 25)
        assertTrue(ReadinessFlag.SHORT_SLEEP in bad.flags)
        assertTrue(ReadinessFlag.RESTING_HR_ELEVATED in bad.flags)
        assertTrue(ReadinessFlag.HRV_LOW in bad.flags)
    }

    @Test
    fun `missing hrv redistributes weights`() {
        val r = ReadinessCalculator.compute(input(hrv = null))

        assertFalse(ReadinessComponent.HRV in r.components)
        val w = ReadinessWeights()
        val expected = r.components.entries.sumOf { (c, v) -> w.of(c) * v } / (1.0 - w.hrv)
        assertEquals(Math.round(expected).toInt(), r.score)
    }

    @Test
    fun `short baseline means calibration and no comparison`() {
        val r = ReadinessCalculator.compute(input(baselineDays = 2))

        assertTrue(r.calibrating)
        assertFalse(ReadinessComponent.RESTING_HR in r.components)
    }

    @Test
    fun `soreness low energy and heavy yesterday are flagged`() {
        val r = ReadinessCalculator.compute(
            input(
                checkIn = DailyCheckIn(date, energy = 2, mood = 3, soreAreas = setOf(BodyArea.SHOULDERS, BodyArea.BACK)),
                load = load(900, AcwrZone.HIGH),
            ),
        )

        assertTrue(r.flags.containsAll(listOf(ReadinessFlag.LOW_ENERGY, ReadinessFlag.SORENESS, ReadinessFlag.HEAVY_YESTERDAY, ReadinessFlag.ACWR_HIGH)))
        assertTrue(r.components.getValue(ReadinessComponent.LOAD) < 30)
    }

    @Test
    fun `irregular sleep onset is flagged across midnight`() {
        val deviation = ReadinessCalculator.sleepOnsetDeviationMin(
            onset(1, LocalTime.of(2, 30)),
            (2L..8L).map { onset(it, LocalTime.of(23, 50)) },
        )
        assertEquals(160.0, deviation!!, 1e-9)
    }
}
