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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.sp
import com.zacco.rytm.phone.ui.components.CardLabel
import com.zacco.rytm.phone.ui.components.IconCircleButton
import com.zacco.rytm.phone.ui.components.RytmIcons
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
    onQuickAdd: () -> Unit,
    onReadiness: () -> Unit,
    onOpenPlan: () -> Unit,
    phoneRunActive: Boolean = false,
    onPhoneWorkout: () -> Unit = {},
    morning: MorningNote? = null,
    onCoach: () -> Unit = {},
    onResetPlan: () -> Unit = {},
    initial: String = "",
) {
    // Nagłówek: „+” (wszystkie wpisy) i menu profilu. Pływający „+” zasłaniał treść, a chipy się ucinały.
    ScreenHeader(sub = Format.dayLong(s.date), title = greeting()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(RytmIcons.plus, "Dodaj wpis", primary = true, onClick = onQuickAdd)
            ProfileMenu(initial, s, sync, onProfile, onEntries, onSync)
        }
    }

    if (hc != HcStatus.READY && hc != HcStatus.CHECKING) HealthConnectCard(hc, onConnect)
    sync.error?.let { RytmCard { SmallText(it, color = Rytm.colors.bad) } }

    PlanCard(s, morning, onReadiness, onCheckIn, onOpenPlan, phoneRunActive, onPhoneWorkout, onCoach, onResetPlan)
    NutritionCard(s, sync, onSync, onNutrition)
    if (s.habits.isNotEmpty()) HabitsCard(s.habits, onToggleHabit)
    ActivityTodayCard(s, onQuickAdd)
}

private fun greeting(): String {
    val h = java.time.LocalTime.now(RytmTime.zone).hour
    return when (h) {
        in 5..11 -> "Dzień dobry"
        in 12..17 -> "Cześć"
        else -> "Dobry wieczór"
    }
}

/** Kółko z inicjałem: profil, wpisy i synchronizacja w jednym menu. */
@Composable
private fun ProfileMenu(initial: String, s: TodaySnapshot, sync: SyncUi, onProfile: () -> Unit, onEntries: () -> Unit, onSync: () -> Unit) {
    val c = Rytm.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(1.dp, c.outline, CircleShape)
                .clickable(role = Role.Button, onClickLabel = "Profil, wpisy i synchronizacja") { open = true }
                .semantics { contentDescription = "Profil, wpisy i synchronizacja" },
            contentAlignment = Alignment.Center,
        ) {
            Text(initial.ifBlank { "≡" }, style = Rytm.type.planTitle.copy(fontSize = 16.sp), color = c.ink)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.surface) {
            DropdownMenuItem(text = { Text("Profil i cele", style = Rytm.type.body, color = c.ink) }, onClick = { open = false; onProfile() })
            DropdownMenuItem(text = { Text("Wpisy i eksport", style = Rytm.type.body, color = c.ink) }, onClick = { open = false; onEntries() })
            val syncLabel = when {
                sync.running -> "Synchronizuję…"
                s.lastSync != null -> "Synchronizuj (ostatnio " + s.lastSync!!.atZone(RytmTime.zone).format(hhmm) + ")"
                else -> "Synchronizuj"
            }
            DropdownMenuItem(text = { Text(syncLabel, style = Rytm.type.body, color = c.ink) }, onClick = { open = false; onSync() })
        }
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
private fun readinessColor(level: ReadinessLevel): Color = when (level) {
    ReadinessLevel.HIGH -> Rytm.colors.good
    ReadinessLevel.MODERATE -> Rytm.colors.warn
    ReadinessLevel.LOW -> Rytm.colors.bad
}

/** Jedna linia pod gotowością: sen, tętno, co boli, alkohol. Reszta w arkuszu po dotknięciu. */
private fun readinessSummary(s: TodaySnapshot): String = listOfNotNull(
    s.night?.sleepMin?.let { "Sen " + Format.minutes(it) } ?: "Brak snu",
    s.restingHr?.let { "tętno $it" },
    s.checkIn?.soreAreas?.takeIf { it.isNotEmpty() }?.let { a -> "bolą: " + a.joinToString { Format.body(it).lowercase() } },
    s.alcoholYesterday.takeIf { it > 0 }?.let { "alkohol $it" },
).joinToString(" · ")

/** Najważniejsza karta: gotowość w jednym wierszu i plan dnia z dwoma przyciskami. */
@Composable
private fun PlanCard(
    s: TodaySnapshot,
    morning: MorningNote?,
    onReadiness: () -> Unit,
    onCheckIn: () -> Unit,
    onOpenPlan: () -> Unit,
    phoneRunActive: Boolean,
    onPhoneWorkout: () -> Unit,
    onCoach: () -> Unit,
    onResetPlan: () -> Unit,
) {
    val c = Rytm.colors
    val r = s.readiness
    val p = s.plan
    RytmCard {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Szczegóły gotowości", onClick = onReadiness),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Ring(r.score / 100f, readinessColor(r.level), size = 60.dp, stroke = 7.dp) {
                Text("${r.score}", style = Rytm.type.planTitle, color = c.ink)
            }
            Column(Modifier.weight(1f)) {
                Text("Gotowość " + Format.level(r.level).lowercase() + if (r.calibrating) " · kalibracja" else "", style = Rytm.type.bodyStrong, color = c.ink)
                SmallText(readinessSummary(s))
            }
            Icon(RytmIcons.chevron, contentDescription = null, tint = c.muted, modifier = Modifier.size(20.dp))
        }
        if (s.checkIn == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallText("Brak porannego check-inu.", Modifier.weight(1f))
                Chip("Zrób check-in", ChipTone.ACCENT, onClick = onCheckIn)
            }
        }
        Divider()
        Column(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Pokaż plan", onClick = onOpenPlan),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            CardLabel(if (s.dayOff) "Dziś wolne" else "Plan na dziś")
            Text(p.title, style = Rytm.type.planTitle.copy(fontSize = 22.sp), color = c.ink)
            SmallText(planSubtitle(s))
            (morning?.headline ?: p.rationale.firstOrNull())?.let { Text(it, style = Rytm.type.small, color = c.ink) }
        }
        if (s.planChanged) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallText("Zmienione z trenerem.", Modifier.weight(1f), color = c.accent)
                Chip("Przywróć plan", ChipTone.MUTED, onClick = onResetPlan)
            }
        }
        p.warnings.firstOrNull()?.let { SmallText("⚠ $it", color = c.warn) }
        if (s.setsToday.isNotEmpty()) SmallText("✓ Zapisano ${s.setsToday.size} serii", color = c.good)
        ButtonRow {
            if (p.workout != null) {
                RytmButton(if (phoneRunActive) "Wróć do treningu" else "Zacznij", Modifier.weight(1f), primary = true, onClick = onPhoneWorkout)
            } else {
                RytmButton("Szczegóły", Modifier.weight(1f), primary = true, onClick = onOpenPlan)
            }
            RytmButton("Zmień", Modifier.weight(1f), onClick = onCoach)
        }
    }
}

private fun planSubtitle(s: TodaySnapshot): String {
    val p = s.plan
    return listOfNotNull(
        Format.intensity(p.intensity),
        p.workout?.let { "${it.exercises.size} ćwiczeń" },
        p.cardio?.let { "${it.steps.size} etapy" },
        "pora snu ${s.bedtime.format(hhmm)}",
    ).joinToString(" · ")
}

/** Szczegóły gotowości w arkuszu od dołu: składniki, kalibracja, poprawki check-inu i snu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadinessSheet(s: TodaySnapshot, onDismiss: () -> Unit, onCheckIn: () -> Unit, onAddSleep: () -> Unit) {
    val c = Rytm.colors
    val r = s.readiness
    var showCalibration by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 26.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Ring(r.score / 100f, readinessColor(r.level), size = 76.dp, stroke = 8.dp) {
                    Text("${r.score}", style = Rytm.type.planTitle.copy(fontSize = 22.sp), color = c.ink)
                }
                Column(Modifier.weight(1f)) {
                    Text("Gotowość " + Format.level(r.level).lowercase(), style = Rytm.type.planTitle, color = c.ink)
                    SmallText(
                        when (r.level) {
                            ReadinessLevel.HIGH -> "Pełny trening OK."
                            ReadinessLevel.MODERATE -> "Umiarkowanie: trening, ale bez rekordów."
                            ReadinessLevel.LOW -> "Lżej albo odpoczynek."
                        },
                    )
                }
            }
            CardHeader("Z czego się składa", help = "Gotowość 0–100 mówi, na ile organizm jest dziś gotowy na wysiłek. Składa się ze snu, tętna spoczynkowego i HRV na tle Twoich ostatnich dni, check-inu (energia, nastrój, co boli), alkoholu z wczoraj i wczorajszego obciążenia. Powyżej 70 pełny trening, 40–70 umiarkowanie, poniżej 40 lżej albo odpoczynek. Pierwsze dni to kalibracja, bo Rytm uczy się Twoich norm.") {
                val cal = s.calibration
                if (r.calibrating && cal != null) {
                    Chip("Kalibracja ${cal.restingHrDays.coerceAtMost(cal.needDays)}/${cal.needDays} " + if (showCalibration) "▴" else "▾", ChipTone.WARN) {
                        showCalibration = !showCalibration
                    }
                }
            }
            s.calibration?.takeIf { showCalibration && r.calibrating }?.let { CalibrationDetails(it) }
            val comp = r.components
            ComponentRow(
                "Sen",
                s.night?.sleepMin?.let { Format.minutes(it) + if (s.night?.sleepManual == true) " (ręcznie)" else "" } ?: "brak zapisu",
                comp[ReadinessComponent.SLEEP],
            )
            ComponentRow(
                "Tętno spoczynkowe",
                s.restingHr?.let { hr -> "$hr" + (if (s.restingHrFromDay) " w dzień" else "") + (s.restingHrAvg?.let { " (śr. ${Format.roundInt(it)})" } ?: "") } ?: "brak",
                comp[ReadinessComponent.RESTING_HR],
            )
            ComponentRow(
                "HRV",
                s.checkIn?.hrvRmssd?.let { v -> "${Format.roundInt(v)} ms" + (s.hrvAvg?.let { " (śr. ${Format.roundInt(it)})" } ?: "") } ?: "brak pomiaru",
                comp[ReadinessComponent.HRV],
            )
            ComponentRow(
                "Energia · nastrój",
                s.checkIn?.let { "${it.energy} · ${it.mood} z 5" } ?: "brak check-inu",
                comp[ReadinessComponent.SUBJECTIVE],
            )
            KcalRow(
                "Alkohol wczoraj",
                if (s.alcoholYesterday > 0) "${s.alcoholYesterday} porc. (−${com.zacco.rytm.domain.readiness.ReadinessCalculator.alcoholPenalty(s.alcoholYesterday)} pkt)" else "nie",
            )
            ComponentRow("Obciążenie wczoraj", Format.int(s.load.yesterday), comp[ReadinessComponent.LOAD])
            s.checkIn?.soreAreas?.takeIf { it.isNotEmpty() }?.let { a -> SmallText("Bolą: " + a.joinToString { Format.body(it).lowercase() } + ".") }
            if (s.restingHrFromDay) SmallText("Brak snu z zegarka, więc tętno spoczynkowe liczę z najspokojniejszych 30 min dnia.")
            ButtonRow {
                RytmButton(if (s.checkIn == null) "Zrób check-in" else "Popraw check-in", Modifier.weight(1f), primary = s.checkIn == null) { onDismiss(); onCheckIn() }
                RytmButton(if (s.night?.sleepMin == null) "Wpisz sen" else "Popraw sen", Modifier.weight(1f), primary = s.checkIn != null && s.night?.sleepMin == null) { onDismiss(); onAddSleep() }
            }
        }
    }
}

@Composable
private fun KcalRow(label: String, value: String, color: Color? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = Rytm.type.small, color = Rytm.colors.muted, modifier = Modifier.weight(1f))
        Text(value, style = Rytm.type.small, color = color ?: Rytm.colors.ink, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun ComponentRow(name: String, value: String, score: Int?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Text(name, style = Rytm.type.body, color = Rytm.colors.ink, modifier = Modifier.weight(1f))
            Text(value, style = Rytm.type.bodyStrong, color = Rytm.colors.ink, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        Bar((score ?: 0) / 100f, Rytm.colors.accent)
    }
}

/** Jedzenie: jedna duża liczba (ile zostało), pasek kcal i makro w jednej linii. */
@Composable
private fun NutritionCard(s: TodaySnapshot, sync: SyncUi, onSync: () -> Unit, onNutrition: () -> Unit) {
    val c = Rytm.colors
    val n = s.nutrition
    val t = s.nutritionTargets
    RytmCard(onClick = onNutrition) {
        CardHeader(
            "Jedzenie",
            help = "Dane z Fitatu przez Health Connect. Dotknij karty, żeby poprawić albo wpisać ręcznie." +
                if (s.trainingKcal > 0) " Cel jest dziś większy o ${Format.int(s.trainingKcal)} kcal z treningu (${Format.dec(s.trainingKcalFactor, 2).trimEnd('0').trimEnd(',')} × kcal z zegarka), makro proporcjonalnie." else "",
        ) {
            val label = when {
                sync.running -> "synchronizuję…"
                s.lastSync != null -> "Fitatu · " + s.lastSync!!.atZone(RytmTime.zone).format(hhmm)
                else -> "synchronizuj"
            }
            Chip(label, ChipTone.MUTED, onClick = onSync)
        }
        if (n == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallText("Brak danych z Fitatu na dziś. Cel: ${Format.int(t.kcal)} kcal.", Modifier.weight(1f))
                Chip("Wpisz", ChipTone.ACCENT, onClick = onNutrition)
            }
            return@RytmCard
        }
        val left = t.kcal - n.kcal
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(Format.int(kotlin.math.abs(left)), style = Rytm.type.bigNumber.copy(fontSize = 28.sp), color = if (left < 0) c.warn else c.ink)
            Text(
                if (left >= 0) "kcal zostało z ${Format.int(t.kcal)}" else "kcal ponad cel ${Format.int(t.kcal)}",
                style = Rytm.type.body, color = c.muted, modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Bar(if (t.kcal > 0) n.kcal.toFloat() / t.kcal else 0f, if (left < 0) c.warn else c.accent, height = 8.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MacroText("Białko", Format.roundInt(n.proteinG), t.proteinG, Modifier.weight(1f))
            MacroText("Tłuszcz", Format.roundInt(n.fatG), t.fatG, Modifier.weight(1f))
            MacroText("Węgle", Format.roundInt(n.carbsG), t.carbsG, Modifier.weight(1f))
        }
    }
}

/** „Białko 162/183”; powyżej celu na pomarańczowo ze strzałką. Białko poniżej celu to nie błąd. */
@Composable
private fun MacroText(name: String, value: Int, goal: Int, modifier: Modifier) {
    val c = Rytm.colors
    val over = goal > 0 && value > goal * 1.05 && name != "Białko"
    Column(modifier) {
        Text(name, style = Rytm.type.tiny, color = c.muted)
        Text(
            "${Format.int(value)}/${Format.int(goal)} g" + if (over) " ↑" else "",
            style = Rytm.type.small.copy(fontWeight = FontWeight.Medium),
            color = if (over) c.warn else c.ink,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Nawyki na dziś: pole do odhaczenia, nazwa i wartość (np. kroki) po prawej. */
@Composable
fun HabitsCard(habits: List<HabitStatus>, onToggle: (HabitStatus) -> Unit) {
    RytmCard(spacing = 4.dp) {
        CardHeader("Nawyki") {
            SmallText("${habits.count { it.done }} z ${habits.size}")
        }
        habits.forEach { h -> HabitRow(h, onToggle) }
    }
}

@Composable
private fun HabitRow(h: HabitStatus, onToggle: (HabitStatus) -> Unit) {
    val c = Rytm.colors
    val auto = h.habit.autoOnly
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .then(if (auto) Modifier else Modifier.clickable(role = Role.Checkbox, onClickLabel = "Odhacz") { onToggle(h) })
            .semantics { contentDescription = h.habit.name + if (h.done) ", zrobione" else ", do zrobienia" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (h.done) c.good else c.surface)
                // Obrys o kontraście min. 3:1; wcześniej puste kółko ginęło na ciemnym tle.
                .border(2.dp, if (h.done) c.good else c.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (h.done) Text("✓", style = Rytm.type.small.copy(fontWeight = FontWeight.Bold), color = c.onAccent)
        }
        Text(h.habit.name, style = Rytm.type.body, color = c.ink, modifier = Modifier.weight(1f))
        habitValue(h)?.let { Text(it, style = Rytm.type.small, color = c.muted, maxLines = 1) }
    }
}

private fun habitValue(h: HabitStatus): String? = when {
    h.habit.autoSource == HabitAutoSource.STEPS -> "${Format.int(h.value ?: 0)} / ${Format.int(h.habit.target ?: 0)}"
    h.habit.autoSource == HabitAutoSource.MOBILITY && (h.value ?: 0) > 0 -> "${h.value} min"
    h.habit.kind == HabitKind.MINUTES && !h.done -> "${h.habit.target} min"
    else -> null
}

/** Ćwiczenia sesji: numer, nazwa, serie × powtórzenia, ciężar po prawej. Dotknięcie pokazuje opis ćwiczenia. */
@Composable
fun WorkoutList(w: com.zacco.rytm.domain.model.StrengthWorkout) {
    val c = Rytm.colors
    var info by remember { mutableStateOf<ExerciseInfo?>(null) }
    info?.let { ExerciseSheet(it) { info = null } }
    Column {
        w.exercises.forEachIndexed { i, e ->
            if (i > 0) Divider()
            val guide = ExerciseGuide.find(e.name)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).then(if (guide != null) Modifier.clickable(onClickLabel = "Opis ćwiczenia") { info = guide } else Modifier)
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(c.track), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = Rytm.type.small.copy(fontWeight = FontWeight.SemiBold), color = c.muted)
                }
                Column(Modifier.weight(1f)) {
                    Text(e.name, style = Rytm.type.body.copy(fontWeight = FontWeight.Medium), color = c.ink)
                    val reps = if (e.reps.first == e.reps.last) "${e.reps.first}" else "${e.reps.first}–${e.reps.last}"
                    SmallText(
                        listOfNotNull(
                            "${e.sets} × $reps",
                            e.tempo?.let { "tempo $it" },
                            e.restSec.takeIf { it > 0 }?.let { "przerwa ${it} s" },
                        ).joinToString(" · "),
                    )
                    e.note?.let { SmallText(it, color = c.accent) }
                }
                Text(e.weightKg?.let { "${Format.kg(it)} kg" } ?: "", style = Rytm.type.bodyStrong, color = c.ink, maxLines = 1, softWrap = false)
            }
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
    var expanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    // Zwinięta: jedna linia z nazwami i kcal. Szczegóły po dotknięciu.
    RytmCard(onClick = if (acts.isEmpty()) onAdd else ({ expanded = !expanded })) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                CardLabel("Aktywność")
                Text(
                    if (acts.isEmpty()) "Jeszcze nic dziś" else acts.joinToString(", ") { a -> a.notes?.takeIf { it.isNotBlank() } ?: Format.activity(a.type) },
                    style = Rytm.type.body, color = c.ink, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            if (s.activityKcal > 0) Text("${Format.int(s.activityKcal)} kcal", style = Rytm.type.bodyStrong, color = c.ink, maxLines = 1, softWrap = false)
            if (acts.isEmpty()) Chip("+ dodaj", ChipTone.ACCENT, onClick = onAdd)
            else Icon(RytmIcons.chevron, contentDescription = null, tint = c.muted, modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = if (expanded) 270f else 90f })
        }
        if (!expanded) return@RytmCard
        CardHeader(
            "Szczegóły",
            help = "Do celu jedzenia doliczam ${(s.trainingKcalFactor * 100).toInt()}% kcal z treningów (zegarki zwykle zawyżają spalanie), bez spacerów, bo te są już w ruchu z profilu. " +
                "Kcal z „~” to szacunek z czasu i RPE, gdy zegarek ich nie podał. „Cały dzień” to całe spalanie dnia, razem z przemianą spoczynkową.",
        ) { Chip("+ dodaj", ChipTone.ACCENT, onClick = onAdd) }
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
