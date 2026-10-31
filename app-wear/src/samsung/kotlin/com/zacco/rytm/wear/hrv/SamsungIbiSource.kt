package com.zacco.rytm.wear.hrv

import android.content.Context
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey
import com.zacco.rytm.domain.hrv.IbiSample
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * IBI z Samsung Health Sensor SDK (tracker HEART_RATE_CONTINUOUS, SDK 1.3.0+).
 * Wymaga trybu deweloperskiego Health Platform na zegarku (patrz docs/faza-0.md).
 */
class SamsungIbiSource(private val context: Context) : IbiSource {

    override val unavailableReason: String? = null

    override fun measure(): Flow<IbiBatch> = callbackFlow {
        var tracker: HealthTracker? = null
        lateinit var service: HealthTrackingService

        val eventListener = object : HealthTracker.TrackerEventListener {
            override fun onDataReceived(dataPoints: List<DataPoint>) {
                dataPoints.forEach { trySend(it.toBatch()) }
            }

            override fun onFlushCompleted() = Unit

            override fun onError(error: HealthTracker.TrackerError) {
                val hint = when (error) {
                    HealthTracker.TrackerError.PERMISSION_ERROR -> "brak uprawnienia do czujników"
                    HealthTracker.TrackerError.SDK_POLICY_ERROR -> "włącz tryb deweloperski Health Platform"
                    else -> error.name
                }
                close(IllegalStateException("Błąd sensora: $hint"))
            }
        }

        service = HealthTrackingService(
            object : ConnectionListener {
                override fun onConnectionSuccess() {
                    val supported = service.trackingCapability.supportHealthTrackerTypes
                    if (HealthTrackerType.HEART_RATE_CONTINUOUS !in supported) {
                        close(IllegalStateException("Zegarek nie obsługuje HEART_RATE_CONTINUOUS"))
                        return
                    }
                    tracker = service.getHealthTracker(HealthTrackerType.HEART_RATE_CONTINUOUS).also {
                        it.setEventListener(eventListener)
                    }
                }

                override fun onConnectionEnded() = Unit

                override fun onConnectionFailed(e: HealthTrackerException) {
                    close(IllegalStateException("Nie udało się połączyć z Samsung Health (kod ${e.errorCode})", e))
                }
            },
            context,
        )
        service.connectService()

        awaitClose {
            tracker?.unsetEventListener()
            service.disconnectService()
        }
    }

    private fun DataPoint.toBatch(): IbiBatch {
        val ibis: List<Int> = getValue(ValueKey.HeartRateSet.IBI_LIST).orEmpty()
        val statuses: List<Int> = getValue(ValueKey.HeartRateSet.IBI_STATUS_LIST).orEmpty()
        return IbiBatch(
            timestampMs = timestamp,
            hrBpm = getValue(ValueKey.HeartRateSet.HEART_RATE),
            hrStatus = getValue(ValueKey.HeartRateSet.HEART_RATE_STATUS),
            // Status 0 oznacza poprawny odstęp; 0 ms to brak wartości.
            ibis = ibis.mapIndexed { i, ms -> IbiSample(ms, sensorValid = statuses.getOrNull(i) == 0 && ms > 0) },
            rawIbiStatuses = statuses,
        )
    }
}
