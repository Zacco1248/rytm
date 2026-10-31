package com.zacco.rytm.domain.hrv

/**
 * Pojedynczy odstęp między uderzeniami serca (inter-beat interval).
 *
 * @param ms długość odstępu w milisekundach
 * @param sensorValid czy sensor oznaczył próbkę jako poprawną (np. status IBI w Samsung Health Sensor SDK)
 */
data class IbiSample(val ms: Int, val sensorValid: Boolean = true)
