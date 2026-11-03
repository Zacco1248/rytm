package com.zacco.rytm.wear.workout

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.zacco.rytm.domain.workout.RunExercise
import com.zacco.rytm.domain.workout.WorkoutRun
import com.zacco.rytm.sync.WatchWorkout
import com.zacco.rytm.wear.ui.Body
import com.zacco.rytm.wear.ui.Label
import com.zacco.rytm.wear.ui.Labels
import com.zacco.rytm.wear.ui.ScreenColumn
import com.zacco.rytm.wear.ui.Stepper
import com.zacco.rytm.wear.ui.Title
import com.zacco.rytm.wear.ui.ToggleChip
import com.zacco.rytm.wear.ui.WideButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val workoutPermissions: Array<String> = buildList {
    add(if (Build.VERSION.SDK_INT >= 36) "android.permission.health.READ_HEART_RATE" else Manifest.permission.BODY_SENSORS)
    add(Manifest.permission.ACTIVITY_RECOGNITION)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

/** Cały trening na zegarku: podgląd, seria po serii z przerwami, podsumowanie z RPE. */
@Composable
fun WorkoutFlow(planned: WatchWorkout?, onExit: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val run by WorkoutStore.observe(context).collectAsStateWithLifecycle()
    val live by WorkoutStore.live().collectAsStateWithLifecycle()
    var finishing by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Bez zgody na tętno trening i tak działa, tylko bez tętna i kcal.
        WorkoutService.start(context)
    }

    val r = run
    when {
        r == null -> {
            if (planned == null) {
                ScreenColumn {
                    Title("Trening")
                    Body("Dziś w planie nie ma sesji z ćwiczeniami.")
                    WideButton("Wróć", onExit, tonal = true)
                }
            } else {
                PreviewScreen(planned, onStart = {
                    WorkoutStore.start(context, planned)
                    finishing = false
                    permissions.launch(workoutPermissions)
                }, onBack = onExit)
            }
        }
        finishing || r.isFinished -> FinishScreen(
            r, live,
            onSave = { rpe ->
                scope.launch {
                    WorkoutStore.finish(context, rpe)
                    WorkoutService.stop(context)
                    finishing = false
                    onExit()
                }
            },
            onBackToWorkout = if (r.isFinished) null else ({ finishing = false }),
            onDiscard = {
                WorkoutStore.clear(context)
                WorkoutService.stop(context)
                finishing = false
                onExit()
            },
        )
        else -> RunScreen(
            r, live,
            onChange = { change -> WorkoutStore.update(context, change) },
            onFinish = { finishing = true },
        )
    }
}

@Composable
private fun PreviewScreen(w: WatchWorkout, onStart: () -> Unit, onBack: () -> Unit) {
    ScreenColumn {
        Title(w.title)
        if (w.doneToday) Label("Dziś już zapisany. Możesz zrobić drugi.")
        w.exercises.forEach { e ->
            Body("${e.name}\n${e.sets} × ${reps(e.repsMin, e.repsMax)}" + (e.weightKg?.let { " · ${kg(it)} kg" } ?: ""))
        }
        WideButton("Start", onStart)
        WideButton("Wróć", onBack, tonal = true)
    }
}

@Composable
private fun RunScreen(r: WorkoutRun, live: LiveStats, onChange: ((WorkoutRun) -> WorkoutRun) -> Unit, onFinish: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(r.restUntilMs) {
        while (true) {
            now = System.currentTimeMillis()
            val until = r.restUntilMs
            if (until == null || now > until) break
            delay(500)
        }
    }
    val ex = r.current ?: return
    if (r.isResting(now)) {
        RestScreen(r, ex, live, now, onChange)
        return
    }
    // Pola zapamiętane per seria: po zapisie wracają do podpowiedzi z poprzedniej serii.
    val key = "${r.exerciseIdx}-${r.setNo}"
    var reps by rememberSaveable(key) { mutableIntStateOf(r.suggestedReps()) }
    var half by rememberSaveable(key) { mutableIntStateOf(((r.suggestedWeight() ?: 0.0) * 2).toInt()) }
    var rir by rememberSaveable(key) { mutableStateOf<Int?>(null) }
    val hasWeight = ex.weightKg != null || r.suggestedWeight() != null
    val stepHalf = (r.stepKg * 2).toInt().coerceAtLeast(1)

    ScreenColumn {
        Label("${r.exerciseIdx + 1}/${r.exercises.size} · seria ${r.setNo}/${ex.sets}" + (live.hr?.let { " · ♥ $it" } ?: ""))
        Title(ex.name)
        Body("Cel ${reps(ex.repsMin, ex.repsMax)}" + (ex.tempo?.let { " · tempo $it" } ?: ""))
        ex.note?.takeIf { r.setNo == 1 }?.let { Label(it) }
        Label(if (ex.name.contains("sekundy")) "Sekundy" else "Powtórzenia")
        Stepper(reps, 0..300, { reps = it })
        if (hasWeight) {
            Label("Ciężar")
            Stepper(half, 0..400, { half = it }, step = stepHalf, display = { kg(it / 2.0) + " kg" })
        }
        Label("Zapas powtórzeń (opcjonalnie)")
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(0, 1, 2, 3).forEach { v ->
                ToggleChip(if (v == 3) "3+" else "$v", rir == v, Modifier.weight(1f)) { rir = if (rir == v) null else v }
            }
        }
        WideButton("Seria zrobiona", {
            val w = if (hasWeight) half / 2.0 else null
            onChange { it.log(reps, w, rir, System.currentTimeMillis()) }
        })
        WideButton("+1 seria", { onChange { it.addSet() } }, tonal = true)
        WideButton("Pomiń ćwiczenie", { onChange { it.skipExercise() } }, tonal = true)
        WideButton("Zakończ trening", onFinish, tonal = true)
    }
}

@Composable
private fun RestScreen(r: WorkoutRun, next: RunExercise, live: LiveStats, now: Long, onChange: ((WorkoutRun) -> WorkoutRun) -> Unit) {
    val left = r.restLeftSec(now)
    ScreenColumn {
        Label("Przerwa")
        Text("%d:%02d".format(Locale.ROOT, left / 60, left % 60), style = MaterialTheme.typography.displayMedium)
        live.hr?.let { Body("♥ $it") }
        Label("Dalej: seria ${r.setNo}/${next.sets}")
        Body(next.name + (r.suggestedWeight()?.let { " · ${kg(it)} kg" } ?: ""))
        WideButton("Pomiń przerwę", { onChange { it.skipRest() } })
        WideButton("+15 s", { onChange { it.addRest(15, System.currentTimeMillis()) } }, tonal = true)
    }
}

@Composable
private fun FinishScreen(
    r: WorkoutRun,
    live: LiveStats,
    onSave: (Int) -> Unit,
    onBackToWorkout: (() -> Unit)?,
    onDiscard: () -> Unit,
) {
    var rpe by rememberSaveable(r.id) { mutableIntStateOf(7) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val minutes = ((System.currentTimeMillis() - r.startedAtMs) / 60_000).toInt().coerceAtLeast(1)
    val done = r.sets.count { it.reps > 0 }
    ScreenColumn {
        Title("Koniec treningu")
        Body("$done serii · ${Labels.minutes(minutes)}" + (live.avgHr?.let { " · śr. ♥ $it" } ?: "") + (live.kcal?.let { " · $it kcal" } ?: ""))
        if (r.skipped.isNotEmpty()) Label("Pominięte: ${r.skipped.size}")
        Label("Jak ciężko było?")
        Stepper(rpe, 1..10, { rpe = it })
        Body(Labels.rpe(rpe))
        WideButton("Zapisz", { onSave(rpe) })
        onBackToWorkout?.let { WideButton("Wróć do treningu", it, tonal = true) }
        if (confirmDiscard) {
            WideButton("Na pewno wyrzucić?", onDiscard, secondary = "serie nie zostaną zapisane", tonal = true)
        } else {
            WideButton("Wyrzuć trening", { confirmDiscard = true }, tonal = true)
        }
    }
}

private fun reps(min: Int, max: Int) = if (min == max) "$min" else "$min–$max"

private fun kg(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.ROOT, "%.1f", v).replace('.', ',')
