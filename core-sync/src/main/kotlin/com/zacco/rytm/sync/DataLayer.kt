package com.zacco.rytm.sync

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import java.util.UUID

/** Zapis do Data Layer. DataItemy są synchronizowane przez Google Play Services także po ponownym połączeniu. */
object DataLayer {

    suspend fun publishSnapshot(context: Context, snapshot: WatchSnapshot) {
        val request = PutDataMapRequest.create(DataLayerPaths.SNAPSHOT).apply {
            dataMap.putByteArray(DataLayerPaths.KEY_JSON, WatchCodec.encode(snapshot))
            // Znacznik czasu wymusza dostarczenie, nawet gdy treść się nie zmieniła.
            dataMap.putLong("t", snapshot.generatedAt)
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request).await()
    }

    suspend fun sendEvent(context: Context, event: WatchEvent) {
        val request = PutDataMapRequest.create(DataLayerPaths.EVENT_PREFIX + UUID.randomUUID()).apply {
            dataMap.putByteArray(DataLayerPaths.KEY_JSON, WatchCodec.encode(event))
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request).await()
    }
}
