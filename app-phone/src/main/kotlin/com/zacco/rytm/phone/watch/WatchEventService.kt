package com.zacco.rytm.phone.watch

import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.zacco.rytm.data.watch.WatchBridge
import com.zacco.rytm.sync.DataLayerPaths
import com.zacco.rytm.sync.WatchCodec
import com.zacco.rytm.sync.WatchEvent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

/** Odbiera wpisy z zegarka (nawyki, check-in, RPE, inna aktywność), zapisuje je i odsyła nowy stan dnia. */
class WatchEventService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        // Bufor jest ważny tylko w tym wywołaniu, więc najpierw kopiujemy dane.
        val events = mutableListOf<Pair<Uri, WatchEvent?>>()
        dataEvents.forEach { e ->
            val item = e.dataItem
            if (e.type != DataEvent.TYPE_CHANGED || item.uri.path?.startsWith(DataLayerPaths.EVENT_PREFIX) != true) return@forEach
            val bytes = DataMapItem.fromDataItem(item).dataMap.getByteArray(DataLayerPaths.KEY_JSON)
            val event = bytes?.let { runCatching { WatchCodec.decodeEvent(it) }.getOrNull() }
            events += item.uri to event
        }
        if (events.isEmpty()) return

        // onDataChanged działa w wątku w tle, więc można tu poczekać na zapis do bazy.
        runBlocking {
            val client = Wearable.getDataClient(this@WatchEventService)
            for ((uri, event) in events) {
                if (event != null) {
                    runCatching { WatchBridge.apply(this@WatchEventService, event) }
                        .onFailure { Log.w(TAG, "Nie zapisano wpisu z zegarka", it) }
                }
                runCatching { client.deleteDataItems(uri).await() }
            }
            WatchBridge.publish(this@WatchEventService)
        }
    }

    private companion object {
        const val TAG = "RytmWatch"
    }
}
