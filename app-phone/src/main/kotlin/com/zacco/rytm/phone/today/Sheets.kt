package com.zacco.rytm.phone.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zacco.rytm.domain.model.BodyArea
import com.zacco.rytm.domain.model.DailyCheckIn
import com.zacco.rytm.domain.model.LoadedArea
import com.zacco.rytm.phone.app.Format
import com.zacco.rytm.phone.ui.components.RytmButton
import com.zacco.rytm.phone.ui.components.SmallText
import com.zacco.rytm.phone.ui.theme.Rytm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RytmSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Rytm.colors.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 26.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = Rytm.type.planTitle.copy(fontSize = Rytm.type.planTitle.fontSize * 0.95f), color = Rytm.colors.ink)
            content()
        }
    }
}

/** Skala 1–N jako rząd przycisków (check-in, RPE). */
@Composable
private fun Scale(range: IntRange, selected: Int?, onSelect: (Int) -> Unit) {
    val c = Rytm.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        range.forEach { v ->
            val on = v == selected
            Text(
                "$v",
                style = Rytm.type.bodyStrong,
                color = if (on) c.onAccent else c.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) c.accent else c.surface)
                    .border(1.dp, if (on) c.accent else c.line, RoundedCornerShape(12.dp))
                    .clickable(role = Role.RadioButton) { onSelect(v) }
                    .padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun Pill(text: String, on: Boolean, onClick: () -> Unit) {
    val c = Rytm.colors
    Text(
        text,
        style = Rytm.type.small,
        color = if (on) c.bg else c.ink,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (on) c.ink else c.surface)
            .border(1.dp, if (on) c.ink else c.line, CircleShape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun Label(text: String) = Text(text, style = Rytm.type.body, color = Rytm.colors.ink)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckInSheet(current: DailyCheckIn?, onDismiss: () -> Unit, onSave: (Int, Int, Set<BodyArea>) -> Unit) {
    var energy by remember { mutableIntStateOf(current?.energy ?: 3) }
    var mood by remember { mutableIntStateOf(current?.mood ?: 3) }
    var sore by remember { mutableStateOf(current?.soreAreas ?: emptySet()) }
    RytmSheet("Poranny check-in", onDismiss) {
        Label("Energia")
        Scale(1..5, energy) { energy = it }
        Label("Nastrój")
        Scale(1..5, mood) { mood = it }
        Label("Co boli?")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BodyArea.entries.forEach { a -> Pill(Format.body(a), a in sore) { sore = if (a in sore) sore - a else sore + a } }
        }
        RytmButton("Zapisz", Modifier.fillMaxWidth(), primary = true) { onSave(energy, mood, sore); onDismiss() }
    }
}

private val DURATIONS = listOf(30, 60, 90, 120, 180, 240)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OtherActivitySheet(onDismiss: () -> Unit, onSave: (String, Int, Int?, Set<LoadedArea>) -> Unit) {
    var description by remember { mutableStateOf("") }
    var duration by remember { mutableIntStateOf(60) }
    var rpe by remember { mutableStateOf<Int?>(null) }
    var areas by remember { mutableStateOf(emptySet<LoadedArea>()) }
    var areasTouched by remember { mutableStateOf(false) }

    RytmSheet("Zaloguj inną aktywność", onDismiss) {
        OutlinedTextField(
            value = description,
            onValueChange = {
                description = it
                // Domyślne zaznaczenie z opisu („remont” → plecy i barki), dopóki użytkownik sam nie wybierze.
                if (!areasTouched) areas = LoadedArea.defaultsFor(it)
            },
            label = { Text("Co to było? np. remont: noszenie płyt") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Label("Ile trwało")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DURATIONS.forEach { d -> Pill(Format.minutes(d), d == duration) { duration = d } }
        }
        Label("Jak ciężko (RPE 1–10)")
        Scale(1..5, rpe) { rpe = it }
        Scale(6..10, rpe) { rpe = it }
        Label("Co obciążyło")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LoadedArea.entries.forEach { a ->
                Pill(Format.loaded(a), a in areas) {
                    areasTouched = true
                    areas = if (a in areas) areas - a else areas + a
                }
            }
        }
        SmallText("Od 2 godzin albo sRPE 400 planer odciąży jutro zaznaczone partie.")
        RytmButton("Zapisz", Modifier.fillMaxWidth(), primary = true, enabled = description.isNotBlank()) {
            onSave(description, duration, rpe, areas)
            onDismiss()
        }
    }
}

@Composable
fun NutritionSheet(current: com.zacco.rytm.domain.model.NutritionDay?, onDismiss: () -> Unit, onSave: (Int, Double, Double?, Double?) -> Unit) {
    var kcal by remember { mutableStateOf(current?.kcal?.toString().orEmpty()) }
    var protein by remember { mutableStateOf(current?.proteinG?.let { Format.roundInt(it).toString() }.orEmpty()) }
    var fat by remember { mutableStateOf(current?.fatG?.takeIf { it > 0 }?.let { Format.roundInt(it).toString() }.orEmpty()) }
    var carbs by remember { mutableStateOf(current?.carbsG?.takeIf { it > 0 }?.let { Format.roundInt(it).toString() }.orEmpty()) }
    val kcalVal = kcal.toIntOrNull()
    val proteinVal = protein.replace(',', '.').toDoubleOrNull()

    RytmSheet("Żywienie z Fitatu", onDismiss) {
        SmallText("Przepisz podsumowanie dnia z Fitatu. Kcal i białko wystarczą.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Kcal", kcal, Modifier.weight(1f)) { kcal = it }
            NumberField("Białko (g)", protein, Modifier.weight(1f)) { protein = it }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Tłuszcz (g)", fat, Modifier.weight(1f)) { fat = it }
            NumberField("Węgle (g)", carbs, Modifier.weight(1f)) { carbs = it }
        }
        RytmButton("Zapisz", Modifier.fillMaxWidth(), primary = true, enabled = kcalVal != null && proteinVal != null) {
            onSave(kcalVal!!, proteinVal!!, fat.replace(',', '.').toDoubleOrNull(), carbs.replace(',', '.').toDoubleOrNull())
            onDismiss()
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == ',' || it == '.' }.take(6)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
        modifier = modifier,
    )
}

/** Ocena RPE treningów z danego dnia (zegarek nie zapisuje odczuć, a tętno je zaniża). */
@Composable
fun RpeSheet(activities: List<com.zacco.rytm.domain.model.Activity>, onDismiss: () -> Unit, onSave: (String, Int) -> Unit) {
    var selected by remember { mutableStateOf(activities.associate { it.id to it.rpe }) }
    RytmSheet("Jak ciężko było? (RPE)", onDismiss) {
        SmallText("1 = bardzo lekko, 5 = umiarkowanie, 7 = ciężko, 10 = maksimum.")
        activities.forEach { a ->
            Label(Format.activity(a.type) + " · " + Format.minutes(a.durationMin))
            Scale(1..5, selected[a.id]) { v -> selected = selected + (a.id to v) }
            Scale(6..10, selected[a.id]) { v -> selected = selected + (a.id to v) }
        }
        RytmButton("Zapisz", Modifier.fillMaxWidth(), primary = true) {
            selected.forEach { (id, rpe) -> if (rpe != null) onSave(id, rpe) }
            onDismiss()
        }
    }
}

@Composable
fun WeighInSheet(onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var kg by remember { mutableStateOf("") }
    val value = kg.replace(',', '.').toDoubleOrNull()?.takeIf { it in 30.0..250.0 }
    RytmSheet("Dodaj ważenie", onDismiss) {
        SmallText("Wpisz wynik z Xiaomi Home. Najlepiej rano, po toalecie, przed jedzeniem.")
        NumberField("Waga (kg)", kg, Modifier.fillMaxWidth()) { kg = it }
        RytmButton("Zapisz", Modifier.fillMaxWidth(), primary = true, enabled = value != null) {
            onSave(value!!)
            onDismiss()
        }
    }
}
