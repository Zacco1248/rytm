package com.zacco.rytm.ai.cost

import com.zacco.rytm.ai.LlmException
import com.zacco.rytm.ai.LlmResponse
import com.zacco.rytm.ai.ModelPrices
import java.time.Clock
import java.time.YearMonth

/** Licznik tokenów i kosztów; implementacja trwała (Room) w :core-data. */
interface UsageStore {
    suspend fun spentUsd(month: YearMonth): Double
    suspend fun record(month: YearMonth, model: String, inputTokens: Long, outputTokens: Long, costUsd: Double)
}

class InMemoryUsageStore : UsageStore {
    private val spent = mutableMapOf<YearMonth, Double>()
    override suspend fun spentUsd(month: YearMonth) = spent[month] ?: 0.0
    override suspend fun record(month: YearMonth, model: String, inputTokens: Long, outputTokens: Long, costUsd: Double) {
        spent[month] = spentUsd(month) + costUsd
    }
}

/** Miesięczny limit kosztów (sekcja 10): po przekroczeniu tylko reguły. */
class CostGuard(
    private val store: UsageStore,
    private val monthlyBudgetUsd: () -> Double,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private fun month() = YearMonth.now(clock)

    suspend fun ensureBudget() {
        val spent = store.spentUsd(month())
        if (spent >= monthlyBudgetUsd()) {
            throw LlmException.BudgetExceeded("Budżet LLM na ten miesiąc wyczerpany (%.2f USD)".format(java.util.Locale.ROOT, spent))
        }
    }

    suspend fun record(response: LlmResponse): Double {
        val cost = ModelPrices.of(response.model).costUsd(response.inputTokens, response.outputTokens)
        store.record(month(), response.model, response.inputTokens, response.outputTokens, cost)
        return cost
    }

    suspend fun spentThisMonth(): Double = store.spentUsd(month())
}
