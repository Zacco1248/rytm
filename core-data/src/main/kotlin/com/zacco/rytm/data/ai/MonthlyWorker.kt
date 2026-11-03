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
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zacco.rytm.data.RytmData
import com.zacco.rytm.domain.time.RytmTime
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Analiza długoterminowa 1. dnia miesiąca o 19:00, za ok. 12 tygodni kończących się wczoraj.
 * Po wykonaniu planuje kolejny miesiąc. Bez klucza API zapisuje analizę z reguł.
 */
class MonthlyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val data = RytmData.get(applicationContext)
        try {
            if (data.ai.settings.value.monthlyEnabled) {
                data.profiles.ensureLoaded()
                val saved = data.ai.monthly(LocalDate.now(RytmTime.zone).minusDays(1))
                notify(applicationContext, saved.report.summary)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Analiza miesięczna nieudana", e)
        } finally {
            schedule(applicationContext, replace = true, afterRun = true)
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "RytmMonthly"
        private const val WORK = "monthly-report"
        private const val CHANNEL = "monthly"
        private const val NOTIFICATION_ID = 23
        private const val AT_MIN = 19 * 60

        fun schedule(context: Context, replace: Boolean = false, afterRun: Boolean = false) {
            val now = ZonedDateTime.now(RytmTime.zone)
            var next = now.toLocalDate().withDayOfMonth(1)
                .atStartOfDay(RytmTime.zone).plusMinutes(AT_MIN.toLong())
            if (!next.isAfter(now.plusMinutes(if (afterRun) 60 else 0))) next = next.plusMonths(1)
            val request = OneTimeWorkRequestBuilder<MonthlyWorker>()
                .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
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
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Analiza miesięczna", NotificationManager.IMPORTANCE_DEFAULT))
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val open = launch?.let {
                android.app.PendingIntent.getActivity(context, 2, it, android.app.PendingIntent.FLAG_IMMUTABLE)
            }
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle("Analiza miesiąca")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .apply { open?.let { setContentIntent(it) } }
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n) }
        }
    }
}
