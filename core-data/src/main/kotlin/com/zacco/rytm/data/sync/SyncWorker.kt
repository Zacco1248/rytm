package com.zacco.rytm.data.sync

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zacco.rytm.data.RytmData
import com.zacco.rytm.data.watch.WatchBridge
import java.util.concurrent.TimeUnit

/**
 * Synchronizacja co 15 minut w tle. Wymaga uprawnienia READ_HEALTH_DATA_IN_BACKGROUND;
 * bez niego Health Connect odrzuci odczyt, a dane odświeżą się przy otwarciu aplikacji.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (HealthConnectClient.getSdkStatus(applicationContext) != HealthConnectClient.SDK_AVAILABLE) return Result.success()
        return try {
            val data = RytmData.get(applicationContext)
            val result = HealthConnectSync(HealthConnectClient.getOrCreate(applicationContext), data.db).sync()
            Log.i(TAG, "Synchronizacja: ${result.daysUpdated} dni, pełny import: ${result.fullImport}")
            // Przy każdej synchronizacji odświeżamy też zegarek (m.in. nowy dzień po północy).
            WatchBridge.publish(applicationContext)
            Result.success()
        } catch (e: SecurityException) {
            // Brak uprawnień (np. do odczytu w tle): nie ponawiamy, poczekamy na otwarcie aplikacji.
            Log.w(TAG, "Brak uprawnień: ${e.message}")
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Synchronizacja nieudana", e)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "RytmSync"
        private const val PERIODIC = "hc-sync-periodic"
        private const val NOW = "hc-sync-now"
        private const val MAX_RETRIES = 3

        fun schedulePeriodic(context: Context) {
            // 15 min to minimum WorkManagera. UPDATE zmienia okres także w już zainstalowanej aplikacji (wcześniej co godzinę).
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
