package com.zacco.rytm.wear.workout

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.endExercise
import androidx.health.services.client.getCapabilities
import androidx.health.services.client.startExercise
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.zacco.rytm.wear.MainActivity
import com.zacco.rytm.wear.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Usługa na czas treningu: trzyma sesję Health Services (tętno, kcal), wibruje na koniec przerwy
 * i pokazuje „trwającą aktywność”, więc po wybudzeniu zegarek wraca do treningu.
 */
class WorkoutService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var exerciseStarted = false
    private var restJob: Job? = null

    private val callback = object : ExerciseUpdateCallback {
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val hr = update.latestMetrics.getData(DataType.HEART_RATE_BPM).lastOrNull()?.value?.roundToInt()
            val kcal = update.latestMetrics.getData(DataType.CALORIES_TOTAL)?.total?.roundToInt()
            WorkoutStore.onMetrics(hr, kcal)
        }

        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) = Unit
        override fun onRegistered() = Unit
        override fun onRegistrationFailed(throwable: Throwable) {
            Log.w(TAG, "Health Services: rejestracja nieudana", throwable)
        }

        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) = Unit
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopWorkout()
            return START_NOT_STICKY
        }
        if (!goForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!exerciseStarted) {
            exerciseStarted = true
            scope.launch { startExercise() }
            watchRest()
        }
        return START_STICKY
    }

    private fun goForeground(): Boolean = runCatching {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Trening", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SCREEN, MainActivity.SCREEN_WORKOUT)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = WorkoutStore.current(this)?.title ?: "Trening"
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_workout)
            .setContentTitle(title)
            .setContentText("Trening trwa")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setContentIntent(open)
        OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_workout)
            .setTouchIntent(open)
            .setStatus(Status.Builder().addTemplate(title).build())
            .build()
            .apply(applicationContext)
        val notification: Notification = builder.build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        true
    }.getOrElse {
        // Bez uprawnień do tętna system nie pozwala na usługę typu „health”; trening działa wtedy tylko z ekranem.
        Log.w(TAG, "Bez usługi treningu: ${it.message}")
        false
    }

    private suspend fun startExercise() {
        runCatching {
            val client = HealthServices.getClient(this).exerciseClient
            client.setUpdateCallback(callback)
            val caps = client.getCapabilities()
            val wanted = exerciseTypeFor(WorkoutStore.current(this)?.type)
            val type = if (wanted in caps.supportedExerciseTypes) wanted else ExerciseType.WORKOUT
            val supported = caps.getExerciseTypeCapabilities(type).supportedDataTypes
            val dataTypes = setOf(DataType.HEART_RATE_BPM, DataType.CALORIES_TOTAL).filter { it in supported }.toSet()
            client.startExercise(
                ExerciseConfig.builder(type)
                    .setDataTypes(dataTypes)
                    .setIsAutoPauseAndResumeEnabled(false)
                    .setIsGpsEnabled(false)
                    .build(),
            )
        }.onFailure { Log.w(TAG, "Health Services: nie wystartował trening", it) }
    }

    /** Krótka wibracja 10 s przed końcem przerwy i podwójna na koniec. */
    private fun watchRest() {
        restJob?.cancel()
        restJob = scope.launch {
            WorkoutStore.observe(this@WorkoutService).map { it?.restUntilMs }.distinctUntilChanged().collectLatest { until ->
                if (until == null) return@collectLatest
                val warnAt = until - WARN_MS
                val now = System.currentTimeMillis()
                if (warnAt > now) {
                    delay(warnAt - now)
                    buzz(longArrayOf(0, 120))
                }
                val left = until - System.currentTimeMillis()
                if (left > 0) delay(left)
                buzz(longArrayOf(0, 350, 150, 350))
            }
        }
    }

    private fun buzz(pattern: LongArray) {
        runCatching {
            val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }

    private fun stopWorkout() {
        scope.launch {
            if (exerciseStarted) {
                runCatching { HealthServices.getClient(this@WorkoutService).exerciseClient.endExercise() }
                    .onFailure { Log.w(TAG, "Health Services: nie zakończono", it) }
            }
            exerciseStarted = false
            ServiceCompat.stopForeground(this@WorkoutService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RytmWorkout"
        private const val CHANNEL = "workout"
        private const val NOTIFICATION_ID = 42
        private const val WARN_MS = 10_000L
        const val ACTION_STOP = "com.zacco.rytm.wear.workout.STOP"

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, WorkoutService::class.java)) }
                .onFailure { Log.w(TAG, "Nie uruchomiono usługi treningu: ${it.message}") }
        }

        fun stop(context: Context) {
            runCatching { context.startService(Intent(context, WorkoutService::class.java).setAction(ACTION_STOP)) }
        }

        private fun exerciseTypeFor(type: String?): ExerciseType = when (type) {
            "MOBILITY" -> ExerciseType.STRETCHING
            "CARDIO" -> ExerciseType.HIGH_INTENSITY_INTERVAL_TRAINING
            else -> ExerciseType.STRENGTH_TRAINING
        }
    }
}
