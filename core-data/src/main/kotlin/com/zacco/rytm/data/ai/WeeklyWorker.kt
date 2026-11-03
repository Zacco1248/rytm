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
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * Raport tygodniowy w niedzielę o 20:00 (przed wieczorną analizą dnia). Po wykonaniu planuje kolejną niedzielę.
 * Bez klucza API zapisuje raport z reguł.
 */
class WeeklyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val data = RytmData.get(applicationContext)
        try {
            if (data.ai.settings.value.weeklyEnabled) {
                data.profiles.ensureLoaded()
                val saved = data.ai.weekly(LocalDate.now(RytmTime.zone))
                notify(applicationContext, saved.report.summary)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Raport tygodniowy nieudany", e)
        } finally {
            schedule(applicationContext, replace = true, afterRun = true)
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "RytmWeekly"
        private const val WORK = "weekly-report"
        private const val CHANNEL = "weekly"
        private const val NOTIFICATION_ID = 22
        private const val AT_MIN = 20 * 60

        fun schedule(context: Context, replace: Boolean = false, afterRun: Boolean = false) {
            val now = ZonedDateTime.now(RytmTime.zone)
            var next = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                .atStartOfDay(RytmTime.zone).plusMinutes(AT_MIN.toLong())
            if (!next.isAfter(now.plusMinutes(if (afterRun) 60 else 0))) next = next.plusWeeks(1)
            val request = OneTimeWorkRequestBuilder<WeeklyWorker>()
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
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Raport tygodniowy", NotificationManager.IMPORTANCE_DEFAULT))
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val open = launch?.let {
                android.app.PendingIntent.getActivity(context, 1, it, android.app.PendingIntent.FLAG_IMMUTABLE)
            }
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle("Raport tygodnia")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .apply { open?.let { setContentIntent(it) } }
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n) }
        }
    }
}
