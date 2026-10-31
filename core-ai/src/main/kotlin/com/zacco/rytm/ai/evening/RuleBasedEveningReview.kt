package com.zacco.rytm.ai.evening

/** Fallback bez LLM: ten sam kształt odpowiedzi, złożony z reguł. */
object RuleBasedEveningReview {

    fun build(input: EveningInput): EveningReview {
        val activities = if (input.activities.isEmpty()) {
            "Dziś bez zarejestrowanych treningów"
        } else {
            "Dziś: " + input.activities.joinToString { a ->
                val name = if (a.type == "OTHER") "inna aktywność" + (a.note?.let { " ($it)" } ?: "") else a.type.lowercase()
                "$name ${a.minutes} min"
            }
        }
        val nutrition = input.proteinG?.let { p ->
            if (p < input.proteinTarget * 0.9) "białko $p g z ${input.proteinTarget} g, warto dobić" else "białko $p g, cel osiągnięty"
        }
        val summary = listOfNotNull(
            activities,
            input.steps?.let { "$it kroków" },
            nutrition,
            input.habitsDone.takeIf { it.isNotEmpty() }?.let { "nawyki: ${it.joinToString()}" },
        ).joinToString(", ") + "."

        val interpretation = when {
            input.acwr != null && input.acwr > 1.5 -> "Obciążenie z ostatnich dni jest wysokie (ACWR ${"%.1f".format(input.acwr)}), organizm potrzebuje lżejszego dnia."
            input.loadToday >= 600 -> "Dzisiejszy dzień był mocny (obciążenie ${input.loadToday}), regeneracja jest dziś priorytetem."
            input.loadToday == 0 -> "Dzień bez treningu sprzyja regeneracji."
            else -> "Obciążenie umiarkowane (${input.loadToday}), w normie."
        } + if (input.calibrating) " Wskaźniki dopiero się kalibrują." else ""

        val tomorrow = buildString {
            append("Jutro: ${input.tomorrowPlan}")
            append(
                when (input.tomorrowIntensity) {
                    "LOW" -> ", spokojnie."
                    "HIGH" -> ", pełna intensywność."
                    else -> ", umiarkowanie."
                },
            )
            input.tomorrowWarnings.forEach { append(" $it") }
        }

        return EveningReview(
            summary = summary,
            interpretation = interpretation,
            tomorrow = tomorrow,
            bedtime = input.suggestedBedtime,
            focusPoints = input.habitsMissed.take(2).map { "Jutro: $it" },
        )
    }
}
