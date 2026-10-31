package com.zacco.rytm.ai.cost

import com.zacco.rytm.ai.LlmException
import com.zacco.rytm.ai.LlmResponse
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class CostGuardTest {

    private val clock = Clock.fixed(Instant.parse("2026-11-15T20:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `records cost and blocks after budget`() = runTest {
        val guard = CostGuard(InMemoryUsageStore(), { 0.05 }, clock)

        guard.ensureBudget()
        val cost = guard.record(LlmResponse("{}", "claude-opus-5-5", inputTokens = 5_000, outputTokens = 2_000))

        assertEquals(0.06, cost, 1e-12)
        assertThrows<LlmException.BudgetExceeded> { guard.ensureBudget() }
    }

    @Test
    fun `unknown model is priced conservatively`() = runTest {
        val guard = CostGuard(InMemoryUsageStore(), { 10.0 }, clock)
        assertEquals(0.06, guard.record(LlmResponse("{}", "nowy-model", 1_000, 1_000)), 1e-12)
    }
}
