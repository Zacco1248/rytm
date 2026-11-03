package com.zacco.rytm.wear.data

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.zacco.rytm.sync.DataLayerPaths
import com.zacco.rytm.sync.WatchCodec

/** Odbiera stan dnia z telefonu, także gdy aplikacja na zegarku nie jest otwarta. */
class SnapshotListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { e ->
            if (e.type != DataEvent.TYPE_CHANGED || e.dataItem.uri.path != DataLayerPaths.SNAPSHOT) return@forEach
            val bytes = DataMapItem.fromDataItem(e.dataItem).dataMap.getByteArray(DataLayerPaths.KEY_JSON) ?: return@forEach
            runCatching { WatchCodec.decodeSnapshot(bytes) }.getOrNull()?.let { WatchStore.save(this, it) }
        }
    }
}
