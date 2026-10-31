package com.zacco.rytm.phone.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zacco.rytm.domain.coach.HabitStatus
import com.zacco.rytm.domain.coach.TodaySnapshot
import com.zacco.rytm.domain.habits.HabitAutoSource
import com.zacco.rytm.domain.habits.HabitKind
import com.zacco.rytm.domain.readiness.ReadinessComponent
import com.zacco.rytm.domain.readiness.ReadinessLevel
import com.zacco.rytm.domain.time.RytmTime
import com.zacco.rytm.phone.app.Format
import com.zacco.rytm.phone.app.HcStatus
import com.zacco.rytm.phone.app.SyncUi
import com.zacco.rytm.phone.ui.components.Bar
import com.zacco.rytm.phone.ui.components.ButtonRow
import com.zacco.rytm.phone.ui.components.CardHeader
import com.zacco.rytm.phone.ui.components.Chip
import com.zacco.rytm.phone.ui.components.ChipTone
import com.zacco.rytm.phone.ui.components.Ring
import com.zacco.rytm.phone.ui.components.RytmButton
import com.zacco.rytm.phone.ui.components.RytmCard
import com.zacco.rytm.phone.ui.components.ScreenHeader
import com.zacco.rytm.phone.ui.components.SmallText
import com.zacco.rytm.phone.ui.components.WhyBox
import com.zacco.rytm.phone.ui.theme.Rytm
import java.time.format.DateTimeFormatter

private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun TodayScreen(
    s: TodaySnapshot,
    hc: HcStatus,
    sync: SyncUi,
    onConnect: () -> Unit,
    onSync: () -> Unit,
    onProfile: () -> Unit,
    onToggleHabit: (HabitStatus) -> Unit,
    onCheckIn: () -> Unit,
    onOtherActivity: () -> Unit,
    onNutrition: () -> Unit,
    onDayOff: () -> Unit,
    onAlcohol: () -> Unit,
) {
    ScreenHeader(sub = Format.dayLong(s.date), title = greeting()) {
        Chip("Profil", ChipTone.MUTED, onClick = onProfile)
    }

    if (hc != HcStatus.READY && hc != HcStatus.CHECKING) HealthConnectCard(hc, onConnect)
    sync.error?.let { RytmCard { SmallText(it, color = Rytm.colors.bad) } }

    ReadinessCard(s, onCheckIn)
    PlanCard(s, onDayOff)
    NutritionCard(s, sync, onSync, onNutrition)
    HabitsCard(s.habits, onToggleHabit)

    RytmCard {
        CardHeader("Szybki wpis")
        ButtonRow {
            RytmButton("Zaloguj inną aktywność", Modifier.weight(1f), onClick = onOtherActivity)
            RytmButton("Check-in", Modifier.weight(1f), onClick = onCheckIn)
        }
        ButtonRow {
            RytmButton("Żywienie (też wstecz)", Modifier.weight(1f), onClick = onNutrition)
            RytmButton(if (s.alcoholToday + s.alcoholYesterday > 0) "Alkohol ✓" else "Alkohol", Modifier.weight(1f), onClick = onAlcohol)
        }
    }
}

private fun greeting(): String {
    val h = java.time.LocalTime.now(RytmTime.zone).hour
    return when (h) {
        in 5..11 -> "Dzień dobry"
        in 12..17 -> "Cześć"
        else -> "Dobry wieczór"
    }
}

@Composable
private fun HealthConnectCard(hc: HcStatus, onConnect: () -> Unit) {
    RytmCard {
        CardHeader("Health Connect") { Chip("niepołączony", ChipTone.WARN) }
        SmallText(
            if (hc == HcStatus.NOT_INSTALLED) "Zainstaluj albo zaktualizuj Health Connect, żeby Rytm widział sen, tętno, treningi, Fitatu i wagę."
            else "Rytm potrzebuje dostępu do snu, tętna, treningów, żywienia i wagi. Zaznacz wszystko, także odczyt w tle.",
        )
        RytmButton("Połącz z Health Connect", Modifier.fillMaxWidth(), primary = true, onClick = onConnect)
    }
}

@Composable
private fun ReadinessCard(s: TodaySnapshot, onCheckIn: () -> Unit) {
    val c = Rytm.colors
    val r = s.readiness
    val ringColor = when (r.level) {
        ReadinessLevel.HIGH -> c.good
        ReadinessLevel.MODERATE -> c.warn
        ReadinessLevel.LOW -> c.bad
    }
    RytmCard {
        CardHeader("Gotowość") {
            if (r.calibrating) Chip("Kalibracja ${s.baselineDays}/14", ChipTone.WARN)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Ring(r.score / 100f, ringColor) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${r.score}", style = Rytm.type.ringNumber, color = c.ink)
                    Text(Format.level(r.level), style = Rytm.type.tiny, color = c.muted)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val comp = r.components
                ComponentRow("Sen", s.night?.sleepMin?.let { Format.minutes(it) } ?: "brak zapisu", comp[ReadinessComponent.SLEEP])
                ComponentRow(
                    "Tętno spocz.",
                    s.restingHr?.let { hr ->
                        "$hr" + (if (s.restingHrFromDay) " w dzień" else "") + (s.restingHrAvg?.let { " (śr. ${Format.roundInt(it)})" } ?: "")
                    } ?: "brak",
                    comp[ReadinessComponent.RESTING_HR],
                )
                ComponentRow(
                    "HRV",
                    s.checkIn?.hrvRmssd?.let { v -> "${Format.roundInt(v)} ms" + (s.hrvAvg?.let { " (śr. ${Format.roundInt(it)})" } ?: "") } ?: "brak pomiaru",
                    comp[ReadinessComponent.HRV],
                )
                ComponentRow(
                    "Samopoczucie",
                    s.checkIn?.let { "energia ${it.energy} · nastrój ${it.mood}" } ?: "brak check-inu",
                    comp[ReadinessComponent.SUBJECTIVE],
                )
                ComponentRow("Wczorajszy load", Format.int(s.load.yesterday), comp[ReadinessComponent.LOAD])
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val ci = s.checkIn
            SmallText(
                if (ci == null) "Brak porannego check-inu." else
                    "Check-in zapisany" + (if (ci.soreAreas.isNotEmpty()) " · bolą: " + ci.soreAreas.joinToString { Format.body(it).lowercase() } else "") + ".",
                Modifier.weight(1f),
            )
            Chip(if (ci == null) "Zrób check-in" else "Popraw", ChipTone.ACCENT, onClick = onCheckIn)
        }
        if (s.alcoholYesterday > 0) {
            SmallText(
                "Wczoraj alkohol: ${s.alcoholYesterday} porc. (−${com.zacco.rytm.domain.readiness.ReadinessCalculator.alcoholPenalty(s.alcoholYesterday)} pkt).",
                color = Rytm.colors.warn,
            )
        }
        if (s.restingHrFromDay) SmallText("Brak snu z zegarka, więc tętno spoczynkowe liczę z najspokojniejszych 30 min dnia.")
    }
}

@Composable
private fun ComponentRow(name: String, value: String, score: Int?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, style = Rytm.type.small, color = Rytm.colors.ink)
            Text(value, style = Rytm.type.small, color = Rytm.colors.muted)
        }
        Bar((score ?: 0) / 100f, Rytm.colors.accent)
    }
}

@Composable
private fun PlanCard(s: TodaySnapshot, onDayOff: () -> Unit) {
    val p = s.plan
    RytmCard {
        CardHeader("Plan na dziś") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(if (s.dayOff) "Cofnij wolne" else "Dziś wolne", ChipTone.MUTED, onClick = onDayOff)
                Chip(Format.intensity(p.intensity))
            }
        }
        Text(p.title, style = Rytm.type.planTitle, color = Rytm.colors.ink)
        p.workout?.let { w -> WorkoutList(w) }
        p.warnings.forEach { SmallText("⚠ $it", color = Rytm.colors.warn) }
        if (p.rationale.isNotEmpty()) WhyBox(p.rationale.joinToString(" "))
        SmallText("Jutro: ${s.tomorrow.title.lowercase()}. Pora snu dziś: ${s.bedtime.format(hhmm)}.")
    }
}

@Composable
private fun NutritionCard(s: TodaySnapshot, sync: SyncUi, onSync: () -> Unit, onNutrition: () -> Unit) {
    val c = Rytm.colors
    val n = s.nutrition
    val t = s.nutritionTargets
    RytmCard {
        CardHeader("Żywienie · Fitatu") {
            val label = when {
                sync.running -> "synchronizuję…"
                s.lastSync != null -> "sync " + s.lastSync!!.atZone(RytmTime.zone).format(hhmm)
                else -> "synchronizuj"
            }
            Chip(label, ChipTone.MUTED, onClick = onSync)
        }
        if (s.trainingKcal > 0) SmallText("Cel powiększony o ${Format.int(s.trainingKcal)} kcal z treningu (0,75 × kcal z zegarka), makro proporcjonalnie.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Macro("Kcal", n?.kcal ?: 0, t.kcal, "", c.accent, Modifier.weight(1f))
            Macro("Białko", Format.roundInt(n?.proteinG ?: 0.0), t.proteinG, " g", c.good, Modifier.weight(1f))
            Macro("Tłuszcz", Format.roundInt(n?.fatG ?: 0.0), t.fatG, " g", c.warn, Modifier.weight(1f))
            Macro("Węgle", Format.roundInt(n?.carbsG ?: 0.0), t.carbsG, " g", c.kindOther, Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallText(if (n == null) "Brak danych z Fitatu na dziś." else "Zgadza się z Fitatu?", Modifier.weight(1f))
            Chip(if (n == null) "Wpisz z Fitatu" else "Popraw", ChipTone.ACCENT, onClick = onNutrition)
        }
    }
}

@Composable
private fun Macro(name: String, value: Int, goal: Int, unit: String, color: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(name, style = Rytm.type.tiny, color = Rytm.colors.muted)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Format.int(value), style = Rytm.type.bodyStrong, color = Rytm.colors.ink)
            Text(" / ${Format.int(goal)}$unit", style = Rytm.type.tiny, color = Rytm.colors.muted)
        }
        Bar(if (goal > 0) value.toFloat() / goal else 0f, color, height = 6.dp)
    }
}

@Composable
fun HabitsCard(habits: List<HabitStatus>, onToggle: (HabitStatus) -> Unit, showStreak: Boolean = false) {
    RytmCard {
        CardHeader("Nawyki") {
            SmallText("${habits.count { it.done }} / ${habits.size}")
        }
        habits.forEach { h -> HabitRow(h, onToggle, showStreak) }
    }
}

@Composable
private fun HabitRow(h: HabitStatus, onToggle: (HabitStatus) -> Unit, showStreak: Boolean) {
    val c = Rytm.colors
    val auto = h.habit.autoSource != null
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (h.done) c.good else c.surface)
                .border(2.dp, if (h.done) c.good else c.line, CircleShape)
                .then(if (auto) Modifier else Modifier.clickable(role = Role.Checkbox) { onToggle(h) })
                .semantics { contentDescription = "Odhacz: ${h.habit.name}" },
            contentAlignment = Alignment.Center,
        ) {
            if (h.done) Text("✓", style = Rytm.type.bodyStrong.copy(fontWeight = FontWeight.Bold), color = c.bg)
        }
        Column(Modifier.weight(1f)) {
            Text(h.habit.name, style = Rytm.type.body.copy(fontWeight = FontWeight.Medium), color = c.ink)
            SmallText(habitDetail(h))
        }
        if (showStreak && h.streak > 0) Chip("seria ${h.streak}", ChipTone.GOOD)
    }
}

private fun habitDetail(h: HabitStatus): String = when {
    h.habit.autoSource == HabitAutoSource.STEPS ->
        "${Format.int(h.value ?: 0)} / ${Format.int(h.habit.target ?: 0)} kroków · z Health Connect"
    h.habit.kind == HabitKind.MINUTES -> "${h.habit.target} min dziennie"
    else -> "codziennie"
}

/** Ćwiczenia sesji: serie × powtórzenia, ciężar, tempo. Ciężar to punkt startu; progresja przyjdzie z dziennikiem serii. */
@Composable
private fun WorkoutList(w: com.zacco.rytm.domain.model.StrengthWorkout) {
    val c = Rytm.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SmallText(w.name)
        w.exercises.forEachIndexed { i, e ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${i + 1}.", style = Rytm.type.small, color = c.muted)
                Column(Modifier.weight(1f)) {
                    Text(e.name, style = Rytm.type.small.copy(fontWeight = FontWeight.Medium), color = c.ink)
                    val reps = if (e.reps.first == e.reps.last) "${e.reps.first}" else "${e.reps.first}–${e.reps.last}"
                    SmallText(
                        listOfNotNull(
                            "${e.sets} × $reps",
                            e.weightKg?.let { "do ${Format.dec(it, 0)} kg" },
                            e.tempo?.let { "tempo $it" },
                            e.restSec.takeIf { it > 0 }?.let { "przerwa ${it}s" },
                        ).joinToString(" · "),
                    )
                }
            }
        }
        if (w.exercises.size > 3 && w.name != "Mobilność") {
            SmallText("Mało czasu? Zrób 3 pierwsze ćwiczenia po 2 serie (ok. 20 min). Plan tygodnia się dostosuje.")
        }
    }
}
