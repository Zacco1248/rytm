package com.zacco.rytm.ai.monthly

import com.zacco.rytm.ai.CoachPersona

import com.zacco.rytm.ai.CoachLlm
import com.zacco.rytm.ai.LlmException
import com.zacco.rytm.ai.LlmRequest
import com.zacco.rytm.ai.ModelTier
import com.zacco.rytm.ai.cost.CostGuard
import com.zacco.rytm.domain.coach.CoachData
import com.zacco.rytm.domain.load.LoadCalculator
import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.time.RytmTime
import com.zacco.rytm.domain.workout.SetLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/** Jeden tydzień w analizie długoterminowej (średnie i sumy). */
@Serializable
data class WeekAggregate(
    val weekStart: String,
    val load: Int,
    val strengthSessions: Int,
    val cardioSessions: Int,
    val sportSessions: Int,
    val avgSleepMin: Int?,
    val avgRestingHr: Int?,
    val avgHrv: Int?,
    val avgSteps: Int?,
    val avgEnergy: Double?,
    val avgKcal: Int?,
    val avgProteinG: Int?,
    val nutritionDays: Int,
    val weightAvgKg: Double?,
    val bodyFatPct: Double?,
    val alcoholDrinks: Int,
)

/** Wejście analizy miesięcznej: do 12 tygodni, postęp ćwiczeń i streszczenia raportów tygodniowych. */
@Serializable
data class MonthlyInput(
    val periodStart: String,
    val periodEnd: String,
    val goal: String,
    val goalNote: String?,
    val kcalTarget: Int,
    val proteinTarget: Int,
    val sleepTargetMin: Int,
    val weeks: List<WeekAggregate>,
    /** „ćwiczenie: najlepsza seria na początku → teraz”. */
    val strengthProgress: List<String>,
    val weeklySummaries: List<String>,
)

/** Analiza długoterminowa (raz w miesiącu). */
@Serializable
data class MonthlyReport(
    val summary: String,
    val trends: List<String>,
    /** Co działa i warto utrzymać. */
    val working: List<String>,
    /** Co hamuje postęp. */
    val blockers: List<String>,
    /** Ocena postępu względem celu. */
    val goalCheck: String,
    /** 1–3 priorytety na kolejny miesiąc. */
    val nextMonth: List<String>,
) {
    fun validate(): MonthlyReport {
        require(summary.isNotBlank() && goalCheck.isNotBlank()) { "puste pole" }
        require(nextMonth.size in 1..3) { "priorytety: ${nextMonth.size}" }
        require(trends.size <= 6 && working.size <= 4 && blockers.size <= 4) { "za długie listy" }
        return this
    }

    companion object {
        private fun list(desc: String) = mapOf("type" to "array", "items" to mapOf("type" to "string"), "description" to desc)

        val jsonSchema: Map<String, Any?> = mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "required" to listOf("summary", "trends", "working", "blockers", "goalCheck", "nextMonth"),
            "properties" to mapOf(
                "summary" to mapOf("type" to "string", "description" to "Ostatnie tygodnie w 2–4 zdaniach."),
                "trends" to list("Do 6 trendów z liczbami z kilku tygodni: waga, siła, sen, tętno, obciążenie, żywienie."),
                "working" to list("Do 4 rzeczy, które działają."),
                "blockers" to list("Do 4 rzeczy, które hamują postęp. Pusta lista, gdy brak."),
                "goalCheck" to mapOf("type" to "string", "description" to "Czy tempo zmian pasuje do celu, z liczbami."),
                "nextMonth" to list("1–3 priorytety na kolejny miesiąc."),
            ),
        )
    }
}

object MonthlyInputs {
    const val WEEKS = 12

    /** Składa wejście z danych kończących się [end]; [data] musi obejmować ok. [WEEKS] tygodni wstecz. */
    fun from(data: CoachData, end: LocalDate, weeklySummaries: List<String> = emptyList()): MonthlyInput {
        val loads = LoadCalculator.dailyLoads(data.activities)
        val firstData = listOfNotNull(
            data.metrics.minOfOrNull { it.date },
            data.activities.minOfOrNull { it.start.atZone(RytmTime.zone).toLocalDate() },
            data.weighIns.minOfOrNull { it.date },
        ).minOrNull() ?: end
        val weeks = (WEEKS - 1 downTo 0).map { back ->
            val to = end.minusDays(7L * back)
            val from = to.minusDays(6)
            from to to
        }.filter { (_, to) -> !to.isBefore(firstData) }

        val agg = weeks.map { (from, to) ->
            val range = from..to
            val acts = data.activities.filter { it.start.atZone(RytmTime.zone).toLocalDate() in range }
            val m = data.metrics.filter { it.date in range }
            val c = data.checkIns.filter { it.date in range }
            val n = data.nutrition.filter { it.date in range }
            val w = data.weighIns.filter { it.date in range }
            WeekAggregate(
                weekStart = from.toString(),
                load = loads.filterKeys { it in range }.values.sum(),
                strengthSessions = acts.count { it.type == ActivityType.STRENGTH || it.type == ActivityType.PERSONAL_TRAINING },
                cardioSessions = acts.count { it.type == ActivityType.CARDIO },
                sportSessions = acts.count { it.type == ActivityType.VOLLEYBALL || it.type == ActivityType.SQUASH },
                avgSleepMin = m.mapNotNull { it.sleepMin }.avgInt(),
                avgRestingHr = m.mapNotNull { it.nightRestingHr }.avgInt(),
                avgHrv = c.mapNotNull { it.hrvRmssd }.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
                avgSteps = m.mapNotNull { it.steps }.avgInt(),
                avgEnergy = c.map { it.energy }.takeIf { it.isNotEmpty() }?.average()?.round1(),
                avgKcal = n.map { it.kcal }.avgInt(),
                avgProteinG = n.map { it.proteinG }.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
                nutritionDays = n.size,
                weightAvgKg = w.mapNotNull { it.weightKg }.takeIf { it.isNotEmpty() }?.average()?.round1(),
                bodyFatPct = w.mapNotNull { it.bodyFatPct }.takeIf { it.isNotEmpty() }?.average()?.round1(),
                alcoholDrinks = data.dayNotes.filter { it.date in range }.sumOf { it.alcoholDrinks },
            )
        }
        val start = weeks.firstOrNull()?.first ?: end.minusDays(6)
        return MonthlyInput(
            periodStart = start.toString(),
            periodEnd = end.toString(),
            goal = data.profile.goal.name,
            goalNote = data.profile.goalNote.takeIf { it.isNotBlank() },
            kcalTarget = data.profile.nutrition.kcal,
            proteinTarget = data.profile.nutrition.proteinG,
            sleepTargetMin = data.profile.sleep.minDurationMin,
            weeks = agg,
            strengthProgress = strengthProgress(data.sets.filter { it.date in start..end }),
            weeklySummaries = weeklySummaries.map { it.take(400) }.take(WEEKS),
        )
    }

    /** Najlepsza seria z pierwszej i ostatniej sesji każdego ćwiczenia (co najmniej 2 sesje). */
    fun strengthProgress(sets: List<SetLog>, limit: Int = 10): List<String> =
        sets.groupBy { it.exercise }.mapNotNull { (name, own) ->
            val byDate = own.groupBy { it.date }.toSortedMap()
            if (byDate.size < 2) return@mapNotNull null
            val first = byDate.values.first().maxBy(::score)
            val last = byDate.values.last().maxBy(::score)
            Triple(name, "$name: ${label(first)} → ${label(last)} (${byDate.size} sesji)", byDate.lastKey())
        }.sortedByDescending { it.third }.take(limit).map { it.second }

    private fun score(s: SetLog): Double = s.weightKg?.let { it * (1 + s.reps / 30.0) } ?: s.reps.toDouble()

    private fun label(s: SetLog): String =
        s.weightKg?.let { w -> String.format(Locale.ROOT, "%s kg×%d", if (w % 1.0 == 0.0) w.toInt().toString() else "%.1f".format(Locale.ROOT, w), s.reps) }
            ?: "${s.reps} powt."

    private fun List<Int>.avgInt(): Int? = takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    private fun Double.round1(): Double = (this * 10).roundToInt() / 10.0
}

object RuleBasedMonthlyReport {
    fun build(i: MonthlyInput): MonthlyReport {
        val w = i.weeks
        val half = (w.size / 2).coerceAtLeast(1)
        val early = w.take(half)
        val late = w.takeLast(half)
        fun <T : Number> avg(l: List<WeekAggregate>, f: (WeekAggregate) -> T?): Double? =
            l.mapNotNull(f).map { it.toDouble() }.takeIf { it.isNotEmpty() }?.average()

        val trends = mutableListOf<String>()
        val weightA = avg(early) { it.weightAvgKg }
        val weightB = avg(late) { it.weightAvgKg }
        val weightDelta = if (weightA != null && weightB != null) weightB - weightA else null
        weightDelta?.let { trends += "Waga: %.1f → %.1f kg (%+.1f kg).".format(Locale.ROOT, weightA, weightB, it) }
        val sleepA = avg(early) { it.avgSleepMin }
        val sleepB = avg(late) { it.avgSleepMin }
        if (sleepA != null && sleepB != null) trends += "Sen: średnio ${hm(sleepA)} → ${hm(sleepB)}."
        val hrA = avg(early) { it.avgRestingHr }
        val hrB = avg(late) { it.avgRestingHr }
        if (hrA != null && hrB != null) trends += "Tętno spoczynkowe: ${hrA.roundToInt()} → ${hrB.roundToInt()} uderzeń/min."
        val loadA = avg(early) { it.load }
        val loadB = avg(late) { it.load }
        if (loadA != null && loadB != null) trends += "Obciążenie tygodniowe: ${loadA.roundToInt()} → ${loadB.roundToInt()}."
        val strengthPerWeek = avg(w) { it.strengthSessions } ?: 0.0
        trends += "Siła: średnio %.1f sesji w tygodniu.".format(Locale.ROOT, strengthPerWeek)

        val working = mutableListOf<String>()
        val blockers = mutableListOf<String>()
        if (i.strengthProgress.isNotEmpty()) working += "Ciężary rosną: " + i.strengthProgress.first()
        if (strengthPerWeek >= 1.8) working += "Regularne treningi siłowe."
        val sleepAll = avg(w) { it.avgSleepMin }
        if (sleepAll != null && sleepAll < i.sleepTargetMin) blockers += "Sen poniżej celu (${hm(sleepAll)} zamiast ${hm(i.sleepTargetMin.toDouble())})."
        val protein = avg(w) { it.avgProteinG }
        if (protein != null && protein < i.proteinTarget * 0.9) blockers += "Białko średnio ${protein.roundToInt()} g przy celu ${i.proteinTarget} g."
        val loggedDays = w.sumOf { it.nutritionDays }
        if (loggedDays < w.size * 3) blockers += "Mało wpisów żywienia ($loggedDays dni), trudno ocenić kalorie."
        val alcohol = w.sumOf { it.alcoholDrinks }
        if (alcohol >= w.size * 4) blockers += "Alkohol: $alcohol porcji w ${w.size} tyg."

        val goalCheck = when {
            weightDelta == null -> "Za mało ważeń, żeby ocenić tempo zmian. Waż się 2–3 razy w tygodniu rano."
            i.goal == "FAT_LOSS" && weightDelta > -0.3 -> "Waga stoi (%+.1f kg), a cel to redukcja. Zejdź o ok. 200 kcal dziennie.".format(Locale.ROOT, weightDelta)
            i.goal == "MUSCLE_GAIN" && weightDelta < 0.3 -> "Waga nie rośnie (%+.1f kg), a cel to masa. Dodaj ok. 200 kcal dziennie.".format(Locale.ROOT, weightDelta)
            else -> "Waga %+.1f kg w tym okresie; tempo pasuje do celu.".format(Locale.ROOT, weightDelta)
        }
        val next = buildList {
            blockers.firstOrNull()?.let { add("Popraw najpierw: " + it.replaceFirstChar { c -> c.lowercase() }) }
            if (strengthPerWeek < 1.8) add("Dwa treningi siłowe w tygodniu jako minimum.")
            if (isEmpty()) add("Utrzymaj obecny rytm i dokładaj ciężar, gdy seria wychodzi z zapasem.")
        }.take(3)
        val summary = "Analiza ${w.size} tygodni (${i.periodStart} – ${i.periodEnd}). " +
            (if (blockers.isEmpty()) "Bez większych hamulców." else "Najwięcej do poprawy: ${blockers.first().lowercase()}")
        return MonthlyReport(summary, trends.take(6), working.take(4), blockers.take(4), goalCheck, next)
    }

    private fun hm(min: Double): String = "%d h %02d min".format(Locale.ROOT, min.toInt() / 60, min.toInt() % 60)
}

data class MonthlyResult(val report: MonthlyReport, val fromLlm: Boolean, val costUsd: Double = 0.0, val error: String? = null, val model: String? = null)

/** Analiza długoterminowa na najmocniejszym modelu (LONG_TERM); przy błędzie albo bez klucza z reguł. */
class MonthlyReportService(private val llm: CoachLlm?, private val costGuard: CostGuard?) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun report(input: MonthlyInput): MonthlyResult {
        val fallback = RuleBasedMonthlyReport.build(input)
        if (llm == null) return MonthlyResult(fallback, false)
        return try {
            costGuard?.ensureBudget()
            val response = llm.complete(
                LlmRequest(
                    tier = ModelTier.LONG_TERM,
                    system = SYSTEM,
                    user = "Dane z ostatnich tygodni:\n" + json.encodeToString(MonthlyInput.serializer(), input),
                    schemaName = "monthly_report",
                    jsonSchema = MonthlyReport.jsonSchema,
                    maxOutputTokens = 3_000,
                ),
            )
            val cost = costGuard?.record(response) ?: 0.0
            val parsed = try {
                json.decodeFromString(MonthlyReport.serializer(), response.text).validate()
            } catch (e: Exception) {
                throw LlmException.InvalidOutput("Niepoprawny JSON z LLM: ${e.message}", e)
            }
            MonthlyResult(parsed, true, cost, model = response.model)
        } catch (e: LlmException) {
            MonthlyResult(fallback, false, error = e.message)
        }
    }

    companion object {
        val SYSTEM = """
            ${CoachPersona.ROLE}
            Raz w miesiącu robisz analizę długoterminową, po polsku, konkretnie i życzliwie.
            Dostajesz tygodniowe agregaty z ostatnich do 12 tygodni (weeks, od najstarszego), postęp ćwiczeń i streszczenia raportów tygodniowych.
            - summary: jak wyglądały te tygodnie,
            - trends: zmiany w liczbach w czasie (waga ze średnich tygodniowych, siła, sen, tętno spoczynkowe, HRV, obciążenie, żywienie),
            - working: co działa,
            - blockers: co hamuje postęp (np. sen poniżej celu, mało białka, alkohol, przerwy w treningach),
            - goalCheck: czy tempo zmian pasuje do celu (goal, goalNote), z liczbami; zdrowe tempo redukcji to ok. 0,5% masy ciała tygodniowo,
            - nextMonth: 1–3 priorytety na kolejny miesiąc.
            Zasady: pole null to brak danych, nie wymyślaj liczb. Szukaj zależności między tygodniami (np. tygodnie z większym obciążeniem a sen i tętno).
            Gdy danych jest mało, powiedz, co zapisywać. Plan układa silnik reguł; zmiany proponuj jako ustawienia w profilu. Bez porad medycznych.
        """.trimIndent()
    }
}
