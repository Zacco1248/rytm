package com.zacco.rytm.ai

import com.zacco.rytm.ai.evening.EveningReview
import kotlinx.serialization.Serializable

/**
 * Ustawienia asystenta AI na tym telefonie. Każda osoba ma własny budżet miesięczny,
 * nawet gdy klucz jest wspólny (np. znajomi na kluczu właściciela).
 */
@Serializable
data class AiSettings(
    val provider: LlmProvider = LlmProvider.CLAUDE,
    val monthlyBudgetUsd: Double = 3.0,
    val eveningEnabled: Boolean = true,
    /** Pora wieczornej analizy, minuty od północy (domyślnie 21:30). */
    val eveningAtMin: Int = 21 * 60 + 30,
) {
    fun toLlmSettings(): LlmSettings = LlmSettings(provider = provider, monthlyBudgetUsd = monthlyBudgetUsd)
}

/** Zapisana wieczorna analiza (ostatnie dni trzymamy lokalnie). */
@Serializable
data class SavedEveningReview(
    val date: String,
    val review: EveningReview,
    /** LLM albo RULES. */
    val source: String,
    val model: String? = null,
    val costUsd: Double = 0.0,
    val error: String? = null,
    val createdAt: Long,
    val reflection: String? = null,
)
