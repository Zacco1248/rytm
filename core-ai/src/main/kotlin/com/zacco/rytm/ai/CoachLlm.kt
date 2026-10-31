package com.zacco.rytm.ai

/**
 * Punkt wpięcia modelu językowego (Claude API albo lokalny model na domowym serwerze).
 * Implementacje i schematy JSON dochodzą w fazie 4.
 */
interface CoachLlm {
    suspend fun complete(systemPrompt: String, userPrompt: String): String
}
