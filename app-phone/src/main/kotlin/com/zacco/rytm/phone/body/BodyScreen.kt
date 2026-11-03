package com.zacco.rytm.phone.body

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zacco.rytm.domain.coach.BodySnapshot
import com.zacco.rytm.domain.coach.Macro
import com.zacco.rytm.domain.coach.WeightPace
import com.zacco.rytm.domain.nutrition.NutritionSuggestion
import com.zacco.rytm.phone.app.Format
import com.zacco.rytm.phone.ui.components.CardHeader
import com.zacco.rytm.phone.ui.components.Pill
import com.zacco.rytm.phone.ui.components.Chip
import com.zacco.rytm.phone.ui.components.ChipTone
import com.zacco.rytm.phone.ui.components.RytmCard
import com.zacco.rytm.phone.ui.components.ScreenHeader
import com.zacco.rytm.phone.ui.components.SmallText
import com.zacco.rytm.phone.ui.components.WhyBox
import com.zacco.rytm.phone.ui.theme.Rytm
import java.time.temporal.ChronoUnit

@Composable
fun BodyScreen(b: BodySnapshot, kcalTarget: Int, onAddWeighIn: () -> Unit) {
    val (paceLabel, paceTone) = when (b.pace) {
        WeightPace.UNKNOWN -> "za mało ważeń" to ChipTone.MUTED
        WeightPace.OK -> "Tempo OK" to ChipTone.GOOD
        WeightPace.TOO_FAST -> "Za szybko w dół" to ChipTone.WARN
        WeightPace.GAINING -> "Waga rośnie" to ChipTone.WARN
    }
    ScreenHeader(sub = "Rekompozycja · ostatnie 4 tygodnie", title = "Sylwetka") { Chip(paceLabel, paceTone) }
    WeightCard(b, onAddWeighIn)
    MacroCard(b)
    RytmCard {
        CardHeader("Siła")
        if (b.strength.isEmpty()) {
            SmallText("Zapisz pierwszy trening (na zegarku albo „Zapisz trening” w planie na dziś). Rytm dobierze z tego ciężar na kolejny raz i oceni, czy rekompozycja idzie dobrze: waga stoi albo lekko spada, a powtórzenia rosną.")
        } else {
            b.strength.forEach { e ->
                Column {
                    Text(e.exercise, style = Rytm.type.body, color = Rytm.colors.ink)
                    val arrow = when (e.improved) {
                        true -> " ↑"
                        false -> " ="
                        null -> ""
                    }
                    SmallText(
                        Format.dayMonth(e.lastDate) + ": " + e.last + arrow + (e.previous?.let { " (wcześniej $it)" } ?: ""),
                    )
                }
            }
        }
    }
    RytmCard {
        CardHeader("Skład ciała") {
            if (b.latestBodyFatPct == null && b.latestComposition == null) Chip("brak pomiaru", ChipTone.MUTED)
        }
        b.latestBodyFatPct?.let { SmallText("Tkanka tłuszczowa: ${Format.dec(it)}%.") }
        val comp = b.latestComposition
        val c = comp?.composition
        if (comp != null && c != null) {
            SmallText("Pomiar z ${Format.dayMonth(comp.date)}: " + com.zacco.rytm.phone.today.compositionSummary(c) + ".")
        }
        SmallText(
            if (b.latestBodyFatPct == null && comp == null) {
                "Dodaj ważenie i wczytaj zrzut ekranu z Xiaomi Home, żeby zapisać mięśnie, wodę, tłuszcz trzewny i resztę pomiarów."
            } else {
                "Pomiary z wagi łazienkowej mocno się wahają, więc patrzymy na trend z miesięcy, nie na pojedynczy wynik."
            },
        )
    }
}

@Composable
private fun WeightCard(b: BodySnapshot, onAddWeighIn: () -> Unit) {
    val c = Rytm.colors
    RytmCard {
        CardHeader("Waga · średnia 7 dni") { Chip("+ Ważenie", ChipTone.ACCENT, onClick = onAddWeighIn) }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(b.currentAvg7?.let { Format.dec(it) + " kg" } ?: "–", style = Rytm.type.bigNumber, color = c.ink)
            b.kgPerWeek?.let { Chip(Format.signedKg(it) + " kg / tydz.", if (b.pace == WeightPace.OK) ChipTone.GOOD else ChipTone.WARN) }
        }
        if (b.weighIns.isEmpty()) {
            SmallText("Brak ważeń z ostatnich 4 tygodni. Dodaj wynik z Xiaomi Home przyciskiem „+ Ważenie”.")
            return@RytmCard
        }
        val values = b.weighIns.map { it.second } + b.weightAvg7.map { it.second }
        val lo = (values.min() - 0.3).toFloat()
        val hi = (values.max() + 0.3).toFloat()
        val days = ChronoUnit.DAYS.between(b.from, b.to).toFloat().coerceAtLeast(1f)
        Canvas(Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = "Waga, średnia 7-dniowa z 4 tygodni" }) {
            fun x(d: java.time.LocalDate) = size.width * (ChronoUnit.DAYS.between(b.from, d) / days)
            fun y(v: Double) = size.height * (1f - ((v.toFloat() - lo) / (hi - lo)))
            // Pojedyncze ważenia: blade kropki.
            b.weighIns.forEach { (d, v) -> drawCircle(c.muted.copy(alpha = 0.3f), 2.dp.toPx(), Offset(x(d), y(v))) }
            if (b.weightAvg7.size >= 2) {
                val line = Path()
                b.weightAvg7.forEachIndexed { i, (d, v) -> if (i == 0) line.moveTo(x(d), y(v)) else line.lineTo(x(d), y(v)) }
                val area = Path().apply {
                    addPath(line)
                    lineTo(x(b.weightAvg7.last().first), size.height)
                    lineTo(x(b.weightAvg7.first().first), size.height)
                    close()
                }
                drawPath(area, Brush.verticalGradient(listOf(c.accent.copy(alpha = 0.18f), c.accent.copy(alpha = 0f))))
                drawPath(line, c.accent, style = Stroke(2.4.dp.toPx(), join = StrokeJoin.Round))
            }
            b.weightAvg7.lastOrNull()?.let { (d, v) ->
                drawCircle(c.surface, 6.5.dp.toPx(), Offset(x(d), y(v)))
                drawCircle(c.accent, 4.5.dp.toPx(), Offset(x(d), y(v)))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SmallText(Format.dayMonth(b.from))
            SmallText(Format.dayMonth(b.to))
        }
        SmallText("Punkty to pojedyncze ważenia; decyzje opierają się tylko na linii średniej.")
    }
}

@Composable
private fun MacroCard(b: BodySnapshot) {
    val c = Rytm.colors
    var macro by rememberSaveable { mutableStateOf(Macro.KCAL) }
    val last7 = (6 downTo 0).map { b.to.minusDays(it.toLong()) }
    val byDate = b.macros.associateBy { it.date }
    val unit = if (macro == Macro.KCAL) " kcal" else " g"
    RytmCard {
        CardHeader("Makro") { SmallText("ostatnie 7 dni") }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Macro.entries.forEach { m -> Pill(macroLabel(m), m == macro) { macro = m } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            last7.forEach { d ->
                val day = byDate[d]
                val v = day?.actual(macro) ?: 0.0
                val t = day?.target(macro)?.toDouble() ?: 1.0
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Canvas(Modifier.width(18.dp).height(56.dp)) {
                        val h = size.height * (v / (t * 1.25)).toFloat().coerceIn(0f, 1f)
                        val color = when {
                            day == null || v <= 0 -> c.track
                            day.inTarget(macro) -> c.good
                            else -> c.warn
                        }
                        val hh = if (day == null || v <= 0) 3.dp.toPx() else h
                        drawRoundRect(color, Offset(0f, size.height - hh), Size(size.width, hh), CornerRadius(4.dp.toPx()))
                        // kreska celu
                        val ty = size.height - size.height / 1.25f
                        drawLine(c.muted, Offset(-2.dp.toPx(), ty), Offset(size.width + 2.dp.toPx(), ty), 1.dp.toPx())
                    }
                    Text(Format.weekdayShort(d.dayOfWeek), style = Rytm.type.tiny, color = c.muted)
                }
            }
        }
        val week = b.macros.filter { it.date in last7 }
        if (week.isEmpty()) {
            SmallText("Brak wpisów żywienia z ostatniego tygodnia. Dane przychodzą z Fitatu przez Health Connect albo z ręcznego wpisu.")
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Macro.entries.forEach { m ->
                    val avg = week.map { it.actual(m) }.average()
                    val target = week.map { it.target(m) }.average()
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(macroLabel(m), style = Rytm.type.tiny, color = c.muted)
                        Text(Format.roundInt(avg).toString(), style = Rytm.type.bodyStrong, color = if (m == macro) c.accent else c.ink)
                        Text("/ ${Format.roundInt(target)}", style = Rytm.type.tiny, color = c.muted)
                    }
                }
            }
            SmallText(
                "${macroLabel(macro)}: ${week.count { it.inTarget(macro) }} z ${week.size} dni w celu. Kreska to cel dnia, z doliczonym treningiem" +
                    (if (macro == Macro.PROTEIN) "; białko liczy się od 90% celu." else "; w celu znaczy ±10%."),
            )
        }
        val tips = b.weekReport.suggestions.mapNotNull {
            when (it) {
                NutritionSuggestion.PROTEIN_LOW -> "W tym tygodniu białko częściej poniżej celu. Dorzuć porcję skyru albo odżywki."
                NutritionSuggestion.INCOMPLETE_LOGGING -> null
                NutritionSuggestion.INCREASE_CALORIES -> "Waga spada za szybko, a siła słabnie: rozważ +150 kcal dziennie."
                NutritionSuggestion.RECOMPOSITION_ON_TRACK -> "Waga stoi, a siła rośnie: rekompozycja idzie dobrze."
            }
        }
        if (tips.isNotEmpty()) WhyBox(tips.joinToString(" "))
    }
}

private fun macroLabel(m: Macro) = when (m) {
    Macro.KCAL -> "Kcal"
    Macro.PROTEIN -> "Białko"
    Macro.FAT -> "Tłuszcz"
    Macro.CARBS -> "Węgle"
}
