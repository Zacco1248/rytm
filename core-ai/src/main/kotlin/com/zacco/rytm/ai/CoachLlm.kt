package com.zacco.rytm.ai

/**
 * Punkt wpięcia modelu językowego. Implementacje: [com.zacco.rytm.ai.claude.ClaudeCoachLlm],
 * [com.zacco.rytm.ai.openai.OpenAiCoachLlm]; w przyszłości lokalny model na domowym serwerze.
 */
interface CoachLlm {
    val provider: LlmProvider

    /** Zwraca odpowiedź w JSON zgodnym z [LlmRequest.jsonSchema]. Rzuca [LlmException] przy błędzie. */
    suspend fun complete(request: LlmRequest): LlmResponse
}

enum class LlmProvider { CLAUDE, OPENAI }

/**
 * DAILY: codzienne podsumowania (tani model), WEEKLY: raport tygodniowy (mocny model),
 * LONG_TERM: analiza długoterminowa, np. raz w miesiącu (najmocniejszy model).
 */
enum class ModelTier { DAILY, WEEKLY, LONG_TERM }

data class LlmRequest(
    val tier: ModelTier,
    val system: String,
    val user: String,
    /** Nazwa schematu (wymagana przez OpenAI) i sam schemat JSON (obiekt z additionalProperties = false). */
    val schemaName: String,
    val jsonSchema: Map<String, Any?>,
    val maxOutputTokens: Long = 4_000,
)

data class LlmResponse(
    val text: String,
    val model: String,
    val inputTokens: Long,
    val outputTokens: Long,
)

sealed class LlmException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Refused(message: String) : LlmException(message)
    class Truncated(message: String) : LlmException(message)
    class Transport(message: String, cause: Throwable?) : LlmException(message, cause)
    class BudgetExceeded(message: String) : LlmException(message)
    class InvalidOutput(message: String, cause: Throwable? = null) : LlmException(message, cause)
}
