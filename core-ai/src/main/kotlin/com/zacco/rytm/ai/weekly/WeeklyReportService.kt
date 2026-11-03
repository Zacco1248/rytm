package com.zacco.rytm.ai.weekly

import com.zacco.rytm.ai.CoachLlm
import com.zacco.rytm.ai.LlmException
import com.zacco.rytm.ai.LlmRequest
import com.zacco.rytm.ai.ModelTier
import com.zacco.rytm.ai.cost.CostGuard
import kotlinx.serialization.json.Json

data class WeeklyResult(val report: WeeklyReport, val fromLlm: Boolean, val costUsd: Double = 0.0, val error: String? = null, val model: String? = null)

/** Raport tygodniowy na mocniejszym modelu (WEEKLY); przy błędzie albo bez klucza z reguł. */
class WeeklyReportService(private val llm: CoachLlm?, private val costGuard: CostGuard?) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun report(input: WeeklyInput): WeeklyResult {
        val fallback = RuleBasedWeeklyReport.build(input)
        if (llm == null) return WeeklyResult(fallback, false)
        return try {
            costGuard?.ensureBudget()
            val response = llm.complete(
                LlmRequest(
                    tier = ModelTier.WEEKLY,
                    system = WeeklyPrompt.system,
                    user = WeeklyPrompt.user(input),
                    schemaName = "weekly_report",
                    jsonSchema = WeeklyReport.jsonSchema,
                    maxOutputTokens = 2_500,
                ),
            )
            val cost = costGuard?.record(response) ?: 0.0
            val parsed = try {
                json.decodeFromString(WeeklyReport.serializer(), response.text).validate()
            } catch (e: Exception) {
                throw LlmException.InvalidOutput("Niepoprawny JSON z LLM: ${e.message}", e)
            }
            WeeklyResult(parsed, true, cost, model = response.model)
        } catch (e: LlmException) {
            WeeklyResult(fallback, false, error = e.message)
        }
    }
}
