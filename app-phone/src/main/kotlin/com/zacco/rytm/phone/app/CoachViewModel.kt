package com.zacco.rytm.phone.app

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zacco.rytm.data.RytmData
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
import com.zacco.rytm.domain.model.DailyCheckIn
import com.zacco.rytm.domain.model.LoadedArea
import com.zacco.rytm.domain.planner.PlannerConfig
import com.zacco.rytm.domain.time.RytmTime
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
    val cancelled: Set<LocalDate>,
    val personalTrainingUntil: LocalDate?,
)

/** Jeden ViewModel na trzy ekrany: wszystkie liczą się z tych samych danych i tego samego silnika. */
@OptIn(ExperimentalCoroutinesApi::class)
class CoachViewModel(app: Application) : AndroidViewModel(app) {

    private val data = RytmData.get(app)
    private val engine = CoachEngine()
    private val planner = PlannerConfig()

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
            val upcoming = (0L..6L).map { d.today.plusDays(it) }.filter { it <= d.today.with(java.time.DayOfWeek.SUNDAY) }
            CoachUiState(
                today = engine.today(d),
                week = engine.week(d),
                body = engine.body(d),
                nextMatch = upcoming.firstOrNull { it.dayOfWeek in planner.fixedSports },
                nextPersonal = upcoming.firstOrNull { planner.hasPersonalTraining(it) },
                cancelled = d.cancelled,
                personalTrainingUntil = planner.personalTrainingUntil,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        SyncWorker.schedulePeriodic(app)
        refreshHealthConnect()
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
                HealthConnectSync(HealthConnectClient.getOrCreate(getApplication()), data.db).sync()
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
        }
    }

    fun logOtherActivity(description: String, durationMin: Int, rpe: Int?, areas: Set<LoadedArea>) {
        val end = Instant.now()
        viewModelScope.launch {
            data.coach.logOtherActivity(description, end.minus(Duration.ofMinutes(durationMin.toLong())), durationMin, rpe, areas)
        }
    }

    fun saveNutrition(kcal: Int, proteinG: Double, fatG: Double?, carbsG: Double?) {
        val date = LocalDate.now(RytmTime.zone)
        viewModelScope.launch { data.coach.saveNutrition(date, kcal, proteinG, fatG, carbsG) }
    }

    fun addWeighIn(kg: Double, bodyFatPct: Double?) {
        viewModelScope.launch { data.coach.addWeighIn(kg, bodyFatPct) }
    }

    fun setRpe(activityId: String, rpe: Int?) {
        viewModelScope.launch { data.coach.setRpe(activityId, rpe) }
    }

    fun toggleCancelled(date: LocalDate) {
        val cancelled = date in (state.value?.cancelled ?: emptySet())
        viewModelScope.launch { data.coach.setCancelled(date, !cancelled) }
    }
}
