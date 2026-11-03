package com.zacco.rytm.domain.coach

import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.model.DayKind
import com.zacco.rytm.domain.readiness.ReadinessFlag
import com.zacco.rytm.domain.readiness.ReadinessLevel
import java.time.LocalTime
import kotlin.math.roundToInt

/** Rodzaje podpowiedzi w ciągu dnia; każdy można wyłączyć osobno. */
enum class NudgeKind(val label: String, val hint: String) {
    SLEEP("Po krótkiej nocy", "rano, gdy sen był krótki albo gotowość niska"),
    WORKOUT("Trening z planu", "ok. 17–20, gdy trening z planu jeszcze nie zrobiony"),
    MOVE("Brak ruchu", "po południu, po 3 dniach bez aktywności i przy małej liczbie kroków"),
    PROTEIN("Mało białka", "wieczorem, gdy w Fitatu brakuje sporo białka do celu"),
}

data class Nudge(val kind: NudgeKind, val title: String, val text: String)

/**
 * Krótkie, rzadkie podpowiedzi z reguł (bez AI, bez kosztu). Najwyżej [MAX_PER_DAY] dziennie,
 * każdy rodzaj raz dziennie, cisza nocna 22–7.
 */
object Nudges {
    const val MAX_PER_DAY = 2
    private const val MOVE_DAYS = 3L
    private const val LOW_STEPS = 4000
    private const val PROTEIN_SHARE = 0.75

    /** Pierwsza podpowiedź, która pasuje teraz; null, gdy nic albo limit dnia. */
    fun pick(
        data: CoachData,
        s: TodaySnapshot,
        now: LocalTime,
        enabled: Set<NudgeKind>,
        sentToday: Set<NudgeKind>,
    ): Nudge? {
        if (now.hour < 7 || now.hour >= 22) return null
        if (sentToday.size >= MAX_PER_DAY) return null
        val candidates = NudgeKind.entries.filter { it in enabled && it !in sentToday }
        return candidates.firstNotNullOfOrNull { kind ->
            when (kind) {
                NudgeKind.SLEEP -> sleep(s, now)
                NudgeKind.WORKOUT -> workout(s, now)
                NudgeKind.MOVE -> move(data, s, now)
                NudgeKind.PROTEIN -> protein(s, now)
            }
        }
    }

    private fun sleep(s: TodaySnapshot, now: LocalTime): Nudge? {
        if (now.hour !in 7..11) return null
        val short = ReadinessFlag.SHORT_SLEEP in s.readiness.flags
        val low = s.readiness.level == ReadinessLevel.LOW
        if (!short && !low) return null
        val sleep = s.night?.sleepMin?.let { " (${it / 60} h ${it % 60} min)" } ?: ""
        val plan = s.plan.title.replaceFirstChar { it.lowercase() }
        return Nudge(
            NudgeKind.SLEEP,
            if (low) "Dziś lżej" else "Krótka noc",
            (if (short) "Krótka noc$sleep. " else "Gotowość ${s.readiness.score}. ") +
                "Plan na dziś: $plan. Pij więcej wody i nie dokładaj kofeiny po 14:00.",
        )
    }

    private fun workout(s: TodaySnapshot, now: LocalTime): Nudge? {
        if (now.hour !in 17..19) return null
        if (s.dayOff || s.plan.workout == null || s.plan.kind != DayKind.STRENGTH) return null
        if (s.setsToday.isNotEmpty()) return null
        if (s.activitiesToday.any { it.type == ActivityType.STRENGTH || it.type == ActivityType.PERSONAL_TRAINING }) return null
        return Nudge(
            NudgeKind.WORKOUT,
            "Trening na dziś",
            "W planie: ${s.plan.title.replaceFirstChar { it.lowercase() }}. Mało czasu? Zapytaj trenera o krótszą wersję.",
        )
    }

    private fun move(data: CoachData, s: TodaySnapshot, now: LocalTime): Nudge? {
        if (now.hour !in 15..19) return null
        val from = data.today.minusDays(MOVE_DAYS - 1)
        if (data.activities.any { day(it) in from..data.today }) return null
        if ((s.steps ?: 0) >= LOW_STEPS) return null
        // Dopiero gdy mamy historię (nowa instalacja to nie „3 dni bez ruchu”).
        if (data.activities.none { day(it) < from } && data.metrics.none { it.date < from }) return null
        return Nudge(
            NudgeKind.MOVE,
            "Chwila ruchu?",
            "Od $MOVE_DAYS dni bez aktywności" + (s.steps?.let { ", dziś $it kroków" } ?: "") +
                ". Krótki spacer 20 minut poprawi sen i regenerację.",
        )
    }

    private fun day(a: com.zacco.rytm.domain.model.Activity) = a.start.atZone(com.zacco.rytm.domain.time.RytmTime.zone).toLocalDate()

    private fun protein(s: TodaySnapshot, now: LocalTime): Nudge? {
        if (now.hour < 19 || now.hour > 21) return null
        val n = s.nutrition ?: return null
        val target = s.nutritionTargets.proteinG
        if (target <= 0 || n.proteinG >= target * PROTEIN_SHARE) return null
        val missing = (target - n.proteinG).roundToInt()
        return Nudge(
            NudgeKind.PROTEIN,
            "Mało białka",
            "Do celu brakuje ok. $missing g białka. Szybko: twaróg 200 g (ok. 36 g), skyr 150 g (ok. 18 g) albo 3 jajka (ok. 19 g).",
        )
    }
}
