package com.zacco.rytm.domain.readiness

import java.time.LocalDate

data class Readiness(
    val date: LocalDate,
    /** 0–100 */
    val score: Int,
    val level: ReadinessLevel,
    /** Składowe 0–100; brakujące nie występują (ich waga przeszła na pozostałe). */
    val components: Map<ReadinessComponent, Int>,
    val flags: List<ReadinessFlag>,
    /** Baseline'y jeszcze się budują (pierwsze 7–14 dni). */
    val calibrating: Boolean,
)

enum class ReadinessComponent { SLEEP, RESTING_HR, HRV, SUBJECTIVE, LOAD }

enum class ReadinessLevel { LOW, MODERATE, HIGH }

enum class ReadinessFlag {
    SHORT_SLEEP,
    IRREGULAR_SLEEP,
    RESTING_HR_ELEVATED,
    HRV_LOW,
    LOW_ENERGY,
    SORENESS,
    HEAVY_YESTERDAY,
    ACWR_HIGH,
    NO_CHECK_IN,
}

/**
 * Wagi składowych (sekcja 3.4). Korekta po fazie 0 (2026-10-31): tętno z zegarka w nocy jest gęste (ok. 1 próbka/min),
 * więc tętno spoczynkowe dostaje więcej, a HRV z jednego 60-s pomiaru mniej.
 */
data class ReadinessWeights(
    val sleep: Double = 0.30,
    val restingHr: Double = 0.25,
    val hrv: Double = 0.15,
    val subjective: Double = 0.20,
    val load: Double = 0.10,
) {
    fun of(component: ReadinessComponent): Double = when (component) {
        ReadinessComponent.SLEEP -> sleep
        ReadinessComponent.RESTING_HR -> restingHr
        ReadinessComponent.HRV -> hrv
        ReadinessComponent.SUBJECTIVE -> subjective
        ReadinessComponent.LOAD -> load
    }
}
