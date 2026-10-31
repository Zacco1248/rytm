package com.zacco.rytm.ai.openai

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.core.JsonValue
import com.openai.errors.OpenAIException
import com.openai.models.ResponseFormatJsonSchema
import com.openai.models.chat.completions.ChatCompletion
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.zacco.rytm.ai.CoachLlm
import com.zacco.rytm.ai.LlmException
import com.zacco.rytm.ai.LlmProvider
import com.zacco.rytm.ai.LlmRequest
import com.zacco.rytm.ai.LlmResponse
import com.zacco.rytm.ai.LlmSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** OpenAI Chat Completions przez oficjalne SDK (openai-java), z wymuszonym schematem JSON. */
class OpenAiCoachLlm(
    private val client: OpenAIClient,
    private val settings: () -> LlmSettings,
) : CoachLlm {

    constructor(apiKey: String, settings: () -> LlmSettings) :
        this(OpenAIOkHttpClient.builder().apiKey(apiKey).build(), settings)

    override val provider = LlmProvider.OPENAI

    override suspend fun complete(request: LlmRequest): LlmResponse = withContext(Dispatchers.IO) {
        val model = settings().modelFor(request.tier)
        val params = ChatCompletionCreateParams.builder()
            .model(model)
            .maxCompletionTokens(request.maxOutputTokens)
            .addSystemMessage(request.system)
            .addUserMessage(request.user)
            .responseFormat(
                ResponseFormatJsonSchema.builder()
                    .jsonSchema(
                        ResponseFormatJsonSchema.JsonSchema.builder()
                            .name(request.schemaName)
                            .strict(true)
                            .schema(
                                ResponseFormatJsonSchema.JsonSchema.Schema.builder()
                                    .putAllAdditionalProperties(request.jsonSchema.mapValues { JsonValue.from(it.value) })
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()

        val completion: ChatCompletion = try {
            client.chat().completions().create(params)
        } catch (e: OpenAIException) {
            throw LlmException.Transport("OpenAI API: ${e.message}", e)
        }

        val choice = completion.choices().firstOrNull() ?: throw LlmException.InvalidOutput("Brak odpowiedzi")
        choice.message().refusal().ifPresent { throw LlmException.Refused("OpenAI odmówił: $it") }
        if (choice.finishReason() == ChatCompletion.Choice.FinishReason.LENGTH) {
            throw LlmException.Truncated("Odpowiedź ucięta na limicie tokenów")
        }
        val usage = completion.usage().orElse(null)
        LlmResponse(
            text = choice.message().content().orElse(""),
            model = model,
            inputTokens = usage?.promptTokens() ?: 0,
            outputTokens = usage?.completionTokens() ?: 0,
        )
    }
}
