package com.zacco.rytm.phone.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zacco.rytm.ai.chat.ChatMessage
import com.zacco.rytm.ai.chat.CoachChatService
import com.zacco.rytm.domain.model.PlannedDay
import com.zacco.rytm.phone.ui.components.CardHeader
import com.zacco.rytm.phone.ui.components.Chip
import com.zacco.rytm.phone.ui.components.ChipTone
import com.zacco.rytm.phone.ui.components.Pill
import com.zacco.rytm.phone.ui.components.RytmButton
import com.zacco.rytm.phone.ui.components.RytmCard
import com.zacco.rytm.phone.ui.components.ScreenHeader
import com.zacco.rytm.phone.ui.components.SmallText
import com.zacco.rytm.phone.ui.theme.Rytm

private val SUGGESTIONS = listOf(
    "Mam dziś tylko 20 minut",
    "Boli mnie bark, co zmienić?",
    "Jestem padnięty, zrób lżej",
    "Co zjeść po treningu?",
)

/**
 * Rozmowa z trenerem AI. Trener zna dane z dziś i z tygodnia; gdy zmienia plan dnia, zmiana od razu trafia
 * do „Plan na dziś”, treningu na telefonie i zegarka.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    plan: PlannedDay,
    planChanged: Boolean,
    running: Boolean,
    hasKey: Boolean,
    onSend: (String) -> Unit,
    onResetPlan: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    ScreenHeader(sub = "Rozmowa z trenerem", title = "Trener") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (messages.isNotEmpty()) Chip("Wyczyść", ChipTone.MUTED, onClick = onClear)
            Pill("Zwiń", false, onClick = onClose)
        }
    }

    RytmCard {
        CardHeader("Plan na dziś") {
            if (planChanged) Chip("Przywróć plan", ChipTone.MUTED, onClick = onResetPlan)
        }
        Text(plan.title, style = Rytm.type.bodyStrong, color = Rytm.colors.ink)
        plan.workout?.exercises?.takeIf { it.isNotEmpty() }?.let { ex ->
            SmallText(ex.joinToString(" · ") { "${it.name} ${it.sets}×${it.reps.last}" })
        }
        SmallText(
            if (planChanged) "Zmienione w tej rozmowie. „Przywróć plan” wraca do planu z reguł."
            else "Napisz, ile masz czasu albo co boli, a trener od razu ułoży dzisiejszy plan na nowo.",
        )
    }

    if (!hasKey) {
        RytmCard { SmallText("Trener potrzebuje klucza AI. Dodasz go w zakładce Wieczór.", color = Rytm.colors.warn) }
    }

    if (messages.isEmpty()) {
        SmallText("Na przykład:")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SUGGESTIONS.forEach { s -> Pill(s, false) { if (!running && hasKey) onSend(s) } }
        }
    }

    messages.forEach { Bubble(it) }
    if (running) SmallText("Trener pisze…")

    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(1000) },
        label = { Text("Napisz do trenera") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
    )
    RytmButton(
        if (running) "Czekam na odpowiedź…" else "Wyślij",
        Modifier.fillMaxWidth(),
        primary = true,
        enabled = !running && text.isNotBlank() && hasKey,
    ) {
        onSend(text)
        text = ""
    }
    SmallText("Trener widzi Twój plan, gotowość, check-in, ostatnie serie i jedzenie z dziś. Każda wiadomość liczy się do miesięcznego budżetu AI.")
}

@Composable
private fun Bubble(m: ChatMessage) {
    val c = Rytm.colors
    val mine = m.role == CoachChatService.USER
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .background(if (mine) c.accentSoft else c.surface, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(m.text, style = Rytm.type.body, color = if (m.error) c.warn else c.ink)
            if (m.changedPlan) SmallText("✓ Plan na dziś zmieniony", color = c.good)
        }
    }
}
