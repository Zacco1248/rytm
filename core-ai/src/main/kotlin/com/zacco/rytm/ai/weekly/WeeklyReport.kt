package com.zacco.rytm.ai.weekly

import kotlinx.serialization.Serializable

/** Raport tygodniowy (niedziela wieczór): co poszło, co nie, trendy, zależności, sylwetka, plan i 1–3 sugestie. */
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
    /** Co poszło dobrze, 1–3 punkty. */
    val wins: List<String> = emptyList(),
    /** Co nie wyszło, 0–3 punkty (bez oceniania). */
    val misses: List<String> = emptyList(),
    /** Plan na kolejny tydzień: kluczowe dni i na czym się skupić, 2–4 zdania. */
    val nextWeek: String = "",
) {
    fun validate(): WeeklyReport {
        require(summary.isNotBlank() && body.isNotBlank()) { "puste pole" }
        require(suggestions.size in 1..3) { "sugestie: ${suggestions.size}" }
        require(trends.size <= 5 && correlations.size <= 4) { "za długie listy" }
        require(wins.size <= 3 && misses.size <= 3) { "za długie listy" }
        return this
    }

    companion object {
        private fun list(desc: String) = mapOf("type" to "array", "items" to mapOf("type" to "string"), "description" to desc)

        val jsonSchema: Map<String, Any?> = mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "required" to listOf("summary", "wins", "misses", "trends", "correlations", "body", "nextWeek", "suggestions"),
            "properties" to mapOf(
                "summary" to mapOf("type" to "string", "description" to "Jak minął tydzień, 2–3 zdania."),
                "wins" to list("1–3 rzeczy, które poszły dobrze, z liczbami."),
                "misses" to list("0–3 rzeczy, które nie wyszły, rzeczowo i bez oceniania."),
                "nextWeek" to mapOf("type" to "string", "description" to "Plan na kolejny tydzień: kluczowe dni z nextWeekPlan i na czym się skupić, 2–4 zdania."),
                "trends" to list("Do 5 trendów z liczbami, porównanie z poprzednim tygodniem."),
                "correlations" to list("Do 4 zależności widocznych w dniach (sen vs dni ze sportem, nawyki vs nastrój, białko vs regeneracja). Pusta lista, gdy danych za mało."),
                "body" to mapOf("type" to "string", "description" to "Sylwetka i siła: waga (średnia 7-dniowa), trend siły, zgodność z celem."),
                "suggestions" to list("1–3 konkretne sugestie na kolejny tydzień."),
            ),
        )
    }
}
