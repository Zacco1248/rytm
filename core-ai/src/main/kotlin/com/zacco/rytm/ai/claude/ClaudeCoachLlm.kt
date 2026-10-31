package com.zacco.rytm.ai.claude

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicException
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.zacco.rytm.ai.CoachLlm
import com.zacco.rytm.ai.LlmException
import com.zacco.rytm.ai.LlmProvider
import com.zacco.rytm.ai.LlmRequest
import com.zacco.rytm.ai.LlmResponse
import com.zacco.rytm.ai.LlmSettings
import com.zacco.rytm.ai.ModelTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Claude API przez oficjalne SDK (anthropic-java). Odpowiedź wymuszona schematem JSON (structured outputs). */
class ClaudeCoachLlm(
    private val client: AnthropicClient,
    private val settings: () -> LlmSettings,
) : CoachLlm {

    constructor(apiKey: String, settings: () -> LlmSettings) :
        this(AnthropicOkHttpClient.builder().apiKey(apiKey).build(), settings)

    override val provider = LlmProvider.CLAUDE

    override suspend fun complete(request: LlmRequest): LlmResponse = withContext(Dispatchers.IO) {
        val model = settings().modelFor(request.tier)
        val builder = MessageCreateParams.builder()
            .model(model)
            .maxTokens(request.maxOutputTokens)
            .system(request.system)
            .addUserMessage(request.user)
            .outputConfig(
                OutputConfig.builder()
                    .effort(if (request.tier == ModelTier.WEEKLY) OutputConfig.Effort.HIGH else OutputConfig.Effort.MEDIUM)
                    .format(
                        JsonOutputFormat.builder()
                            .schema(
                                JsonOutputFormat.Schema.builder()
                                    .putAllAdditionalProperties(request.jsonSchema.mapValues { JsonValue.from(it.value) })
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
        if (supportsServerSideFallback(model)) {
            // Gdy klasyfikator bezpieczeństwa odmówi, serwer sam ponawia na odpowiednim modelu.
            builder.putAdditionalHeader("anthropic-beta", SERVER_SIDE_FALLBACK_BETA)
            builder.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        }

        val message: Message = try {
            client.messages().create(builder.build())
        } catch (e: AnthropicException) {
            throw LlmException.Transport("Claude API: ${e.message}", e)
        }

        when (message.stopReason().orElse(null)) {
            StopReason.REFUSAL -> throw LlmException.Refused("Claude odmówił odpowiedzi")
            StopReason.MAX_TOKENS -> throw LlmException.Truncated("Odpowiedź ucięta na limicie tokenów")
            else -> Unit
        }

        val text = message.content().flatMap { block -> block.text().map { listOf(it.text()) }.orElse(emptyList()) }
            .joinToString("")
        LlmResponse(
            text = text,
            model = model,
            inputTokens = message.usage().inputTokens(),
            outputTokens = message.usage().outputTokens(),
        )
    }

    private fun supportsServerSideFallback(model: String) =
        model.startsWith("claude-opus-5") || model.startsWith("claude-fable-5") || model == "claude-sonnet-5-5"

    private companion object {
        const val SERVER_SIDE_FALLBACK_BETA = "server-side-fallback-2026-07-01"
    }
}
