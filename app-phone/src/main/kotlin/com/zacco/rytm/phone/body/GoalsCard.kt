package com.zacco.rytm.phone.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zacco.rytm.domain.goals.GoalKind
import com.zacco.rytm.domain.goals.GoalProgress
import com.zacco.rytm.domain.goals.UserGoal
import com.zacco.rytm.domain.time.RytmTime
import com.zacco.rytm.domain.workout.Progression
import com.zacco.rytm.phone.ui.components.Bar
import com.zacco.rytm.phone.ui.components.CardHeader
import com.zacco.rytm.phone.ui.components.Chip
import com.zacco.rytm.phone.ui.components.ChipTone
import com.zacco.rytm.phone.ui.components.Pill
import com.zacco.rytm.phone.ui.components.RytmButton
import com.zacco.rytm.phone.ui.components.RytmCard
import com.zacco.rytm.phone.ui.components.SmallText
import com.zacco.rytm.phone.ui.theme.Rytm
import java.time.LocalDate
import java.util.UUID

/** Cele z kamieniami milowymi i prognozą terminu; dodawanie i usuwanie. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GoalsCard(
    goals: List<GoalProgress>,
    exercises: List<String>,
    currentWeight: Double?,
    onAdd: (UserGoal) -> Unit,
    onRemove: (String) -> Unit,
    onUpdate: (UserGoal) -> Unit = {},
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    RytmCard {
        CardHeader(
            "Cele",
            help = "Ustaw cel z listy albo własny, np. 10 podciągnięć do marca, 78 kg, płaski brzuch czy bieg 30 min. Rytm liczy postęp z ważeń, aktywności i dziennika serii, dzieli drogę na 4 kamienie milowe " +
                "(25, 50, 75 i 100%) i z trendu ostatnich 6 tygodni prognozuje, kiedy dojdziesz do celu. Za każdy kamień przychodzi krótkie powiadomienie.",
        ) {
            if (!adding) Chip("+ Cel", ChipTone.ACCENT) { adding = true }
        }
        if (goals.isEmpty() && !adding) SmallText("Brak celów. Dodaj pierwszy z listy albo wpisz własny.")
        goals.forEach { GoalRow(it, onRemove, onUpdate) }
        if (adding) {
            AddGoal(exercises, currentWeight, onAdd = { onAdd(it); adding = false }, onCancel = { adding = false })
        }
    }
}

@Composable
private fun GoalRow(p: GoalProgress, onRemove: (String) -> Unit, onUpdate: (UserGoal) -> Unit) {
    val c = Rytm.colors
    var confirm by rememberSaveable(p.goal.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(p.goal.title, style = Rytm.type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
            Chip(if (p.done) "✓ zrobione" else "${(p.share * 100).toInt()}%", if (p.done) ChipTone.GOOD else ChipTone.MUTED)
            Chip("Usuń", ChipTone.MUTED) { confirm = !confirm }
        }
        Bar(p.share.toFloat(), if (p.done) c.good else c.accent, height = 6.dp, modifier = Modifier.fillMaxWidth())
        val unit = when (p.goal.kind) {
            GoalKind.REPS, GoalKind.CUSTOM -> ""
            GoalKind.FAT_PCT -> "%"
            GoalKind.CARDIO_MIN -> " min"
            else -> " kg"
        }
        if (p.goal.kind == GoalKind.CUSTOM && !p.done) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.25, 0.5, 0.75, 1.0).forEach { v ->
                    Pill("${(v * 100).toInt()}%", kotlin.math.abs(p.goal.manualShare - v) < 1e-6) { onUpdate(p.goal.copy(manualShare = v)) }
                }
            }
        }
        if (p.goal.kind != GoalKind.CUSTOM) SmallText(
            listOfNotNull(
                p.start?.let { "start ${fmt(it)}$unit" },
                p.current?.let { "teraz ${fmt(it)}$unit" },
                "kamienie ${p.milestonesReached}/4",
            ).joinToString(" · "),
        )
        SmallText(p.note, color = if (p.done) c.good else c.muted)
        if (confirm) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RytmButton("Usuń cel", Modifier.weight(1f), primary = true) { onRemove(p.goal.id) }
                RytmButton("Zostaw", Modifier.weight(1f)) { confirm = false }
            }
        }
    }
}

/** Gotowe cele z listy: rodzaj, nazwa i podpowiedź wartości. */
private enum class GoalPreset(val label: String, val kind: GoalKind, val goalName: String?, val exercise: String? = null) {
    REPS("Powtórzenia", GoalKind.REPS, null),
    LIFT("Ciężar", GoalKind.LIFT, null),
    WEIGHT("Waga ciała", GoalKind.WEIGHT, null),
    FLAT_BELLY("Płaski brzuch", GoalKind.FAT_PCT, "Płaski brzuch"),
    RUN("Bieg bez przerwy", GoalKind.CARDIO_MIN, "Bieg bez przerwy", "Bieg"),
    CUSTOM("Własny", GoalKind.CUSTOM, null),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddGoal(exercises: List<String>, currentWeight: Double?, onAdd: (UserGoal) -> Unit, onCancel: () -> Unit) {
    var preset by rememberSaveable { mutableStateOf(GoalPreset.REPS) }
    val kind = preset.kind
    var exercise by rememberSaveable { mutableStateOf(exercises.firstOrNull { it.contains("Podciąganie") } ?: exercises.firstOrNull().orEmpty()) }
    var target by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var months by rememberSaveable { mutableStateOf<Int?>(3) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Nowy cel", style = Rytm.type.bodyStrong, color = Rytm.colors.ink)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GoalPreset.entries.forEach { p -> Pill(p.label, preset == p) { preset = p } }
        }
        when (kind) {
            GoalKind.REPS, GoalKind.LIFT -> {
                OutlinedTextField(exercise, { exercise = it.take(60) }, label = { Text("Ćwiczenie") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (exercises.isNotEmpty()) {
                    SmallText("Z Twoich treningów:")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        exercises.take(8).forEach { e -> Pill(e, exercise == e) { exercise = e } }
                    }
                }
            }
            GoalKind.FAT_PCT -> SmallText("Płaski brzuch to w praktyce mniej tkanki tłuszczowej: Rytm śledzi % tłuszczu z ważeń (trend, nie pojedynczy pomiar). U mężczyzn brzuch zwykle widać przy ok. 12–15%.")
            GoalKind.CARDIO_MIN -> SmallText("Najdłuższe cardio bez przerwy (bieg, rower) z zegarka albo z ręcznego wpisu.")
            GoalKind.CUSTOM -> OutlinedTextField(text, { text = it.take(60) }, label = { Text("Twój cel, np. „Przebiec 10 km”") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            GoalKind.WEIGHT -> Unit
        }
        if (kind != GoalKind.CUSTOM) {
            OutlinedTextField(
                target,
                { target = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }.take(6) },
                label = {
                    Text(
                        when (kind) {
                            GoalKind.REPS -> "Ile powtórzeń w serii"
                            GoalKind.LIFT -> "Ciężar (kg)"
                            GoalKind.FAT_PCT -> "Docelowy tłuszcz (%)"
                            GoalKind.CARDIO_MIN -> "Ile minut bez przerwy"
                            else -> "Docelowa waga (kg)" + (currentWeight?.let { ", teraz ${fmt(it)}" } ?: "")
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        SmallText("Termin:")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1, 3, 6).forEach { m -> Pill("$m mies.", months == m) { months = m } }
            Pill("bez terminu", months == null) { months = null }
        }
        val value = if (kind == GoalKind.CUSTOM) 1.0 else target.replace(',', '.').toDoubleOrNull()
        val valid = value != null && value > 0 && when (kind) {
            GoalKind.REPS, GoalKind.LIFT -> exercise.isNotBlank()
            GoalKind.CUSTOM -> text.isNotBlank()
            GoalKind.FAT_PCT -> value in 3.0..50.0
            else -> true
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RytmButton("Dodaj cel", Modifier.weight(1f), primary = true, enabled = valid) {
                val today = LocalDate.now(RytmTime.zone)
                onAdd(
                    UserGoal(
                        id = UUID.randomUUID().toString(),
                        kind = kind,
                        exercise = when (kind) {
                            GoalKind.REPS, GoalKind.LIFT -> exercise.trim()
                            GoalKind.CARDIO_MIN -> preset.exercise
                            else -> null
                        },
                        target = value!!,
                        deadline = months?.let { today.plusMonths(it.toLong()).toString() },
                        createdOn = today.toString(),
                        label = if (kind == GoalKind.CUSTOM) text.trim() else preset.goalName,
                    ),
                )
            }
            RytmButton("Anuluj", Modifier.weight(1f), onClick = onCancel)
        }
    }
}

private fun fmt(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else Progression.kg(v)
