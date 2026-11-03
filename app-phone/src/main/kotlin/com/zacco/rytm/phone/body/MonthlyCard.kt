package com.zacco.rytm.phone.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zacco.rytm.ai.SavedMonthlyReport
import com.zacco.rytm.phone.app.Format
import com.zacco.rytm.phone.ui.components.CardHeader
import com.zacco.rytm.phone.ui.components.Chip
import com.zacco.rytm.phone.ui.components.ChipTone
import com.zacco.rytm.phone.ui.components.RytmButton
import com.zacco.rytm.phone.ui.components.RytmCard
import com.zacco.rytm.phone.ui.components.SmallText
import com.zacco.rytm.phone.ui.components.WhyBox
import com.zacco.rytm.phone.ui.theme.Rytm
import java.time.LocalDate
import java.util.Locale

/** Analiza długoterminowa (raz w miesiącu): trendy z ok. 12 tygodni, co działa, co hamuje, priorytety. */
@Composable
internal fun MonthlyReportCard(saved: SavedMonthlyReport?, running: Boolean, onRun: () -> Unit) {
    val c = Rytm.colors
    var expanded by remember(saved?.createdAt) { mutableStateOf(false) }
    RytmCard {
        CardHeader("Analiza miesiąca") {
            if (saved != null) {
                val end = runCatching { LocalDate.parse(saved.periodEnd) }.getOrNull()
                Chip(
                    (if (saved.source == "LLM") "AI" else "reguły") + (end?.let { " · do " + Format.dayMonth(it) } ?: "") + if (expanded) " ▴" else " ▾",
                    if (saved.source == "LLM") ChipTone.GOOD else ChipTone.MUTED,
                ) { expanded = !expanded }
            }
        }
        if (saved == null) {
            SmallText("Raz w miesiącu (1. dnia o 19:00) Rytm patrzy na ostatnie 12 tygodni: waga, siła, sen, tętno, obciążenie i żywienie. Ocenia tempo względem celu i daje 1–3 priorytety.")
        } else {
            val r = saved.report
            Text(r.summary, style = Rytm.type.small, color = c.ink)
            if (expanded) {
                Section("Trendy", r.trends)
                Section("Co działa", r.working)
                Section("Co hamuje", r.blockers)
                Text("Cel", style = Rytm.type.bodyStrong, color = c.ink)
                SmallText(r.goalCheck)
                Text("Priorytety na miesiąc", style = Rytm.type.bodyStrong, color = c.ink)
                r.nextMonth.forEachIndexed { i, t -> WhyBox("${i + 1}. $t") }
                saved.error?.let { SmallText("AI niedostępne, analiza z reguł: $it", color = c.warn) }
                if (saved.costUsd > 0) SmallText("Koszt: %.3f USD".format(Locale.ROOT, saved.costUsd))
            }
        }
        RytmButton(
            if (running) "Analizuję…" else if (saved == null) "Zrób analizę teraz" else "Przelicz analizę",
            Modifier.fillMaxWidth(),
            enabled = !running,
            onClick = onRun,
        )
    }
}

@Composable
private fun Section(title: String, items: List<String>) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = Rytm.type.bodyStrong, color = Rytm.colors.ink)
        items.forEach { SmallText("• $it") }
    }
}
