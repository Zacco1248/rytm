package com.zacco.rytm.wear.hrv

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zacco.rytm.domain.hrv.HrvCalculator
import com.zacco.rytm.domain.hrv.HrvResult
import com.zacco.rytm.domain.hrv.IbiSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

class HrvViewModel(application: Application) : AndroidViewModel(application) {

    private val source: IbiSource = createIbiSource(application)

    private val _uiState = MutableStateFlow<HrvUiState>(
        source.unavailableReason?.let { HrvUiState.Unavailable(it) } ?: HrvUiState.Idle,
    )
    val uiState: StateFlow<HrvUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true || source.unavailableReason != null) return
        job = viewModelScope.launch {
            _uiState.value = HrvUiState.Connecting
            val batches = mutableListOf<IbiBatch>()
            val accepted = mutableListOf<IbiSample>()
            var startedAt: Long? = null
            var lastHr: Int? = null

            val ticker = launch {
                while (true) {
                    startedAt?.let { start ->
                        val elapsed = SystemClock.elapsedRealtime() - start
                        _uiState.value = if (elapsed < WARMUP_MS) {
                            HrvUiState.WarmingUp(secondsLeft = ((WARMUP_MS - elapsed) / 1000).toInt() + 1, hrBpm = lastHr)
                        } else {
                            HrvUiState.Measuring(
                                secondsLeft = ((TOTAL_MS - elapsed).coerceAtLeast(0) / 1000).toInt(),
                                hrBpm = lastHr,
                                beats = accepted.size,
                            )
                        }
                    }
                    delay(250)
                }
            }

            try {
                withTimeout(CONNECT_TIMEOUT_MS + TOTAL_MS) {
                    source.measure()
                        .transformWhile { batch ->
                            val now = SystemClock.elapsedRealtime()
                            val start = startedAt ?: now.also { startedAt = it }
                            batch.hrBpm?.takeIf { it > 0 }?.let { lastHr = it }
                            batches += batch
                            if (now - start >= WARMUP_MS) accepted += batch.ibis
                            emit(batch)
                            now - start < TOTAL_MS
                        }
                        .collect { }
                }
                ticker.cancel()
                val result = HrvCalculator.compute(accepted)
                val file = save(result, accepted, batches)
                _uiState.value = HrvUiState.Done(result, file.name)
            } catch (e: Exception) {
                ticker.cancel()
                Log.w(TAG, "Pomiar przerwany", e)
                _uiState.value = HrvUiState.Error(e.message ?: e::class.simpleName.orEmpty())
            }
        }
    }

    fun reset() {
        job?.cancel()
        _uiState.value = source.unavailableReason?.let { HrvUiState.Unavailable(it) } ?: HrvUiState.Idle
    }

    /** Zapis surowych danych do porównań w fazie 0. Odczyt: adb pull (patrz docs/faza-0.md). */
    private suspend fun save(result: HrvResult, samples: List<IbiSample>, batches: List<IbiBatch>): File =
        withContext(Dispatchers.IO) {
            val now = Instant.now()
            val json = JSONObject()
                .put("measuredAt", now.toString())
                .put("warmupMs", WARMUP_MS)
                .put("durationMs", TOTAL_MS - WARMUP_MS)
                .put(
                    "result",
                    JSONObject()
                        .put("rmssdMs", result.rmssdMs)
                        .put("lnRmssd", result.lnRmssd)
                        .put("meanHrBpm", result.meanHrBpm)
                        .put("beatsTotal", result.beatsTotal)
                        .put("beatsAccepted", result.beatsAccepted)
                        .put("pairsUsed", result.pairsUsed)
                        .put("quality", result.quality.name),
                )
                .put("ibiMs", JSONArray(samples.map { it.ms }))
                .put("ibiSensorValid", JSONArray(samples.map { it.sensorValid }))
                .put(
                    "batches",
                    JSONArray(
                        batches.map { b ->
                            JSONObject()
                                .put("t", b.timestampMs)
                                .put("hr", b.hrBpm)
                                .put("hrStatus", b.hrStatus)
                                .put("ibi", JSONArray(b.ibis.map { it.ms }))
                                .put("ibiStatus", JSONArray(b.rawIbiStatuses))
                        },
                    ),
                )
            val dir = File(getApplication<Application>().filesDir, "hrv").apply { mkdirs() }
            File(dir, "hrv-${now.epochSecond}.json").apply { writeText(json.toString(2)) }
                .also { Log.i(TAG, "HRV zapisane: ${it.absolutePath} rmssd=${result.rmssdMs} quality=${result.quality}") }
        }

    private companion object {
        const val TAG = "RytmHrv"
        const val WARMUP_MS = 5_000L
        const val TOTAL_MS = WARMUP_MS + 60_000L
        const val CONNECT_TIMEOUT_MS = 15_000L
    }
}

sealed interface HrvUiState {
    data object Idle : HrvUiState
    data object Connecting : HrvUiState
    data class WarmingUp(val secondsLeft: Int, val hrBpm: Int?) : HrvUiState
    data class Measuring(val secondsLeft: Int, val hrBpm: Int?, val beats: Int) : HrvUiState
    data class Done(val result: HrvResult, val fileName: String) : HrvUiState
    data class Error(val message: String) : HrvUiState
    data class Unavailable(val reason: String) : HrvUiState
}
