package com.zacco.rytm.domain.hrv

import kotlinx.serialization.Serializable

@Serializable
data class HrvResult(
    /** RMSSD w ms; null, gdy zostało za mało poprawnych par. */
    val rmssdMs: Double?,
    /** ln(RMSSD), wygodne do porównań z baseline (rozkład bliższy normalnemu). */
    val lnRmssd: Double?,
    val meanHrBpm: Double?,
    val beatsTotal: Int,
    val beatsAccepted: Int,
    val pairsUsed: Int,
    val quality: HrvQuality,
) {
    val acceptedRatio: Double get() = if (beatsTotal == 0) 0.0 else beatsAccepted.toDouble() / beatsTotal
}

@Serializable
enum class HrvQuality { GOOD, ACCEPTABLE, POOR }
