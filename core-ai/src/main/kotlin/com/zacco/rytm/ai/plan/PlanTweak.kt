package com.zacco.rytm.ai.plan

import com.zacco.rytm.ai.CoachLlm
import com.zacco.rytm.ai.LlmException
import com.zacco.rytm.ai.LlmRequest
import com.zacco.rytm.ai.ModelTier
import com.zacco.rytm.ai.cost.CostGuard
import com.zacco.rytm.domain.exercise.Muscle
import com.zacco.rytm.domain.planner.EquipmentKind
import com.zacco.rytm.domain.profile.ActivityChoice
import com.zacco.rytm.domain.profile.Goal
import com.zacco.rytm.domain.profile.PersonalProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Zmiana ustawień planu zaproponowana przez AI na prośbę w kroku 5 kreatora („we wtorki nie mam czasu”).
 * AI zmienia tylko parametry; sam plan dalej układają reguły. Null: bez zmiany.
 */
@Serializable
data class PlanTweak(
    val activities: List<String>? = null,
    val goal: String? = null,
    /** 1 = poniedziałek … 7 = niedziela. */
    val trainingDays: List<Int>? = null,
    val sessionsPerWeek: Int? = null,
    val restDay: Int? = null,
    val sessionMinutes: Int? = null,
    val equipment: String? = null,
    val treadmill: Boolean? = null,
    val focus: List<String>? = null,
    /** Co i dlaczego zmieniono, 1–3 zdania po polsku. */
    val explanation: String,
) {
    /** Nakłada zmianę na profil, odrzucając wartości spoza dozwolonych. */
    fun applyTo(p: PersonalProfile): PersonalProfile {
        var r = p
        activities?.mapNotNull { a -> ActivityChoice.entries.firstOrNull { it.name == a } }?.takeIf { it.isNotEmpty() }
            ?.let { r = r.copy(activities = it.distinct()) }
        goal?.let { g -> Goal.entries.firstOrNull { it.name == g } }?.let { r = r.copy(goal = it) }
        trainingDays?.filter { it in 1..7 }?.distinct()?.takeIf { it.isNotEmpty() }?.let { r = r.copy(strengthDays = it) }
        sessionsPerWeek?.takeIf { it in 0..7 }?.let { r = r.copy(strengthPerWeek = it) }
        restDay?.let { d -> r = r.copy(restDay = d.takeIf { it in 1..7 }) }
        sessionMinutes?.let { m -> r = r.copy(sessionMinutes = ALLOWED_MINUTES.minBy { kotlin.math.abs(it - m) }) }
        equipment?.let { e -> EquipmentKind.entries.firstOrNull { it.name == e } }?.let { r = r.copy(equipment = it) }
        treadmill?.let { r = r.copy(treadmill = it) }
        focus?.mapNotNull { m -> Muscle.entries.firstOrNull { it.name == m } }?.let { r = r.copy(focus = it.distinct().take(4)) }
        return r
    }

    companion object {
        val ALLOWED_MINUTES = listOf(20, 30, 45, 60, 90)

        private fun nullable(type: String, extra: Map<String, Any?> = emptyMap()) = mapOf("type" to listOf(type, "null")) + extra
        private fun enumArray(values: List<String>) = mapOf(
            "type" to listOf("array", "null"),
            "items" to mapOf("type" to "string", "enum" to values),
        )

        val jsonSchema: Map<String, Any?> = mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "required" to listOf(
                "activities", "goal", "trainingDays", "sessionsPerWeek", "restDay", "sessionMinutes",
                "equipment", "treadmill", "focus", "explanation",
            ),
            "properties" to mapOf(
                "activities" to enumArray(ActivityChoice.entries.map { it.name }),
                "goal" to nullable("string", mapOf("description" to "Jedno z: " + Goal.entries.joinToString { it.name })),
                "trainingDays" to mapOf("type" to listOf("array", "null"), "items" to mapOf("type" to "integer")),
                "sessionsPerWeek" to nullable("integer"),
                "restDay" to nullable("integer"),
                "sessionMinutes" to nullable("integer"),
                "equipment" to nullable("string", mapOf("description" to "Jedno z: " + EquipmentKind.entries.joinToString { it.name })),
                "treadmill" to nullable("boolean"),
                "focus" to enumArray(Muscle.entries.map { it.name }),
                "explanation" to mapOf("type" to "string", "description" to "Co zmieniono i dlaczego, 1–3 zdania po polsku."),
            ),
        )
    }
}

/** Stan profilu, który widzi AI (bez imienia i danych zdrowotnych). */
@Serializable
data class PlanSettings(
    val activities: List<String>,
    val goal: String,
    val goalNote: String,
    val trainingDays: List<Int>,
    val sessionsPerWeek: Int,
    val restDay: Int?,
    val sessionMinutes: Int,
    val equipment: String,
    val treadmill: Boolean,
    val focus: List<String>,
    val fixedSports: List<String>,
) {
    companion object {
        fun of(p: PersonalProfile) = PlanSettings(
            activities = (p.activities ?: listOf(ActivityChoice.STRENGTH_HOME)).map { it.name },
            goal = p.goal.name,
            goalNote = p.goalNote,
            trainingDays = p.strengthDays,
            sessionsPerWeek = p.strengthPerWeek,
            restDay = p.restDay,
            sessionMinutes = p.sessionMinutes,
            equipment = p.equipment.name,
            treadmill = p.treadmill,
            focus = p.focus.map { it.name },
            fixedSports = p.fixedSports.map { "${it.day}: ${it.name}" },
        )
    }
}

data class PlanTweakResult(val tweak: PlanTweak?, val costUsd: Double = 0.0, val error: String? = null)

/** „Popraw z AI” w kreatorze: prośba słowami → zmiana parametrów planu do zatwierdzenia. */
class PlanTweakService(private val llm: CoachLlm, private val costGuard: CostGuard?) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun tweak(profile: PersonalProfile, request: String): PlanTweakResult = try {
        costGuard?.ensureBudget()
        val response = llm.complete(
            LlmRequest(
                tier = ModelTier.DAILY,
                system = SYSTEM,
                user = "Obecne ustawienia:\n" + json.encodeToString(PlanSettings.serializer(), PlanSettings.of(profile)) +
                    "\n\nProśba użytkownika:\n" + request.take(500),
                schemaName = "plan_tweak",
                jsonSchema = PlanTweak.jsonSchema,
                maxOutputTokens = 800,
            ),
        )
        val cost = costGuard?.record(response) ?: 0.0
        val parsed = try {
            json.decodeFromString(PlanTweak.serializer(), response.text)
        } catch (e: Exception) {
            throw LlmException.InvalidOutput("Niepoprawny JSON z LLM: ${e.message}", e)
        }
        PlanTweakResult(parsed, cost)
    } catch (e: LlmException) {
        PlanTweakResult(null, error = e.message)
    }

    companion object {
        val SYSTEM = """
            Jesteś trenerem w aplikacji Rytm. Użytkownik prosi o zmianę planu treningowego.
            Zmieniasz wyłącznie ustawienia z JSON-a; plan dni układa potem silnik reguł.
            Zasady:
            - Pola, których nie zmieniasz, zwróć jako null.
            - trainingDays i restDay to dni tygodnia 1–7 (1 = poniedziałek). Nie kładź treningów na dni stałych sportów.
            - sessionsPerWeek 0–7, sessionMinutes jedno z: 20, 30, 45, 60, 90.
            - Bez sensu medycznego: przy bólu lub kontuzji poradź konsultację z lekarzem lub fizjoterapeutą w explanation.
            - explanation: 1–3 krótkie zdania po polsku, co zmieniasz i dlaczego.
        """.trimIndent()
    }
}
