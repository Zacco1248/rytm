package com.zacco.rytm.domain.sleep

import com.zacco.rytm.domain.model.Intensity
import com.zacco.rytm.domain.profile.SleepTarget
import com.zacco.rytm.domain.readiness.ReadinessLevel
import org.junit.jupiter.api.Test
import java.time.LocalTime
import kotlin.test.assertEquals

class BedtimeAdvisorTest {
    @Test
    fun `normal day starts the target window and hard days go earlier`() {
        val target = SleepTarget()
        assertEquals(LocalTime.MIDNIGHT, BedtimeAdvisor.suggest(target, ReadinessLevel.HIGH, Intensity.MODERATE, heavyDayToday = false))
        assertEquals(LocalTime.of(23, 30), BedtimeAdvisor.suggest(target, ReadinessLevel.HIGH, Intensity.HIGH, heavyDayToday = false))
        assertEquals(LocalTime.of(23, 30), BedtimeAdvisor.suggest(target, ReadinessLevel.LOW, Intensity.LOW, heavyDayToday = false))
    }
}
