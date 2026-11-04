package com.zacco.rytm.phone.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zacco.rytm.domain.coach.HabitStatus
import com.zacco.rytm.domain.habits.HabitAutoSource
import com.zacco.rytm.domain.habits.HabitKind
import com.zacco.rytm.domain.habits.HabitSpec
import com.zacco.rytm.phone.app.Format
import com.zacco.rytm.phone.today.RytmSheet
import com.zacco.rytm.phone.today.Scale
import com.zacco.rytm.phone.ui.components.Divider
import com.zacco.rytm.phone.ui.components.Pill
import com.zacco.rytm.phone.ui.components.RytmButton
import com.zacco.rytm.phone.ui.components.RytmCard
import com.zacco.rytm.phone.ui.components.RytmIcons
import com.zacco.rytm.phone.ui.components.CardHeader
import com.zacco.rytm.phone.ui.components.ScreenHeader
import com.zacco.rytm.phone.ui.components.SmallText
import com.zacco.rytm.phone.ui.theme.Rytm
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Zakładka „Nawyki”: tydzień każdego nawyku w kropkach i lista do zarządzania (edycja, usuwanie, nowy).
 * Usunięcie od razu, z paskiem „Cofnij” (obsługuje RytmRoot).
 */
@Composable
fun HabitsScreen(
    habits: List<HabitStatus>,
    specs: List<HabitSpec>,
    today: LocalDate,
    onToggle: (HabitStatus) -> Unit,
    onSave: (HabitSpec) -> Unit,
    onDelete: (HabitSpec) -> Unit,
) {
    var editing by remember { mutableStateOf<HabitSpec?>(null) }
    var adding by remember { mutableStateOf(false) }
    ScreenHeader(sub = "Tydzień od poniedziałku", title = "Nawyki")

    if (habits.isEmpty()) {
        RytmCard { SmallText("Nie masz żadnych nawyków. Dodaj pierwszy przyciskiem poniżej.") }
    } else {
        WeekCard(habits, today, onToggle)
    }

    RytmCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp), spacing = 0.dp) {
        CardHeader("Zarządzaj")
        specs.forEach { spec ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(spec.name, style = Rytm.type.body, color = Rytm.colors.ink)
                    SmallText(describe(spec, habits.firstOrNull { it.habit.id == spec.id }?.habit?.target))
                }
                IconAction(RytmIcons.edit, "Zmień nawyk ${spec.name}", Rytm.colors.muted) { editing = spec }
                IconAction(RytmIcons.trash, "Usuń nawyk ${spec.name}", Rytm.colors.bad) { onDelete(spec) }
            }
            Divider()
        }
        Text(
            "+ Nowy nawyk",
            style = Rytm.type.bodyStrong,
            color = Rytm.colors.accent,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { adding = true }
                .padding(top = 13.dp),
        )
    }

    if (adding) HabitEditSheet(null, onDismiss = { adding = false }) { onSave(it); adding = false }
    editing?.let { e -> HabitEditSheet(e, onDismiss = { editing = null }) { onSave(it); editing = null } }
}

private fun describe(spec: HabitSpec, target: Int?): String {
    val how = spec.timesPerWeek?.let { "$it× w tygodniu" } ?: "codziennie"
    return when {
        spec.auto == HabitAutoSource.STEPS -> "${Format.int(target ?: spec.target ?: 0)} kroków · z Health Connect"
        spec.auto == HabitAutoSource.MOBILITY -> "${spec.target ?: 10} min · samo po rozciąganiu"
        spec.kind == HabitKind.MINUTES -> "${spec.target ?: 10} min · $how"
        spec.kind == HabitKind.COUNT -> "${spec.target ?: 1} razy · $how"
        else -> how
    }
}

@Composable
private fun IconAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = label, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Tydzień w kropkach: zielona zrobione, obrys nie, blada przyszłość. Dzisiejszą kropkę można odhaczyć. */
@Composable
private fun WeekCard(habits: List<HabitStatus>, today: LocalDate, onToggle: (HabitStatus) -> Unit) {
    val c = Rytm.colors
    val monday = today.with(DayOfWeek.MONDAY)
    RytmCard {
        Row(Modifier.fillMaxWidth()) {
            (0L until 7L).map { monday.plusDays(it) }.forEach { d ->
                Text(
                    Format.weekdayShort(d.dayOfWeek),
                    style = Rytm.type.tiny.copy(fontWeight = if (d == today) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (d == today) c.ink else c.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        habits.forEachIndexed { i, h ->
            if (i > 0) Divider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(h.habit.name, style = Rytm.type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
                val streak = h.streak
                Text(
                    if (streak >= 2) "seria $streak" else "${h.weekDone} z ${h.weekRequired}",
                    style = Rytm.type.small,
                    color = if (streak >= 2) c.good else c.muted,
                )
            }
            Row(Modifier.fillMaxWidth()) {
                h.week.forEachIndexed { idx, done ->
                    val isToday = monday.plusDays(idx.toLong()) == today
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        val canToggle = isToday && !h.habit.autoOnly
                        Box(
                            Modifier
                                .size(if (canToggle) 44.dp else 30.dp)
                                .then(if (canToggle) Modifier.clip(CircleShape).clickable(role = Role.Checkbox, onClickLabel = "Odhacz dziś") { onToggle(h) } else Modifier)
                                .semantics {
                                    contentDescription = Format.weekdayShort(monday.plusDays(idx.toLong()).dayOfWeek) + when (done) {
                                        true -> ": zrobione"
                                        false -> ": nie"
                                        null -> ""
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .then(
                                        when (done) {
                                            true -> Modifier.background(c.good)
                                            false -> Modifier.border(2.dp, if (isToday) c.accent else c.outline, CircleShape)
                                            null -> Modifier.background(c.track)
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (done == true) Text("✓", style = Rytm.type.tiny.copy(fontWeight = FontWeight.Bold), color = c.onAccent)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Nowy nawyk albo zmiana istniejącego. Kroki i mobilność mają stały rodzaj, zmieniasz nazwę i cel. */
@Composable
private fun HabitEditSheet(initial: HabitSpec?, onDismiss: () -> Unit, onSave: (HabitSpec) -> Unit) {
    val auto = initial?.auto
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var kind by remember { mutableStateOf(initial?.kind ?: HabitKind.YES_NO) }
    var target by remember { mutableStateOf((initial?.target ?: if (auto == HabitAutoSource.STEPS) 6000 else 10).toString()) }
    var times by remember { mutableStateOf(initial?.timesPerWeek) }
    RytmSheet(if (initial == null) "Nowy nawyk" else "Zmień nawyk", onDismiss) {
        OutlinedTextField(
            name, { name = it.take(40) },
            label = { Text("Nazwa, np. „Bieg”, „Woda 2 l”, „Bez słodyczy”") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (auto == null) {
            SmallText("Rodzaj")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("Tak / nie", kind == HabitKind.YES_NO) { kind = HabitKind.YES_NO }
                Pill("Minuty", kind == HabitKind.MINUTES) { kind = HabitKind.MINUTES }
                Pill("Liczba", kind == HabitKind.COUNT) { kind = HabitKind.COUNT }
            }
        }
        if (auto != null || kind != HabitKind.YES_NO) {
            OutlinedTextField(
                target, { v -> target = v.filter { it.isDigit() }.take(6) },
                label = {
                    Text(
                        when {
                            auto == HabitAutoSource.STEPS -> "Cel kroków dziennie"
                            kind == HabitKind.MINUTES || auto == HabitAutoSource.MOBILITY -> "Ile minut"
                            else -> "Ile razy"
                        },
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (auto == null) {
            SmallText("Jak często")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("Codziennie", times == null) { times = null }
                Pill("Kilka razy w tygodniu", times != null) { if (times == null) times = 3 }
            }
            times?.let { t ->
                SmallText("Ile razy w tygodniu")
                Scale(1..6, t) { times = it }
            }
        }
        RytmButton("Zapisz", Modifier.fillMaxWidth(), primary = true, enabled = name.isNotBlank()) {
            val n = target.toIntOrNull()?.coerceAtLeast(1)
            onSave(
                HabitSpec(
                    id = initial?.id ?: ("h-" + System.currentTimeMillis().toString(36)),
                    name = name.trim(),
                    kind = if (auto != null) initial?.kind ?: kind else kind,
                    target = if (auto != null || kind != HabitKind.YES_NO) n else null,
                    auto = auto,
                    timesPerWeek = if (auto != null) null else times,
                ),
            )
        }
    }
}
