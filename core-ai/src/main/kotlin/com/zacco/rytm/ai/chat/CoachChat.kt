package com.zacco.rytm.ai.chat

import com.zacco.rytm.ai.CoachLlm
import com.zacco.rytm.ai.CoachPersona
import com.zacco.rytm.ai.LlmException
import com.zacco.rytm.ai.LlmRequest
import com.zacco.rytm.ai.ModelTier
import com.zacco.rytm.ai.cost.CostGuard
import com.zacco.rytm.domain.coach.CoachData
import com.zacco.rytm.domain.coach.DayOverride
import com.zacco.rytm.domain.coach.OverrideExercise
import com.zacco.rytm.domain.coach.TodaySnapshot
import com.zacco.rytm.domain.coach.WeekSnapshot
import com.zacco.rytm.domain.exercise.ExerciseGuide
import com.zacco.rytm.domain.model.PlannedDay
import com.zacco.rytm.domain.planner.EquipmentKind
import com.zacco.rytm.domain.workout.Progression
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/** Jedna wiadomość rozmowy z trenerem. [role]: USER albo COACH. */
@Serializable
data class ChatMessage(
    val role: String,
    val text: String,
    val at: Long,
    /** Odpowiedź trenera zmieniła plan dnia. */
    val changedPlan: Boolean = false,
    val error: Boolean = false,
)

@Serializable
data class ChatExercise(val name: String, val sets: Int, val reps: String, val weightKg: Double?, val restSec: Int)

@Serializable
data class ChatDay(
    val date: String,
    val title: String,
    val kind: String,
    val intensity: String,
    val exercises: List<ChatExercise> = emptyList(),
    val reasons: List<String> = emptyList(),
) {
    companion object {
        fun of(d: PlannedDay) = ChatDay(
            date = d.date.toString(),
            title = d.title,
            kind = d.kind.name,
            intensity = d.intensity.name,
            exercises = d.workout?.exercises.orEmpty().map {
                ChatExercise(it.name, it.sets, "${it.reps.first}-${it.reps.last}", it.weightKg, it.restSec)
            },
            reasons = d.rationale.take(2),
        )
    }
}

/** Co trener wie o osobie: profil, forma, plan dnia i tygodnia, ostatnie serie, jedzenie. Bez imienia. */
@Serializable
data class ChatContext(
    val date: String,
    val time: String,
    val goal: String,
    val goalNote: String,
    val weightKg: Double,
    val equipment: String,
    val dumbbellPairMaxKg: Double?,
    val dumbbellSingleMaxKg: Double?,
    val bands: Boolean,
    val pullUpBar: Boolean,
    val weightStepKg: Double,
    val sessionMinutes: Int,
    val focus: List<String>,
    val readiness: Int,
    val readinessLevel: String,
    val flags: List<String>,
    val sleepMin: Int?,
    val energy: Int?,
    val mood: Int?,
    val sore: List<String>,
    val alcoholYesterday: Int,
    val acwr: Double?,
    val loadToday: Int,
    val today: ChatDay,
    val todayChanged: Boolean,
    val setsDoneToday: Int,
    val tomorrow: ChatDay,
    val week: List<String>,
    val activitiesToday: List<String>,
    val recentStrength: List<String>,
    val kcalEaten: Int?,
    val kcalTarget: Int,
    val proteinEaten: Int?,
    val proteinTarget: Int,
    /** Ćwiczenia, które Rytm zna (z opisem techniki), z partiami. */
    val knownExercises: List<String>,
    /** Cele osoby z postępem i prognozą. */
    val goals: List<String> = emptyList(),
    /** Tydzień lżej (deload), gdy trwa. */
    val deload: String? = null,
) {
    companion object {
        fun from(data: CoachData, s: TodaySnapshot, week: WeekSnapshot, nowTime: String): ChatContext {
            val eq = data.profile.equipment
            val recent = data.sets.filter { it.date >= data.today.minusDays(21) }
                .groupBy { it.exercise }
                .map { (name, list) ->
                    val last = list.maxOf { it.date }
                    "$name: $last, " + Progression.summary(list.filter { it.date == last }.sortedBy { it.setIndex })
                }
                .take(15)
            return ChatContext(
                date = s.date.toString(),
                time = nowTime,
                goal = data.profile.goal.name,
                goalNote = data.profile.goalNote.take(200),
                weightKg = data.profile.weightKg,
                equipment = eq.kind.name,
                dumbbellPairMaxKg = eq.pairMaxKg.takeIf { eq.kind == EquipmentKind.DUMBBELLS },
                dumbbellSingleMaxKg = eq.singleMaxKg.takeIf { eq.kind == EquipmentKind.DUMBBELLS },
                bands = eq.bands,
                pullUpBar = eq.pullUpBar,
                weightStepKg = eq.stepKg,
                sessionMinutes = eq.sessionMinutes,
                focus = data.profile.focus.map { it.name },
                readiness = s.readiness.score,
                readinessLevel = s.readiness.level.name,
                flags = s.readiness.flags.map { it.name },
                sleepMin = s.night?.sleepMin,
                energy = s.checkIn?.energy,
                mood = s.checkIn?.mood,
                sore = s.checkIn?.soreAreas.orEmpty().map { it.name },
                alcoholYesterday = s.alcoholYesterday,
                acwr = s.load.acwr?.let { (it * 100).roundToInt() / 100.0 },
                loadToday = s.load.today,
                today = ChatDay.of(s.plan),
                todayChanged = data.overrides.any { it.date == s.date.toString() },
                setsDoneToday = s.setsToday.size,
                tomorrow = ChatDay.of(s.tomorrow),
                week = week.days.map { d ->
                    "${d.plan.date} ${d.plan.date.dayOfWeek}: ${d.plan.title}" +
                        (if (d.activities.isNotEmpty()) " (zrobione: ${d.activities.joinToString { it.type.name }})" else "") +
                        (if (d.cancelled) " (odwołane)" else "")
                },
                activitiesToday = s.activitiesToday.map { "${it.type.name} ${it.durationMin} min" + (it.rpe?.let { r -> " RPE $r" } ?: "") },
                recentStrength = recent,
                kcalEaten = s.nutrition?.kcal,
                kcalTarget = s.nutritionTargets.kcal,
                proteinEaten = s.nutrition?.proteinG?.roundToInt(),
                proteinTarget = s.nutritionTargets.proteinG,
                knownExercises = ExerciseGuide.all().map { e -> e.name + " [" + e.muscles.joinToString(",") { it.name } + "]" },
                goals = data.profile.goals.map { g ->
                    val p = com.zacco.rytm.domain.goals.Goals.progress(g, data.today, data.weighIns, data.sets)
                    "${g.title}: ${(p.share * 100).toInt()}%, ${p.note}"
                },
                deload = s.deload?.text,
            )
        }
    }
}

/** Zmiana planu dnia zaproponowana w odpowiedzi. action: NONE, REPLACE_TODAY, RESET_TODAY. */
@Serializable
data class ChatPlanChange(
    val action: String = "NONE",
    val title: String? = null,
    /** STRENGTH, MOBILITY, REST */
    val kind: String? = null,
    val intensity: String? = null,
    val exercises: List<OverrideExercise>? = null,
    val reason: String? = null,
)

@Serializable
data class ChatReply(val reply: String, val change: ChatPlanChange = ChatPlanChange()) {
    companion object {
        private fun nullable(type: String) = mapOf("type" to listOf(type, "null"))

        val jsonSchema: Map<String, Any?> = mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "required" to listOf("reply", "change"),
            "properties" to mapOf(
                "reply" to mapOf("type" to "string", "description" to "Odpowiedź po polsku, krótko i konkretnie."),
                "change" to mapOf(
                    "type" to "object",
                    "additionalProperties" to false,
                    "required" to listOf("action", "title", "kind", "intensity", "exercises", "reason"),
                    "properties" to mapOf(
                        "action" to mapOf("type" to "string", "enum" to listOf("NONE", "REPLACE_TODAY", "RESET_TODAY")),
                        "title" to nullable("string"),
                        "kind" to nullable("string") + mapOf("description" to "STRENGTH, MOBILITY albo REST"),
                        "intensity" to nullable("string") + mapOf("description" to "LOW, MODERATE albo HIGH"),
                        "exercises" to mapOf(
                            "type" to listOf("array", "null"),
                            "items" to mapOf(
                                "type" to "object",
                                "additionalProperties" to false,
                                "required" to listOf("name", "sets", "repsMin", "repsMax", "weightKg", "restSec", "note"),
                                "properties" to mapOf(
                                    "name" to mapOf("type" to "string"),
                                    "sets" to mapOf("type" to "integer"),
                                    "repsMin" to mapOf("type" to "integer"),
                                    "repsMax" to mapOf("type" to "integer"),
                                    "weightKg" to nullable("number"),
                                    "restSec" to mapOf("type" to "integer"),
                                    "note" to nullable("string"),
                                ),
                            ),
                        ),
                        "reason" to nullable("string"),
                    ),
                ),
            ),
        )
    }
}

/** Wynik wiadomości: odpowiedź, a gdy trener zmienia plan, nowy plan dnia (albo [reset] do planu z reguł). */
data class ChatResult(
    val reply: String,
    val override: DayOverride? = null,
    val reset: Boolean = false,
    val costUsd: Double = 0.0,
    val error: String? = null,
)

/** Rozmowa z trenerem (tani model DAILY). Trener widzi dane i może podmienić plan dzisiejszego dnia. */
class CoachChatService(private val llm: CoachLlm, private val costGuard: CostGuard?) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun send(context: ChatContext, history: List<ChatMessage>, message: String, nowMs: Long): ChatResult = try {
        costGuard?.ensureBudget()
        val past = history.filterNot { it.error }.takeLast(HISTORY).joinToString("\n") { m ->
            (if (m.role == USER) "Osoba: " else "Trener: ") + m.text.take(600)
        }
        val response = llm.complete(
            LlmRequest(
                tier = ModelTier.DAILY,
                system = SYSTEM,
                user = "Dane (JSON):\n" + json.encodeToString(ChatContext.serializer(), context) +
                    (if (past.isNotBlank()) "\n\nWcześniej w rozmowie:\n$past" else "") +
                    "\n\nNowa wiadomość osoby:\n" + message.take(1000),
                schemaName = "coach_chat",
                jsonSchema = ChatReply.jsonSchema,
                maxOutputTokens = 1_500,
            ),
        )
        val cost = costGuard?.record(response) ?: 0.0
        val parsed = try {
            json.decodeFromString(ChatReply.serializer(), response.text)
        } catch (e: Exception) {
            throw LlmException.InvalidOutput("Niepoprawny JSON z LLM: ${e.message}", e)
        }
        toResult(parsed, context, nowMs).copy(costUsd = cost)
    } catch (e: LlmException) {
        ChatResult("Nie udało się zapytać trenera: ${e.message}", error = e.message)
    }

    companion object {
        const val USER = "USER"
        const val COACH = "COACH"
        private const val HISTORY = 12

        /** Sprawdza zmianę planu: limity serii, powtórzeń, przerw i ciężaru sprzętu. Zła zmiana = brak zmiany. */
        fun toResult(r: ChatReply, ctx: ChatContext, nowMs: Long): ChatResult {
            val c = r.change
            return when (c.action) {
                "RESET_TODAY" -> ChatResult(r.reply, reset = ctx.todayChanged)
                "REPLACE_TODAY" -> {
                    val kind = c.kind?.takeIf { it in setOf("STRENGTH", "MOBILITY", "REST") } ?: return ChatResult(r.reply)
                    val cap = ctx.dumbbellSingleMaxKg
                    val exercises = if (kind == "REST") emptyList() else c.exercises.orEmpty().take(10).mapNotNull { e ->
                        if (e.name.isBlank()) return@mapNotNull null
                        val min = e.repsMin.coerceIn(1, 300)
                        e.copy(
                            name = e.name.trim().take(60),
                            sets = e.sets.coerceIn(1, 6),
                            repsMin = min,
                            repsMax = e.repsMax.coerceIn(min, 300),
                            weightKg = e.weightKg?.takeIf { it > 0 }?.let { w -> if (cap != null) minOf(w, cap) else w },
                            restSec = e.restSec.coerceIn(0, 300),
                            note = e.note?.take(120),
                        )
                    }
                    if (kind != "REST" && exercises.isEmpty()) return ChatResult(r.reply)
                    ChatResult(
                        r.reply,
                        override = DayOverride(
                            date = ctx.date,
                            title = c.title?.takeIf { it.isNotBlank() }?.take(60) ?: if (kind == "REST") "Odpoczynek" else "Plan od trenera",
                            kind = kind,
                            intensity = c.intensity?.takeIf { it in setOf("LOW", "MODERATE", "HIGH") } ?: "MODERATE",
                            exercises = exercises,
                            reason = c.reason?.takeIf { it.isNotBlank() }?.take(160) ?: "zmiana na Twoją prośbę",
                            createdAt = nowMs,
                        ),
                    )
                }
                else -> ChatResult(r.reply)
            }
        }

        val SYSTEM = """
            ${CoachPersona.ROLE}
            Rozmawiasz z osobą na czacie w aplikacji Rytm. Znasz jej dane z JSON-a: profil, sprzęt, gotowość, check-in, plan dnia i tygodnia, ostatnie serie, jedzenie.
            Odpowiadasz po polsku, krótko (zwykle 2–5 zdań), konkretnie i z odwołaniem do jej danych. Zwracaj się neutralnie płciowo (bez form typu „zrobiłeś”).
            Możesz zmienić plan DZISIEJSZEGO dnia, gdy osoba o to prosi albo to wynika z rozmowy („mam 20 minut”, „boli bark”, „jestem padnięty”):
            - change.action = REPLACE_TODAY i podaj pełny nowy plan dnia: title, kind (STRENGTH, MOBILITY albo REST), intensity, exercises, reason (jedno zdanie).
            - Ćwiczenia wybieraj z knownExercises (dokładna nazwa bez nawiasu z partiami), dopasowane do sprzętu. Hantle: nie przekraczaj dumbbellSingleMaxKg; ćwiczenia na dwa hantle do dumbbellPairMaxKg.
            - Ciężary bierz z today.exercises i recentStrength; przy bólu lub zmęczeniu lżej. Mniej czasu: mniej ćwiczeń i serii, krótsze przerwy (ok. 3 min na ćwiczenie z przerwami).
            - Przy bólu omijaj obciążanie bolącej partii; przy ostrym bólu, drętwieniu albo urazie zaproponuj odpoczynek i konsultację z lekarzem lub fizjoterapeutą.
            - RESET_TODAY: osoba chce wrócić do planu z reguł (todayChanged = true).
            - NONE: zwykła odpowiedź bez zmiany planu (wtedy pozostałe pola change = null).
            Gdy zmieniasz plan, w reply napisz krótko, co zmieniasz. Nie zmieniaj innych dni; o stałych ustawieniach (dni treningów, sprzęt) powiedz, że zmienia się je w Profilu.
            Bez diagnoz medycznych. Gdy pytanie nie dotyczy treningu, snu, regeneracji ani jedzenia, odpowiedz krótko i wróć do tematu.
        """.trimIndent()
    }
}
