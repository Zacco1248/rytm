package com.zacco.rytm.phone.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.zacco.rytm.ai.morning.MorningNote
import com.zacco.rytm.domain.model.PlannedDay
import com.zacco.rytm.phone.app.Format
import com.zacco.rytm.phone.ui.components.ButtonRow
import com.zacco.rytm.phone.ui.components.CardLabel
import com.zacco.rytm.phone.ui.components.IconCircleButton
import com.zacco.rytm.phone.ui.components.RytmButton
import com.zacco.rytm.phone.ui.components.RytmCard
import com.zacco.rytm.phone.ui.components.RytmIcons
import com.zacco.rytm.phone.ui.components.SmallText
import com.zacco.rytm.phone.ui.theme.Rytm
import java.time.LocalDate

/** Co pokazać na ekranie treningu; akcje tylko dla dzisiejszego dnia. */
data class PlanPreviewUi(
    val plan: PlannedDay,
    val today: LocalDate,
    /** Notatka trenera do tej sesji: poranna (AI albo reguły) albo z planu tygodnia. */
    val coachNote: String?,
    val tip: String? = null,
    val dayOff: Boolean = false,
    val planChanged: Boolean = false,
    val setsToday: Int = 0,
    val phoneRunActive: Boolean = false,
)

/**
 * Ekran treningu przed startem: notatka od trenera, lista ćwiczeń z seriami i ciężarem, start na telefonie.
 * Z „Dziś” i z „Tydzień” (dla innych dni tylko podgląd).
 */
@Composable
fun PlanPreviewScreen(
    ui: PlanPreviewUi,
    onClose: () -> Unit,
    onStart: () -> Unit,
    onLogWorkout: () -> Unit,
    onDayOff: () -> Unit,
    onCoach: () -> Unit,
    onResetPlan: () -> Unit,
) {
    val c = Rytm.colors
    val p = ui.plan
    val isToday = p.date == ui.today
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconCircleButton(RytmIcons.back, "Wróć", onClick = onClose)
        Column(Modifier.weight(1f)) {
            SmallText(
                listOfNotNull(
                    Format.dayShortLabel(p.date, ui.today),
                    Format.intensity(p.intensity),
                    p.workout?.let { "${it.exercises.size} ćwiczeń" },
                ).joinToString(" · "),
            )
            Text(p.title, style = Rytm.type.screenTitle, color = c.ink)
        }
    }

    val note = ui.coachNote ?: p.rationale.takeIf { it.isNotEmpty() }?.joinToString(" ")
    if (note != null) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(c.accentSoft)
                .border(1.dp, c.accent.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CardLabel("Od trenera")
            Text(note, style = Rytm.type.body, color = c.ink)
            ui.tip?.let { Text(it, style = Rytm.type.small, color = c.ink) }
        }
    }
    p.warnings.forEach { SmallText("⚠ $it", color = c.warn) }

    p.workout?.let { w ->
        RytmCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp), spacing = 0.dp) {
            WorkoutList(w)
        }
        SmallText("Dotknij ćwiczenia, żeby zobaczyć opis. Po każdej serii powiesz, jak było (lekko, w sam raz, ciężko), a Rytm dobierze ciężar.")
        if (w.exercises.size > 3 && w.name != "Mobilność") {
            SmallText("Mało czasu? Zrób 3 pierwsze ćwiczenia po 2 serie (ok. 20 min). Plan tygodnia się dostosuje.")
        }
    }
    p.cardio?.let { cs ->
        RytmCard {
            CardLabel("Przebieg")
            cs.steps.forEachIndexed { i, step -> Text("${i + 1}. $step", style = Rytm.type.body, color = c.ink) }
        }
        SmallText("Tętno i kcal przyjdą z zegarka albo opaski przez Health Connect.")
    }

    if (!isToday) {
        SmallText("To podgląd. Plan może się jeszcze zmienić po porannym check-inie.")
        return
    }
    if (ui.setsToday > 0) SmallText("✓ Zapisano ${ui.setsToday} serii", color = c.good)
    if (p.workout != null) {
        RytmButton(if (ui.phoneRunActive) "Wróć do treningu" else "Zacznij na telefonie", Modifier.fillMaxWidth(), primary = true, onClick = onStart)
        SmallText("Na zegarku z Wear OS to samo daje „Zacznij trening”.")
        RytmButton(if (ui.setsToday == 0) "Wpisz trening po fakcie" else "Wpisz kolejny trening", Modifier.fillMaxWidth(), onClick = onLogWorkout)
    }
    ButtonRow {
        RytmButton("Zmień z trenerem", Modifier.weight(1f), onClick = onCoach)
        RytmButton(if (ui.dayOff) "Cofnij wolne" else "Dziś wolne", Modifier.weight(1f), onClick = onDayOff)
    }
    if (ui.planChanged) RytmButton("Przywróć plan sprzed rozmowy", Modifier.fillMaxWidth(), onClick = onResetPlan)
}
