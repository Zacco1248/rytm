package com.zacco.rytm.ai.weekly

import kotlinx.serialization.Serializable

/** Raport tygodniowy (niedziela wieczór): trendy, zależności, sylwetka i 1–3 sugestie. */
@Serializable
data class WeeklyReport(
    /** Jak minął tydzień, 2–3 zdania. */
    val summary: String,
    /** Trendy w liczbach (sen, obciążenie, kroki, waga). */
    val trends: List<String>,
    /** Zależności z danych, np. „po meczach śpisz o 40 min krócej”. */
    val correlations: List<String>,
    /** Postęp sylwetki i siły. */
    val body: String,
    /** 1–3 konkretne sugestie na kolejny tydzień (trening, żywienie, nawyki). */
    val suggestions: List<String>,
) {
    fun validate(): WeeklyReport {
        require(summary.isNotBlank() && body.isNotBlank()) { "puste pole" }
        require(suggestions.size in 1..3) { "sugestie: ${suggestions.size}" }
        require(trends.size <= 5 && correlations.size <= 4) { "za długie listy" }
        return this
    }

    companion object {
        private fun list(desc: String) = mapOf("type" to "array", "items" to mapOf("type" to "string"), "description" to desc)

        val jsonSchema: Map<String, Any?> = mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "required" to listOf("summary", "trends", "correlations", "body", "suggestions"),
            "properties" to mapOf(
                "summary" to mapOf("type" to "string", "description" to "Jak minął tydzień, 2–3 zdania."),
                "trends" to list("Do 5 trendów z liczbami, porównanie z poprzednim tygodniem."),
                "correlations" to list("Do 4 zależności widocznych w dniach (sen vs dni ze sportem, nawyki vs nastrój, białko vs regeneracja). Pusta lista, gdy danych za mało."),
                "body" to mapOf("type" to "string", "description" to "Sylwetka i siła: waga (średnia 7-dniowa), trend siły, zgodność z celem."),
                "suggestions" to list("1–3 konkretne sugestie na kolejny tydzień."),
            ),
        )
    }
}
