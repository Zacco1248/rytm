package com.zacco.rytm.ai.evening

import kotlinx.serialization.Serializable

/** Wynik wieczornej analizy, z LLM albo z silnika reguł. */
@Serializable
data class EveningReview(
    /** Co się wydarzyło, 1–2 zdania. */
    val summary: String,
    /** Jak dzień wpłynął na regenerację, np. „squash + remont = wysoki load, jutro lżej”. */
    val interpretation: String,
    /** Rekomendacja na jutro zgodna z planem. */
    val tomorrow: String,
    /** HH:MM */
    val bedtime: String,
    /** 0–3 krótkie punkty do zapamiętania. */
    val focusPoints: List<String>,
) {
    fun validate(): EveningReview {
        require(summary.isNotBlank() && interpretation.isNotBlank() && tomorrow.isNotBlank()) { "puste pole" }
        require(BEDTIME.matches(bedtime)) { "zła pora snu: $bedtime" }
        require(focusPoints.size <= 3) { "za dużo punktów" }
        return this
    }

    companion object {
        private val BEDTIME = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

        /** Schemat JSON do structured outputs (Claude i OpenAI strict). */
        val jsonSchema: Map<String, Any?> = mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "required" to listOf("summary", "interpretation", "tomorrow", "bedtime", "focusPoints"),
            "properties" to mapOf(
                "summary" to mapOf("type" to "string", "description" to "Co się dziś wydarzyło, 1–2 zdania."),
                "interpretation" to mapOf("type" to "string", "description" to "Jak dzień wpłynął na regenerację i obciążenie."),
                "tomorrow" to mapOf("type" to "string", "description" to "Rekomendacja na jutro zgodna z tomorrowPlan."),
                "bedtime" to mapOf("type" to "string", "description" to "Pora snu HH:MM, równa suggestedBedtime."),
                "focusPoints" to mapOf(
                    "type" to "array",
                    "items" to mapOf("type" to "string"),
                    "description" to "Maksymalnie 3 krótkie punkty.",
                ),
            ),
        )
    }
}
