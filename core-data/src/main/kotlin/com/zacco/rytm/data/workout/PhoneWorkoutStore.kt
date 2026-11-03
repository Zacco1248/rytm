package com.zacco.rytm.data.workout

import android.content.Context
import com.zacco.rytm.domain.workout.WorkoutRun
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Trening prowadzony na telefonie (dla osób bez zegarka z Wear OS). Stan zapisany od razu po każdej serii,
 * więc przeżyje zamknięcie aplikacji albo wyłączenie ekranu.
 */
class PhoneWorkoutStore(context: Context) {
    private val prefs = context.getSharedPreferences("phone-workout", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _run = MutableStateFlow(load())
    val run: StateFlow<WorkoutRun?> = _run.asStateFlow()

    fun set(run: WorkoutRun?) {
        _run.value = run
        prefs.edit().apply {
            if (run == null) remove(KEY) else putString(KEY, json.encodeToString(WorkoutRun.serializer(), run))
        }.apply()
    }

    fun update(f: (WorkoutRun) -> WorkoutRun) {
        _run.value?.let { set(f(it)) }
    }

    private fun load(): WorkoutRun? =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(WorkoutRun.serializer(), it) }.getOrNull() }

    private companion object {
        const val KEY = "run"
    }
}
