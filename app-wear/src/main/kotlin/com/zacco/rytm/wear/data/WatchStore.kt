package com.zacco.rytm.wear.data

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.zacco.rytm.sync.DataLayer
import com.zacco.rytm.sync.DataLayerPaths
import com.zacco.rytm.sync.WatchCodec
import com.zacco.rytm.sync.WatchEvent
import com.zacco.rytm.sync.WatchSnapshot
import com.zacco.rytm.wear.complication.ReadinessComplicationService
import com.zacco.rytm.wear.tile.HabitsTileService
import com.zacco.rytm.wear.tile.TodayTileService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId

/**
 * Ostatni stan dnia z telefonu, zapamiętany na zegarku (kafelki muszą działać bez telefonu w pobliżu).
 * Wpisy z zegarka od razu zmieniają lokalną kopię, a telefon po zapisie odsyła pełny, przeliczony stan.
 */
object WatchStore {

    private const val TAG = "RytmWatch"
    private const val PREFS = "watch-store"
    private const val KEY = "snapshot"

    private val state = MutableStateFlow<WatchSnapshot?>(null)
    @Volatile private var loaded = false

    fun observe(context: Context): StateFlow<WatchSnapshot?> {
        load(context)
        return state.asStateFlow()
    }

    fun current(context: Context): WatchSnapshot? {
        load(context)
        return state.value
    }

    /** Dzisiejsza data na zegarku; stan z innego dnia jest nieaktualny. */
    fun today(): String = LocalDate.now(ZoneId.systemDefault()).toString()

    fun isStale(s: WatchSnapshot): Boolean = s.date != today()

    fun save(context: Context, snapshot: WatchSnapshot) {
        val current = state.value
        // Starszy stan (np. opóźniona dostawa) nie nadpisuje nowszego.
        if (current != null && current.generatedAt > snapshot.generatedAt && current.date == snapshot.date) return
        write(context, snapshot)
    }

    /** Wysyła wpis na telefon i od razu pokazuje jego skutek na zegarku. */
    suspend fun send(context: Context, event: WatchEvent) {
        current(context)?.let { s -> write(context, applyLocally(s, event)) }
        runCatching { DataLayer.sendEvent(context, event) }
            .onFailure { Log.w(TAG, "Nie wysłano wpisu: ${it.message}") }
    }

    /** Przy starcie aplikacji: pobiera ostatni stan zapisany w Data Layer (np. gdy zegarek był wyłączony). */
    suspend fun pull(context: Context) {
        runCatching {
            val uri = Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(DataLayerPaths.SNAPSHOT).build()
            val items = Wearable.getDataClient(context).getDataItems(uri).await()
            try {
                items.mapNotNull { item ->
                    DataMapItem.fromDataItem(item).dataMap.getByteArray(DataLayerPaths.KEY_JSON)
                        ?.let { runCatching { WatchCodec.decodeSnapshot(it) }.getOrNull() }
                }.maxByOrNull { it.generatedAt }?.let { save(context, it) }
            } finally {
                items.release()
            }
        }.onFailure { Log.i(TAG, "Brak stanu z telefonu: ${it.message}") }
    }

    private fun applyLocally(s: WatchSnapshot, e: WatchEvent): WatchSnapshot = when (e) {
        is WatchEvent.HabitToggle -> s.copy(habits = s.habits.map { if (it.id == e.habitId) it.copy(done = e.done) else it })
        is WatchEvent.CheckIn -> s.copy(checkInDone = true)
        is WatchEvent.Rpe -> s.copy(toRate = s.toRate.filterNot { it.id == e.activityId })
        is WatchEvent.OtherActivity -> s
    }

    private fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            state.value = json?.let { runCatching { WatchCodec.decodeSnapshot(it.encodeToByteArray()) }.getOrNull() }
            loaded = true
        }
    }

    private fun write(context: Context, snapshot: WatchSnapshot) {
        load(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, WatchCodec.encode(snapshot).decodeToString())
            .apply()
        state.value = snapshot
        refreshSurfaces(context)
    }

    fun refreshSurfaces(context: Context) {
        runCatching {
            val updater = TileService.getUpdater(context)
            updater.requestUpdate(TodayTileService::class.java)
            updater.requestUpdate(HabitsTileService::class.java)
            ComplicationDataSourceUpdateRequester
                .create(context, ComponentName(context, ReadinessComplicationService::class.java))
                .requestUpdateAll()
        }.onFailure { Log.w(TAG, "Nie odświeżono kafelków", it) }
    }
}
