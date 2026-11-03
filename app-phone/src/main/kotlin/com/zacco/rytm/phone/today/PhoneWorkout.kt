package com.zacco.rytm.phone.today

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.zacco.rytm.domain.exercise.ExerciseGuide
import com.zacco.rytm.domain.workout.Effort
import com.zacco.rytm.domain.workout.WorkoutRun
import com.zacco.rytm.phone.app.Format
import com.zacco.rytm.phone.ui.components.CardHeader
import com.zacco.rytm.phone.ui.components.HelpDot
import com.zacco.rytm.phone.ui.components.Pill
import com.zacco.rytm.phone.ui.components.Ring
import com.zacco.rytm.phone.ui.components.RytmButton
import com.zacco.rytm.phone.ui.components.RytmCard
import com.zacco.rytm.phone.ui.components.ScreenHeader
import com.zacco.rytm.phone.ui.components.SmallText
import com.zacco.rytm.phone.ui.components.WhyBox
import com.zacco.rytm.phone.ui.theme.Rytm
import kotlinx.coroutines.delay

/**
 * Trening prowadzony na telefonie, seria po serii, dla osób bez zegarka z Wear OS (np. opaska Xiaomi albo Fitbit).
 * Przerwa z odliczaniem i wibracją; tętno i kcal dociągną się z opaski przez Health Connect po treningu.
 */
@Composable
fun PhoneWorkoutScreen(
    run: WorkoutRun,
    onUpdate: ((WorkoutRun) -> WorkoutRun) -> Unit,
    onFinish: (Int?) -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(run.restUntilMs) {
        var warned = false
        while (run.isResting(System.currentTimeMillis())) {
            now = System.currentTimeMillis()
            val left = run.restLeftSec(now)
            if (left == 10 && !warned) {
                vibrate(context, 150)
                warned = true
            }
            delay(250)
        }
        now = System.currentTimeMillis()
        if (run.restUntilMs != null && now >= run.restUntilMs!!) vibrate(context, 500)
    }

    ScreenHeader(sub = "Trening na telefonie", title = run.title.substringBefore(" ·")) {
        Pill("Zwiń", false, onClick = onClose)
    }
    val ex = run.current
    when {
        ex == null -> FinishCard(run, onFinish)
        run.isResting(now) -> RestCard(run, now, onUpdate)
        else -> SetCard(run, onUpdate)
    }
    if (ex != null) {
        RytmButton("Zakończ trening", Modifier.fillMaxWidth()) { onUpdate { r -> r.copy(exerciseIdx = r.exercises.size, restUntilMs = null) } }
    }
    var confirmCancel by remember { mutableStateOf(false) }
    if (confirmCancel) {
        RytmCard {
            SmallText("Na pewno przerwać bez zapisu? Zapisanych serii nie będzie w dzienniku.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RytmButton("Przerwij", Modifier.weight(1f), onClick = onCancel)
                RytmButton("Wróć", Modifier.weight(1f), primary = true) { confirmCancel = false }
            }
        }
    } else {
        RytmButton("Przerwij bez zapisu", Modifier.fillMaxWidth()) { confirmCancel = true }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetCard(run: WorkoutRun, onUpdate: ((WorkoutRun) -> WorkoutRun) -> Unit) {
    val ex = run.current ?: return
    var reps by remember(ex.name, run.setNo) { mutableIntStateOf(run.suggestedReps()) }
    var kg by remember(ex.name, run.setNo) { mutableStateOf(run.suggestedWeight()) }
    var rir by remember(ex.name, run.setNo) { mutableStateOf<Int?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    val guide = ExerciseGuide.find(ex.name)
    if (showInfo && guide != null) ExerciseSheet(guide) { showInfo = false }

    RytmCard {
        CardHeader("Ćwiczenie ${run.exerciseIdx + 1}/${run.exercises.size}") {
            if (guide != null) Pill("Jak to zrobić?", false) { showInfo = true }
        }
        Text(ex.name, style = Rytm.type.planTitle, color = Rytm.colors.ink)
        SmallText(
            listOfNotNull(
                "Seria ${run.setNo} z ${ex.sets}",
                "cel ${ex.repsMin}–${ex.repsMax}".takeIf { ex.repsMin != ex.repsMax } ?: "cel ${ex.repsMax}",
                ex.tempo?.let { "tempo $it" },
            ).joinToString(" · "),
        )
        val advice = run.currentAdvice()
        if (advice != null) SmallText(advice.message, color = Rytm.colors.accent) else ex.note?.let { SmallText(it, color = Rytm.colors.accent) }
        if (advice?.offerEndExercise == true) {
            RytmButton("Skończ to ćwiczenie", Modifier.fillMaxWidth()) { onUpdate { it.endExercise() } }
        }
        Text("Powtórzenia", style = Rytm.type.bodyStrong, color = Rytm.colors.ink)
        Stepper(reps.toString(), onMinus = { reps = (reps - 1).coerceAtLeast(0) }, onPlus = { reps += 1 })
        if (kg != null) {
            Text("Ciężar", style = Rytm.type.bodyStrong, color = Rytm.colors.ink)
            Stepper(
                Format.kg(kg!!) + " kg",
                onMinus = { kg = (kg!! - run.stepKg).coerceAtLeast(0.0) },
                onPlus = { kg = kg!! + run.stepKg },
            )
        }
        var effortHelp by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Jak poszło?", style = Rytm.type.bodyStrong, color = Rytm.colors.ink)
            HelpDot(effortHelp) { effortHelp = !effortHelp }
        }
        if (effortHelp) WhyBox(Effort.HELP)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Effort.entries.forEach { e -> Pill(e.label, rir == e.rir) { rir = if (rir == e.rir) null else e.rir } }
        }
        RytmButton(
            "Seria zrobiona" + if (ex.restSec > 0) " · przerwa ${ex.restSec} s" else "",
            Modifier.fillMaxWidth(),
            primary = true,
        ) { onUpdate { it.log(reps, kg, rir, System.currentTimeMillis()) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RytmButton("Seria więcej", Modifier.weight(1f)) { onUpdate { it.addSet() } }
            RytmButton("Pomiń ćwiczenie", Modifier.weight(1f)) { onUpdate { it.skipExercise() } }
        }
    }
    val done = run.sets.filter { it.exercise == ex.name }
    if (done.isNotEmpty()) {
        RytmCard {
            CardHeader("Zapisane")
            done.forEach { s ->
                SmallText("Seria ${s.setIndex}: ${s.reps}" + (s.weightKg?.let { " × ${Format.kg(it)} kg" } ?: "") + (Effort.of(s.rir)?.let { " · ${it.label.lowercase()}" } ?: ""))
            }
        }
    }
}

@Composable
private fun RestCard(run: WorkoutRun, now: Long, onUpdate: ((WorkoutRun) -> WorkoutRun) -> Unit) {
    val ex = run.current ?: return
    val left = run.restLeftSec(now)
    val total = run.sets.lastOrNull()?.let { last -> run.exercises.firstOrNull { it.name == last.exercise }?.restSec } ?: ex.restSec
    RytmCard {
        CardHeader("Przerwa")
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Ring(if (total > 0) left / total.toFloat() else 0f, Rytm.colors.accent) {
                Text("%d:%02d".format(left / 60, left % 60), style = Rytm.type.ringNumber, color = Rytm.colors.ink)
            }
            SmallText("Wibracja 10 s przed końcem i na koniec.")
            Text("Dalej: ${ex.name}, seria ${run.setNo} z ${ex.sets}", style = Rytm.type.body, color = Rytm.colors.ink)
            run.suggestedWeight()?.let { SmallText("${Format.kg(it)} kg") }
        }
        run.visibleAdvice()?.let { a ->
            WhyBox(a.message)
            if (a.offerExtraSet) RytmButton("Dołóż serię: ${a.exercise}", Modifier.fillMaxWidth()) { onUpdate { it.extraSet() } }
            if (a.offerEndExercise) RytmButton("Skończ to ćwiczenie", Modifier.fillMaxWidth()) { onUpdate { it.endExercise() } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RytmButton("+30 s", Modifier.weight(1f)) { onUpdate { it.addRest(30, System.currentTimeMillis()) } }
            RytmButton("Pomiń przerwę", Modifier.weight(1f), primary = true) { onUpdate { it.skipRest() } }
        }
    }
}

@Composable
private fun FinishCard(run: WorkoutRun, onFinish: (Int?) -> Unit) {
    var rpe by remember { mutableStateOf<Int?>(null) }
    val minutes = ((System.currentTimeMillis() - run.startedAtMs) / 60_000).toInt()
    val volume = run.sets.sumOf { s -> s.weightKg?.let { it * s.reps } ?: 0.0 }
    RytmCard {
        CardHeader("Koniec treningu")
        Text("${run.sets.count { it.reps > 0 }} serii · ${Format.minutes(minutes.coerceAtLeast(1))}", style = Rytm.type.planTitle, color = Rytm.colors.ink)
        if (volume > 0) SmallText("Objętość ${Format.dec(volume, 0)} kg")
        if (run.skipped.isNotEmpty()) SmallText("Pominięte: " + run.skipped.joinToString())
        Text("Jak ciężko było? (RPE 1–10)", style = Rytm.type.bodyStrong, color = Rytm.colors.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { (1..5).forEach { v -> Pill("$v", rpe == v) { rpe = v } } }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { (6..10).forEach { v -> Pill("$v", rpe == v) { rpe = v } } }
        WhyBox("Tętno i kcal dociągną się z opaski albo zegarka przez Health Connect, jeśli zapisały ten trening. Z serii Rytm dobierze ciężar na kolejny raz.")
        RytmButton("Zapisz trening", Modifier.fillMaxWidth(), primary = true) { onFinish(rpe) }
    }
}

@Composable
private fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        RytmButton("−", Modifier.width(64.dp), onClick = onMinus)
        Text(value, style = Rytm.type.bigNumber, color = Rytm.colors.ink)
        RytmButton("+", Modifier.width(64.dp), onClick = onPlus)
    }
}

private fun vibrate(context: Context, ms: Long) {
    runCatching {
        val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
