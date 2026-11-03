package com.zacco.rytm.data.ai

import android.content.Context
import android.util.Log
import com.zacco.rytm.ai.AiSettings
import com.zacco.rytm.ai.CoachLlm
import com.zacco.rytm.ai.plan.PlanTweakResult
import com.zacco.rytm.ai.plan.PlanTweakService
import com.zacco.rytm.domain.profile.PersonalProfile
import com.zacco.rytm.ai.LlmProvider
import com.zacco.rytm.ai.SavedEveningReview
import com.zacco.rytm.ai.SavedMorningNote
import com.zacco.rytm.ai.SavedWeeklyReport
import com.zacco.rytm.ai.morning.MorningInput
import com.zacco.rytm.ai.morning.MorningService
import com.zacco.rytm.ai.weekly.WeeklyInputs
import com.zacco.rytm.ai.weekly.WeeklyReportService
import com.zacco.rytm.ai.claude.ClaudeCoachLlm
import com.zacco.rytm.ai.cost.CostGuard
import com.zacco.rytm.ai.evening.EveningInputs
import com.zacco.rytm.ai.evening.EveningResult
import com.zacco.rytm.ai.evening.EveningReviewService
import com.zacco.rytm.ai.evening.RuleBasedEveningReview
import com.zacco.rytm.ai.openai.OpenAiCoachLlm
import com.zacco.rytm.data.repo.CoachRepository
import com.zacco.rytm.ai.cost.UsageStore
import com.zacco.rytm.domain.coach.CoachEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * Asystent AI na tym telefonie: klucz (zaszyfrowany), ustawienia, budżet i zapisane wieczorne analizy.
 * Bez klucza albo po wyczerpaniu budżetu wszystko działa na regułach.
 */
class AiCoach(
    context: Context,
    private val coach: CoachRepository,
    private val usage: UsageStore,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val keys = AiKeyStore(context)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AiSettings> = _settings.asStateFlow()

    private val _reviews = MutableStateFlow(loadReviews())
    val reviews: StateFlow<List<SavedEveningReview>> = _reviews.asStateFlow()

    private val _weekly = MutableStateFlow(loadList(KEY_WEEKLY, SavedWeeklyReport.serializer()))
    val weeklyReports: StateFlow<List<SavedWeeklyReport>> = _weekly.asStateFlow()

    private val _morning = MutableStateFlow(loadList(KEY_MORNING, SavedMorningNote.serializer()))
    val morningNotes: StateFlow<List<SavedMorningNote>> = _morning.asStateFlow()

    /** Zmienia się przy zapisie i usunięciu klucza, żeby ekran od razu wiedział, czy AI jest aktywne. */
    private val _hasKey = MutableStateFlow(keys.has(_settings.value.provider))
    val hasKey: StateFlow<Boolean> = _hasKey.asStateFlow()

    fun saveSettings(s: AiSettings) {
        val clean = s.copy(monthlyBudgetUsd = s.monthlyBudgetUsd.coerceIn(0.0, 50.0), eveningAtMin = s.eveningAtMin.coerceIn(0, 24 * 60 - 1))
        prefs.edit().putString(KEY_SETTINGS, json.encodeToString(AiSettings.serializer(), clean)).apply()
        _settings.value = clean
        _hasKey.value = keys.has(clean.provider)
    }

    fun saveKey(provider: LlmProvider, apiKey: String) {
        if (apiKey.isBlank()) return
        keys.set(provider, apiKey)
        _hasKey.value = keys.has(_settings.value.provider)
    }

    fun clearKey(provider: LlmProvider) {
        keys.clear(provider)
        _hasKey.value = keys.has(_settings.value.provider)
    }

    suspend fun spentThisMonth(): Double = costGuard().spentThisMonth()

    fun reviewFor(date: LocalDate): SavedEveningReview? = _reviews.value.firstOrNull { it.date == date.toString() }

    /**
     * Wieczorna analiza dnia. Gotowej analizy z tego dnia nie liczymy drugi raz (koszt), chyba że [force]
     * albo zmieniła się refleksja.
     */
    suspend fun evening(date: LocalDate, reflection: String? = null, force: Boolean = false): SavedEveningReview {
        val existing = reviewFor(date)
        if (existing != null && !force && reflection == existing.reflection && existing.source == "LLM") return existing

        val data = coach.observe(date).first()
        val today = CoachEngine().today(data)
        val input = EveningInputs.from(data, today, reflection)
        val result: EveningResult = runCatching {
            EveningReviewService(llm(), costGuard()).review(input)
        }.getOrElse { e ->
            Log.w(TAG, "Analiza AI nie powiodła się", e)
            EveningResult(RuleBasedEveningReview.build(input), EveningResult.Source.RULES, error = e.message)
        }
        val saved = SavedEveningReview(
            date = date.toString(),
            review = result.review,
            source = result.source.name,
            model = result.model,
            costUsd = result.costUsd,
            error = result.error,
            createdAt = System.currentTimeMillis(),
            reflection = reflection?.trim()?.takeIf { it.isNotEmpty() },
        )
        val list = (listOf(saved) + _reviews.value.filterNot { it.date == saved.date }).sortedByDescending { it.date }.take(KEEP_DAYS)
        prefs.edit().putString(KEY_REVIEWS, json.encodeToString(ListSerializer(SavedEveningReview.serializer()), list)).apply()
        _reviews.value = list
        return saved
    }

    fun morningFor(date: LocalDate): SavedMorningNote? = _morning.value.firstOrNull { it.date == date.toString() }

    /**
     * Poranne uzasadnienie planu po check-inie. Ten sam check-in nie jest liczony drugi raz.
     * Bez check-inu zwraca null (notka ma sens dopiero, gdy wiemy, jak się czujesz).
     */
    suspend fun morning(date: LocalDate, force: Boolean = false): SavedMorningNote? {
        val data = coach.observe(date).first()
        val today = CoachEngine().today(data)
        val checkIn = today.checkIn ?: return null
        val key = listOf(checkIn.energy, checkIn.mood, checkIn.soreAreas.map { it.name }.sorted(), today.plan.title, today.readiness.score).joinToString("|")
        val existing = morningFor(date)
        if (!force && existing != null && existing.checkInKey == key) return existing
        val input = MorningInput.from(data, today)
        val llm = if (_settings.value.morningEnabled) llm() else null
        val result = runCatching { MorningService(llm, costGuard()).note(input) }
            .getOrElse { e ->
                Log.w(TAG, "Poranna notka nieudana", e)
                MorningService(null, null).note(input).copy(error = e.message)
            }
        val saved = SavedMorningNote(
            date = date.toString(),
            note = result.note,
            source = if (result.fromLlm) "LLM" else "RULES",
            checkInKey = key,
            costUsd = result.costUsd,
            error = result.error,
            createdAt = System.currentTimeMillis(),
        )
        val list = (listOf(saved) + _morning.value.filterNot { it.date == saved.date }).sortedByDescending { it.date }.take(KEEP_DAYS)
        saveList(KEY_MORNING, SavedMorningNote.serializer(), list)
        _morning.value = list
        return saved
    }

    fun weeklyFor(weekEnd: LocalDate): SavedWeeklyReport? = _weekly.value.firstOrNull { it.weekEnd == weekEnd.toString() }

    /**
     * Raport tygodniowy za 7 dni kończących się [weekEnd]. Gotowego raportu z AI nie liczymy ponownie
     * (droższy model), chyba że [force].
     */
    suspend fun weekly(weekEnd: LocalDate, force: Boolean = false): SavedWeeklyReport {
        val existing = weeklyFor(weekEnd)
        if (existing != null && !force && existing.source == "LLM") return existing
        val data = coach.observe(weekEnd).first()
        val from = weekEnd.minusDays(6).toString()
        val reflections = _reviews.value
            .filter { it.date in from..weekEnd.toString() }
            .sortedBy { it.date }
            .mapNotNull { r -> r.reflection?.let { "${r.date}: $it" } }
        val input = WeeklyInputs.from(data, weekEnd, reflections)
        val result = runCatching { WeeklyReportService(llm(), costGuard()).report(input) }
            .getOrElse { e ->
                Log.w(TAG, "Raport tygodniowy nieudany", e)
                WeeklyReportService(null, null).report(input).copy(error = e.message)
            }
        val saved = SavedWeeklyReport(
            weekEnd = weekEnd.toString(),
            report = result.report,
            source = if (result.fromLlm) "LLM" else "RULES",
            model = result.model,
            costUsd = result.costUsd,
            error = result.error,
            createdAt = System.currentTimeMillis(),
        )
        val list = (listOf(saved) + _weekly.value.filterNot { it.weekEnd == saved.weekEnd }).sortedByDescending { it.weekEnd }.take(KEEP_WEEKS)
        saveList(KEY_WEEKLY, SavedWeeklyReport.serializer(), list)
        _weekly.value = list
        return saved
    }

    /** „Popraw z AI” w kreatorze: zmiana ustawień planu z prośby słowami. Bez klucza zwraca błąd do pokazania. */
    suspend fun tweakPlan(profile: PersonalProfile, request: String): PlanTweakResult {
        val llm = llm() ?: return PlanTweakResult(null, error = "Brak klucza AI. Dodasz go później w zakładce Wieczór; na razie popraw kroki ręcznie.")
        return runCatching { PlanTweakService(llm, costGuard()).tweak(profile, request) }
            .getOrElse { e ->
                Log.w(TAG, "Poprawka planu z AI nie powiodła się", e)
                PlanTweakResult(null, error = e.message)
            }
    }

    private fun llm(): CoachLlm? {
        val s = _settings.value
        val key = keys.get(s.provider) ?: return null
        return when (s.provider) {
            LlmProvider.CLAUDE -> ClaudeCoachLlm(key) { _settings.value.toLlmSettings() }
            LlmProvider.OPENAI -> OpenAiCoachLlm(key) { _settings.value.toLlmSettings() }
        }
    }

    private fun costGuard() = CostGuard(usage, { _settings.value.monthlyBudgetUsd })

    private fun loadSettings(): AiSettings =
        prefs.getString(KEY_SETTINGS, null)?.let { runCatching { json.decodeFromString(AiSettings.serializer(), it) }.getOrNull() }
            ?: AiSettings()

    private fun loadReviews(): List<SavedEveningReview> =
        prefs.getString(KEY_REVIEWS, null)?.let {
            runCatching { json.decodeFromString(ListSerializer(SavedEveningReview.serializer()), it) }.getOrNull()
        }.orEmpty()

    private fun <T> loadList(key: String, item: KSerializer<T>): List<T> =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString(ListSerializer(item), it) }.getOrNull() }.orEmpty()

    private fun <T> saveList(key: String, item: KSerializer<T>, list: List<T>) {
        prefs.edit().putString(key, json.encodeToString(ListSerializer(item), list)).apply()
    }

    private companion object {
        const val TAG = "RytmAi"
        const val PREFS = "ai"
        const val KEY_SETTINGS = "settings"
        const val KEY_REVIEWS = "reviews"
        const val KEY_WEEKLY = "weekly"
        const val KEY_MORNING = "morning"
        const val KEEP_DAYS = 14
        const val KEEP_WEEKS = 12
    }
}
