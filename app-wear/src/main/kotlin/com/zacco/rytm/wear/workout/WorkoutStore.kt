package com.zacco.rytm.wear.workout

import android.content.Context
import android.util.Log
import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.model.PlannedExercise
import com.zacco.rytm.domain.model.StrengthWorkout
import com.zacco.rytm.domain.workout.WorkoutRun
import com.zacco.rytm.sync.WatchEvent
import com.zacco.rytm.sync.WatchSet
import com.zacco.rytm.sync.WatchWorkout
import com.zacco.rytm.wear.data.WatchStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.util.UUID

/** Tętno i kcal z Health Services w trakcie treningu. */
data class LiveStats(val hr: Int? = null, val avgHr: Int? = null, val kcal: Int? = null)

/**
 * Trwający trening na zegarku. Stan zapisujemy po każdej zmianie, bo system potrafi zabić proces w przerwie.
 * Serie idą na telefon dopiero po zakończeniu, jednym wpisem (Data Layer dostarczy go, gdy telefon będzie w zasięgu).
 */
object WorkoutStore {

    private const val TAG = "RytmWorkout"
    private const val PREFS = "workout"
    private const val KEY = "run"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val run = MutableStateFlow<WorkoutRun?>(null)
    private val live = MutableStateFlow(LiveStats())
    @Volatile private var loaded = false
    private var hrSum = 0L
    private var hrCount = 0

    fun observe(context: Context): StateFlow<WorkoutRun?> {
        load(context)
        return run.asStateFlow()
    }

    fun live(): StateFlow<LiveStats> = live.asStateFlow()

    fun current(context: Context): WorkoutRun? {
        load(context)
        return run.value
    }

    fun start(context: Context, w: WatchWorkout, nowMs: Long = System.currentTimeMillis()): WorkoutRun {
        val plan = StrengthWorkout(
            w.title,
            w.exercises.map { PlannedExercise(it.name, it.sets, it.repsMin..it.repsMax, it.weightKg, it.tempo, it.restSec, it.note) },
        )
        val type = ActivityType.entries.firstOrNull { it.name == w.type } ?: ActivityType.STRENGTH
        val r = WorkoutRun.start("watch-" + UUID.randomUUID(), plan, type, w.stepKg, nowMs)
        hrSum = 0
        hrCount = 0
        live.value = LiveStats()
        write(context, r)
        return r
    }

    fun update(context: Context, change: (WorkoutRun) -> WorkoutRun) {
        val r = current(context) ?: return
        write(context, change(r))
    }

    /** Wywoływane z usługi treningu przy każdej paczce danych z Health Services. */
    fun onMetrics(hr: Int?, kcal: Int?) {
        if (hr != null && hr > 0) {
            hrSum += hr
            hrCount++
        }
        live.value = LiveStats(
            hr = hr ?: live.value.hr,
            avgHr = if (hrCount > 0) (hrSum / hrCount).toInt() else null,
            kcal = kcal ?: live.value.kcal,
        )
    }

    /** Wysyła trening na telefon i czyści stan. */
    suspend fun finish(context: Context, rpe: Int?, nowMs: Long = System.currentTimeMillis()) {
        val r = current(context) ?: return
        val stats = live.value
        val event = WatchEvent.WorkoutDone(
            date = WatchStore.today(),
            id = r.id,
            title = r.title,
            type = r.type,
            startedAt = r.startedAtMs,
            endedAt = nowMs,
            rpe = rpe,
            avgHr = stats.avgHr,
            kcal = stats.kcal,
            sets = r.sets.filter { it.reps > 0 }.map { WatchSet(it.exercise, it.setIndex, it.reps, it.weightKg, it.rir) },
        )
        WatchStore.send(context, event)
        clear(context)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
        run.value = null
        live.value = LiveStats()
    }

    private fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            run.value = raw?.let {
                runCatching { json.decodeFromString(WorkoutRun.serializer(), it) }
                    .onFailure { e -> Log.w(TAG, "Nie odczytano treningu", e) }
                    .getOrNull()
            }
            loaded = true
        }
    }

    private fun write(context: Context, r: WorkoutRun) {
        load(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, json.encodeToString(WorkoutRun.serializer(), r))
            .apply()
        run.value = r
    }
}
