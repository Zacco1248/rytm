package com.zacco.rytm.domain.load

import com.zacco.rytm.domain.model.Activity
import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.model.Source
import com.zacco.rytm.domain.time.RytmTime
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoadCalculatorTest {

    private val today = LocalDate.of(2026, 11, 2)

    private fun activity(date: LocalDate, minutes: Int, rpe: Int?, avgHr: Int? = null) = Activity(
        id = "$date-$minutes",
        type = ActivityType.SQUASH,
        start = date.atTime(LocalTime.of(18, 0)).atZone(RytmTime.zone).toInstant(),
        durationMin = minutes,
        rpe = rpe,
        avgHr = avgHr,
        kcal = null,
        source = Source.HEALTH_CONNECT,
    )

    @Test
    fun `srpe is rpe times minutes`() {
        assertEquals(420, activity(today, 60, 7).load)
        assertNull(activity(today, 60, null).load)
    }

    @Test
    fun `steady load gives acwr of one`() {
        val loads = (0L until 28L).associate { today.minusDays(it) to 100 }

        val s = LoadCalculator.summary(loads, today)

        assertEquals(700, s.acute7d)
        assertEquals(700, s.chronicWeeklyAvg)
        assertEquals(1.0, s.acwr!!, 1e-9)
        assertEquals(AcwrZone.SAFE, s.zone)
        assertTrue(s.reliable)
    }

    @Test
    fun `spike in last week gives high acwr`() {
        val loads = (0L until 28L).associate { today.minusDays(it) to if (it < 7) 300 else 100 }

        val s = LoadCalculator.summary(loads, today)

        // ostre 2100, przewlekłe (2100 + 2100) / 4 = 1050 → ACWR 2,0
        assertEquals(2.0, s.acwr!!, 1e-9)
        assertEquals(AcwrZone.HIGH, s.zone)
    }

    @Test
    fun `short history is flagged as unreliable`() {
        val s = LoadCalculator.summary(mapOf(today to 300, today.minusDays(1) to 200), today)
        assertFalse(s.reliable)
        assertEquals(200, s.yesterday)
    }

    @Test
    fun `daily loads fall back to heart rate estimate`() {
        val profile = HrProfile(maxHr = 190, restingHr = 55)
        val loads = LoadCalculator.dailyLoads(
            listOf(activity(today, 60, 6), activity(today, 30, null, avgHr = 150)),
            profile,
        )
        val estimatedRpe = RpeEstimator.fromHeartRate(150, profile)
        assertEquals(360 + estimatedRpe * 30, loads[today])
    }

    @Test
    fun `rpe estimate grows with heart rate and stays in range`() {
        val p = HrProfile(190, 55)
        assertEquals(1, RpeEstimator.fromHeartRate(60, p))
        assertTrue(RpeEstimator.fromHeartRate(120, p) < RpeEstimator.fromHeartRate(170, p))
        assertEquals(10, RpeEstimator.fromHeartRate(190, p))
    }
}
