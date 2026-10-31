package com.zacco.rytm.phone

import android.app.Application
import com.zacco.rytm.data.RytmData
import com.zacco.rytm.data.sync.SyncWorker
import com.zacco.rytm.data.watch.WatchBridge
import com.zacco.rytm.domain.time.RytmTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.LocalDate

class RytmApplication : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        SyncWorker.schedulePeriodic(this)

        // Póki proces żyje, każda zmiana danych (wpis, synchronizacja) od razu trafia na zegarek.
        val data = RytmData.get(this)
        scope.launch {
            flow {
                while (true) {
                    emit(LocalDate.now(RytmTime.zone))
                    delay(60_000)
                }
            }.distinctUntilChanged()
                .flatMapLatest { day -> data.coach.observe(day) }
                .debounce(1_500)
                .collect { d -> runCatching { WatchBridge.publish(this@RytmApplication, d) } }
        }
    }
}
