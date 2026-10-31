package com.zacco.rytm.ai.evening

import com.zacco.rytm.ai.CoachLlm
import com.zacco.rytm.ai.LlmException
import com.zacco.rytm.ai.LlmProvider
import com.zacco.rytm.ai.LlmRequest
import com.zacco.rytm.ai.LlmResponse
import com.zacco.rytm.ai.cost.CostGuard
import com.zacco.rytm.ai.cost.InMemoryUsageStore
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EveningReviewServiceTest {

    private val input = EveningInput(
        date = "2026-11-04", weekday = "środa", plannedToday = "Squash",
        activities = listOf(ActivityLine("SQUASH", 60, 8, 480), ActivityLine("OTHER", 120, 5, 600, note = "remont")),
        loadToday = 1080, acwr = 1.4, readinessToday = 68, readinessFlags = emptyList(),
        sleepLastNightMin = 410, steps = 9400, kcal = 2250, kcalTarget = 2300, proteinG = 128, proteinTarget = 161,
        energy = 3, mood = 4, soreAreas = listOf("SHOULDERS"),
        habitsDone = listOf("Bez ekranu po 23:30"), habitsMissed = listOf("10 min mobilności"),
        reflection = null, tomorrowPlan = "Siatkówka", tomorrowIntensity = "HIGH",
        tomorrowWarnings = listOf("Wczoraj ciężka aktywność (remont): dziś bez dużego obciążenia partii, które pracowały."),
        suggestedBedtime = "23:30", calibrating = true,
    )

    private class FakeLlm(private val answer: () -> String) : CoachLlm {
        var lastRequest: LlmRequest? = null
        override val provider = LlmProvider.CLAUDE
        override suspend fun complete(request: LlmRequest): LlmResponse {
            lastRequest = request
            return LlmResponse(answer(), "claude-haiku-5-5", inputTokens = 1_500, outputTokens = 400)
        }
    }

    private val validJson = """
        {"summary":"Squash i dwie godziny remontu.","interpretation":"Wysoki load.","tomorrow":"Siatkówka spokojniej.",
         "bedtime":"01:00","focusPoints":["Dobij białko"]}
    """.trimIndent()

    @Test
    fun `llm answer is parsed and bedtime comes from rules`() = runTest {
        val llm = FakeLlm { validJson }
        val guard = CostGuard(InMemoryUsageStore(), { 2.0 })

        val result = EveningReviewService(llm, guard).review(input)

        assertEquals(EveningResult.Source.LLM, result.source)
        assertEquals("23:30", result.review.bedtime)
        assertEquals((1_500 * 0.10 + 400 * 0.50) / 1_000_000, result.costUsd, 1e-12)
        assertTrue("\"loadToday\":1080" in llm.lastRequest!!.user)
    }

    @Test
    fun `invalid json falls back to rules`() = runTest {
        val result = EveningReviewService(FakeLlm { "nie json" }, null).review(input)

        assertEquals(EveningResult.Source.RULES, result.source)
        assertNotNull(result.error)
        assertEquals("23:30", result.review.bedtime)
    }

    @Test
    fun `exhausted budget skips the llm`() = runTest {
        val store = InMemoryUsageStore()
        val guard = CostGuard(store, { 0.0 })
        val llm = FakeLlm { validJson }

        val result = EveningReviewService(llm, guard).review(input)

        assertEquals(EveningResult.Source.RULES, result.source)
        assertNull(llm.lastRequest)
    }

    @Test
    fun `transport errors fall back to rules`() = runTest {
        val failing = object : CoachLlm {
            override val provider = LlmProvider.OPENAI
            override suspend fun complete(request: LlmRequest): LlmResponse = throw LlmException.Transport("timeout", null)
        }
        assertEquals(EveningResult.Source.RULES, EveningReviewService(failing, null).review(input).source)
    }

    @Test
    fun `rule based review mentions protein gap and warnings`() {
        val review = RuleBasedEveningReview.build(input).validate()

        assertTrue("128 g z 161 g" in review.summary)
        assertTrue("partii, które pracowały" in review.tomorrow)
        assertTrue("inna aktywność (remont)" in review.summary)
        assertTrue("kalibrują" in review.interpretation)
    }

    @Test
    fun `schema is strict for structured outputs`() {
        val schema = EveningReview.jsonSchema
        @Suppress("UNCHECKED_CAST")
        val properties = schema["properties"] as Map<String, Any?>
        assertEquals(false, schema["additionalProperties"])
        assertEquals(properties.keys, (schema["required"] as List<*>).toSet())
    }
}
