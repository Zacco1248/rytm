package com.zacco.rytm.phone.evening

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.zacco.rytm.ai.AiSettings
import com.zacco.rytm.ai.LlmProvider
import com.zacco.rytm.ai.SavedEveningReview
import com.zacco.rytm.phone.app.CoachUiState
import com.zacco.rytm.phone.app.Format
import com.zacco.rytm.phone.ui.components.CardHeader
import com.zacco.rytm.phone.ui.components.Chip
import com.zacco.rytm.phone.ui.components.ChipTone
import com.zacco.rytm.phone.ui.components.Pill
import com.zacco.rytm.domain.coach.NudgeKind
import com.zacco.rytm.phone.ui.components.RytmButton
import com.zacco.rytm.phone.ui.components.RytmCard
import com.zacco.rytm.phone.ui.components.ScreenHeader
import com.zacco.rytm.phone.ui.components.SmallText
import com.zacco.rytm.phone.ui.components.WhyBox
import com.zacco.rytm.phone.ui.theme.Rytm
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Stan asystenta AI dla ekranu „Wieczór”. */
data class AiUi(
    val settings: AiSettings,
    val hasKey: Boolean,
    val review: SavedEveningReview?,
    val running: Boolean,
    val spentUsd: Double?,
)

@Composable
fun EveningScreen(
    s: CoachUiState,
    ai: AiUi,
    onRun: (String?) -> Unit,
    onSaveSettings: (AiSettings) -> Unit,
    onSaveKey: (LlmProvider, String) -> Unit,
    onClearKey: (LlmProvider) -> Unit,
) {
    val t = s.today
    ScreenHeader(sub = Format.dayLong(t.date), title = "Wieczór")
    RytmCard {
        CardHeader("Pora snu")
        Text(t.bedtime.format(DateTimeFormatter.ofPattern("HH:mm")), style = Rytm.type.bigNumber, color = Rytm.colors.ink)
        SmallText("Jutro: ${t.tomorrow.title.lowercase()} (${Format.intensity(t.tomorrow.intensity).lowercase()}).")
        if (t.bedtime != com.zacco.rytm.domain.profile.SleepTarget().onsetFrom) {
            WhyBox("Pół godziny wcześniej niż zwykle: niska gotowość, ciężki dzień albo mocny trening jutro.")
        }
    }
    ReviewCard(ai, onRun)
    // Ustawienia rzadko zmieniane: schowane w wierszach, rozwijane po dotknięciu.
    com.zacco.rytm.phone.ui.components.MoreList(
        key = "evening",
        items = listOf(
            com.zacco.rytm.phone.ui.components.MoreItem("Podpowiedzi w ciągu dnia", if (ai.settings.nudgesEnabled) "włączone" else "wyłączone") {
                NudgesCard(ai.settings, onSaveSettings)
            },
            com.zacco.rytm.phone.ui.components.MoreItem(
                "Asystent AI",
                if (!ai.hasKey) "bez klucza" else ai.spentUsd?.let { Format.dec(it, 2) + " z " + Format.dec(ai.settings.monthlyBudgetUsd, 2) + " USD" },
            ) {
                AiSettingsCard(ai, onSaveSettings, onSaveKey, onClearKey)
            },
        ),
    )
}

/** Podpowiedzi w ciągu dnia: włącznik całości i każdego rodzaju. Z reguł, więc bez klucza AI i bez kosztu. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NudgesCard(st: AiSettings, onSave: (AiSettings) -> Unit) {
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    RytmCard {
        CardHeader(
            "Podpowiedzi w ciągu dnia",
            help = "Krótkie powiadomienia, gdy coś warto zmienić dziś: po krótkiej nocy, gdy trening z planu jeszcze czeka, " +
                "po 3 dniach bez ruchu albo gdy wieczorem brakuje białka. Najwyżej 2 dziennie, nic między 22 a 7. " +
                "Liczone z reguł na telefonie, bez AI i bez kosztu.",
        ) {
            Chip(if (st.nudgesEnabled) "włączone" else "wyłączone", if (st.nudgesEnabled) ChipTone.GOOD else ChipTone.MUTED) {
                if (!st.nudgesEnabled && Build.VERSION.SDK_INT >= 33) notify.launch(Manifest.permission.POST_NOTIFICATIONS)
                onSave(st.copy(nudgesEnabled = !st.nudgesEnabled))
            }
        }
        if (st.nudgesEnabled) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                NudgeKind.entries.forEach { k ->
                    val on = k.name !in st.nudgesOff
                    Pill(k.label, on) {
                        if (Build.VERSION.SDK_INT >= 33) notify.launch(Manifest.permission.POST_NOTIFICATIONS)
                        onSave(st.copy(nudgesOff = if (on) st.nudgesOff + k.name else st.nudgesOff - k.name))
                    }
                }
            }
            SmallText(NudgeKind.entries.filter { it.name !in st.nudgesOff }.joinToString("; ") { "${it.label}: ${it.hint}" }.ifEmpty { "Wszystkie rodzaje wyłączone." })
        }
    }
}

@Composable
private fun ReviewCard(ai: AiUi, onRun: (String?) -> Unit) {
    var reflection by rememberSaveable { mutableStateOf(ai.review?.reflection.orEmpty()) }
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    RytmCard {
        CardHeader("Przegląd dnia") {
            ai.review?.let { r ->
                Chip(if (r.source == "LLM") "AI" else "reguły", if (r.source == "LLM") ChipTone.ACCENT else ChipTone.MUTED)
            }
        }
        val r = ai.review
        if (r == null) {
            SmallText(
                "Analiza dnia przyjdzie sama o " + hhmm(ai.settings.eveningAtMin) + ". Możesz ją też zrobić teraz." +
                    if (!ai.hasKey) " Bez klucza AI złożą ją reguły." else "",
            )
        } else {
            Text(r.review.summary, style = Rytm.type.body, color = Rytm.colors.ink)
            SmallText(r.review.interpretation)
            WhyBox(r.review.tomorrow)
            r.review.focusPoints.forEach { SmallText("• $it") }
            if (r.source != "LLM" && r.error != null && ai.hasKey) SmallText("AI niedostępne: ${r.error}", color = Rytm.colors.warn)
        }
        OutlinedTextField(
            value = reflection,
            onValueChange = { reflection = it.take(500) },
            label = { Text("Jak minął dzień? (opcjonalnie)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        RytmButton(
            when {
                ai.running -> "Analizuję…"
                r == null -> "Zrób analizę teraz"
                else -> "Odśwież analizę"
            },
            Modifier.fillMaxWidth(),
            primary = true,
            enabled = !ai.running,
        ) {
            if (Build.VERSION.SDK_INT >= 33) notify.launch(Manifest.permission.POST_NOTIFICATIONS)
            onRun(reflection)
        }
    }
}

@Composable
private fun AiSettingsCard(
    ai: AiUi,
    onSaveSettings: (AiSettings) -> Unit,
    onSaveKey: (LlmProvider, String) -> Unit,
    onClearKey: (LlmProvider) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(!ai.hasKey) }
    var key by remember { mutableStateOf("") }
    var budget by remember(ai.settings.monthlyBudgetUsd) { mutableStateOf(Format.dec(ai.settings.monthlyBudgetUsd, 2)) }
    val st = ai.settings
    RytmCard {
        CardHeader("Asystent AI") {
            Chip(if (ai.hasKey) "aktywny" else "bez klucza", if (ai.hasKey) ChipTone.GOOD else ChipTone.MUTED, onClick = { open = !open })
        }
        val spent = ai.spentUsd?.let { "%.2f".format(Locale.ROOT, it).replace('.', ',') } ?: "–"
        SmallText("W tym miesiącu: $spent z ${Format.dec(st.monthlyBudgetUsd, 2)} USD. Po przekroczeniu limitu analizę robią reguły.")
        if (open) {
            SmallText("Dostawca:")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Claude", if (st.provider == LlmProvider.CLAUDE) ChipTone.ACCENT else ChipTone.MUTED) {
                    onSaveSettings(st.copy(provider = LlmProvider.CLAUDE))
                }
                Chip("OpenAI", if (st.provider == LlmProvider.OPENAI) ChipTone.ACCENT else ChipTone.MUTED) {
                    onSaveSettings(st.copy(provider = LlmProvider.OPENAI))
                }
            }
            SmallText(
                "Klucz API zostaje zaszyfrowany tylko na tym telefonie. Znajomym daj osobny klucz z własnym limitem " +
                    "(np. osobny workspace u dostawcy), wtedy możesz go w każdej chwili wyłączyć.",
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it.trim() },
                label = { Text(if (ai.hasKey) "Nowy klucz (zastąpi obecny)" else "Klucz API") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                RytmButton("Zapisz klucz", Modifier.weight(1f), primary = true, enabled = key.length >= 20) {
                    onSaveKey(st.provider, key)
                    key = ""
                }
                if (ai.hasKey) RytmButton("Usuń klucz", Modifier.weight(1f)) { onClearKey(st.provider) }
            }
            OutlinedTextField(
                value = budget,
                onValueChange = { v -> budget = v.filter { it.isDigit() || it == ',' || it == '.' }.take(5) },
                label = { Text("Limit miesięczny (USD)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            SmallText("Wieczorna analiza o:")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(21 * 60 + 30, 22 * 60 + 30, 23 * 60 + 30).forEach { m ->
                    Chip(hhmm(m), if (st.eveningAtMin == m) ChipTone.ACCENT else ChipTone.MUTED) { onSaveSettings(st.copy(eveningAtMin = m)) }
                }
                Chip(if (st.eveningEnabled) "włączona" else "wyłączona", if (st.eveningEnabled) ChipTone.GOOD else ChipTone.MUTED) {
                    onSaveSettings(st.copy(eveningEnabled = !st.eveningEnabled))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Rano: " + if (st.morningEnabled) "AI" else "reguły", if (st.morningEnabled) ChipTone.GOOD else ChipTone.MUTED) {
                    onSaveSettings(st.copy(morningEnabled = !st.morningEnabled))
                }
                Chip("Raport niedz. 20:00: " + if (st.weeklyEnabled) "tak" else "nie", if (st.weeklyEnabled) ChipTone.GOOD else ChipTone.MUTED) {
                    onSaveSettings(st.copy(weeklyEnabled = !st.weeklyEnabled))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Analiza miesiąca: " + if (st.monthlyEnabled) "tak" else "nie", if (st.monthlyEnabled) ChipTone.GOOD else ChipTone.MUTED) {
                    onSaveSettings(st.copy(monthlyEnabled = !st.monthlyEnabled))
                }
            }
            SmallText("Rano po check-inie AI tłumaczy plan dnia (tani model). Raport tygodniowy liczy mocniejszy model, ok. 0,05 USD, a analizę miesiąca najmocniejszy, ok. 0,3 USD.")
            RytmButton("Zapisz limit", Modifier.fillMaxWidth()) {
                budget.replace(',', '.').toDoubleOrNull()?.let { onSaveSettings(st.copy(monthlyBudgetUsd = it)) }
            }
        }
    }
}

private fun hhmm(min: Int) = "%02d:%02d".format(Locale.ROOT, min / 60, min % 60)
