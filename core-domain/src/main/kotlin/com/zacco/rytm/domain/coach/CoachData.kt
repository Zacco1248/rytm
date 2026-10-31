package com.zacco.rytm.domain.coach

import com.zacco.rytm.domain.habits.DefaultHabits
import com.zacco.rytm.domain.habits.Habit
import com.zacco.rytm.domain.habits.HabitLog
import com.zacco.rytm.domain.model.Activity
import com.zacco.rytm.domain.model.BodyMetrics
import com.zacco.rytm.domain.model.DailyCheckIn
import com.zacco.rytm.domain.model.DailyMetrics
import com.zacco.rytm.domain.model.NutritionDay
import com.zacco.rytm.domain.profile.UserProfile
import java.time.Instant
import java.time.LocalDate

/**
 * Wszystkie dane, z których składamy ekrany „Dziś”, „Tydzień” i „Sylwetka”.
 * Warstwa danych zbiera je z bazy, a [CoachEngine] liczy z nich wszystko bez Androida.
 */
data class CoachData(
    val today: LocalDate,
    /** Co najmniej 35 dni wstecz: 28 dni na obciążenie przewlekłe plus bieżący tydzień. */
    val activities: List<Activity> = emptyList(),
    /** Dzień = noc zakończona tego dnia (sen, tętno spoczynkowe) plus kroki i kcal z tego dnia. */
    val metrics: List<DailyMetrics> = emptyList(),
    val checkIns: List<DailyCheckIn> = emptyList(),
    val nutrition: List<NutritionDay> = emptyList(),
    /** Pojedyncze ważenia (jeden wpis na pomiar), co najmniej 5 tygodni wstecz. */
    val weighIns: List<BodyMetrics> = emptyList(),
    val habitLogs: List<HabitLog> = emptyList(),
    /** Dni, w które stały sport albo trening personalny został odwołany. */
    val cancelled: Set<LocalDate> = emptySet(),
    val habits: List<Habit> = DefaultHabits.all,
    val profile: UserProfile = UserProfile(),
    /** Kiedy ostatnio udała się synchronizacja z Health Connect. */
    val lastSync: Instant? = null,
)
