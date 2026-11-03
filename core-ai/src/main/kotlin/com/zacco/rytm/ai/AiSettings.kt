package com.zacco.rytm.ai

import com.zacco.rytm.ai.evening.EveningReview
import com.zacco.rytm.ai.monthly.MonthlyReport
import com.zacco.rytm.ai.morning.MorningNote
import com.zacco.rytm.ai.weekly.WeeklyReport
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
    /** Raport tygodniowy w niedzielę o 20:00 (mocniejszy model, ok. 0,05 USD). */
    val weeklyEnabled: Boolean = true,
    /** Krótkie uzasadnienie planu po porannym check-inie (tani model). */
    val morningEnabled: Boolean = true,
    /** Analiza długoterminowa 1. dnia miesiąca o 19:00 (najmocniejszy model, ok. 0,3 USD). */
    val monthlyEnabled: Boolean = true,
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

/** Zapisany raport tygodniowy; [weekEnd] to ostatni dzień tygodnia (zwykle niedziela). */
@Serializable
data class SavedWeeklyReport(
    val weekEnd: String,
    val report: WeeklyReport,
    /** LLM albo RULES. */
    val source: String,
    val model: String? = null,
    val costUsd: Double = 0.0,
    val error: String? = null,
    val createdAt: Long,
)

/** Zapisana poranna notka; [checkInKey] pozwala nie liczyć jej drugi raz dla tego samego check-inu. */
@Serializable
data class SavedMorningNote(
    val date: String,
    val note: MorningNote,
    val source: String,
    val checkInKey: String,
    val costUsd: Double = 0.0,
    val error: String? = null,
    val createdAt: Long,
)

/** Zapisana analiza długoterminowa; [periodEnd] to ostatni dzień analizowanego okresu. */
@Serializable
data class SavedMonthlyReport(
    val periodEnd: String,
    val report: MonthlyReport,
    val source: String,
    val model: String? = null,
    val costUsd: Double = 0.0,
    val error: String? = null,
    val createdAt: Long,
)
