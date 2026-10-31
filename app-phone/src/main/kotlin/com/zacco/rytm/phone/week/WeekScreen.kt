package com.zacco.rytm.phone.week

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.zacco.rytm.domain.coach.WeekDay
import com.zacco.rytm.domain.coach.WeekSnapshot
import com.zacco.rytm.domain.load.AcwrZone
import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.model.DayKind
import com.zacco.rytm.phone.app.CoachUiState
import com.zacco.rytm.phone.app.Format
import com.zacco.rytm.phone.ui.components.ButtonRow
import com.zacco.rytm.phone.ui.components.CardHeader
import com.zacco.rytm.phone.ui.components.Chip
import com.zacco.rytm.phone.ui.components.ChipTone
import com.zacco.rytm.phone.ui.components.Divider
import com.zacco.rytm.phone.ui.components.RytmButton
import com.zacco.rytm.phone.ui.components.RytmCard
import com.zacco.rytm.phone.ui.components.ScreenHeader
import com.zacco.rytm.phone.ui.components.SmallText
import com.zacco.rytm.phone.ui.theme.Rytm
import com.zacco.rytm.phone.ui.theme.RytmColors
import java.time.LocalDate

@Composable
fun WeekScreen(state: CoachUiState, onToggleCancelled: (LocalDate) -> Unit) {
    val w = state.week
    ScreenHeader(sub = Format.range(w.monday, w.monday.plusDays(6)), title = "Tydzień") {
        Chip("${w.strengthDone} / ${w.strengthRequired} siła" + if (w.strengthDone >= w.strengthRequired) " ✓" else "",
            if (w.strengthDone >= w.strengthRequired) ChipTone.GOOD else ChipTone.MUTED)
    }
    LoadCard(w)
    AcwrCard(w)
    PlanCard(state, onToggleCancelled)
}

// --- obciążenie ---

private fun RytmColors.forType(t: ActivityType): Color = when (t) {
    ActivityType.STRENGTH -> kindStrength
    ActivityType.VOLLEYBALL, ActivityType.SQUASH -> kindSport
    ActivityType.PERSONAL_TRAINING -> kindPt
    ActivityType.MOBILITY, ActivityType.WALK -> kindMobility
    ActivityType.CARDIO, ActivityType.OTHER -> kindOther
}

private fun RytmColors.forKind(k: DayKind): Color = when (k) {
    DayKind.STRENGTH -> kindStrength
    DayKind.FIXED_SPORT -> kindSport
    DayKind.PERSONAL_TRAINING -> kindPt
    DayKind.MOBILITY -> kindMobility
    DayKind.REST -> kindRest
    DayKind.FLEX -> kindOther
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoadCard(w: WeekSnapshot) {
    val c = Rytm.colors
    val max = (w.days.maxOfOrNull { maxOf(it.actualLoad, it.plannedLoad) } ?: 0).coerceAtLeast(300).toFloat()
    RytmCard {
        CardHeader("Obciążenie") { SmallText("sRPE = RPE × min") }
        Row(
            Modifier.fillMaxWidth().semantics { contentDescription = "Obciążenie dzienne sRPE w tym tygodniu" },
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            w.days.forEach { d -> LoadBar(d, max, d.date == w.today) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Legend(c.kindStrength, "siła")
            Legend(c.kindSport, "mecz")
            Legend(c.kindPt, "personalny")
            Legend(c.kindMobility, "mobilność")
            Legend(c.kindOther, "inne")
        }
    }
}

@Composable
private fun LoadBar(d: WeekDay, max: Float, today: Boolean) {
    val c = Rytm.colors
    val planned = d.actualLoad == 0 && d.plannedLoad > 0
    val value = if (planned) d.plannedLoad else d.actualLoad
    val barHeight = 92.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(if (value > 0) (if (planned) "~" else "") + Format.int(value) else "", style = Rytm.type.tiny, color = c.muted)
        Canvas(Modifier.width(30.dp).height(barHeight)) {
            val r = CornerRadius(5.dp.toPx())
            if (planned) {
                val h = size.height * (d.plannedLoad / max)
                drawRoundRect(c.forKind(d.plan.kind).copy(alpha = 0.35f), Offset(0f, size.height - h), Size(size.width, h), r)
            } else {
                // Słupek składany z rodzajów aktywności tego dnia.
                var top = size.height
                d.loadByType.entries.sortedBy { it.key.ordinal }.forEach { (type, load) ->
                    val h = size.height * (load / max)
                    top -= h
                    drawRoundRect(c.forType(type), Offset(0f, top), Size(size.width, h), r)
                }
            }
            drawLine(c.line, Offset(-8f, size.height), Offset(size.width + 8f, size.height), 1.dp.toPx())
        }
        Text(
            Format.weekdayShort(d.date.dayOfWeek),
            style = Rytm.type.tiny,
            color = if (today) c.ink else c.muted,
        )
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text("  $text", style = Rytm.type.tiny.copy(fontSize = Rytm.type.chip.fontSize), color = Rytm.colors.muted)
    }
}

// --- ACWR ---

@Composable
private fun AcwrCard(w: WeekSnapshot) {
    val c = Rytm.colors
    val load = w.load
    val (label, tone) = when (load.zone) {
        AcwrZone.UNKNOWN -> "za mało danych" to ChipTone.MUTED
        AcwrZone.LOW -> "niskie" to ChipTone.ACCENT
        AcwrZone.SAFE -> "bezpiecznie" to ChipTone.GOOD
        AcwrZone.ELEVATED -> "podwyższone" to ChipTone.WARN
        AcwrZone.HIGH -> "wysokie" to ChipTone.BAD
    }
    RytmCard {
        CardHeader("ACWR") {
            Chip((load.acwr?.let { Format.dec(it, 2) + " · " } ?: "") + label, tone)
        }
        val lo = 0.5f
        val hi = 2.0f
        fun frac(v: Float) = ((v - lo) / (hi - lo)).coerceIn(0f, 1f)
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            val y = (size.height - 10.dp.toPx()) / 2
            val h = 10.dp.toPx()
            val r = CornerRadius(6.dp.toPx())
            drawRoundRect(c.track, Offset(0f, y), Size(size.width, h), r)
            fun zone(from: Float, to: Float, color: Color) {
                drawRect(color.copy(alpha = 0.2f), Offset(size.width * frac(from), y), Size(size.width * (frac(to) - frac(from)), h))
            }
            zone(0.8f, 1.3f, c.good)
            zone(1.3f, 1.5f, c.warn)
            zone(1.5f, 2.0f, c.bad)
            load.acwr?.let {
                val x = size.width * frac(it.toFloat())
                drawRoundRect(c.ink, Offset(x - 2.dp.toPx(), 0f), Size(4.dp.toPx(), size.height), CornerRadius(2.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0,5", "0,8", "1,3", "1,5", "2,0").forEach { SmallText(it) }
        }
        SmallText(
            "Ostre ${Format.int(load.acute7d)} (7 dni) / przewlekłe ${Format.int(load.chronicWeeklyAvg)} (śr. tyg. z 28 dni)." +
                if (load.reliable) " Powyżej 1,5 planer wymusi lżejsze dni." else " Wiarygodne po 4 tygodniach danych.",
        )
    }
}

// --- plan ---

@Composable
private fun PlanCard(state: CoachUiState, onToggleCancelled: (LocalDate) -> Unit) {
    val w = state.week
    RytmCard {
        CardHeader("Plan")
        Column {
            w.days.forEachIndexed { i, d ->
                if (i > 0 && d.date != w.today && w.days[i - 1].date != w.today) Divider()
                DayRow(d, d.date == w.today, d.date < w.today)
            }
        }
        ButtonRow {
            val match = state.nextMatch
            val pt = state.nextPersonal
            RytmButton(
                if (match != null && match in state.cancelled) "Przywróć mecz" else "Mecz odwołany",
                Modifier.weight(1f),
                enabled = match != null,
            ) { match?.let(onToggleCancelled) }
            RytmButton(
                if (pt != null && pt in state.cancelled) "Przywróć personalny" else "Personalny odwołany",
                Modifier.weight(1f),
                enabled = pt != null,
            ) { pt?.let(onToggleCancelled) }
        }
        state.personalTrainingUntil?.let { until ->
            if (!w.today.isAfter(until)) SmallText("Treningi personalne do ${Format.dayMonth(until)}. Potem piątek wraca do planu: siła B · dół + core.")
        }
    }
}

@Composable
private fun DayRow(d: WeekDay, today: Boolean, past: Boolean) {
    val c = Rytm.colors
    val done = d.activities.isNotEmpty()
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (today) Modifier.clip(RoundedCornerShape(12.dp)).background(c.accentSoft) else Modifier)
            .padding(horizontal = if (today) 10.dp else 0.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${d.date.dayOfMonth}", style = Rytm.type.dayNumber, color = c.ink)
            Text(Format.weekdayShort(d.date.dayOfWeek).uppercase(), style = Rytm.type.tiny, color = c.muted)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(c.forKind(d.plan.kind)))
                Text("  ", style = Rytm.type.bodyStrong)
                val title = when {
                    done -> d.activities.joinToString(" + ") { it.notes?.takeIf { n -> it.type == ActivityType.OTHER && n.isNotBlank() } ?: Format.activity(it.type) }
                    else -> d.plan.title
                }
                Text(
                    title,
                    style = Rytm.type.bodyStrong,
                    color = if (past && !done) c.muted else c.ink,
                    textDecoration = if (past && !done && d.plan.kind != DayKind.REST) TextDecoration.LineThrough else null,
                )
            }
            SmallText(subtitle(d, past))
        }
        Column(horizontalAlignment = Alignment.End) {
            when {
                done -> {
                    Text(Format.int(d.actualLoad), style = Rytm.type.bodyStrong, color = c.ink, textAlign = TextAlign.End)
                    SmallText("load")
                }
                d.plannedLoad > 0 -> SmallText("~" + Format.int(d.plannedLoad))
            }
        }
    }
}

private fun subtitle(d: WeekDay, past: Boolean): String {
    if (d.activities.isNotEmpty()) {
        return d.activities.joinToString(" · ") { a ->
            Format.minutes(a.durationMin) + (a.rpe?.let { " · RPE $it" } ?: " · bez RPE")
        }
    }
    val why = d.rationale()
    return when {
        d.cancelled -> "odwołane" + (why?.let { " · $it" } ?: "")
        past -> "nie odnotowano"
        else -> "plan · " + Format.intensity(d.plan.intensity).lowercase() + (why?.let { " · $it" } ?: "")
    }
}

private fun WeekDay.rationale(): String? = plan.rationale.firstOrNull()?.trimEnd('.')?.replaceFirstChar { it.lowercase() }
