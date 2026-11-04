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
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    RytmCard {
        CardHeader(
            "Cele",
            help = "Ustaw cel, np. 10 podciągnięć do marca albo 78 kg. Rytm liczy postęp z ważeń i dziennika serii, dzieli drogę na 4 kamienie milowe " +
                "(25, 50, 75 i 100%) i z trendu ostatnich 6 tygodni prognozuje, kiedy dojdziesz do celu. Za każdy kamień przychodzi krótkie powiadomienie.",
        ) {
            if (!adding) Chip("+ Cel", ChipTone.ACCENT) { adding = true }
        }
        if (goals.isEmpty() && !adding) SmallText("Brak celów. Dodaj pierwszy: waga, liczba powtórzeń albo ciężar w ćwiczeniu.")
        goals.forEach { GoalRow(it, onRemove) }
        if (adding) {
            AddGoal(exercises, currentWeight, onAdd = { onAdd(it); adding = false }, onCancel = { adding = false })
        }
    }
}

@Composable
private fun GoalRow(p: GoalProgress, onRemove: (String) -> Unit) {
    val c = Rytm.colors
    var confirm by rememberSaveable(p.goal.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(p.goal.title, style = Rytm.type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
            Chip(if (p.done) "✓ zrobione" else "${(p.share * 100).toInt()}%", if (p.done) ChipTone.GOOD else ChipTone.MUTED)
            Chip("Usuń", ChipTone.MUTED) { confirm = !confirm }
        }
        Bar(p.share.toFloat(), if (p.done) c.good else c.accent, height = 6.dp, modifier = Modifier.fillMaxWidth())
        val unit = if (p.goal.kind == GoalKind.REPS) "" else " kg"
        SmallText(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddGoal(exercises: List<String>, currentWeight: Double?, onAdd: (UserGoal) -> Unit, onCancel: () -> Unit) {
    var kind by rememberSaveable { mutableStateOf(GoalKind.REPS) }
    var exercise by rememberSaveable { mutableStateOf(exercises.firstOrNull { it.contains("Podciąganie") } ?: exercises.firstOrNull().orEmpty()) }
    var target by rememberSaveable { mutableStateOf("") }
    var months by rememberSaveable { mutableStateOf<Int?>(3) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Nowy cel", style = Rytm.type.bodyStrong, color = Rytm.colors.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("Powtórzenia", kind == GoalKind.REPS) { kind = GoalKind.REPS }
            Pill("Ciężar", kind == GoalKind.LIFT) { kind = GoalKind.LIFT }
            Pill("Waga ciała", kind == GoalKind.WEIGHT) { kind = GoalKind.WEIGHT }
        }
        if (kind != GoalKind.WEIGHT) {
            OutlinedTextField(exercise, { exercise = it.take(60) }, label = { Text("Ćwiczenie") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (exercises.isNotEmpty()) {
                SmallText("Z Twoich treningów:")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    exercises.take(8).forEach { e -> Pill(e, exercise == e) { exercise = e } }
                }
            }
        }
        OutlinedTextField(
            target,
            { target = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }.take(6) },
            label = {
                Text(
                    when (kind) {
                        GoalKind.REPS -> "Ile powtórzeń w serii"
                        GoalKind.LIFT -> "Ciężar (kg)"
                        GoalKind.WEIGHT -> "Docelowa waga (kg)" + (currentWeight?.let { ", teraz ${fmt(it)}" } ?: "")
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        SmallText("Termin:")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1, 3, 6).forEach { m -> Pill("$m mies.", months == m) { months = m } }
            Pill("bez terminu", months == null) { months = null }
        }
        val value = target.replace(',', '.').toDoubleOrNull()
        val valid = value != null && value > 0 && (kind == GoalKind.WEIGHT || exercise.isNotBlank())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RytmButton("Dodaj cel", Modifier.weight(1f), primary = true, enabled = valid) {
                val today = LocalDate.now(RytmTime.zone)
                onAdd(
                    UserGoal(
                        id = UUID.randomUUID().toString(),
                        kind = kind,
                        exercise = exercise.trim().takeIf { kind != GoalKind.WEIGHT },
                        target = value!!,
                        deadline = months?.let { today.plusMonths(it.toLong()).toString() },
                        createdOn = today.toString(),
                    ),
                )
            }
            RytmButton("Anuluj", Modifier.weight(1f), onClick = onCancel)
        }
    }
}

private fun fmt(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else Progression.kg(v)
