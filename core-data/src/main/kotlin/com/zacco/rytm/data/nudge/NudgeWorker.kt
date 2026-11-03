package com.zacco.rytm.data.nudge

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zacco.rytm.data.RytmData
import com.zacco.rytm.domain.coach.CoachEngine
import com.zacco.rytm.domain.coach.Nudge
import com.zacco.rytm.domain.coach.NudgeKind
import com.zacco.rytm.domain.coach.Nudges
import com.zacco.rytm.domain.time.RytmTime
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Co godzinę sprawdza reguły podpowiedzi ([Nudges]) i wysyła najwyżej jedną naraz (2 dziennie).
 * Bez sieci i bez AI, więc nic nie kosztuje.
 */
class NudgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching { check(applicationContext) }.onFailure { Log.w(TAG, "Podpowiedź nieudana", it) }
        return Result.success()
    }

    companion object {
        private const val TAG = "RytmNudge"
        private const val WORK = "nudges"
        private const val CHANNEL = "nudges"
        private const val PREFS = "nudges"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NudgeWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        suspend fun check(context: Context) {
            val data = RytmData.get(context)
            val settings = data.ai.settings.value
            if (!settings.nudgesEnabled || !canNotify(context)) return
            data.profiles.ensureLoaded()
            if (data.profiles.current() == null) return
            val today = LocalDate.now(RytmTime.zone)
            val coach = data.coach.observe(today).first()
            val snapshot = CoachEngine().today(coach)
            val enabled = NudgeKind.entries.filter { it.name !in settings.nudgesOff }.toSet()
            val sent = sentOn(context, today)
            val nudge = Nudges.pick(coach, snapshot, LocalTime.now(RytmTime.zone), enabled, sent) ?: return
            if (show(context, nudge)) markSent(context, today, sent + nudge.kind)
        }

        private fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        private fun sentOn(context: Context, date: LocalDate): Set<NudgeKind> {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(date.toString(), null) ?: return emptySet()
            return raw.split(',').mapNotNull { n -> NudgeKind.entries.firstOrNull { it.name == n } }.toSet()
        }

        private fun markSent(context: Context, date: LocalDate, kinds: Set<NudgeKind>) {
            // Trzymamy tylko dzisiejszy wpis.
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear()
                .putString(date.toString(), kinds.joinToString(",") { it.name }).apply()
        }

        @android.annotation.SuppressLint("MissingPermission") // sprawdzane w canNotify
        private fun show(context: Context, n: Nudge): Boolean {
            if (!canNotify(context)) return false
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Podpowiedzi w ciągu dnia", NotificationManager.IMPORTANCE_DEFAULT))
            val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                PendingIntent.getActivity(context, 1, it, PendingIntent.FLAG_IMMUTABLE)
            }
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(com.zacco.rytm.data.R.drawable.ic_stat_rytm)
                .setContentTitle(n.title)
                .setContentText(n.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(n.text))
                .setAutoCancel(true)
                .apply { open?.let { setContentIntent(it) } }
                .build()
            return runCatching { NotificationManagerCompat.from(context).notify(100 + n.kind.ordinal, notification) }.isSuccess
        }
    }
}
