package com.zacco.rytm.ai

/** Ustawienia LLM z ekranu ustawień. Klucze API trzymamy poza repo (local.properties lub EncryptedSharedPreferences). */
data class LlmSettings(
    val provider: LlmProvider = LlmProvider.CLAUDE,
    val models: Map<ModelTier, String> = defaultModels(provider),
    /**
     * Miesięczny budżet w USD; po przekroczeniu tylko silnik reguł.
     * Szacunek przy Claude: ok. 2,1 USD (dzienne Haiku, tygodniowe Opus, miesięczna analiza Fable), stąd 3 USD.
     */
    val monthlyBudgetUsd: Double = 3.0,
) {
    fun modelFor(tier: ModelTier): String = models.getValue(tier)

    companion object {
        fun defaultModels(provider: LlmProvider): Map<ModelTier, String> = when (provider) {
            // Wybór użytkownika z 2026-10-31.
            LlmProvider.CLAUDE -> mapOf(
                ModelTier.DAILY to "claude-haiku-5-5",
                ModelTier.WEEKLY to "claude-opus-5-5",
                ModelTier.LONG_TERM to "claude-fable-5-1",
            )
            LlmProvider.OPENAI -> mapOf(
                ModelTier.DAILY to "gpt-6.1-terra",
                ModelTier.WEEKLY to "gpt-6.1-sol",
                ModelTier.LONG_TERM to "gpt-6-astra",
            )
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
        "claude-fable-5-1" to ModelPrice(10.0, 50.0),
        // Ceny OpenAI z 2026-10-31 wg zestawień cenników; sprawdzić na openai.com/api/pricing.
        "gpt-6.1-terra" to ModelPrice(1.0, 5.0),
        "gpt-6.1-sol" to ModelPrice(2.0, 10.0),
        "gpt-6-astra" to ModelPrice(10.0, 50.0),
    )

    private val fallback = ModelPrice(10.0, 50.0)

    fun of(model: String): ModelPrice = known[model] ?: fallback
}
