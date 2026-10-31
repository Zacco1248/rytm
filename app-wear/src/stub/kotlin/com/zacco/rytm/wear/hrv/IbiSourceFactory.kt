package com.zacco.rytm.wear.hrv

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Wariant bez Samsung Health Sensor SDK (brak AAR w app-wear/libs). */
fun createIbiSource(@Suppress("UNUSED_PARAMETER") context: Context): IbiSource = object : IbiSource {
    override val unavailableReason =
        "Ta wersja jest zbudowana bez Samsung Health Sensor SDK. Wrzuć AAR do app-wear/libs i zbuduj ponownie."

    override fun measure(): Flow<IbiBatch> = emptyFlow()
}
