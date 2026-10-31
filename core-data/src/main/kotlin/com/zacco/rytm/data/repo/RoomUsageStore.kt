package com.zacco.rytm.data.repo

import com.zacco.rytm.ai.cost.UsageStore
import com.zacco.rytm.data.db.LlmUsageDao
import com.zacco.rytm.data.db.LlmUsageEntity
import java.time.Instant
import java.time.YearMonth

/** Licznik kosztów LLM w bazie, żeby budżet miesięczny przetrwał restart aplikacji. */
class RoomUsageStore(private val dao: LlmUsageDao) : UsageStore {
    override suspend fun spentUsd(month: YearMonth): Double = dao.spentUsd(month.toString())

    override suspend fun record(month: YearMonth, model: String, inputTokens: Long, outputTokens: Long, costUsd: Double) {
        dao.insert(LlmUsageEntity(month = month.toString(), model = model, inputTokens = inputTokens, outputTokens = outputTokens, costUsd = costUsd, at = Instant.now()))
    }
}
