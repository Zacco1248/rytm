package com.zacco.rytm.phone.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.planner.EquipmentKind
import com.zacco.rytm.domain.profile.ActivityLevel
import com.zacco.rytm.domain.profile.CustomTargets
import com.zacco.rytm.domain.profile.FixedSportEntry
import com.zacco.rytm.domain.profile.Goal
import com.zacco.rytm.domain.profile.NutritionCalculator
import com.zacco.rytm.domain.profile.PersonalProfile
import com.zacco.rytm.domain.profile.PersonalTrainingEntry
import com.zacco.rytm.domain.profile.Sex
import com.zacco.rytm.domain.time.RytmTime
import com.zacco.rytm.phone.app.Format
import com.zacco.rytm.phone.ui.components.CardHeader
import com.zacco.rytm.phone.ui.components.RytmButton
import com.zacco.rytm.phone.ui.components.RytmCard
import com.zacco.rytm.phone.ui.components.ScreenHeader
import com.zacco.rytm.phone.ui.components.SmallText
import com.zacco.rytm.phone.ui.theme.Rytm
import java.time.LocalDate

private val dayNames = listOf("Pn", "Wt", "Śr", "Cz", "Pt", "Sb", "Nd")

/** Gotowe sporty do wyboru; nazwę można zmienić. */
private val sportPresets = listOf(
    "Siatkówka" to ActivityType.VOLLEYBALL,
    "Squash" to ActivityType.SQUASH,
    "Piłka nożna" to ActivityType.OTHER,
    "Koszykówka" to ActivityType.OTHER,
    "Tenis" to ActivityType.OTHER,
    "Bieganie" to ActivityType.CARDIO,
    "Rower" to ActivityType.CARDIO,
    "Pływanie" to ActivityType.CARDIO,
    "Joga" to ActivityType.MOBILITY,
    "Inny" to ActivityType.OTHER,
)

/**
 * Profil: przy pierwszym uruchomieniu jako kreator ([onboarding]), później do edycji z zakładki „Dziś”.
 * Wszystko zostaje na tym telefonie.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    initial: PersonalProfile?,
    onboarding: Boolean,
    onSave: (PersonalProfile) -> Unit,
    onDiagnostics: (() -> Unit)? = null,
) {
    var p by remember { mutableStateOf(initial ?: PersonalProfile()) }
    var birthYear by remember { mutableStateOf(p.birthYear?.toString().orEmpty()) }
    var height by remember { mutableStateOf(p.heightCm.toString()) }
    var weight by remember { mutableStateOf(Format.dec(p.weightKg)) }
    var steps by remember { mutableStateOf(p.stepsTarget.toString()) }
    var maxHr by remember { mutableStateOf(p.maxHr?.toString().orEmpty()) }
    var pair by remember { mutableStateOf(Format.dec(p.dumbbellPairMaxKg, 0)) }
    var single by remember { mutableStateOf(Format.dec(p.dumbbellSingleMaxKg, 0)) }
    var custom by remember { mutableStateOf(p.customTargets != null) }
    val ct = p.customTargets ?: CustomTargets(0, 0, 0, 0)
    var kcal by remember { mutableStateOf(ct.kcal.takeIf { it > 0 }?.toString().orEmpty()) }
    var protein by remember { mutableStateOf(ct.proteinG.takeIf { it > 0 }?.toString().orEmpty()) }
    var fat by remember { mutableStateOf(ct.fatG.takeIf { it > 0 }?.toString().orEmpty()) }
    var carbs by remember { mutableStateOf(ct.carbsG.takeIf { it > 0 }?.toString().orEmpty()) }
    var ptUntil by remember { mutableStateOf(p.personalTraining?.until.orEmpty()) }

    val today = LocalDate.now(RytmTime.zone)
    fun build(): PersonalProfile? {
        val h = height.toIntOrNull()?.takeIf { it in 120..230 } ?: return null
        val w = weight.replace(',', '.').toDoubleOrNull()?.takeIf { it in 30.0..250.0 } ?: return null
        val by = birthYear.toIntOrNull()?.takeIf { it in 1920..today.year - 10 }
        val targets = if (custom) {
            val k = kcal.toIntOrNull() ?: return null
            val pr = protein.toIntOrNull() ?: return null
            CustomTargets(k, pr, fat.toIntOrNull() ?: 0, carbs.toIntOrNull() ?: 0)
        } else {
            null
        }
        return p.copy(
            birthYear = by,
            heightCm = h,
            weightKg = w,
            stepsTarget = steps.toIntOrNull()?.coerceIn(1000, 30000) ?: p.stepsTarget,
            maxHr = maxHr.toIntOrNull()?.takeIf { it in 120..230 },
            dumbbellPairMaxKg = pair.replace(',', '.').toDoubleOrNull() ?: p.dumbbellPairMaxKg,
            dumbbellSingleMaxKg = single.replace(',', '.').toDoubleOrNull() ?: p.dumbbellSingleMaxKg,
            customTargets = targets,
            personalTraining = p.personalTraining?.copy(until = ptUntil.takeIf { runCatching { LocalDate.parse(it) }.isSuccess }),
        )
    }
    val built = build()

    ScreenHeader(
        sub = if (onboarding) "Kilka pytań na start" else "Twoje ustawienia",
        title = if (onboarding) "Witaj w Rytmie" else "Profil",
    )
    if (onboarding) {
        RytmCard {
            SmallText(
                "Rytm to Twój trener i asystent zdrowia: plan treningów, gotowość z zegarka, sen i żywienie. " +
                    "Na podstawie tych odpowiedzi policzy cele i ułoży tydzień. Dane zostają na tym telefonie. Wszystko zmienisz później w „Profil”.",
            )
        }
    }

    RytmCard {
        CardHeader("O Tobie")
        Field("Imię (opcjonalnie)", p.name, KeyboardType.Text) { p = p.copy(name = it.take(30)) }
        Pills(listOf("Mężczyzna" to Sex.MALE, "Kobieta" to Sex.FEMALE), p.sex) { p = p.copy(sex = it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Rok urodzenia", birthYear, KeyboardType.Number, Modifier.weight(1f)) { birthYear = it.filter(Char::isDigit).take(4) }
            Field("Wzrost (cm)", height, KeyboardType.Number, Modifier.weight(1f)) { height = it.filter(Char::isDigit).take(3) }
            Field("Waga (kg)", weight, KeyboardType.Decimal, Modifier.weight(1f)) { weight = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(5) }
        }
        SmallText("Ruch w ciągu dnia (bez treningów, te Rytm dolicza z zegarka):")
        Pills(
            listOf(
                "Siedząca praca" to ActivityLevel.SEDENTARY,
                "Trochę ruchu" to ActivityLevel.LIGHT,
                "Dużo chodzenia" to ActivityLevel.MODERATE,
                "Praca fizyczna" to ActivityLevel.ACTIVE,
            ),
            p.activity,
        ) { p = p.copy(activity = it) }
    }

    RytmCard {
        CardHeader("Cel")
        Pills(
            listOf(
                "Rekompozycja" to Goal.RECOMP,
                "Redukcja wagi" to Goal.FAT_LOSS,
                "Masa mięśniowa" to Goal.MUSCLE_GAIN,
                "Siła" to Goal.STRENGTH,
                "Kondycja" to Goal.ENDURANCE,
                "Zdrowie i forma" to Goal.HEALTH,
            ),
            p.goal,
        ) { p = p.copy(goal = it) }
        SmallText(goalHint(p.goal))
        OutlinedTextField(
            value = p.goalNote,
            onValueChange = { p = p.copy(goalNote = it.take(400)) },
            label = { Text("Twój cel własnymi słowami (opcjonalnie)") },
            placeholder = { Text("np. schudnąć 5 kg do lata i przebiec 10 km") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    RytmCard {
        CardHeader("Trening")
        SmallText("Sprzęt:")
        Pills(
            listOf("Bez sprzętu" to EquipmentKind.NONE, "Gumy" to EquipmentKind.BANDS, "Hantle" to EquipmentKind.DUMBBELLS, "Siłownia" to EquipmentKind.GYM),
            p.equipment,
        ) { p = p.copy(equipment = it) }
        if (p.equipment == EquipmentKind.DUMBBELLS) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Maks. na hantel, para (kg)", pair, KeyboardType.Decimal, Modifier.weight(1f)) { pair = it.take(4) }
                Field("Maks. jeden hantel (kg)", single, KeyboardType.Decimal, Modifier.weight(1f)) { single = it.take(4) }
            }
            Pill("Mam też gumy", p.bands) { p = p.copy(bands = !p.bands) }
        }
        SmallText("Czas na jedną sesję:")
        Pills(listOf(20, 30, 45, 60, 90).map { "$it min" to it }, p.sessionMinutes) { p = p.copy(sessionMinutes = it) }
        SmallText("Dni, w które możesz trenować siłowo (kolejność kliknięć = kolejność preferencji):")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..7).forEach { d ->
                val idx = p.strengthDays.indexOf(d)
                Pill(dayNames[d - 1] + if (idx >= 0) " ${idx + 1}" else "", idx >= 0) {
                    p = p.copy(strengthDays = if (idx >= 0) p.strengthDays - d else p.strengthDays + d)
                }
            }
        }
        SmallText("Ile sesji siłowych w tygodniu:")
        Pills((1..5).map { "$it" to it }, p.strengthPerWeek) { p = p.copy(strengthPerWeek = it) }
        SmallText("Dzień odpoczynku:")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..7).forEach { d -> Pill(dayNames[d - 1], p.restDay == d) { p = p.copy(restDay = if (p.restDay == d) null else d) } }
        }
    }

    RytmCard {
        CardHeader("Stałe sporty i zajęcia")
        SmallText("Np. siatkówka we wtorki. Plan nie da ciężkich nóg dzień wcześniej, a dzień po meczu będzie lżejszy.")
        p.fixedSports.forEachIndexed { i, s ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Nazwa", s.name, KeyboardType.Text, Modifier.weight(1f)) { v ->
                        p = p.copy(fixedSports = p.fixedSports.toMutableList().also { it[i] = s.copy(name = v.take(30)) })
                    }
                    RytmButton("Usuń") { p = p.copy(fixedSports = p.fixedSports.filterIndexed { j, _ -> j != i }) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { d ->
                        Pill(dayNames[d - 1], s.day == d) {
                            p = p.copy(fixedSports = p.fixedSports.toMutableList().also { it[i] = s.copy(day = d) })
                        }
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sportPresets.forEach { (name, type) ->
                Pill("+ $name", false) {
                    val freeDay = (1..7).firstOrNull { d -> p.fixedSports.none { it.day == d } && d !in p.strengthDays } ?: 2
                    p = p.copy(fixedSports = p.fixedSports + FixedSportEntry(freeDay, if (name == "Inny") "" else name, type))
                }
            }
        }
        Pill("Mam trening personalny / zajęcia z trenerem", p.personalTraining != null) {
            p = p.copy(personalTraining = if (p.personalTraining == null) PersonalTrainingEntry(5) else null)
        }
        p.personalTraining?.let { pt ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..7).forEach { d -> Pill(dayNames[d - 1], pt.day == d) { p = p.copy(personalTraining = pt.copy(day = d)) } }
            }
            Field("Do kiedy (RRRR-MM-DD, puste = bez końca)", ptUntil, KeyboardType.Text) { ptUntil = it.take(10) }
        }
    }

    RytmCard {
        CardHeader("Codziennie")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Cel kroków", steps, KeyboardType.Number, Modifier.weight(1f)) { steps = it.filter(Char::isDigit).take(5) }
            Field("Tętno max (jeśli znasz)", maxHr, KeyboardType.Number, Modifier.weight(1f)) { maxHr = it.filter(Char::isDigit).take(3) }
        }
        SmallText("Pora zaśnięcia:")
        Pills(
            listOf("22–23" to 22 * 60, "23–24" to 23 * 60, "24–01" to 24 * 60, "01–02" to 25 * 60),
            p.sleepOnsetFromMin,
        ) { p = p.copy(sleepOnsetFromMin = it, sleepOnsetToMin = it + 60) }
        SmallText("Ile snu:")
        Pills(listOf("6–7 h" to 6, "7–8 h" to 7, "8–9 h" to 8), p.sleepHoursMin) { p = p.copy(sleepHoursMin = it, sleepHoursMax = it + 1) }
    }

    RytmCard {
        CardHeader("Żywienie")
        Pills(listOf("Policz z profilu" to false, "Mam własne cele" to true), custom) { custom = it }
        if (custom) {
            SmallText("Np. z Fitatu albo od dietetyka. Treningi i tak doliczają się osobno (0,75 × kcal z zegarka).")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Kcal", kcal, KeyboardType.Number, Modifier.weight(1f)) { kcal = it.filter(Char::isDigit).take(5) }
                Field("Białko g", protein, KeyboardType.Number, Modifier.weight(1f)) { protein = it.filter(Char::isDigit).take(3) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Tłuszcz g", fat, KeyboardType.Number, Modifier.weight(1f)) { fat = it.filter(Char::isDigit).take(3) }
                Field("Węgle g", carbs, KeyboardType.Number, Modifier.weight(1f)) { carbs = it.filter(Char::isDigit).take(3) }
            }
        } else {
            val t = (built ?: p).let { NutritionCalculator.targets(it, today) }
            SmallText(
                "Na dzień bez treningu: ${Format.int(t.kcal)} kcal · białko ${t.proteinG} g · tłuszcz ${t.fatG} g · węgle ${t.carbsG} g. " +
                    "Liczone wzorem Mifflina-St Jeora z ruchem w ciągu dnia i korektą pod cel. Treningi dolicza Rytm.",
            )
        }
    }

    RytmButton(
        if (onboarding) "Zaczynamy" else "Zapisz profil",
        Modifier.fillMaxWidth(),
        primary = true,
        enabled = built != null,
    ) { built?.let(onSave) }
    if (built == null) SmallText("Uzupełnij wzrost i wagę" + if (custom) " oraz kcal i białko." else ".", color = Rytm.colors.warn)
    onDiagnostics?.let { RytmButton("Diagnostyka Health Connect", Modifier.fillMaxWidth(), onClick = it) }
}

private fun goalHint(g: Goal): String = when (g) {
    Goal.RECOMP -> "Mniej tłuszczu, więcej mięśni przy podobnej wadze: lekki deficyt, dużo białka, min. 2 sesje siłowe."
    Goal.FAT_LOSS -> "Spadek wagi ok. 0,5% tygodniowo: deficyt do 500 kcal, dużo białka, żeby nie tracić mięśni."
    Goal.MUSCLE_GAIN -> "Przybieranie mięśni: lekka nadwyżka kcal, 3 sesje siłowe, powolny wzrost wagi."
    Goal.STRENGTH -> "Więcej siły: mniej powtórzeń z większym ciężarem (na siłowni), dłuższe przerwy."
    Goal.ENDURANCE -> "Lepsza kondycja: więcej cardio i obwodów, siła 1–2 razy w tygodniu."
    Goal.HEALTH -> "Ogólne zdrowie: regularny ruch, sen, kroki i dwie krótkie sesje siłowe."
}

@Composable
private fun Field(label: String, value: String, type: KeyboardType, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Pills(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (label, value) -> Pill(label, value == selected) { onSelect(value) } }
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
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
