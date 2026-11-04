package com.zacco.rytm.ai.weekly

import java.util.Locale
import kotlin.math.roundToInt

/** Raport bez LLM: te same pola, złożone z liczb tygodnia. */
object RuleBasedWeeklyReport {

    fun build(i: WeeklyInput): WeeklyReport {
        fun h(min: Int) = "%d h %02d min".format(Locale.ROOT, min / 60, min % 60)
        val summary = buildString {
            append("Tydzień ${i.weekStart} – ${i.weekEnd}: obciążenie ${i.loadWeek}")
            if (i.loadPrevWeek > 0) append(" (poprzedni ${i.loadPrevWeek})")
            append(", siła ${i.strengthSessions}/${i.strengthRequired}")
            if (i.cardioSessions > 0) append(", cardio ${i.cardioSessions}")
            append(".")
        }
        val trends = listOfNotNull(
            i.avgSleepMin?.let { s -> "Sen średnio ${h(s)}" + (i.avgSleepPrevWeekMin?.let { " (tydzień wcześniej ${h(it)})" } ?: "") + "." },
            i.avgSteps?.let { "Kroki średnio $it dziennie (cel ${i.stepsTarget})." },
            i.avgKcal?.let { "Jedzenie średnio $it kcal (cel ${i.kcalTarget}), białko ${i.avgProteinG ?: "–"} g (cel ${i.proteinTarget})." },
        )
        val correlations = buildList {
            val match = i.days.filter { it.hadMatch }.mapNotNull { it.sleepMin }
            val other = i.days.filter { !it.hadMatch }.mapNotNull { it.sleepMin }
            if (match.isNotEmpty() && other.size >= 2) {
                val diff = (match.average() - other.average()).roundToInt()
                if (kotlin.math.abs(diff) >= 20) add("W noce po dniach ze sportem sen był ${if (diff < 0) "krótszy" else "dłuższy"} o ok. ${kotlin.math.abs(diff)} min.")
            }
            val drinks = i.days.filter { it.alcohol > 0 }
            if (drinks.isNotEmpty()) add("Alkohol w ${drinks.size} ${if (drinks.size == 1) "dzień" else "dni"}; następnego dnia gotowość jest obniżana.")
        }
        val body = listOfNotNull(
            i.weightAvg7?.let { "Waga (średnia 7 dni) ${"%.1f".format(Locale.ROOT, it)} kg" + (i.weightChangeKg?.let { c -> ", zmiana ${"%+.1f".format(Locale.ROOT, c)} kg w tydzień" } ?: "") + "." },
            when (i.strengthTrend) {
                "UP" -> "Siła rośnie."
                "DOWN" -> "Siła lekko spada: sprawdź sen i jedzenie."
                "FLAT" -> "Siła stoi w miejscu."
                else -> "Za mało serii, żeby ocenić siłę."
            },
        ).joinToString(" ")
        val suggestions = buildList {
            if ("PROTEIN_LOW" in i.nutritionFlags) add("Białko częściej poniżej celu: dodaj jedną porcję białka dziennie (skyr, twaróg, odżywka).")
            if ("INCREASE_CALORIES" in i.nutritionFlags) add("Waga spada za szybko, a siła słabnie: rozważ +150 kcal dziennie.")
            if (i.strengthSessions < i.strengthRequired) add("Zabrakło ${i.strengthRequired - i.strengthSessions} sesji siłowych: w przyszłym tygodniu zaplanuj je na początek tygodnia.")
            if (i.avgSleepMin != null && i.avgSleepMin < 420) add("Sen poniżej 7 h: kładź się 20–30 min wcześniej, najlepiej o stałej porze.")
            if (i.nutritionDaysLogged < 5) add("Zapisuj jedzenie przez co najmniej 5 dni, żeby wnioski o żywieniu były pewne.")
            if (isEmpty()) add("Dobry tydzień: utrzymaj rytm i zwiększ ciężar tam, gdzie wpada górny zakres powtórzeń.")
        }.take(3)
        val wins = buildList {
            if (i.strengthRequired > 0 && i.strengthSessions >= i.strengthRequired) add("Wszystkie sesje siłowe z planu: ${i.strengthSessions}/${i.strengthRequired}.")
            if (i.avgSleepMin != null && i.avgSleepMin >= 420) add("Sen średnio ${h(i.avgSleepMin)}, w normie.")
            if (i.avgSteps != null && i.stepsTarget > 0 && i.avgSteps >= i.stepsTarget) add("Kroki średnio ${i.avgSteps} dziennie, powyżej celu.")
            if (i.nutritionDaysLogged >= 5 && i.proteinDaysBelowTarget <= 1) add("Białko w celu prawie codziennie.")
            if (i.strengthTrend == "UP") add("Siła rośnie.")
        }.take(3).ifEmpty { listOf("Tydzień zapisany: z tych danych Rytm lepiej dopasuje plan.") }
        val misses = buildList {
            if (i.strengthSessions < i.strengthRequired) add("Siła ${i.strengthSessions}/${i.strengthRequired}: zabrakło ${i.strengthRequired - i.strengthSessions}.")
            if (i.avgSleepMin != null && i.avgSleepMin < 420) add("Sen średnio ${h(i.avgSleepMin)}, poniżej 7 h.")
            if (i.nutritionDaysLogged >= 3 && i.proteinDaysBelowTarget >= 3) add("Białko poniżej celu w ${i.proteinDaysBelowTarget} dni.")
            if (i.avgSteps != null && i.stepsTarget > 0 && i.avgSteps < i.stepsTarget * 0.8) add("Kroki średnio ${i.avgSteps}, cel ${i.stepsTarget}.")
        }.take(3)
        val nextWeek = buildString {
            val key = i.nextWeekPlan.filterNot { it.substringAfter(": ").lowercase().let { t -> t.startsWith("odpoczynek") || t.startsWith("regeneracja") } }
            if (key.isNotEmpty()) append("W planie: ${key.joinToString("; ")}.")
            i.nextWeekDeload?.let { append(" $it") }
            i.goals.firstOrNull()?.let { append(" Cel: $it") }
        }.trim().ifBlank { "Plan na kolejny tydzień pojawi się w zakładce Tydzień." }
        return WeeklyReport(summary, trends, correlations.take(4), body.ifBlank { "Brak danych o sylwetce." }, suggestions, wins, misses, nextWeek)
    }
}
