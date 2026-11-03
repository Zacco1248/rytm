package com.zacco.rytm.data.ai

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zacco.rytm.data.RytmData
import com.zacco.rytm.data.watch.WatchBridge
import com.zacco.rytm.domain.time.RytmTime
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Wieczorna analiza raz dziennie o porze z ustawień (domyślnie 21:30). Po wykonaniu planuje się na kolejny wieczór.
 * Z sieci korzysta tylko przy kluczu API; bez niego zapisuje podsumowanie z reguł.
 */
class EveningWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val data = RytmData.get(applicationContext)
        val ai = data.ai
        try {
            if (ai.settings.value.eveningEnabled) {
                data.profiles.ensureLoaded()
                val today = LocalDate.now(RytmTime.zone)
                val saved = ai.evening(today)
                notify(applicationContext, saved.review.summary)
                WatchBridge.publish(applicationContext)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wieczorna analiza nieudana", e)
        } finally {
            schedule(applicationContext, replace = true, afterRun = true)
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "RytmEvening"
        private const val WORK = "evening-review"
        private const val CHANNEL = "evening"
        private const val NOTIFICATION_ID = 21

        /** Planuje najbliższą wieczorną analizę. Wywoływać przy starcie aplikacji i po zmianie ustawień. */
        fun schedule(context: Context, replace: Boolean = false, afterRun: Boolean = false) {
            val settings = RytmData.get(context).ai.settings.value
            val now = ZonedDateTime.now(RytmTime.zone)
            var next = now.toLocalDate().atStartOfDay(RytmTime.zone).plusMinutes(settings.eveningAtMin.toLong())
            // Tuż po wykonaniu zawsze kolejny dzień, nawet gdy zegar się przesunął.
            if (!next.isAfter(now.plusMinutes(if (afterRun) 60 else 0))) next = next.plusDays(1)
            val request = OneTimeWorkRequestBuilder<EveningWorker>()
                .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK,
                if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
        }

        @android.annotation.SuppressLint("MissingPermission") // sprawdzane na początku
        private fun notify(context: Context, text: String) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Wieczorna analiza", NotificationManager.IMPORTANCE_DEFAULT))
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val open = launch?.let {
                android.app.PendingIntent.getActivity(context, 0, it, android.app.PendingIntent.FLAG_IMMUTABLE)
            }
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(com.zacco.rytm.data.R.drawable.ic_stat_rytm)
                .setContentTitle("Podsumowanie dnia")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .apply { open?.let { setContentIntent(it) } }
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n) }
        }
    }
}
