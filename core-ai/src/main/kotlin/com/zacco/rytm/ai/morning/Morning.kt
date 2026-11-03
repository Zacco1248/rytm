package com.zacco.rytm.ai.morning

import com.zacco.rytm.ai.CoachPersona

import com.zacco.rytm.ai.CoachLlm
import com.zacco.rytm.ai.LlmException
import com.zacco.rytm.ai.LlmRequest
import com.zacco.rytm.ai.ModelTier
import com.zacco.rytm.ai.cost.CostGuard
import com.zacco.rytm.domain.coach.CoachData
import com.zacco.rytm.domain.coach.TodaySnapshot
import com.zacco.rytm.domain.readiness.ReadinessFlag
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/** Dane poranka: gotowość ze składowymi, check-in i plan dnia z silnika reguł. */
@Serializable
data class MorningInput(
    val date: String,
    val readiness: Int,
    val level: String,
    val components: Map<String, Int>,
    val flags: List<String>,
    val calibrating: Boolean,
    val sleepMin: Int?,
    val restingHr: Int?,
    val restingHrAvg: Int?,
    val hrv: Int?,
    val hrvAvg: Int?,
    val energy: Int?,
    val mood: Int?,
    val sore: List<String>,
    val alcoholYesterday: Int,
    val plan: String,
    val intensity: String,
    val planReasons: List<String>,
    val warnings: List<String>,
    val goal: String,
) {
    companion object {
        fun from(data: CoachData, s: TodaySnapshot) = MorningInput(
            date = s.date.toString(),
            readiness = s.readiness.score,
            level = s.readiness.level.name,
            components = s.readiness.components.mapKeys { it.key.name },
            flags = s.readiness.flags.map { it.name },
            calibrating = s.readiness.calibrating,
            sleepMin = s.night?.sleepMin,
            restingHr = s.restingHr,
            restingHrAvg = s.restingHrAvg?.roundToInt(),
            hrv = s.checkIn?.hrvRmssd?.roundToInt(),
            hrvAvg = s.hrvAvg?.roundToInt(),
            energy = s.checkIn?.energy,
            mood = s.checkIn?.mood,
            sore = s.checkIn?.soreAreas.orEmpty().map { it.name },
            alcoholYesterday = s.alcoholYesterday,
            plan = s.plan.title,
            intensity = s.plan.intensity.name,
            planReasons = s.plan.rationale,
            warnings = s.plan.warnings,
            goal = data.profile.goal.name,
        )
    }
}

/** Krótkie poranne uzasadnienie planu po check-inie. */
@Serializable
data class MorningNote(
    /** Jedno zdanie: jak dziś z formą. */
    val headline: String,
    /** 1–2 zdania: dlaczego plan wygląda tak, a nie inaczej. */
    val why: String,
    /** Jedna praktyczna wskazówka na dziś. */
    val tip: String,
) {
    fun validate(): MorningNote {
        require(headline.isNotBlank() && why.isNotBlank() && tip.isNotBlank()) { "puste pole" }
        return this
    }

    companion object {
        val jsonSchema: Map<String, Any?> = mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "required" to listOf("headline", "why", "tip"),
            "properties" to mapOf(
                "headline" to mapOf("type" to "string", "description" to "Jedno zdanie o formie dziś, z liczbą gotowości."),
                "why" to mapOf("type" to "string", "description" to "1–2 zdania: dlaczego plan dnia jest taki (tłumaczysz plan, nie zmieniasz)."),
                "tip" to mapOf("type" to "string", "description" to "Jedna praktyczna wskazówka na dziś."),
            ),
        )
    }
}

object RuleBasedMorningNote {
    fun build(i: MorningInput): MorningNote {
        val headline = when (i.level) {
            "HIGH" -> "Gotowość ${i.readiness}: dobry dzień na pełny trening."
            "MODERATE" -> "Gotowość ${i.readiness}: forma w normie, bez szaleństw."
            else -> "Gotowość ${i.readiness}: organizm prosi o lżejszy dzień."
        } + if (i.calibrating) " Wskaźniki jeszcze się kalibrują." else ""
        val why = (i.planReasons.firstOrNull() ?: "Plan dnia: ${i.plan}.").let { if (it.endsWith(".")) it else "$it." }
        val tip = when {
            ReadinessFlag.SHORT_SLEEP.name in i.flags -> "Krótka noc: dziś połóż się wcześniej i nie dokładaj kofeiny po 14:00."
            ReadinessFlag.ALCOHOL.name in i.flags -> "Po wczorajszym alkoholu pij więcej wody i odpuść maksymalny wysiłek."
            ReadinessFlag.SORENESS.name in i.flags -> "Bolące partie rozgrzej dłużej i oszczędzaj je w treningu."
            ReadinessFlag.RESTING_HR_ELEVATED.name in i.flags -> "Tętno spoczynkowe wyższe niż zwykle: obserwuj, czy nie łapie Cię przeziębienie."
            i.warnings.isNotEmpty() -> i.warnings.first()
            else -> "Zrób 10 minut spaceru na świeżym powietrzu w pierwszej połowie dnia."
        }
        return MorningNote(headline, why, tip)
    }
}

data class MorningResult(val note: MorningNote, val fromLlm: Boolean, val costUsd: Double = 0.0, val error: String? = null)

/** Poranne uzasadnienie (tani model DAILY); przy błędzie albo bez klucza z reguł. */
class MorningService(private val llm: CoachLlm?, private val costGuard: CostGuard?) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun note(input: MorningInput): MorningResult {
        val fallback = RuleBasedMorningNote.build(input)
        if (llm == null) return MorningResult(fallback, false)
        return try {
            costGuard?.ensureBudget()
            val response = llm.complete(
                LlmRequest(
                    tier = ModelTier.DAILY,
                    system = SYSTEM,
                    user = "Dane poranka:\n" + json.encodeToString(MorningInput.serializer(), input),
                    schemaName = "morning_note",
                    jsonSchema = MorningNote.jsonSchema,
                    maxOutputTokens = 600,
                ),
            )
            val cost = costGuard?.record(response) ?: 0.0
            val parsed = try {
                json.decodeFromString(MorningNote.serializer(), response.text).validate()
            } catch (e: Exception) {
                throw LlmException.InvalidOutput("Niepoprawny JSON z LLM: ${e.message}", e)
            }
            MorningResult(parsed, true, cost)
        } catch (e: LlmException) {
            MorningResult(fallback, false, error = e.message)
        }
    }

    companion object {
        val SYSTEM = """
            ${CoachPersona.ROLE}
            Po porannym check-inie piszesz po polsku 3 krótkie pola: headline, why, tip.
            Plan dnia (plan, intensity, planReasons, warnings) ustala silnik reguł: tłumaczysz go, nie zmieniasz.
            Odwołuj się do liczb (sen, tętno spoczynkowe vs średnia, HRV vs średnia, energia). Pole null to brak danych.
            Bez porad medycznych. Gdy calibrating = true, wspomnij o tym krótko. Razem najwyżej 60 słów.
        """.trimIndent()
    }
}
