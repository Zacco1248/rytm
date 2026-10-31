package com.zacco.rytm.ai

/** Ustawienia LLM z ekranu ustawień. Klucze API trzymamy poza repo (local.properties lub EncryptedSharedPreferences). */
data class LlmSettings(
    val provider: LlmProvider = LlmProvider.CLAUDE,
    val models: Map<ModelTier, String> = defaultModels(provider),
    /** Miesięczny budżet w USD; po przekroczeniu tylko silnik reguł. */
    val monthlyBudgetUsd: Double = 2.0,
) {
    fun modelFor(tier: ModelTier): String = models.getValue(tier)

    companion object {
        fun defaultModels(provider: LlmProvider): Map<ModelTier, String> = when (provider) {
            LlmProvider.CLAUDE -> mapOf(ModelTier.DAILY to "claude-haiku-5-5", ModelTier.WEEKLY to "claude-opus-5-5")
            // Sprawdź aktualne nazwy modeli w panelu OpenAI; można je zmienić w ustawieniach.
            LlmProvider.OPENAI -> mapOf(ModelTier.DAILY to "gpt-5-mini", ModelTier.WEEKLY to "gpt-5")
        }
    }
}

/** Cena w USD za milion tokenów. */
data class ModelPrice(val inputPerMTok: Double, val outputPerMTok: Double) {
    fun costUsd(inputTokens: Long, outputTokens: Long): Double =
        (inputTokens * inputPerMTok + outputTokens * outputPerMTok) / 1_000_000.0
}

object ModelPrices {
    /** Ceny z cenników dostawców; aktualizować przy zmianach. Nieznany model liczymy wg najdroższej stawki. */
    val known: Map<String, ModelPrice> = mapOf(
        "claude-haiku-5-5" to ModelPrice(0.10, 0.50),
        "claude-sonnet-5-5" to ModelPrice(2.0, 10.0),
        "claude-opus-5-5" to ModelPrice(4.0, 20.0),
        "gpt-5-mini" to ModelPrice(0.25, 2.0),
        "gpt-5" to ModelPrice(1.25, 10.0),
    )

    private val fallback = ModelPrice(10.0, 50.0)

    fun of(model: String): ModelPrice = known[model] ?: fallback
}
