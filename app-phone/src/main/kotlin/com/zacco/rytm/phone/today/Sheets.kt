package com.zacco.rytm.phone.today

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zacco.rytm.domain.body.ScaleReading
import com.zacco.rytm.domain.model.BodyArea
import com.zacco.rytm.domain.model.BodyComposition
import com.zacco.rytm.domain.model.NutritionDay
import com.zacco.rytm.domain.model.DailyCheckIn
import com.zacco.rytm.domain.model.LoadedArea
import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.time.RytmTime
import com.zacco.rytm.domain.sleep.ManualSleep
import androidx.compose.foundation.verticalScroll
import com.zacco.rytm.phone.app.ActivityEntry
import com.zacco.rytm.phone.app.Format
import com.zacco.rytm.phone.ui.components.Pill
import com.zacco.rytm.phone.ui.components.RytmButton
import com.zacco.rytm.phone.ui.components.SmallText
import com.zacco.rytm.phone.ui.components.WhyBox
import com.zacco.rytm.phone.ui.theme.Rytm
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RytmSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Rytm.colors.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 18.dp).padding(bottom = 26.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = Rytm.type.planTitle.copy(fontSize = Rytm.type.planTitle.fontSize * 0.95f), color = Rytm.colors.ink)
            content()
        }
    }
}

/** Skala 1–N jako rząd przycisków (check-in, RPE). */
@Composable
internal fun Scale(range: IntRange, selected: Int?, onSelect: (Int) -> Unit) {
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
private fun Label(text: String) = Text(text, style = Rytm.type.body, color = Rytm.colors.ink)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckInSheet(
    current: DailyCheckIn?,
    alcoholYesterday: Int,
    onDismiss: () -> Unit,
    onSave: (energy: Int, mood: Int, sore: Set<BodyArea>, drinksYesterday: Int) -> Unit,
) {
    var energy by remember { mutableIntStateOf(current?.energy ?: 3) }
    var mood by remember { mutableIntStateOf(current?.mood ?: 3) }
    var sore by remember { mutableStateOf(current?.soreAreas ?: emptySet()) }
    var drinks by remember { mutableIntStateOf(alcoholYesterday) }
    RytmSheet("Poranny check-in", onDismiss) {
        Label("Energia")
        Scale(1..5, energy) { energy = it }
        Label("Nastrój")
        Scale(1..5, mood) { mood = it }
        Label("Co boli?")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BodyArea.entries.forEach { a -> Pill(Format.body(a), a in sore) { sore = if (a in sore) sore - a else sore + a } }
        }
        // Alkohol tylko jako czynnik gotowości: ile porcji wczoraj (piwo 0,5 l, kieliszek wina, kieliszek wódki = 1).
        Label("Alkohol wczoraj (porcje)")
        Scale(0..5, drinks.coerceAtMost(5)) { drinks = it }
        RytmButton("Zapisz", Modifier.fillMaxWidth(), primary = true) { onSave(energy, mood, sore, drinks); onDismiss() }
    }
}

private val DURATIONS = listOf(10, 15, 20, 30, 45, 60, 90, 120, 180)

/** Gotowe rodzaje aktywności w arkuszu; „Inna” wymaga opisu. */
private enum class ActivityPreset(val label: String, val type: ActivityType, val description: String) {
    WALK("Spacer", ActivityType.WALK, "Spacer"),
    WALK_TREADMILL("Spacer na bieżni", ActivityType.WALK, "Spacer na bieżni"),
    RUN("Bieg", ActivityType.CARDIO, "Bieg"),
    RUN_TREADMILL("Bieg na bieżni", ActivityType.CARDIO, "Bieg na bieżni"),
    BIKE("Rower", ActivityType.CARDIO, "Rower"),
    STRETCH("Rozciąganie / joga", ActivityType.MOBILITY, "Rozciąganie"),
    STRENGTH("Trening siłowy", ActivityType.STRENGTH, "Trening siłowy"),
    OTHER("Inna", ActivityType.OTHER, ""),
}

/**
 * Ręczny wpis aktywności na wybrany dzień: spacer, bieg (też na bieżni), rower, rozciąganie, siła albo inna.
 * Dla opasek bez Health Connect można wpisać kcal z aplikacji opaski.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivitySheet(date: LocalDate, onDismiss: () -> Unit, onSave: (ActivityEntry) -> Unit) {
    val today = LocalDate.now(RytmTime.zone)
    var preset by remember { mutableStateOf(ActivityPreset.WALK) }
    var description by remember { mutableStateOf("") }
    var duration by remember { mutableIntStateOf(30) }
    var rpe by remember { mutableStateOf<Int?>(null) }
    var areas by remember { mutableStateOf(emptySet<LoadedArea>()) }
    var areasTouched by remember { mutableStateOf(false) }
    var kcal by remember { mutableStateOf("") }
    // Godzina końca: null = przed chwilą (tylko dziś).
    var endHour by remember { mutableStateOf<Int?>(if (date == today) null else 18) }

    RytmSheet("Aktywność · " + Format.dayShortLabel(date, today), onDismiss) {
        Label("Co to było")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ActivityPreset.entries.forEach { p -> Pill(p.label, p == preset) { preset = p } }
        }
        if (preset == ActivityPreset.OTHER) {
            OutlinedTextField(
                value = description,
                onValueChange = {
                    description = it
                    // Domyślne zaznaczenie z opisu („remont” → plecy i barki), dopóki użytkownik sam nie wybierze.
                    if (!areasTouched) areas = LoadedArea.defaultsFor(it)
                },
                label = { Text("Opis, np. remont: noszenie płyt") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Label("Ile trwało")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DURATIONS.forEach { d -> Pill(Format.minutes(d), d == duration) { duration = d } }
        }
        Label("Kiedy skończone")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (date == today) Pill("Przed chwilą", endHour == null) { endHour = null }
            val nowHour = java.time.LocalTime.now(RytmTime.zone).hour
            END_HOURS.filter { date != today || it <= nowHour }.forEach { h -> Pill("%02d:00".format(h), endHour == h) { endHour = h } }
        }
        Label("Jak ciężko (RPE 1–10, opcjonalnie)")
        Scale(1..5, rpe) { rpe = if (rpe == it) null else it }
        Scale(6..10, rpe) { rpe = if (rpe == it) null else it }
        NumberField("Kcal z opaski (opcjonalnie)", kcal, Modifier.fillMaxWidth()) { kcal = it.filter(Char::isDigit).take(4) }
        if (preset == ActivityPreset.OTHER) {
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
        }
        if (preset == ActivityPreset.STRETCH) SmallText("Od 10 minut odhaczy się nawyk mobilności.")
        val text = if (preset == ActivityPreset.OTHER) description.trim() else preset.description
        RytmButton("Zapisz", Modifier.fillMaxWidth(), primary = true, enabled = text.isNotBlank()) {
            val end = endHour?.let { date.atTime(it, 0).atZone(RytmTime.zone).toInstant() } ?: Instant.now()
            onSave(
                ActivityEntry(
                    type = preset.type,
                    description = text,
                    end = end,
                    durationMin = duration,
                    rpe = rpe,
                    areas = if (preset == ActivityPreset.OTHER) areas else emptySet(),
                    kcal = kcal.toIntOrNull()?.takeIf { it in 1..5000 },
                ),
            )
            onDismiss()
        }
    }
}

private val END_HOURS = listOf(7, 10, 13, 16, 18, 20, 22)

/** Wpis z Fitatu na dziś albo wstecz (do 13 dni), bo bez Premium Fitatu nie wysyła danych do Health Connect. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NutritionSheet(
    today: LocalDate,
    recent: List<NutritionDay>,
    onDismiss: () -> Unit,
    onSave: (LocalDate, Int, Double, Double?, Double?) -> Unit,
    initial: LocalDate = today,
) {
    var date by remember { mutableStateOf(initial) }
    val current = recent.firstOrNull { it.date == date }
    // Pola wypełniają się wartościami wybranego dnia.
    var kcal by remember(date) { mutableStateOf(current?.kcal?.toString().orEmpty()) }
    var protein by remember(date) { mutableStateOf(current?.proteinG?.let { Format.roundInt(it).toString() }.orEmpty()) }
    var fat by remember(date) { mutableStateOf(current?.fatG?.takeIf { it > 0 }?.let { Format.roundInt(it).toString() }.orEmpty()) }
    var carbs by remember(date) { mutableStateOf(current?.carbsG?.takeIf { it > 0 }?.let { Format.roundInt(it).toString() }.orEmpty()) }
    val kcalVal = kcal.toIntOrNull()
    val proteinVal = protein.replace(',', '.').toDoubleOrNull()

    RytmSheet("Żywienie z Fitatu", onDismiss) {
        SmallText("Przepisz podsumowanie dnia z Fitatu. Kcal i białko wystarczą. Kropka przy dniu: już wpisany.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (0L..6L).map { today.minusDays(it) }.forEach { d ->
                val label = when (d) {
                    today -> "Dziś"
                    today.minusDays(1) -> "Wczoraj"
                    else -> Format.weekdayShort(d.dayOfWeek) + " " + d.dayOfMonth
                }
                val filled = recent.any { it.date == d }
                Pill(label + if (filled) " •" else "", d == date) { date = d }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Kcal", kcal, Modifier.weight(1f)) { kcal = it }
            NumberField("Białko (g)", protein, Modifier.weight(1f)) { protein = it }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Tłuszcz (g)", fat, Modifier.weight(1f)) { fat = it }
            NumberField("Węgle (g)", carbs, Modifier.weight(1f)) { carbs = it }
        }
        RytmButton("Zapisz " + Format.dayMonth(date), Modifier.fillMaxWidth(), primary = true, enabled = kcalVal != null && proteinVal != null) {
            onSave(date, kcalVal!!, proteinVal!!, fat.replace(',', '.').toDoubleOrNull(), carbs.replace(',', '.').toDoubleOrNull())
            onDismiss()
        }
    }
}

/** Alkohol w porcjach standardowych. Wczorajszy obniża dzisiejszą gotowość. */
@Composable
fun AlcoholSheet(date: LocalDate, current: Int, onDismiss: () -> Unit, onSave: (LocalDate, Int) -> Unit) {
    var drinks by remember { mutableIntStateOf(current) }
    RytmSheet("Alkohol · " + Format.dayShortLabel(date, LocalDate.now(RytmTime.zone)), onDismiss) {
        SmallText("1 porcja = piwo 0,5 l, lampka wina (150 ml) albo 50 ml wódki. Liczy się do gotowości następnego dnia.")
        Scale(0..5, drinks.coerceAtMost(5)) { drinks = it }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("6", drinks == 6) { drinks = 6 }
            Pill("8", drinks == 8) { drinks = 8 }
            Pill("10+", drinks >= 10) { drinks = 10 }
        }
        val penalty = com.zacco.rytm.domain.readiness.ReadinessCalculator.alcoholPenalty(drinks)
        SmallText(if (penalty == 0) "Bez wpływu na gotowość." else "Gotowość następnego dnia: −$penalty pkt.")
        RytmButton("Zapisz", Modifier.fillMaxWidth(), primary = true) {
            onSave(date, drinks)
            onDismiss()
        }
    }
}

/**
 * Sen wpisany ręcznie, gdy zegarek go nie zarejestrował (np. ładował się w nocy).
 * [date] to dzień pobudki; zaśnięcie po pobudce na zegarze oznacza dzień wcześniej.
 */
@Composable
fun SleepSheet(
    date: LocalDate,
    initialStart: Instant?,
    initialEnd: Instant?,
    onDismiss: () -> Unit,
    onSave: (LocalDate, Instant, Instant) -> Unit,
) {
    val zone = RytmTime.zone
    val hm = DateTimeFormatter.ofPattern("HH:mm")
    var onset by remember { mutableStateOf(initialStart?.atZone(zone)?.format(hm) ?: "23:30") }
    var wake by remember { mutableStateOf(initialEnd?.atZone(zone)?.format(hm) ?: "07:00") }
    val window = ManualSleep.window(date, ManualSleep.parseTime(onset), ManualSleep.parseTime(wake))
    val minutes = window?.let { java.time.Duration.between(it.first, it.second).toMinutes().toInt() }
    val inFuture = window != null && window.second.isAfter(Instant.now())
    val valid = minutes != null && minutes in 30..16 * 60 && !inFuture
    RytmSheet("Sen · pobudka " + Format.dayShortLabel(date, LocalDate.now(zone)), onDismiss) {
        SmallText("Gdy zegarek nie zapisał snu, wpisz godzinę zaśnięcia i pobudki. Wpis liczy się do gotowości i nie zostanie nadpisany przez synchronizację.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeField("Zaśnięcie", onset, Modifier.weight(1f)) { onset = it }
            TimeField("Pobudka", wake, Modifier.weight(1f)) { wake = it }
        }
        SmallText(
            when {
                window == null -> "Wpisz godziny jako gg:mm, np. 23:45."
                inFuture -> "Pobudka nie może być w przyszłości."
                minutes!! < 30 || minutes > 16 * 60 -> "Sen musi trwać od 30 min do 16 h."
                else -> "Od " + window.first.atZone(zone).format(DateTimeFormatter.ofPattern("EEE HH:mm", java.util.Locale.forLanguageTag("pl"))) +
                    " do " + window.second.atZone(zone).format(hm) + " · " + Format.minutes(minutes)
            },
            color = if (window != null && !valid) Rytm.colors.warn else Rytm.colors.muted,
        )
        RytmButton("Zapisz", Modifier.fillMaxWidth(), primary = true, enabled = valid) {
            onSave(date, window!!.first, window.second)
            onDismiss()
        }
    }
}

@Composable
private fun TimeField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == ':' }.take(5)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
        modifier = modifier,
    )
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
        WhyBox("RPE to Twoja ocena, jak ciężki był wysiłek, w skali 1–10: 1 to bardzo lekko, 5 umiarkowanie (da się rozmawiać zdaniami), 7 ciężko (pojedyncze słowa), 9–10 prawie maksimum. Zegarek nie wie, jak się czujesz, a samo tętno często zaniża siłowe treningi, więc ta ocena mówi Rytmowi najwięcej.")
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

/**
 * Ważenie: ręcznie albo ze zrzutu ekranu Xiaomi Home (rozpoznanie tekstu na telefonie, bez internetu).
 * Odczyt ze zrzutu to rozwiązanie tymczasowe, dopóki pomiary nie przychodzą same do Health Connect.
 */
@Composable
fun WeighInSheet(date: LocalDate, onDismiss: () -> Unit, onSave: (Double, Double?, Instant?, BodyComposition?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kg by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var reading by remember { mutableStateOf<ScaleReading?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var ocrDump by remember { mutableStateOf<String?>(null) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val value = kg.replace(',', '.').toDoubleOrNull()?.takeIf { it in 30.0..250.0 }
    val fatValue = fat.replace(',', '.').toDoubleOrNull()?.takeIf { it in 3.0..60.0 }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        status = "Czytam zrzut…"
        scope.launch {
            val scan = runCatching { ScaleOcr.read(context, uri) }.getOrNull()
            ocrDump = scan?.dump?.takeIf { it.isNotBlank() }
            val r = scan?.reading
            if (r == null) {
                status = "Nie udało się odczytać zrzutu. Wpisz wynik ręcznie."
            } else {
                reading = r
                r.weightKg?.let { kg = Format.dec(it) }
                r.bodyFatPct?.let { fat = Format.dec(it) }
                status = "Odczytano ${r.fieldCount} pomiarów" + (r.measuredAt?.let { " z " + it.format(DateTimeFormatter.ofPattern("d.MM.yyyy HH:mm")) } ?: "") + ". Sprawdź wagę i tłuszcz."
            }
        }
    }

    RytmSheet("Ważenie · " + Format.dayShortLabel(date, LocalDate.now(RytmTime.zone)), onDismiss) {
        SmallText("Wpisz wynik albo wczytaj zrzut ekranu pomiaru z Xiaomi Home. Najlepiej rano, po toalecie, przed jedzeniem.")
        RytmButton("Wczytaj zrzut z Xiaomi Home", Modifier.fillMaxWidth()) {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        status?.let { SmallText(it) }
        ocrDump?.let { d ->
            // Gdy odczyt się myli: tekst z położeniem linii do wklejenia w rozmowie, z tego powstaje test.
            RytmButton("Kopiuj rozpoznany tekst", Modifier.fillMaxWidth()) {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(d))
                status = "Skopiowano tekst z OCR. Wklej go w rozmowie, jeśli coś się nie zgadza."
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Waga (kg)", kg, Modifier.weight(1f)) { kg = it }
            NumberField("Tłuszcz (%)", fat, Modifier.weight(1f)) { fat = it }
        }
        reading?.composition?.takeUnless { it.isEmpty }?.let { c -> SmallText(compositionSummary(c)) }
        SmallText("Tłuszcz z wagi łazienkowej mocno się waha: Rytm pokaże trend, nie pojedynczy wynik.")
        RytmButton("Zapisz", Modifier.fillMaxWidth(), primary = true, enabled = value != null) {
            val r = reading
            val time = r?.measuredAt?.atZone(com.zacco.rytm.domain.time.RytmTime.zone)?.toInstant()?.takeIf { !it.isAfter(Instant.now()) }
            onSave(value!!, fatValue, time, r?.composition?.takeUnless { it.isEmpty })
            onDismiss()
        }
    }
}

fun compositionSummary(c: BodyComposition): String = listOfNotNull(
    c.muscleKg?.let { "mięśnie ${Format.dec(it)} kg" },
    c.skeletalMuscleKg?.let { "szkieletowe ${Format.dec(it)} kg" },
    c.waterPct?.let { "woda ${Format.dec(it)}%" },
    c.proteinPct?.let { "białko ${Format.dec(it)}%" },
    c.boneKg?.let { "kości ${Format.dec(it)} kg" },
    c.visceralFat?.let { "tłuszcz trzewny ${Format.dec(it)}" },
    c.bmrKcal?.let { "BMR ${Format.int(it)} kcal" },
    c.leanMassKg?.let { "beztłuszczowa ${Format.dec(it)} kg" },
    c.waistHipRatio?.let { "talia/biodra ${Format.dec(it, 2)}" },
    c.bmi?.let { "BMI ${Format.dec(it)}" },
).joinToString(" · ")

/** Seria wpisana na telefonie. */
data class SetEntry(val exercise: String, val setIndex: Int, val reps: Int, val weightKg: Double?)

/**
 * Zapis treningu bez zegarka: serie wypełnione celem z planu (z progresji), do poprawienia.
 * Ćwiczenie można odznaczyć, gdy go nie było.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutLogSheet(
    workout: com.zacco.rytm.domain.model.StrengthWorkout,
    onDismiss: () -> Unit,
    onSave: (List<SetEntry>, Int, Int?) -> Unit,
) {
    val reps = remember {
        androidx.compose.runtime.mutableStateMapOf<String, String>().apply {
            workout.exercises.forEachIndexed { i, e -> (1..e.sets).forEach { n -> put("$i-$n", e.reps.last.toString()) } }
        }
    }
    val kgs = remember {
        androidx.compose.runtime.mutableStateMapOf<String, String>().apply {
            workout.exercises.forEachIndexed { i, e -> e.weightKg?.let { put("$i", Format.kg(it)) } }
        }
    }
    var skipped by remember { mutableStateOf(emptySet<Int>()) }
    var minutes by remember { mutableIntStateOf(45) }
    var rpe by remember { mutableStateOf<Int?>(null) }

    RytmSheet("Zapisz trening", onDismiss) {
        SmallText("Wpisane są cele z planu. Popraw powtórzenia i ciężar tam, gdzie było inaczej. Na tej podstawie Rytm dobierze ciężar na następny raz.")
        workout.exercises.forEachIndexed { i, e ->
            val off = i in skipped
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(if (off) "Pominięte" else "Zrobione", !off) { skipped = if (off) skipped - i else skipped + i }
                Label(e.name)
            }
            if (!off) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..e.sets).forEach { n ->
                        NumberField("Seria $n", reps["$i-$n"].orEmpty(), Modifier.fillMaxWidth(0.3f)) { reps["$i-$n"] = it.filter(Char::isDigit) }
                    }
                    if (e.weightKg != null || kgs.containsKey("$i")) {
                        NumberField("kg", kgs["$i"].orEmpty(), Modifier.fillMaxWidth(0.3f)) { kgs["$i"] = it }
                    }
                }
            }
        }
        Label("Ile trwał?")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(20, 30, 45, 60, 75).forEach { m -> Pill("$m min", minutes == m) { minutes = m } }
        }
        Label("Jak ciężko było? (RPE)")
        Scale(1..5, rpe) { rpe = it }
        Scale(6..10, rpe) { rpe = it }
        RytmButton("Zapisz", Modifier.fillMaxWidth(), primary = true) {
            val entries = workout.exercises.flatMapIndexed { i, e ->
                if (i in skipped) emptyList() else (1..e.sets).mapNotNull { n ->
                    val r = reps["$i-$n"]?.toIntOrNull() ?: return@mapNotNull null
                    if (r <= 0) return@mapNotNull null
                    SetEntry(e.name, n, r, kgs["$i"]?.replace(',', '.')?.toDoubleOrNull())
                }
            }
            onSave(entries, minutes, rpe)
            onDismiss()
        }
    }
}

/** Opis ćwiczenia: jak je zrobić, wskazówki, najczęstszy błąd i wideo z sieci. */
@Composable
fun ExerciseSheet(info: com.zacco.rytm.domain.exercise.ExerciseInfo, onDismiss: () -> Unit) {
    val c = Rytm.colors
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    RytmSheet(info.name, onDismiss) {
        if (info.muscles.isNotEmpty()) SmallText("Pracują: " + info.muscles.joinToString { Format.muscle(it).lowercase() })
        Text(info.how, style = Rytm.type.body, color = c.ink)
        Text("Wskazówki", style = Rytm.type.bodyStrong, color = c.ink)
        info.tips.forEach { SmallText("• $it") }
        Text("Najczęstszy błąd", style = Rytm.type.bodyStrong, color = c.ink)
        SmallText(info.mistake, color = c.warn)
        RytmButton("Zobacz wideo (YouTube)", Modifier.fillMaxWidth(), primary = true) { runCatching { uri.openUri(info.videoUrl) } }
        RytmButton("Zamknij", Modifier.fillMaxWidth(), onClick = onDismiss)
    }
}

/** Co można dodać z przycisku „+”. */
enum class QuickEntry { ACTIVITY, WORKOUT, NUTRITION, WEIGH_IN, ALCOHOL, CHECK_IN, SLEEP }

/**
 * Jeden przycisk „+” zamiast rzędu przycisków. Dzień wybierasz raz u góry i dotyczy każdego wpisu;
 * check-in i trening z planu są tylko na dziś.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddSheet(
    today: LocalDate,
    date: LocalDate,
    hasWorkout: Boolean,
    onDate: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    onPick: (QuickEntry) -> Unit,
) {
    val c = Rytm.colors
    RytmSheet("Dodaj wpis", onDismiss) {
        Label("Na dzień")
        Row(
            Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            (0L..13L).map { today.minusDays(it) }.forEach { d -> Pill(Format.dayShortLabel(d, today), d == date) { onDate(d) } }
        }
        val isToday = date == today
        val tiles = buildList {
            add(Triple(QuickEntry.ACTIVITY, "Aktywność", "spacer, bieg, remont…"))
            if (isToday && hasWorkout) add(Triple(QuickEntry.WORKOUT, "Trening z planu", "serie i ciężary"))
            add(Triple(QuickEntry.NUTRITION, "Żywienie", "kcal i makro z Fitatu"))
            add(Triple(QuickEntry.WEIGH_IN, "Ważenie", "ręcznie albo zrzut"))
            add(Triple(QuickEntry.SLEEP, "Sen", "gdy zegarek nie zapisał"))
            if (isToday) add(Triple(QuickEntry.CHECK_IN, "Check-in", "energia i co boli"))
        }
        tiles.chunked(2).forEach { row ->
            // Równa wysokość kafelków w rzędzie, niezależnie od długości opisu.
            Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (entry, title, sub) ->
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, c.line, RoundedCornerShape(14.dp))
                            .clickable(role = Role.Button) { onPick(entry) }
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(title, style = Rytm.type.bodyStrong, color = c.ink)
                        SmallText(sub)
                    }
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
        if (!isToday) SmallText("Check-in i trening z planu dodasz tylko na dziś.")
    }
}
