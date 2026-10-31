package com.zacco.rytm.ai.evening

import com.zacco.rytm.ai.CoachLlm
import com.zacco.rytm.ai.LlmException
import com.zacco.rytm.ai.LlmRequest
import com.zacco.rytm.ai.ModelTier
import com.zacco.rytm.ai.cost.CostGuard
import kotlinx.serialization.json.Json

data class EveningResult(val review: EveningReview, val source: Source, val costUsd: Double = 0.0, val error: String? = null) {
    enum class Source { LLM, RULES }
}

/**
 * Wieczorna analiza: LLM, jeśli jest skonfigurowany i budżet pozwala; przy każdym błędzie fallback do reguł.
 */
class EveningReviewService(
    private val llm: CoachLlm?,
    private val costGuard: CostGuard?,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun review(input: EveningInput): EveningResult {
        val fallback = RuleBasedEveningReview.build(input)
        if (llm == null) return EveningResult(fallback, EveningResult.Source.RULES)

        return try {
            costGuard?.ensureBudget()
            val response = llm.complete(
                LlmRequest(
                    tier = ModelTier.DAILY,
                    system = EveningPrompt.system,
                    user = EveningPrompt.user(input),
                    schemaName = "evening_review",
                    jsonSchema = EveningReview.jsonSchema,
                ),
            )
            val cost = costGuard?.record(response) ?: 0.0
            val parsed = try {
                json.decodeFromString(EveningReview.serializer(), response.text).validate()
            } catch (e: Exception) {
                throw LlmException.InvalidOutput("Niepoprawny JSON z LLM: ${e.message}", e)
            }
            // Pora snu zawsze z reguł, nawet gdy model ją zmienił.
            EveningResult(parsed.copy(bedtime = input.suggestedBedtime), EveningResult.Source.LLM, cost)
        } catch (e: LlmException) {
            EveningResult(fallback, EveningResult.Source.RULES, error = e.message)
        }
    }
}
