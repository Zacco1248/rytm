package com.zacco.rytm.wear.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.zacco.rytm.domain.model.BodyArea
import com.zacco.rytm.domain.model.LoadedArea
import com.zacco.rytm.sync.WatchActivity
import com.zacco.rytm.sync.WatchEvent
import com.zacco.rytm.sync.WatchHabit
import com.zacco.rytm.sync.WatchSnapshot
import com.zacco.rytm.wear.data.WatchStore
import java.util.Locale

@Composable
fun HomeScreen(
    s: WatchSnapshot?,
    onCheckIn: () -> Unit,
    onRate: (WatchActivity) -> Unit,
    onHabits: () -> Unit,
    onOther: () -> Unit,
) {
    ScreenColumn {
        if (s == null) {
            Title("Rytm")
            Body("Czekam na dane z telefonu. Otwórz Rytm na telefonie.")
        } else {
            val stale = WatchStore.isStale(s)
            Text("${s.readinessScore}", style = MaterialTheme.typography.displayLarge)
            Label("Gotowość " + Labels.level(s.readinessLevel) + if (s.calibrating) " · kalibracja" else "")
            Title(s.planTitle)
            Body(Labels.intensity(s.planIntensity) + " · sen do ${s.bedtime}")
            s.planWarning?.let { Body(it) }
            if (stale) Label("Dane z ${s.date}. Otwórz Rytm na telefonie.")
            Spacer(Modifier.height(4.dp))
            if (!stale) {
                if (!s.checkInDone) WideButton("Check-in", onCheckIn)
                s.toRate.forEach { a -> WideButton("Oceń: ${a.title}", { onRate(a) }, secondary = Labels.minutes(a.durationMin), tonal = true) }
                WideButton("Nawyki", onHabits, secondary = "${s.habits.count { it.done }}/${s.habits.size} zrobione", tonal = true)
            }
        }
        WideButton("Inna aktywność", onOther, secondary = "remont, rower, ogród…", tonal = true)
        if (s != null && s.checkInDone && !WatchStore.isStale(s)) WideButton("Popraw check-in", onCheckIn, tonal = true)
    }
}

/** Szkic check-inu trzymany między ekranem check-inu a pomiarem HRV. */
data class CheckInDraft(
    val energy: Int = 3,
    val mood: Int = 3,
    val sore: Set<BodyArea> = emptySet(),
    val hrv: Double? = null,
)

@Composable
fun CheckInScreen(
    draft: CheckInDraft,
    onChange: (CheckInDraft) -> Unit,
    onMeasureHrv: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    ScreenColumn {
        Title("Check-in")
        Label("Energia")
        Stepper(draft.energy, 1..5, { onChange(draft.copy(energy = it)) })
        Body(Labels.energy(draft.energy))
        Label("Nastrój")
        Stepper(draft.mood, 1..5, { onChange(draft.copy(mood = it)) })
        Body(Labels.mood(draft.mood))
        Label("Co boli?")
        ToggleGrid(BodyArea.entries, draft.sore, Labels::body) { a ->
            onChange(draft.copy(sore = if (a in draft.sore) draft.sore - a else draft.sore + a))
        }
        val hrv = draft.hrv
        if (hrv != null) {
            Body("HRV ${String.format(Locale.ROOT, "%.0f", hrv)} ms ✓")
        } else {
            WideButton("Zmierz HRV", onMeasureHrv, secondary = "60 s, opcjonalnie", tonal = true)
        }
        WideButton("Zapisz", onSave)
        WideButton("Anuluj", onCancel, tonal = true)
    }
}

@Composable
fun RpeScreen(activity: WatchActivity, onSave: (Int) -> Unit, onCancel: () -> Unit) {
    var rpe by rememberSaveable(activity.id) { mutableIntStateOf(6) }
    ScreenColumn {
        Label("Jak ciężko było?")
        Title(activity.title)
        Body(Labels.minutes(activity.durationMin))
        Stepper(rpe, 1..10, { rpe = it })
        Body(Labels.rpe(rpe))
        Label("Obciążenie ${rpe * activity.durationMin}")
        WideButton("Zapisz RPE", { onSave(rpe) })
        WideButton("Później", onCancel, tonal = true)
    }
}

@Composable
fun HabitsScreen(s: WatchSnapshot?, onToggle: (WatchHabit) -> Unit, onBack: () -> Unit) {
    ScreenColumn {
        Title("Nawyki")
        val habits = s?.takeUnless { WatchStore.isStale(it) }?.habits.orEmpty()
        if (habits.isEmpty()) Body("Brak nawyków na dziś albo danych z telefonu.")
        habits.forEach { h ->
            val secondary = h.detail.ifBlank { null }
            if (h.auto) {
                WideButton((if (h.done) "✓ " else "") + h.name, {}, secondary = secondary ?: "automatycznie", tonal = !h.done)
            } else {
                WideButton((if (h.done) "✓ " else "") + h.name, { onToggle(h) }, secondary = secondary, tonal = !h.done)
            }
        }
        WideButton("Wróć", onBack, tonal = true)
    }
}

private val presets = listOf("Remont", "Prace w ogrodzie", "Rower", "Basen", "Inne")

@Composable
fun OtherActivityScreen(onSave: (WatchEvent.OtherActivity) -> Unit, onCancel: () -> Unit) {
    var preset by rememberSaveable { mutableStateOf(presets.first()) }
    var minutes by rememberSaveable { mutableIntStateOf(60) }
    var rpe by rememberSaveable { mutableIntStateOf(5) }
    var areas by remember { mutableStateOf(LoadedArea.defaultsFor(presets.first()).map { it.name }.toSet()) }
    ScreenColumn {
        Title("Inna aktywność")
        ToggleGrid(presets, setOf(preset), { it }) { p ->
            preset = p
            areas = LoadedArea.defaultsFor(p).map { it.name }.toSet()
        }
        Label("Czas")
        Stepper(minutes, 15..300, { minutes = it }, step = 15, display = Labels::minutes)
        Label("RPE")
        Stepper(rpe, 1..10, { rpe = it })
        Body(Labels.rpe(rpe))
        Label("Co obciążyło?")
        ToggleGrid(LoadedArea.entries, areas.mapNotNull { n -> LoadedArea.entries.firstOrNull { it.name == n } }.toSet(), Labels::loaded) { a ->
            areas = if (a.name in areas) areas - a.name else areas + a.name
        }
        WideButton("Zapisz", {
            onSave(
                WatchEvent.OtherActivity(
                    date = WatchStore.today(),
                    description = preset,
                    durationMin = minutes,
                    rpe = rpe,
                    areas = areas.toList(),
                    endedAt = System.currentTimeMillis(),
                ),
            )
        })
        WideButton("Anuluj", onCancel, tonal = true)
    }
}

