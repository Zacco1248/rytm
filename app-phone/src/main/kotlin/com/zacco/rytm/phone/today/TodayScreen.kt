package com.zacco.rytm.phone.today

import com.zacco.rytm.ai.morning.MorningNote

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.zacco.rytm.domain.exercise.ExerciseGuide
import com.zacco.rytm.domain.exercise.ExerciseInfo
import com.zacco.rytm.domain.coach.TodaySnapshot
import com.zacco.rytm.domain.habits.HabitAutoSource
import com.zacco.rytm.domain.habits.HabitKind
import com.zacco.rytm.domain.readiness.CalibrationGap
import com.zacco.rytm.domain.readiness.CalibrationStatus
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
import com.zacco.rytm.phone.ui.components.Divider
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
    onEntries: () -> Unit = {},
    onToggleHabit: (HabitStatus) -> Unit,
    onCheckIn: () -> Unit,
    onNutrition: () -> Unit,
    onDayOff: () -> Unit,
    onAdd: () -> Unit,
    onLogWorkout: () -> Unit = {},
    phoneRunActive: Boolean = false,
    onPhoneWorkout: () -> Unit = {},
    morning: MorningNote? = null,
    onCoach: () -> Unit = {},
    onResetPlan: () -> Unit = {},
    onAddSleep: () -> Unit = {},
) {
    ScreenHeader(sub = Format.dayLong(s.date), title = greeting())
    // Chipy pod tytułem: obok długiego „Dobry wieczór” się nie mieściły.
    run {
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val syncLabel = when {
                sync.running -> "↻ …"
                s.lastSync != null -> "↻ " + s.lastSync!!.atZone(RytmTime.zone).format(hhmm)
                else -> "↻ synchronizuj"
            }
            Chip(syncLabel, ChipTone.MUTED, onClick = onSync)
            Chip("Wpisy", ChipTone.MUTED, onClick = onEntries)
            Chip("Profil", ChipTone.MUTED, onClick = onProfile)
        }
    }

    if (hc != HcStatus.READY && hc != HcStatus.CHECKING) HealthConnectCard(hc, onConnect)
    sync.error?.let { RytmCard { SmallText(it, color = Rytm.colors.bad) } }

    ReadinessCard(s, onCheckIn, onAddSleep)
    if (morning != null && s.checkIn != null) MorningCard(morning)
    PlanCard(s, onDayOff, onLogWorkout, phoneRunActive, onPhoneWorkout, onCoach, onResetPlan)
    NutritionCard(s, sync, onSync, onNutrition)
    HabitsCard(s.habits, onToggleHabit)

    ActivityTodayCard(s, onAdd)
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
private fun ReadinessCard(s: TodaySnapshot, onCheckIn: () -> Unit, onAddSleep: () -> Unit) {
    val c = Rytm.colors
    val r = s.readiness
    val ringColor = when (r.level) {
        ReadinessLevel.HIGH -> c.good
        ReadinessLevel.MODERATE -> c.warn
        ReadinessLevel.LOW -> c.bad
    }
    var showCalibration by remember { mutableStateOf(false) }
    RytmCard {
        CardHeader("Gotowość", help = "Gotowość 0–100 mówi, na ile organizm jest dziś gotowy na wysiłek. Składa się ze snu, tętna spoczynkowego i HRV na tle Twoich ostatnich dni, check-inu (energia, nastrój, co boli) i wczorajszego obciążenia. Powyżej 70 pełny trening, 40–70 umiarkowanie, poniżej 40 lżej albo odpoczynek. Pierwsze dni to kalibracja, bo Rytm uczy się Twoich norm.") {
            val cal = s.calibration
            if (r.calibrating && cal != null) {
                Chip("Kalibracja ${cal.restingHrDays.coerceAtMost(cal.needDays)}/${cal.needDays} " + if (showCalibration) "▴" else "▾", ChipTone.WARN) {
                    showCalibration = !showCalibration
                }
            }
        }
        s.calibration?.takeIf { showCalibration && r.calibrating }?.let { CalibrationDetails(it) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Ring(r.score / 100f, ringColor) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${r.score}", style = Rytm.type.ringNumber, color = c.ink)
                    Text(Format.level(r.level), style = Rytm.type.tiny, color = c.muted)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val comp = r.components
                ComponentRow(
                    "Sen",
                    s.night?.sleepMin?.let { Format.minutes(it) + if (s.night?.sleepManual == true) " (ręcznie)" else "" } ?: "brak zapisu",
                    comp[ReadinessComponent.SLEEP],
                )
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
        if (s.night?.sleepMin == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallText("Zegarek nie zapisał snu z tej nocy.", Modifier.weight(1f))
                Chip("Wpisz sen", ChipTone.ACCENT, onClick = onAddSleep)
            }
        } else if (s.night?.sleepManual == true) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallText("Sen wpisany ręcznie.", Modifier.weight(1f))
                Chip("Popraw", ChipTone.MUTED, onClick = onAddSleep)
            }
        }
        if (s.restingHrFromDay) SmallText("Brak snu z zegarka, więc tętno spoczynkowe liczę z najspokojniejszych 30 min dnia.")
    }
}

@Composable
private fun KcalRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = Rytm.type.small, color = Rytm.colors.muted, modifier = Modifier.weight(1f))
        Text(value, style = Rytm.type.small, color = Rytm.colors.ink, maxLines = 1, softWrap = false)
    }
}

/** Poranne uzasadnienie planu po check-inie (AI albo reguły). */
@Composable
private fun MorningCard(n: MorningNote) {
    RytmCard {
        CardHeader("Na dziś")
        Text(n.headline, style = Rytm.type.bodyStrong, color = Rytm.colors.ink)
        WhyBox(n.why)
        SmallText(n.tip)
    }
}

@Composable
private fun ComponentRow(name: String, value: String, score: Int?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Text(name, style = Rytm.type.small, color = Rytm.colors.ink, maxLines = 1, softWrap = false)
            Text(
                value, style = Rytm.type.small, color = Rytm.colors.muted, modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
        Bar((score ?: 0) / 100f, Rytm.colors.accent)
    }
}

@Composable
private fun PlanCard(
    s: TodaySnapshot,
    onDayOff: () -> Unit,
    onLogWorkout: () -> Unit,
    phoneRunActive: Boolean,
    onPhoneWorkout: () -> Unit,
    onCoach: () -> Unit,
    onResetPlan: () -> Unit,
) {
    val p = s.plan
    RytmCard {
        CardHeader("Plan na dziś") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(if (s.dayOff) "Cofnij wolne" else "Dziś wolne", ChipTone.MUTED, onClick = onDayOff)
                Chip(Format.intensity(p.intensity))
            }
        }
        Text(p.title, style = Rytm.type.planTitle, color = Rytm.colors.ink)
        if (s.planChanged) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallText("Zmienione w rozmowie z trenerem.", Modifier.weight(1f), color = Rytm.colors.accent)
                Chip("Przywróć plan", ChipTone.MUTED, onClick = onResetPlan)
            }
        }
        p.workout?.let { w ->
            WorkoutList(w)
            if (s.setsToday.isNotEmpty()) {
                val volume = s.setsToday.sumOf { x -> x.weightKg?.let { it * x.reps } ?: 0.0 }
                SmallText(
                    "✓ Zapisano ${s.setsToday.size} serii" + if (volume > 0) " · objętość ${Format.dec(volume, 0)} kg" else "",
                    color = Rytm.colors.good,
                )
            }
            RytmButton(
                if (phoneRunActive) "Wróć do treningu" else "Zacznij trening na telefonie",
                Modifier.fillMaxWidth(),
                primary = true,
                onClick = onPhoneWorkout,
            )
            RytmButton(if (s.setsToday.isEmpty()) "Wpisz trening po fakcie" else "Wpisz kolejny trening", Modifier.fillMaxWidth(), onClick = onLogWorkout)
            SmallText("Telefon prowadzi seria po serii z przerwą i wibracją. Na zegarku z Wear OS to samo daje „Zacznij trening”.")
        }
        p.cardio?.let { cs ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                cs.steps.forEachIndexed { i, step -> SmallText("${i + 1}. $step", color = Rytm.colors.ink) }
            }
            SmallText("Tętno i kcal przyjdą z zegarka albo opaski przez Health Connect. Bez nich dodaj sesję przyciskiem „+”.")
        }
        p.warnings.forEach { SmallText("⚠ $it", color = Rytm.colors.warn) }
        if (p.rationale.isNotEmpty()) WhyBox(p.rationale.joinToString(" "))
        SmallText("Jutro: ${s.tomorrow.title.lowercase()}. Pora snu dziś: ${s.bedtime.format(hhmm)}.")
        RytmButton("Mniej czasu? Coś boli? Zapytaj trenera", Modifier.fillMaxWidth(), onClick = onCoach)
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
        if (s.trainingKcal > 0) SmallText("Cel powiększony o ${Format.int(s.trainingKcal)} kcal z treningu (${Format.dec(s.trainingKcalFactor, 2).trimEnd('0').trimEnd(',')} × kcal z zegarka), makro proporcjonalnie.")
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
        // Wartość i cel w dwóch liniach: „2 550 / 2 623” nie mieści się w ćwiartce szerokości.
        Text(Format.int(value), style = Rytm.type.bodyStrong, color = Rytm.colors.ink, maxLines = 1, softWrap = false)
        Text("z ${Format.int(goal)}$unit", style = Rytm.type.tiny, color = Rytm.colors.muted, maxLines = 1, softWrap = false)
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
    val auto = h.habit.autoOnly
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
    h.habit.autoSource == HabitAutoSource.MOBILITY && (h.value ?: 0) > 0 -> "${h.value} min z rozciągania, jogi albo mobilności"
    h.habit.autoSource == HabitAutoSource.MOBILITY -> "${h.habit.target} min dziennie · odhaczy się samo po rozciąganiu albo jodze"
    h.habit.kind == HabitKind.MINUTES -> "${h.habit.target} min dziennie"
    else -> "codziennie"
}

/** Ćwiczenia sesji: serie × powtórzenia, ciężar z progresji, tempo i podpowiedź z ostatniego razu. */
@Composable
private fun WorkoutList(w: com.zacco.rytm.domain.model.StrengthWorkout) {
    val c = Rytm.colors
    var info by remember { mutableStateOf<ExerciseInfo?>(null) }
    info?.let { ExerciseSheet(it) { info = null } }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SmallText(w.name + " · dotknij ćwiczenia, żeby zobaczyć opis")
        w.exercises.forEachIndexed { i, e ->
            val guide = ExerciseGuide.find(e.name)
            Row(
                Modifier.fillMaxWidth().then(if (guide != null) Modifier.clickable { info = guide } else Modifier),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("${i + 1}.", style = Rytm.type.small, color = c.muted)
                Column(Modifier.weight(1f)) {
                    Text(e.name + if (guide != null) "  ⓘ" else "", style = Rytm.type.small.copy(fontWeight = FontWeight.Medium), color = c.ink)
                    val reps = if (e.reps.first == e.reps.last) "${e.reps.first}" else "${e.reps.first}–${e.reps.last}"
                    SmallText(
                        listOfNotNull(
                            "${e.sets} × $reps",
                            e.weightKg?.let { "${Format.kg(it)} kg" },
                            e.tempo?.let { "tempo $it" },
                            e.restSec.takeIf { it > 0 }?.let { "przerwa ${it}s" },
                        ).joinToString(" · "),
                    )
                    e.note?.let { SmallText(it, color = c.accent) }
                }
            }
        }
        if (w.exercises.size > 3 && w.name != "Mobilność") {
            SmallText("Mało czasu? Zrób 3 pierwsze ćwiczenia po 2 serie (ok. 20 min). Plan tygodnia się dostosuje.")
        }
    }
}

/** Co weszło do kalibracji i czego brakowało, dzień po dniu. */
@Composable
private fun CalibrationDetails(cal: CalibrationStatus) {
    val c = Rytm.colors
    Column(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(c.soft).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Czego jeszcze brakuje", style = Rytm.type.bodyStrong, color = c.ink)
        val lines = buildList {
            add(
                (if (cal.restingHrReady) "✓ " else "• ") +
                    "Tętno spoczynkowe: ${cal.restingHrDays} z ${cal.needDays} potrzebnych dni (z ostatnich ${CalibrationStatus.WINDOW_DAYS}).",
            )
            cal.hrvDays?.let { add((if (cal.hrvReady) "✓ " else "• ") + "HRV: $it z ${cal.needDays} pomiarów. Mierz rano w check-inie.") }
            add(
                (if (cal.loadReliable) "✓ " else "• ") +
                    "Obciążenie treningowe: ${cal.loadHistoryDays.coerceAtMost(CalibrationStatus.LOAD_DAYS)} z ${CalibrationStatus.LOAD_DAYS} dni historii." +
                    if (cal.loadReliable) "" else " Do tego czasu ACWR jest orientacyjny.",
            )
        }
        lines.forEach { SmallText(it) }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            cal.days.forEach { d ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        Format.weekdayShort(d.date.dayOfWeek) + " " + Format.dayMonth(d.date),
                        style = Rytm.type.tiny,
                        color = c.muted,
                        modifier = Modifier.weight(0.38f),
                    )
                    val (text, color) = when (d.gap) {
                        null -> "✓ noc, tętno ${d.nightRestingHr}" + (if (d.hrv) " · HRV" else "") to c.good
                        CalibrationGap.DAY_ONLY -> "◐ brak nocy (ładowarka?), tętno z dnia ${d.dayRestingHr}" to c.warn
                        CalibrationGap.NO_HR -> "✕ sen bez tętna, sprawdź uprawnienia Health Connect" to c.bad
                        CalibrationGap.NO_DATA -> "✕ brak danych z zegarka" to c.bad
                    }
                    Text(text, style = Rytm.type.tiny, color = color, modifier = Modifier.weight(0.62f))
                }
            }
        }
        SmallText("Dni z tętnem tylko z dnia liczą się osobno: porównujemy je z innymi dniami bez nocy.")
    }
}

/** Wszystko, co dziś się ruszało, z kcal: treningi z planu, spacery spoza planu i wpisy ręczne. */
@Composable
private fun ActivityTodayCard(s: TodaySnapshot, onAdd: () -> Unit) {
    val c = Rytm.colors
    val acts = s.activitiesToday
    RytmCard {
        CardHeader(
            "Aktywność dziś",
            help = "Do celu jedzenia doliczam ${(s.trainingKcalFactor * 100).toInt()}% kcal z treningów (zegarki zwykle zawyżają spalanie), bez spacerów, bo te są już w ruchu z profilu. " +
                "Kcal z „~” to szacunek z czasu i RPE, gdy zegarek ich nie podał. „Cały dzień” to całe spalanie dnia, razem z przemianą spoczynkową.",
        ) {
            Chip(if (s.activityKcal > 0) "${Format.int(s.activityKcal)} kcal" else "+ dodaj", if (s.activityKcal > 0) ChipTone.GOOD else ChipTone.ACCENT, onClick = onAdd)
        }
        if (acts.isEmpty()) {
            SmallText("Jeszcze nic. Spacery i treningi z zegarka albo opaski pojawią się po synchronizacji; resztę dodasz przyciskiem „+”.")
        } else {
            acts.forEach { a ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        a.start.atZone(RytmTime.zone).format(hhmm),
                        style = Rytm.type.tiny,
                        color = c.muted,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(a.notes?.takeIf { it.isNotBlank() } ?: Format.activity(a.type), style = Rytm.type.small.copy(fontWeight = FontWeight.Medium), color = c.ink)
                        SmallText(
                            listOfNotNull(
                                Format.minutes(a.durationMin),
                                a.avgHr?.let { "śr. tętno $it" },
                                a.rpe?.let { "RPE $it" },
                                if (a.source == com.zacco.rytm.domain.model.Source.MANUAL) "wpis ręczny" else null,
                            ).joinToString(" · "),
                        )
                    }
                    val est = a.id in s.estimatedKcalIds
                    Text(
                        a.kcal?.let { (if (est) "~" else "") + "${Format.int(it)} kcal" } ?: "–",
                        style = Rytm.type.small, color = if (a.kcal != null && !est) c.ink else c.muted, maxLines = 1, softWrap = false,
                    )
                }
            }
        }
        if (acts.isNotEmpty()) {
            Divider()
            KcalRow("W aktywnościach", "${Format.int(s.activityKcal)} kcal")
            KcalRow("Doliczone do celu jedzenia", if (s.trainingKcal > 0) "+${Format.int(s.trainingKcal)} kcal" else "0 kcal")
            s.night?.totalKcal?.takeIf { it > 0 }?.let { KcalRow("Cały dzień wg zegarka", "${Format.int(it)} kcal") }
        }
    }
}
