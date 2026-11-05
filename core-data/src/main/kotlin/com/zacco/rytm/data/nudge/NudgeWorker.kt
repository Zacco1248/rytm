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
import com.zacco.rytm.domain.nutrition.KcalAdjuster
import com.zacco.rytm.domain.goals.Goals
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
        runCatching { reviewKcal(applicationContext) }.onFailure { Log.w(TAG, "Przegląd kcal nieudany", it) }
        runCatching { celebrateGoals(applicationContext) }.onFailure { Log.w(TAG, "Cele nieudane", it) }
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

        /**
         * Pętla jedzenie–waga: co 2 tygodnie koryguje cel kcal w profilu ([KcalAdjuster]) i mówi o tym powiadomieniem.
         * Przy pierwszym uruchomieniu tylko zaczyna liczyć 2 tygodnie.
         */
        suspend fun reviewKcal(context: Context) {
            val data = RytmData.get(context)
            data.profiles.ensureLoaded()
            val p = data.profiles.current() ?: return
            val today = LocalDate.now(RytmTime.zone)
            val last = (p.kcalReviewedOn ?: p.startedOn)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (last == null) {
                data.profiles.save(p.copy(kcalReviewedOn = today.toString()))
                return
            }
            if (!KcalAdjuster.isDue(today, last)) return
            val coach = data.coach.observe(today).first()
            val t = coach.profile.nutrition
            val review = KcalAdjuster.review(today, t.kcal, p.kcalAdjust, t.weeklyWeightChangeKg, coach.weighIns, coach.nutrition)
                ?: run {
                    // Za mało ważeń albo wpisów jedzenia: spróbujemy za tydzień.
                    data.profiles.save(p.copy(kcalReviewedOn = today.minusDays(KcalAdjuster.PERIOD_DAYS / 2).toString()))
                    return
                }
            data.profiles.save(p.copy(kcalAdjust = review.newAdjust, kcalReviewedOn = today.toString()))
            if (review.change != 0 && data.ai.settings.value.nudgesEnabled) {
                show(context, Nudge(NudgeKind.PROTEIN, "Nowy cel: ${t.kcal + review.change} kcal", review.reason + " Cofniesz to w Sylwetce."), id = 99)
            }
        }

        /** Małe wygrane: powiadomienie za każdy nowy kamień milowy celu (25, 50, 75, 100%). */
        suspend fun celebrateGoals(context: Context) {
            val data = RytmData.get(context)
            if (!data.ai.settings.value.nudgesEnabled) return
            val hour = LocalTime.now(RytmTime.zone).hour
            if (hour < 7 || hour >= 22) return
            data.profiles.ensureLoaded()
            val p = data.profiles.current() ?: return
            if (p.goals.isEmpty()) return
            val today = LocalDate.now(RytmTime.zone)
            val coach = data.coach.observe(today).first()
            var changed = false
            val goals = p.goals.mapIndexed { i, g ->
                val progress = Goals.progress(g, today, coach.weighIns, coach.sets, coach.activities)
                if (progress.milestonesReached > g.celebrated) {
                    val title = if (progress.done) "Cel osiągnięty!" else "Kamień milowy: ${progress.milestonesReached * 25}%"
                    show(context, Nudge(NudgeKind.WORKOUT, title, "${g.title}. ${progress.note}"), id = 200 + i)
                    changed = true
                    g.copy(celebrated = progress.milestonesReached)
                } else g
            }
            if (changed) data.profiles.save(p.copy(goals = goals))
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
        private fun show(context: Context, n: Nudge, id: Int = 100 + n.kind.ordinal): Boolean {
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
            return runCatching { NotificationManagerCompat.from(context).notify(id, notification) }.isSuccess
        }
    }
}
