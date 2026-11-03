package com.zacco.rytm.ai.weekly

import com.zacco.rytm.ai.CoachPersona

import kotlinx.serialization.json.Json

object WeeklyPrompt {
    private val json = Json { encodeDefaults = true }

    val system: String = """
        ${CoachPersona.ROLE}
        Piszesz po polsku, krótko, konkretnie i życzliwie.
        Dostajesz zagregowane dane tygodnia jako JSON (dni, sumy, poprzedni tydzień, sylwetka). Przygotuj niedzielny raport:
        - summary: jak minął tydzień,
        - trends: zmiany w liczbach względem poprzedniego tygodnia,
        - correlations: zależności, które naprawdę widać w dniach (np. sen po meczach, nawyki a nastrój, białko a gotowość). Nie zgaduj; przy 1–2 przypadkach pisz ostrożnie („w tym tygodniu…”),
        - body: waga z linii średniej (nie z pojedynczych ważeń), trend siły i co to znaczy dla celu,
        - suggestions: 1–3 konkretne kroki na kolejny tydzień.
        Zasady:
        - Plan tygodnia (nextWeekPlan) układa silnik reguł. Możesz zasugerować zmianę, ale napisz, że użytkownik zatwierdza ją w profilu.
        - Nie dawaj porad medycznych. Przy bólu sugeruj odpuszczenie partii, przy utrzymującym się bólu konsultację.
        - Pole null oznacza brak danych; nie wymyślaj liczb. Gdy danych jest mało, powiedz, co warto zapisywać.
        - Uwzględnij cel (goal, goalNote): przy rekompozycji i redukcji białko i utrzymanie siły, przy masie nadwyżka kcal, przy wytrzymałości regeneracja i regularność cardio.
        - Odwołuj się do refleksji użytkownika, jeśli są.
    """.trimIndent()

    fun user(input: WeeklyInput): String = "Dane tygodnia:\n" + json.encodeToString(WeeklyInput.serializer(), input)
}
