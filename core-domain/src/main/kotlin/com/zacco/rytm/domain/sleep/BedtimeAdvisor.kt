package com.zacco.rytm.domain.sleep

import com.zacco.rytm.domain.model.Intensity
import com.zacco.rytm.domain.profile.SleepTarget
import com.zacco.rytm.domain.readiness.ReadinessLevel
import java.time.LocalTime

/** Rekomendowana pora snu: początek okna z celu, wcześniej po ciężkim dniu, przy niskiej gotowości albo przed intensywnym jutrem. */
object BedtimeAdvisor {

    fun suggest(
        target: SleepTarget,
        readiness: ReadinessLevel?,
        tomorrowIntensity: Intensity,
        heavyDayToday: Boolean,
    ): LocalTime {
        val needsMore = readiness == ReadinessLevel.LOW || heavyDayToday || tomorrowIntensity == Intensity.HIGH
        return if (needsMore) target.onsetFrom.minusMinutes(EARLIER_BY_MIN) else target.onsetFrom
    }

    private const val EARLIER_BY_MIN = 30L
}
