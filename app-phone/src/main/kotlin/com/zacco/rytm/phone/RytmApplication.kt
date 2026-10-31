package com.zacco.rytm.phone

import android.app.Application
import com.zacco.rytm.data.sync.SyncWorker

class RytmApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SyncWorker.schedulePeriodic(this)
    }
}
