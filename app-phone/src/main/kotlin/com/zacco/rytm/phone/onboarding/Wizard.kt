package com.zacco.rytm.phone.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zacco.rytm.ai.plan.PlanTweak
import com.zacco.rytm.ai.plan.PlanTweakResult
import com.zacco.rytm.domain.coach.CoachData
import com.zacco.rytm.domain.coach.CoachEngine
import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.planner.EquipmentKind
import com.zacco.rytm.domain.profile.ActivityChoice
import com.zacco.rytm.domain.profile.ActivityLevel
import com.zacco.rytm.domain.profile.FixedSportEntry
import com.zacco.rytm.domain.profile.Goal
import com.zacco.rytm.domain.profile.NutritionCalculator
import com.zacco.rytm.domain.profile.PersonalProfile
import com.zacco.rytm.domain.profile.Sex
import com.zacco.rytm.domain.time.RytmTime
import com.zacco.rytm.phone.app.Format
import com.zacco.rytm.phone.profile.Field
import com.zacco.rytm.phone.profile.Pills
import com.zacco.rytm.phone.profile.activityLabel
import com.zacco.rytm.phone.ui.components.CardHeader
import com.zacco.rytm.phone.ui.components.Pill
import com.zacco.rytm.phone.ui.components.RytmButton
import com.zacco.rytm.phone.ui.components.RytmCard
import com.zacco.rytm.phone.ui.components.ScreenHeader
import com.zacco.rytm.phone.ui.components.SmallText
import com.zacco.rytm.phone.ui.components.WhyBox
import com.zacco.rytm.phone.ui.theme.Rytm
import java.time.LocalDate

private enum class Step { ACTIVITIES, GOAL, WEEK, EQUIPMENT, SUMMARY }

private val dayNames = listOf("pn", "wt", "śr", "cz", "pt", "sb", "nd")

/** Stan dnia w kroku „Kiedy masz czas?”. */
private enum class DayUse { FREE, RYTM, SPORT }

/**
 * Kreator na start: 5 krótkich kroków (co chcesz robić, cel, tydzień, sprzęt, podsumowanie) zamiast jednej ankiety.
 * Krok ze sprzętem pojawia się tylko przy sile. W podsumowaniu można poprawić plan słowami z pomocą AI.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingWizard(
    initial: PersonalProfile?,
    onTweak: (PersonalProfile, String, (PlanTweakResult) -> Unit) -> Unit,
    onFullForm: () -> Unit,
    onFinish: (PersonalProfile) -> Unit,
) {
    var p by remember {
        mutableStateOf(
            initial ?: PersonalProfile(
                activities = emptyList(),
                strengthDays = emptyList(),
                strengthPerWeek = 2,
                restDay = 7,
                weightKg = 75.0,
                heightCm = 175,
            ),
        )
    }
    var stepIndex by remember { mutableIntStateOf(0) }
    val steps = Step.entries.filter { it != Step.EQUIPMENT || p.wantsStrength }
    val step = steps[stepIndex.coerceIn(0, steps.lastIndex)]

    ScreenHeader(sub = "Krok ${stepIndex + 1} z ${steps.size}", title = "Witaj w Rytmie")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        steps.indices.forEach { i ->
            Box(
                Modifier.size(if (i == stepIndex) 22.dp else 8.dp, 8.dp).clip(CircleShape)
                    .background(if (i <= stepIndex) Rytm.colors.accent else Rytm.colors.track),
            )
        }
    }

    when (step) {
        Step.ACTIVITIES -> ActivitiesStep(p) { p = it }
        Step.GOAL -> GoalStep(p) { p = it }
        Step.WEEK -> WeekStep(p) { p = it }
        Step.EQUIPMENT -> EquipmentStep(p) { p = it }
        Step.SUMMARY -> SummaryStep(p, onTweak) { p = it }
    }

    val canNext = when (step) {
        Step.ACTIVITIES -> !p.activities.isNullOrEmpty()
        Step.WEEK -> p.strengthPerWeek == 0 || p.strengthDays.isNotEmpty()
        Step.SUMMARY -> p.heightCm in 120..230 && p.weightKg in 30.0..250.0
        else -> true
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (stepIndex > 0) RytmButton("Wstecz", Modifier.weight(1f)) { stepIndex-- }
        RytmButton(
            if (step == Step.SUMMARY) "Zacznij z Rytmem" else "Dalej",
            Modifier.weight(1f),
            primary = true,
            enabled = canNext,
        ) {
            if (step == Step.SUMMARY) onFinish(finalize(p)) else stepIndex++
        }
    }
    if (step == Step.ACTIVITIES) {
        RytmButton("Wolę jeden formularz", Modifier.fillMaxWidth(), onClick = onFullForm)
    }
}

/** Domyślne rzeczy, których kreator nie pyta wprost. */
private fun finalize(p: PersonalProfile): PersonalProfile {
    val acts = p.activities.orEmpty()
    val equipment = when {
        ActivityChoice.GYM in acts && ActivityChoice.STRENGTH_HOME !in acts -> EquipmentKind.GYM
        else -> p.equipment
    }
    return p.copy(equipment = equipment)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivitiesStep(p: PersonalProfile, onChange: (PersonalProfile) -> Unit) {
    val acts = p.activities.orEmpty()
    RytmCard {
        Text("Co chcesz robić?", style = Rytm.type.planTitle, color = Rytm.colors.ink)
        SmallText("Zaznacz wszystko, co pasuje. Plan ułoży się wokół tego.")
        ActivityChoice.entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { a ->
                    Tile(activityLabel(a), activityHint(a), a in acts, Modifier.weight(1f)) {
                        onChange(p.copy(activities = if (a in acts) acts - a else acts + a))
                    }
                }
            }
        }
        if (ActivityChoice.RUN in acts || ActivityChoice.WALK in acts) {
            Pill("Biegam albo chodzę na bieżni", p.treadmill) { onChange(p.copy(treadmill = !p.treadmill)) }
        }
    }
}

private fun activityHint(a: ActivityChoice) = when (a) {
    ActivityChoice.STRENGTH_HOME -> "hantle, gumy albo masa ciała"
    ActivityChoice.GYM -> "maszyny i wolne ciężary"
    ActivityChoice.WALK -> "pies, kroki, ruch w ciągu dnia"
    ActivityChoice.RUN -> "od pierwszego 5 km do półmaratonu"
    ActivityChoice.BIKE -> "dojazdy i wycieczki"
    ActivityChoice.SWIM -> "basen albo otwarta woda"
    ActivityChoice.SPORT -> "siatkówka, squash, piłka…"
    ActivityChoice.YOGA -> "rozciąganie, pilates"
}

@Composable
private fun GoalStep(p: PersonalProfile, onChange: (PersonalProfile) -> Unit) {
    RytmCard {
        Text("Jaki masz cel?", style = Rytm.type.planTitle, color = Rytm.colors.ink)
        listOf(
            Triple(Goal.RECOMP, "Rekompozycja", "mniej tłuszczu, więcej mięśni, waga stoi"),
            Triple(Goal.FAT_LOSS, "Redukcja", "spokojnie, ok. 0,5 kg na tydzień"),
            Triple(Goal.MUSCLE_GAIN, "Masa mięśniowa", "lekka nadwyżka kcal, progresja siły"),
            Triple(Goal.STRENGTH, "Siła", "cięższe serie, mniej powtórzeń"),
            Triple(Goal.ENDURANCE, "Kondycja i bieganie", "dłużej, szybciej, lżej na schodach"),
            Triple(Goal.HEALTH, "Zdrowie i energia", "ruch, sen i nawyki bez liczenia"),
        ).forEach { (g, title, sub) -> Tile(title, sub, p.goal == g, Modifier.fillMaxWidth()) { onChange(p.copy(goal = g)) } }
        OutlinedTextField(
            value = p.goalNote,
            onValueChange = { onChange(p.copy(goalNote = it.take(400))) },
            label = { Text("Własnymi słowami (opcjonalnie)") },
            placeholder = { Text("np. przebiec 10 km w maju") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekStep(p: PersonalProfile, onChange: (PersonalProfile) -> Unit) {
    fun use(d: Int) = when {
        p.fixedSports.any { it.day == d } -> DayUse.SPORT
        d in p.strengthDays -> DayUse.RYTM
        else -> DayUse.FREE
    }
    RytmCard {
        Text("Kiedy masz czas?", style = Rytm.type.planTitle, color = Rytm.colors.ink)
        SmallText("Dotknij dnia: trening z Rytmem → stały sport (np. siatkówka) → wolne.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..7).forEach { d ->
                val u = use(d)
                val c = Rytm.colors
                val (bg, fg) = when (u) {
                    DayUse.RYTM -> c.accent to c.onAccent
                    DayUse.SPORT -> c.kindSport to c.onAccent
                    DayUse.FREE -> c.surface to c.ink
                }
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(bg).border(1.dp, c.line, RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button) {
                            val next = when (u) {
                                DayUse.FREE -> p.copy(strengthDays = p.strengthDays + d)
                                DayUse.RYTM -> p.copy(
                                    strengthDays = p.strengthDays - d,
                                    fixedSports = p.fixedSports + FixedSportEntry(d, "Sport"),
                                )
                                DayUse.SPORT -> p.copy(fixedSports = p.fixedSports.filterNot { it.day == d })
                            }
                            onChange(next.copy(restDay = next.restDay.takeIf { r -> r != null && r !in next.strengthDays && next.fixedSports.none { it.day == r } }))
                        }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(dayNames[d - 1], style = Rytm.type.bodyStrong, color = fg)
                    Text(
                        when (u) {
                            DayUse.RYTM -> "Rytm"
                            DayUse.SPORT -> "sport"
                            DayUse.FREE -> "–"
                        },
                        style = Rytm.type.tiny,
                        color = fg,
                    )
                }
            }
        }
        p.fixedSports.sortedBy { it.day }.forEach { s ->
            SmallText("Co w ${dayNames[s.day - 1]}?")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Siatkówka" to ActivityType.VOLLEYBALL, "Squash" to ActivityType.SQUASH, "Piłka nożna" to ActivityType.OTHER,
                    "Basen" to ActivityType.CARDIO, "Zajęcia fitness" to ActivityType.OTHER, "Sport" to ActivityType.OTHER).forEach { (name, type) ->
                    Pill(name, s.name == name) {
                        onChange(p.copy(fixedSports = p.fixedSports.map { if (it.day == s.day) it.copy(name = name, type = type) else it }))
                    }
                }
            }
        }
        val max = p.strengthDays.size
        SmallText("Ile treningów z Rytmem w tygodniu:")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RytmButton("−", Modifier.width(52.dp), enabled = p.strengthPerWeek > 0) { onChange(p.copy(strengthPerWeek = p.strengthPerWeek - 1)) }
            Text("${p.strengthPerWeek}", style = Rytm.type.bigNumber, color = Rytm.colors.ink)
            RytmButton("+", Modifier.width(52.dp), enabled = p.strengthPerWeek < maxOf(max, 1) && p.strengthPerWeek < 7) {
                onChange(p.copy(strengthPerWeek = p.strengthPerWeek + 1))
            }
        }
        SmallText(
            if (max == 0) "Zaznacz co najmniej jeden dzień z Rytmem albo ustaw 0 treningów."
            else "Rytm wybierze ${minOf(p.strengthPerWeek, max)} z zaznaczonych dni i przesunie trening, gdy któregoś dnia odpuścisz.",
        )
        if (!p.wantsStrength) {
            SmallText("Ile minut na trening:")
            Pills(listOf(20, 30, 45, 60, 90).map { "$it" to it }, p.sessionMinutes) { onChange(p.copy(sessionMinutes = it)) }
        }
    }
}

@Composable
private fun EquipmentStep(p: PersonalProfile, onChange: (PersonalProfile) -> Unit) {
    var pair by remember { mutableStateOf(Format.dec(p.dumbbellPairMaxKg, 0)) }
    var single by remember { mutableStateOf(Format.dec(p.dumbbellSingleMaxKg, 0)) }
    RytmCard {
        Text("Czym ćwiczysz?", style = Rytm.type.planTitle, color = Rytm.colors.ink)
        Pills(
            listOf("Bez sprzętu" to EquipmentKind.NONE, "Gumy" to EquipmentKind.BANDS, "Hantle" to EquipmentKind.DUMBBELLS, "Siłownia" to EquipmentKind.GYM),
            p.equipment,
        ) { onChange(p.copy(equipment = it)) }
        if (p.equipment == EquipmentKind.DUMBBELLS) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Para, maks. na rękę (kg)", pair, KeyboardType.Decimal, Modifier.weight(1f)) {
                    pair = it.take(4)
                    it.replace(',', '.').toDoubleOrNull()?.let { v -> onChange(p.copy(dumbbellPairMaxKg = v)) }
                }
                Field("Jeden hantel, maks. (kg)", single, KeyboardType.Decimal, Modifier.weight(1f)) {
                    single = it.take(4)
                    it.replace(',', '.').toDoubleOrNull()?.let { v -> onChange(p.copy(dumbbellSingleMaxKg = v)) }
                }
            }
            Pill("Mam też gumy", p.bands) { onChange(p.copy(bands = !p.bands)) }
            SmallText("Skok ciężaru:")
            Pills(listOf(0.5, 1.0, 2.0).map { Format.kg(it) + " kg" to it }, p.weightStepKg ?: 1.0) { onChange(p.copy(weightStepKg = it)) }
        }
        SmallText("Ile minut na trening:")
        Pills(listOf(20, 30, 45, 60, 90).map { "$it" to it }, p.sessionMinutes) { onChange(p.copy(sessionMinutes = it)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryStep(
    p: PersonalProfile,
    onTweak: (PersonalProfile, String, (PlanTweakResult) -> Unit) -> Unit,
    onChange: (PersonalProfile) -> Unit,
) {
    val today = LocalDate.now(RytmTime.zone)
    var birthYear by remember { mutableStateOf(p.birthYear?.toString().orEmpty()) }
    var height by remember { mutableStateOf(p.heightCm.toString()) }
    var weight by remember { mutableStateOf(Format.dec(p.weightKg)) }
    var request by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var proposal by remember { mutableStateOf<PlanTweak?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    RytmCard {
        Text("Gotowe. Tak to widzę", style = Rytm.type.planTitle, color = Rytm.colors.ink)
        Field("Imię (opcjonalnie)", p.name, KeyboardType.Text) { onChange(p.copy(name = it.take(30))) }
        Pills(listOf("Mężczyzna" to Sex.MALE, "Kobieta" to Sex.FEMALE), p.sex) { onChange(p.copy(sex = it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Rok ur.", birthYear, KeyboardType.Number, Modifier.weight(1f)) {
                birthYear = it.filter(Char::isDigit).take(4)
                onChange(p.copy(birthYear = birthYear.toIntOrNull()?.takeIf { y -> y in 1920..today.year - 10 }))
            }
            Field("Wzrost cm", height, KeyboardType.Number, Modifier.weight(1f)) {
                height = it.filter(Char::isDigit).take(3)
                height.toIntOrNull()?.let { h -> onChange(p.copy(heightCm = h)) }
            }
            Field("Waga kg", weight, KeyboardType.Decimal, Modifier.weight(1f)) {
                weight = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(5)
                weight.replace(',', '.').toDoubleOrNull()?.let { w -> onChange(p.copy(weightKg = w)) }
            }
        }
        Pills(
            listOf("Siedząca praca" to ActivityLevel.SEDENTARY, "Trochę ruchu" to ActivityLevel.LIGHT, "Dużo chodzenia" to ActivityLevel.MODERATE, "Praca fizyczna" to ActivityLevel.ACTIVE),
            p.activity,
        ) { onChange(p.copy(activity = it)) }
        val t = NutritionCalculator.targets(p, today)
        WhyBox("Na dzień bez treningu: ${Format.int(t.kcal)} kcal · białko ${t.proteinG} g · tłuszcz ${t.fatG} g · węgle ${t.carbsG} g. Własne cele (np. z Fitatu) wpiszesz w profilu.")
    }

    RytmCard {
        CardHeader("Twój tydzień")
        val plan = CoachEngine().week(CoachData(today = today, profile = p.toUserProfile(today), habits = p.habits())).days
        plan.forEach { d ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(dayNames[d.date.dayOfWeek.value - 1], style = Rytm.type.small, color = Rytm.colors.muted, modifier = Modifier.width(26.dp))
                Text(d.plan.title, style = Rytm.type.small, color = Rytm.colors.ink)
            }
        }
        SmallText("Coś nie pasuje? Wróć do kroku albo napisz, co zmienić.")
        OutlinedTextField(
            value = request,
            onValueChange = { request = it.take(500) },
            label = { Text("Popraw z AI, np. we wtorki nie mam czasu") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        RytmButton(if (running) "AI myśli…" else "Popraw z AI", Modifier.fillMaxWidth(), enabled = request.isNotBlank() && !running) {
            running = true
            error = null
            onTweak(p, request) { r ->
                running = false
                proposal = r.tweak
                error = r.error
            }
        }
        error?.let { SmallText(it, color = Rytm.colors.warn) }
        proposal?.let { tw ->
            WhyBox(tw.explanation)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RytmButton("Zastosuj", Modifier.weight(1f), primary = true) {
                    onChange(tw.applyTo(p))
                    proposal = null
                    request = ""
                }
                RytmButton("Odrzuć", Modifier.weight(1f)) { proposal = null }
            }
        }
    }
}

@Composable
private fun Tile(title: String, sub: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Rytm.colors
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (on) c.accentSoft else c.surface)
            .border(if (on) 2.dp else 1.dp, if (on) c.accent else c.line, RoundedCornerShape(14.dp))
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = Rytm.type.bodyStrong, color = c.ink)
        SmallText(sub)
    }
}
