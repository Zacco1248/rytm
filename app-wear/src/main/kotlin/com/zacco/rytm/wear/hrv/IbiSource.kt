package com.zacco.rytm.wear.hrv

import com.zacco.rytm.domain.hrv.IbiSample
import kotlinx.coroutines.flow.Flow

/** Strumień danych z sensora tętna z odstępami między uderzeniami (IBI). */
interface IbiSource {
    /** null, gdy źródło jest dostępne; w przeciwnym razie powód po polsku. */
    val unavailableReason: String?

    /** Startuje pomiar przy subskrypcji, kończy przy anulowaniu. */
    fun measure(): Flow<IbiBatch>
}

data class IbiBatch(
    val timestampMs: Long,
    val hrBpm: Int?,
    val hrStatus: Int?,
    val ibis: List<IbiSample>,
    /** Surowe statusy IBI z sensora, do zapisu w pliku diagnostycznym. */
    val rawIbiStatuses: List<Int>,
)
