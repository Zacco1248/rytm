package com.zacco.rytm.phone.app

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zacco.rytm.data.RytmData
import com.zacco.rytm.ai.AiSettings
import com.zacco.rytm.ai.LlmProvider
import com.zacco.rytm.ai.SavedEveningReview
import com.zacco.rytm.data.ai.EveningWorker
import com.zacco.rytm.data.ai.WeeklyWorker
import com.zacco.rytm.data.ai.MonthlyWorker
import com.zacco.rytm.ai.SavedMonthlyReport
import com.zacco.rytm.ai.SavedMorningNote
import com.zacco.rytm.ai.SavedWeeklyReport
import com.zacco.rytm.data.healthconnect.HealthConnectPermissions
import com.zacco.rytm.data.sync.HealthConnectSync
import com.zacco.rytm.data.sync.SyncWorker
import com.zacco.rytm.domain.coach.BodySnapshot
import com.zacco.rytm.domain.coach.CoachEngine
import com.zacco.rytm.domain.coach.TodaySnapshot
import com.zacco.rytm.domain.coach.WeekSnapshot
import com.zacco.rytm.domain.habits.Habit
import com.zacco.rytm.domain.habits.HabitKind
import com.zacco.rytm.domain.model.BodyArea
import com.zacco.rytm.domain.model.BodyComposition
import com.zacco.rytm.domain.model.NutritionDay
import com.zacco.rytm.domain.model.DailyCheckIn
import com.zacco.rytm.domain.model.LoadedArea
import com.zacco.rytm.domain.profile.PersonalProfile
import com.zacco.rytm.domain.time.RytmTime
import com.zacco.rytm.data.watch.WatchBridge
import com.zacco.rytm.domain.model.Source
import com.zacco.rytm.domain.workout.SetLog
import com.zacco.rytm.domain.workout.WorkoutLog
import com.zacco.rytm.phone.today.SetEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

enum class HcStatus { CHECKING, NOT_INSTALLED, NEEDS_PERMISSIONS, READY }

data class SyncUi(val running: Boolean = false, val error: String? = null)

data class CoachUiState(
    val today: TodaySnapshot,
    val week: WeekSnapshot,
    val body: BodySnapshot,
    /** Najbliższy mecz i trening personalny (dziś lub później w tym tygodniu), do przycisków „odwołany”. */
    val nextMatch: LocalDate?,
    val nextPersonal: LocalDate?,
    /** Nazwa najbliższego stałego sportu (np. „Siatkówka”) do przycisku odwołania. */
    val nextMatchName: String? = null,
    val cancelled: Set<LocalDate>,
    val personalTrainingUntil: LocalDate?,
    /** Żywienie z ostatnich dni, do wpisu wstecz. */
    val recentNutrition: List<NutritionDay>,
    /** Porcje alkoholu z ostatnich 14 dni, do wpisu na wybrany dzień. */
    val recentAlcohol: Map<LocalDate, Int> = emptyMap(),
)

/** Jeden ViewModel na trzy ekrany: wszystkie liczą się z tych samych danych i tego samego silnika. */
@OptIn(ExperimentalCoroutinesApi::class)
class CoachViewModel(app: Application) : AndroidViewModel(app) {

    private val data = RytmData.get(app)
    private val engine = CoachEngine()
    /** Profil osoby; null przed kreatorem. */
    val profile: StateFlow<PersonalProfile?> = data.profiles.profile
    val profileReady: StateFlow<Boolean> = data.profiles.ready

    private val _hc = MutableStateFlow(HcStatus.CHECKING)
    val hc: StateFlow<HcStatus> = _hc.asStateFlow()

    private val _sync = MutableStateFlow(SyncUi())
    val sync: StateFlow<SyncUi> = _sync.asStateFlow()

    /** Dzień zmienia się o północy także przy otwartej aplikacji. */
    private val today = flow {
        while (true) {
            emit(LocalDate.now(RytmTime.zone))
            delay(60_000)
        }
    }.distinctUntilChanged()

    val state: StateFlow<CoachUiState?> = today
        .flatMapLatest { day -> data.coach.observe(day) }
        .map { d ->
            val planner = d.profile.planner
            val upcoming = (0L..6L).map { d.today.plusDays(it) }.filter { it <= d.today.with(java.time.DayOfWeek.SUNDAY) }
            CoachUiState(
                today = engine.today(d),
                week = engine.week(d),
                body = engine.body(d),
                nextMatch = upcoming.firstOrNull { it.dayOfWeek in planner.fixedSports },
                nextPersonal = upcoming.firstOrNull { planner.hasPersonalTraining(it) },
                nextMatchName = upcoming.firstOrNull { it.dayOfWeek in planner.fixedSports }?.let { m ->
                    planner.sportNames[m.dayOfWeek]?.takeIf { it.isNotBlank() } ?: planner.fixedSports[m.dayOfWeek]?.let(Format::activity)
                },
                cancelled = d.cancelled,
                personalTrainingUntil = planner.personalTrainingUntil,
                recentNutrition = d.nutrition.filter { it.date >= d.today.minusDays(13) },
                recentAlcohol = d.dayNotes.filter { it.date >= d.today.minusDays(13) }
                    .groupBy { it.date }.mapValues { (_, l) -> l.sumOf { it.alcoholDrinks } },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // --- asystent AI (faza 4) ---
    val aiSettings: StateFlow<AiSettings> = data.ai.settings
    val aiHasKey: StateFlow<Boolean> = data.ai.hasKey
    val aiReviews: StateFlow<List<SavedEveningReview>> = data.ai.reviews
    val morningNotes: StateFlow<List<SavedMorningNote>> = data.ai.morningNotes
    val weeklyReports: StateFlow<List<SavedWeeklyReport>> = data.ai.weeklyReports
    val weekPlans: StateFlow<List<com.zacco.rytm.domain.coach.WeekPlan>> = data.ai.weekPlans

    /** Zatwierdzenie albo cofnięcie planu siłowego od trenera; zegarek dostaje nowy plan. */
    fun acceptWeekPlan(weekStart: String, accepted: Boolean) {
        data.ai.acceptWeekPlan(weekStart, accepted)
        viewModelScope.launch { WatchBridge.publish(getApplication()) }
    }

    fun dropWeekPlan(weekStart: String) {
        data.ai.dropWeekPlan(weekStart)
        viewModelScope.launch { WatchBridge.publish(getApplication()) }
    }
    private val _weeklyRunning = MutableStateFlow(false)
    val weeklyRunning: StateFlow<Boolean> = _weeklyRunning.asStateFlow()

    /** Raport tygodniowy na żądanie: za 7 dni kończących się dziś. */
    fun runWeekly() {
        if (_weeklyRunning.value) return
        _weeklyRunning.value = true
        viewModelScope.launch {
            try {
                data.ai.weekly(LocalDate.now(RytmTime.zone), force = true)
            } finally {
                _weeklyRunning.value = false
                refreshAiSpent()
            }
        }
    }

    val monthlyReports: StateFlow<List<SavedMonthlyReport>> = data.ai.monthlyReports
    private val _monthlyRunning = MutableStateFlow(false)
    val monthlyRunning: StateFlow<Boolean> = _monthlyRunning.asStateFlow()

    /** Analiza długoterminowa na żądanie: ok. 12 tygodni kończących się dziś. */
    fun runMonthly() {
        if (_monthlyRunning.value) return
        _monthlyRunning.value = true
        viewModelScope.launch {
            try {
                data.ai.monthly(LocalDate.now(RytmTime.zone), force = true)
            } finally {
                _monthlyRunning.value = false
                refreshAiSpent()
            }
        }
    }

    private val _aiRunning = MutableStateFlow(false)
    val aiRunning: StateFlow<Boolean> = _aiRunning.asStateFlow()
    private val _aiSpent = MutableStateFlow<Double?>(null)
    val aiSpent: StateFlow<Double?> = _aiSpent.asStateFlow()

    fun refreshAiSpent() {
        viewModelScope.launch { _aiSpent.value = runCatching { data.ai.spentThisMonth() }.getOrNull() }
    }

    fun saveAiSettings(s: AiSettings) {
        data.ai.saveSettings(s)
        EveningWorker.schedule(getApplication(), replace = true)
        WeeklyWorker.schedule(getApplication(), replace = true)
        MonthlyWorker.schedule(getApplication(), replace = true)
    }

    fun saveAiKey(provider: LlmProvider, key: String) = data.ai.saveKey(provider, key)

    fun clearAiKey(provider: LlmProvider) = data.ai.clearKey(provider)

    /** Analiza dnia na żądanie (np. po wpisaniu refleksji). */
    fun runEvening(reflection: String?) {
        if (_aiRunning.value) return
        _aiRunning.value = true
        viewModelScope.launch {
            try {
                data.ai.evening(LocalDate.now(RytmTime.zone), reflection, force = true)
                WatchBridge.publish(getApplication())
            } finally {
                _aiRunning.value = false
                refreshAiSpent()
            }
        }
    }

    init {
        viewModelScope.launch { data.profiles.ensureLoaded() }
        EveningWorker.schedule(app)
        refreshAiSpent()
        SyncWorker.schedulePeriodic(app)
        refreshHealthConnect()
        // Notka „Na dziś” powstaje po check-inie; gdy gotowość potem wyraźnie się zmieni (np. dopisany sen,
        // nowe tętno z synchronizacji), liczymy ją od nowa, żeby nie pokazywała starej liczby.
        viewModelScope.launch {
            state.mapNotNull { it?.today }.map { it.date to it.readiness.score }.distinctUntilChanged().collect { (date, score) ->
                val note = data.ai.morningFor(date) ?: return@collect
                val was = note.checkInKey.substringAfterLast('|').toIntOrNull() ?: return@collect
                if (kotlin.math.abs(was - score) >= MORNING_REFRESH_POINTS) {
                    runCatching { data.ai.morning(date) }
                    refreshAiSpent()
                }
            }
        }
    }

    val permissions: Set<String> = HealthConnectPermissions.all

    fun refreshHealthConnect() {
        viewModelScope.launch {
            if (HealthConnectClient.getSdkStatus(getApplication()) != HealthConnectClient.SDK_AVAILABLE) {
                _hc.value = HcStatus.NOT_INSTALLED
                return@launch
            }
            val granted = HealthConnectClient.getOrCreate(getApplication()).permissionController.getGrantedPermissions()
            val core = HealthConnectSync.recordTypes.map { androidx.health.connect.client.permission.HealthPermission.getReadPermission(it) }
            _hc.value = if (core.any { it in granted }) HcStatus.READY else HcStatus.NEEDS_PERMISSIONS
            if (_hc.value == HcStatus.READY) syncNow()
        }
    }

    fun syncNow() {
        if (_sync.value.running || _hc.value != HcStatus.READY) return
        viewModelScope.launch {
            _sync.update { it.copy(running = true, error = null) }
            val error = runCatching {
                HealthConnectSync(HealthConnectClient.getOrCreate(getApplication()), data.db, planner = data.plannerConfig()).sync()
            }.exceptionOrNull()
            _sync.value = SyncUi(running = false, error = error?.let { "Synchronizacja nieudana: ${it.message ?: it::class.simpleName}" })
        }
    }

    // --- wpisy użytkownika ---

    fun toggleHabit(habit: Habit, currentlyDone: Boolean) {
        val date = LocalDate.now(RytmTime.zone)
        val value = when {
            currentlyDone -> null
            habit.kind == HabitKind.YES_NO -> 1
            else -> habit.target ?: 1
        }
        viewModelScope.launch { data.coach.setHabit(habit.id, date, value) }
    }

    fun saveCheckIn(energy: Int, mood: Int, sore: Set<BodyArea>) {
        val date = LocalDate.now(RytmTime.zone)
        viewModelScope.launch {
            val existing = state.value?.today?.checkIn
            data.coach.saveCheckIn(DailyCheckIn(date, energy, mood, sore, existing?.hrvRmssd))
            runCatching { data.ai.morning(date) }
            WatchBridge.publish(getApplication())
            refreshAiSpent()
        }
    }

    /** Aktywność wpisana ręcznie, także wstecz: [end] to koniec, z wybranego dnia i godziny. */
    fun logActivity(entry: ActivityEntry) {
        viewModelScope.launch {
            data.coach.logOtherActivity(
                description = entry.description,
                start = entry.end.minus(Duration.ofMinutes(entry.durationMin.toLong())),
                durationMin = entry.durationMin,
                rpe = entry.rpe,
                loadedAreas = entry.areas,
                type = entry.type,
                kcal = entry.kcal,
            )
        }
    }

    fun saveNutrition(date: LocalDate, kcal: Int, proteinG: Double, fatG: Double?, carbsG: Double?) {
        viewModelScope.launch { data.coach.saveNutrition(date, kcal, proteinG, fatG, carbsG) }
    }

    /** [date]: dzień z arkusza „+”; czas ze zrzutu wygrywa, bo jest dokładny. */
    fun addWeighIn(kg: Double, bodyFatPct: Double?, time: Instant?, composition: BodyComposition?, date: LocalDate? = null) {
        val fallback = date?.takeIf { it != LocalDate.now(RytmTime.zone) }
            ?.atTime(8, 0)?.atZone(RytmTime.zone)?.toInstant() ?: Instant.now()
        viewModelScope.launch { data.coach.addWeighIn(kg, bodyFatPct, time ?: fallback, composition) }
    }

    /** Trening wpisany na telefonie (bez zegarka): dziś, kończy się teraz. */
    fun saveManualWorkout(entries: List<SetEntry>, durationMin: Int, rpe: Int?) {
        val today = state.value?.today ?: return
        val workout = today.plan.workout ?: return
        val end = Instant.now()
        val id = "manual-" + java.util.UUID.randomUUID()
        val date = end.atZone(RytmTime.zone).toLocalDate()
        viewModelScope.launch {
            data.coach.saveWorkout(
                WorkoutLog(
                    id = id,
                    title = workout.name,
                    type = WatchBridge.workoutType(today.plan),
                    start = end.minus(Duration.ofMinutes(durationMin.toLong())),
                    end = end,
                    sets = entries.map { SetLog(id, it.exercise, it.setIndex, it.reps, it.weightKg, null, date) },
                    rpe = rpe,
                    avgHr = null,
                    kcal = null,
                    source = Source.MANUAL,
                ),
            )
        }
    }

    /** Trening prowadzony na telefonie; null, gdy żaden nie trwa. */
    val phoneRun: StateFlow<com.zacco.rytm.domain.workout.WorkoutRun?> = data.phoneWorkout.run

    fun startPhoneWorkout() {
        if (data.phoneWorkout.run.value != null) return
        val today = state.value?.today ?: return
        val workout = today.plan.workout ?: return
        val profile = profile.value
        val eq = profile?.toUserProfile(today.date)?.equipment
        val step = profile?.weightStepKg ?: eq?.stepKg ?: 1.0
        data.phoneWorkout.set(
            com.zacco.rytm.domain.workout.WorkoutRun.start(
                id = "phone-" + java.util.UUID.randomUUID(),
                workout = workout,
                type = WatchBridge.workoutType(today.plan),
                stepKg = step,
                nowMs = System.currentTimeMillis(),
                maxKg = eq?.maxKg,
            ),
        )
    }

    fun updatePhoneRun(f: (com.zacco.rytm.domain.workout.WorkoutRun) -> com.zacco.rytm.domain.workout.WorkoutRun) = data.phoneWorkout.update(f)

    /** Kończy trening z telefonu i zapisuje serie; [rpe] z ekranu końcowego. */
    fun finishPhoneWorkout(rpe: Int?) {
        val run = data.phoneWorkout.run.value ?: return
        data.phoneWorkout.set(null)
        if (run.sets.none { it.reps > 0 }) return
        viewModelScope.launch {
            data.coach.saveWorkout(run.toLog(System.currentTimeMillis(), rpe, null, null, Source.MANUAL))
        }
    }

    fun cancelPhoneWorkout() = data.phoneWorkout.set(null)

    // --- wpisy: przegląd, usuwanie, eksport ---
    val entries: StateFlow<List<com.zacco.rytm.data.repo.Entry>> = today
        .flatMapLatest { day -> data.entries.observe(day) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus.asStateFlow()

    fun deleteEntry(e: com.zacco.rytm.data.repo.Entry) {
        viewModelScope.launch {
            data.entries.delete(e)
            WatchBridge.publish(getApplication())
        }
    }

    /** „Cofnij korektę” celu kcal z pętli jedzenie–waga; kolejny przegląd za 2 tygodnie. */
    fun resetKcalAdjust() {
        val p = data.profiles.profile.value ?: return
        data.profiles.save(p.copy(kcalAdjust = 0, kcalReviewedOn = LocalDate.now(RytmTime.zone).toString()))
    }

    fun addGoal(g: com.zacco.rytm.domain.goals.UserGoal) {
        val p = data.profiles.profile.value ?: return
        // Start celu = dzisiejsza wartość (żeby kamienie milowe liczyły się od teraz).
        val start = state.value?.body?.let { b ->
            when (g.kind) {
                com.zacco.rytm.domain.goals.GoalKind.WEIGHT -> b.currentAvg7
                else -> null
            }
        }
        data.profiles.save(p.copy(goals = p.goals + g.copy(start = start)))
    }

    fun removeGoal(id: String) {
        val p = data.profiles.profile.value ?: return
        data.profiles.save(p.copy(goals = p.goals.filterNot { it.id == id }))
    }

    fun deleteWeighIn(id: String) {
        viewModelScope.launch { data.entries.deleteWeighIn(id) }
    }

    fun exportTo(uri: android.net.Uri) {
        _exportStatus.value = "Zapisuję…"
        viewModelScope.launch {
            _exportStatus.value = runCatching {
                val json = data.entries.exportJson()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                        ?: error("nie można otworzyć pliku")
                }
                "Zapisano (${json.length / 1024} KB)."
            }.getOrElse { "Eksport nieudany: ${it.message}" }
        }
    }

    // --- rozmowa z trenerem ---
    val chat: StateFlow<List<com.zacco.rytm.ai.chat.ChatMessage>> = data.ai.chat
    private val _chatRunning = MutableStateFlow(false)
    val chatRunning: StateFlow<Boolean> = _chatRunning.asStateFlow()

    fun sendChat(text: String) {
        if (_chatRunning.value || text.isBlank()) return
        _chatRunning.value = true
        viewModelScope.launch {
            try {
                if (data.ai.chat(text)) WatchBridge.publish(getApplication())
            } finally {
                _chatRunning.value = false
                refreshAiSpent()
            }
        }
    }

    fun clearChat() = data.ai.clearChat()

    /** Wraca do planu z reguł na dziś (usuwa zmianę z rozmowy). */
    fun resetTodayPlan() {
        data.ai.resetToday()
        viewModelScope.launch { WatchBridge.publish(getApplication()) }
    }

    /** „Popraw z AI” w kreatorze; wynik wraca na główny wątek. */
    fun tweakPlan(p: PersonalProfile, request: String, onResult: (com.zacco.rytm.ai.plan.PlanTweakResult) -> Unit) {
        viewModelScope.launch { onResult(data.ai.tweakPlan(p, request)) }
    }

    fun saveProfile(p: PersonalProfile) {
        // Pierwszy zapis (kreator albo formularz): od dziś liczy się plan, wcześniejsze dni nie są „opuszczone”.
        val first = data.profiles.profile.value == null && p.startedOn == null
        data.profiles.save(if (first) p.copy(startedOn = LocalDate.now(RytmTime.zone).toString()) else p)
    }

    fun toggleDayOff() {
        val date = LocalDate.now(RytmTime.zone)
        val off = state.value?.today?.dayOff ?: false
        viewModelScope.launch { data.coach.setDayOff(date, !off) }
    }

    /** Sen wpisany ręcznie: noc kończąca się w dniu [date]. */
    fun saveSleep(date: LocalDate, start: Instant, end: Instant) {
        viewModelScope.launch {
            runCatching { data.coach.saveSleep(date, start, end) }
            WatchBridge.publish(getApplication())
        }
    }

    fun setAlcohol(date: LocalDate, drinks: Int) {
        viewModelScope.launch { data.coach.setAlcohol(date, drinks) }
    }

    fun setRpe(activityId: String, rpe: Int?) {
        viewModelScope.launch { data.coach.setRpe(activityId, rpe) }
    }

    fun toggleCancelled(date: LocalDate) {
        val cancelled = date in (state.value?.cancelled ?: emptySet())
        viewModelScope.launch { data.coach.setCancelled(date, !cancelled) }
    }
}

/** Wpis z arkusza aktywności. */
data class ActivityEntry(
    val type: com.zacco.rytm.domain.model.ActivityType,
    val description: String,
    val end: Instant,
    val durationMin: Int,
    val rpe: Int?,
    val areas: Set<LoadedArea>,
    val kcal: Int?,
)

/** Od tylu punktów zmiany gotowości notka „Na dziś” liczy się od nowa. */
private const val MORNING_REFRESH_POINTS = 8
