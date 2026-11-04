package com.zacco.rytm.ai.weekplan

import com.zacco.rytm.ai.CoachLlm
import com.zacco.rytm.ai.CoachPersona
import com.zacco.rytm.ai.LlmException
import com.zacco.rytm.ai.LlmRequest
import com.zacco.rytm.ai.ModelTier
import com.zacco.rytm.ai.cost.CostGuard
import com.zacco.rytm.ai.weekly.WeeklyInput
import com.zacco.rytm.domain.coach.CoachData
import com.zacco.rytm.domain.coach.CoachEngine
import com.zacco.rytm.domain.coach.OverrideExercise
import com.zacco.rytm.domain.coach.PlanDay
import com.zacco.rytm.domain.coach.StrengthTuning
import com.zacco.rytm.domain.coach.WeekPlan
import com.zacco.rytm.domain.exercise.ExerciseGuide
import com.zacco.rytm.domain.model.DayKind
import com.zacco.rytm.domain.planner.ExerciseLibrary
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Dzień siłowy z planu reguł na kolejny tydzień: punkt wyjścia dla trenera. */
@Serializable
data class RuleStrengthDay(val date: String, val weekday: String, val focus: String, val title: String, val exercises: List<String>)

@Serializable
data class WeekPlanInput(
    val week: WeeklyInput,
    /** Dni siłowe na kolejny tydzień z planu reguł (daty zostają, zmieniają się ćwiczenia). */
    val strengthDays: List<RuleStrengthDay>,
    /** Ćwiczenia możliwe przy sprzęcie użytkownika; tylko z nich wolno układać plan. */
    val allowedExercises: List<String>,
    /** Ćwiczenia bez postępu od 3 sesji. */
    val plateaus: List<String>,
    val sessionMinutes: Int,
    val dumbbellSingleMaxKg: Double?,
)

@Serializable
data class WeekPlanReply(val days: List<ReplyDay>, val reason: String) {
    @Serializable
    data class ReplyDay(val date: String, val title: String, val note: String, val exercises: List<ReplyExercise>)

    @Serializable
    data class ReplyExercise(val name: String, val sets: Int, val repsMin: Int, val repsMax: Int, val restSec: Int, val note: String?)
}

data class WeekPlanResult(val plan: WeekPlan?, val costUsd: Double = 0.0, val error: String? = null)

/**
 * Plan siłowy na kolejny tydzień od trenera AI (model WEEKLY, raz w tygodniu). Trener dobiera ćwiczenia
 * i objętość z listy dozwolonych; ciężar i tak liczy progresja z reguł. Obowiązuje po zatwierdzeniu.
 */
class WeekPlanService(private val llm: CoachLlm, private val costGuard: CostGuard?) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun propose(input: WeekPlanInput, weekStart: LocalDate): WeekPlanResult {
        if (input.strengthDays.isEmpty()) return WeekPlanResult(null)
        return try {
            costGuard?.ensureBudget()
            val response = llm.complete(
                LlmRequest(
                    tier = ModelTier.WEEKLY,
                    system = SYSTEM,
                    user = "Dane (JSON):\n" + json.encodeToString(WeekPlanInput.serializer(), input),
                    schemaName = "week_plan",
                    jsonSchema = schema(input.allowedExercises),
                    maxOutputTokens = 2_500,
                ),
            )
            val cost = costGuard?.record(response) ?: 0.0
            val parsed = try {
                json.decodeFromString(WeekPlanReply.serializer(), response.text)
            } catch (e: Exception) {
                throw LlmException.InvalidOutput("Niepoprawny JSON z LLM: ${e.message}", e)
            }
            WeekPlanResult(validate(parsed, input, weekStart)?.copy(costUsd = cost), cost)
        } catch (e: LlmException) {
            WeekPlanResult(null, error = e.message)
        }
    }

    companion object {
        private val pl = Locale.forLanguageTag("pl")

        /** Dane wejściowe z danych i planu reguł na tydzień zaczynający się po [weekEnd]. */
        fun input(data: CoachData, weekEnd: LocalDate, week: WeeklyInput): WeekPlanInput {
            val next = CoachEngine().week(data.copy(today = weekEnd.plusDays(1), weekPlans = emptyList(), overrides = emptyList()))
            val days = next.days.map { it.plan }.filter { it.kind == DayKind.STRENGTH && it.workout != null }.map { d ->
                RuleStrengthDay(
                    d.date.toString(),
                    d.date.dayOfWeek.getDisplayName(TextStyle.FULL, pl),
                    d.focus?.name ?: "FULL_BODY",
                    d.title,
                    d.workout!!.exercises.map { "${it.name} ${it.sets}×${it.reps.first}–${it.reps.last}" },
                )
            }
            val eq = data.profile.equipment
            val allowed = ExerciseLibrary(eq, data.profile.goal, data.profile.focus, data.profile.mobility).allowedNames()
            val plateaus = data.sets.map { it.exercise }.distinctBy { ExerciseGuide.key(it) }
                .filter { StrengthTuning.plateaued(data.sets, it, weekEnd.plusDays(1)) }
            return WeekPlanInput(week, days, allowed, plateaus, eq.sessionMinutes, eq.maxKg)
        }

        /** Tylko dni z planu reguł, tylko dozwolone ćwiczenia, rozsądne serie i przerwy. Pusty plan = brak propozycji. */
        fun validate(r: WeekPlanReply, input: WeekPlanInput, weekStart: LocalDate): WeekPlan? {
            val allowed = input.allowedExercises.associateBy { ExerciseGuide.key(it) }
            val dates = input.strengthDays.map { it.date }.toSet()
            val maxExercises = when {
                input.sessionMinutes <= 20 -> 3
                input.sessionMinutes <= 30 -> 4
                else -> 7
            }
            val days = r.days.filter { it.date in dates }.distinctBy { it.date }.mapNotNull { d ->
                val list = d.exercises.mapNotNull { e ->
                    val name = allowed[ExerciseGuide.key(e.name)] ?: return@mapNotNull null
                    val min = e.repsMin.coerceIn(1, 50)
                    OverrideExercise(
                        name = name,
                        sets = e.sets.coerceIn(1, 5),
                        repsMin = min,
                        repsMax = e.repsMax.coerceIn(min, 60),
                        weightKg = null,
                        restSec = e.restSec.coerceIn(30, 240),
                        note = e.note?.take(100)?.takeIf { it.isNotBlank() },
                    )
                }.distinctBy { ExerciseGuide.key(it.name) }.take(maxExercises)
                if (list.size < 2) null else PlanDay(d.date, d.title.take(50).ifBlank { "Siła" }, list, d.note.take(160))
            }
            if (days.isEmpty()) return null
            return WeekPlan(weekStart.with(DayOfWeek.MONDAY).toString(), days, r.reason.take(400))
        }

        private val SYSTEM = """
            ${CoachPersona.ROLE}
            Układasz plan siłowy na kolejny tydzień. Piszesz po polsku, krótko.
            Dostajesz dane tygodnia (week), dni siłowe z planu reguł (strengthDays), dozwolone ćwiczenia (allowedExercises),
            ćwiczenia bez postępu (plateaus), długość sesji i sprzęt.
            Zasady:
            - Dla każdego dnia ze strengthDays podaj sesję na ten sam dzień (ta sama data, ten sam rodzaj: UPPER = góra, LOWER = dół, FULL_BODY = całe ciało).
            - Używaj wyłącznie nazw z allowedExercises. Ciężaru nie podajesz: dobiera go progresja z dziennika serii.
            - Ćwiczenie z celu (week.goals) dołóż tam, gdzie pasuje; przy celu wagi bez nadmiernej objętości.
            - Ćwiczenie z plateaus wymień na podobne albo zmień zakres powtórzeń.
            - Uwzględnij tydzień: dużo sportu albo słaby sen = mniej serii, bóle (week.days.sore) = omiń te partie, opuszczone sesje = nie nadrabiaj objętością.
            - Mieść się w sessionMinutes (ok. 3 serie na 6–8 minut z przerwami). Przy lekkim sprzęcie wyższe zakresy powtórzeń (10–20).
            - Nie zmieniaj większości ćwiczeń z tygodnia na tydzień: progresja potrzebuje powtarzalności. Zmieniaj to, co ma powód.
            - reason: 1–3 zdania, co zmieniasz względem planu reguł i dlaczego. note dnia: jedno krótkie zdanie.
        """.trimIndent()

        fun schema(allowed: List<String>): Map<String, Any?> = mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "required" to listOf("days", "reason"),
            "properties" to mapOf(
                "reason" to mapOf("type" to "string"),
                "days" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "object",
                        "additionalProperties" to false,
                        "required" to listOf("date", "title", "note", "exercises"),
                        "properties" to mapOf(
                            "date" to mapOf("type" to "string"),
                            "title" to mapOf("type" to "string"),
                            "note" to mapOf("type" to "string"),
                            "exercises" to mapOf(
                                "type" to "array",
                                "items" to mapOf(
                                    "type" to "object",
                                    "additionalProperties" to false,
                                    "required" to listOf("name", "sets", "repsMin", "repsMax", "restSec", "note"),
                                    "properties" to mapOf(
                                        "name" to mapOf("type" to "string", "enum" to allowed),
                                        "sets" to mapOf("type" to "integer"),
                                        "repsMin" to mapOf("type" to "integer"),
                                        "repsMax" to mapOf("type" to "integer"),
                                        "restSec" to mapOf("type" to "integer"),
                                        "note" to mapOf("type" to listOf("string", "null")),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }
}
