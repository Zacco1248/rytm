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
