package com.zacco.rytm.data.repo

import com.zacco.rytm.data.db.ActivityEntity
import com.zacco.rytm.data.db.CancellationEntity
import com.zacco.rytm.data.db.DailyMetricsEntity
import com.zacco.rytm.data.db.DayNoteEntity
import com.zacco.rytm.data.db.HabitLogEntity
import com.zacco.rytm.data.db.NutritionDayEntity
import com.zacco.rytm.data.db.WeighInEntity
import com.zacco.rytm.data.db.WorkoutSetEntity
import com.zacco.rytm.data.db.RytmDatabase
import com.zacco.rytm.data.db.originForDedup
import com.zacco.rytm.data.db.toDomain
import com.zacco.rytm.data.db.toEntity
import com.zacco.rytm.data.profile.ProfileStore
import com.zacco.rytm.data.sync.HealthConnectSync
import com.zacco.rytm.domain.coach.CoachData
import com.zacco.rytm.domain.ingest.Ingest
import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.model.BodyComposition
import com.zacco.rytm.domain.model.DailyCheckIn
import com.zacco.rytm.domain.model.LoadedArea
import com.zacco.rytm.domain.model.Source
import com.zacco.rytm.domain.profile.PersonalProfile
import com.zacco.rytm.domain.time.RytmTime
import com.zacco.rytm.domain.workout.SetLog
import com.zacco.rytm.domain.workout.WorkoutLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Zbiera dane z bazy w [CoachData] i zapisuje ręczne wpisy użytkownika. */
class CoachRepository(
    private val db: RytmDatabase,
    private val profiles: ProfileStore,
    private val overrides: DayOverrideStore? = null,
) {

    /** Okno danych: 28 dni na obciążenie przewlekłe + bieżący tydzień + zapas na średnie wagi. */
    fun observe(today: LocalDate, lookbackDays: Long = LOOKBACK_DAYS): Flow<CoachData> {
        val from = today.minusDays(lookbackDays)
        val to = today.plusDays(7)
        val activities = db.activities().observe(from, to)
        val metrics = db.dailyMetrics().observe(from, today)
        val checkIns = db.checkIns().observe(from, today)
        val nutrition = db.nutrition().observe(from, today)
        val weighIns = db.weighIns().observe(from, today)
        val habitLogs = db.habitLogs().observe(from, today)
        val cancelled = db.cancellations().observe(from, to)
        val lastSync = db.syncState().observe(HealthConnectSync.KEY_LAST_SYNC)
        val dayNotes = db.dayNotes().observe(from, to)
        val sets = db.workoutSets().observe(today.minusDays(maxOf(SETS_LOOKBACK_DAYS, lookbackDays)), today)

        val part1 = combine(activities, metrics, checkIns, nutrition) { a, m, c, n ->
            CoachData(
                today = today,
                activities = Ingest.dedupActivities(a.map { it.toDomain() to it.originForDedup() }),
                metrics = m.map { it.toDomain() },
                checkIns = c.map { it.toDomain() },
                nutrition = n.map { it.toDomain() },
            )
        }
        val part2 = combine(part1, weighIns, habitLogs, cancelled, lastSync) { base, w, h, x, sync ->
            base.copy(
                weighIns = w.map { it.toDomain() },
                habitLogs = h.map { it.toDomain() },
                cancelled = x.map { it.date }.toSet(),
                lastSync = sync?.toLongOrNull()?.let(Instant::ofEpochMilli),
            )
        }
        val overrideFlow = overrides?.all ?: kotlinx.coroutines.flow.flowOf(emptyList())
        return combine(part2, dayNotes, profiles.profile, sets, overrideFlow) { base, notes, profile, s, o ->
            // Bez profilu (przed kreatorem) silnik liczy na wartościach domyślnych; ekran i tak pokazuje kreator.
            val p = profile ?: PersonalProfile()
            base.copy(
                dayNotes = notes.map { it.toDomain() },
                profile = p.toUserProfile(today),
                habits = p.habits(),
                sets = s.map { SetLog(it.workoutId, it.exercise, it.setIndex, it.reps, it.weightKg, it.rir, it.date) },
                overrides = o,
            )
        }
    }

    suspend fun setHabit(habitId: String, date: LocalDate, value: Int?) {
        if (value == null) db.habitLogs().delete(habitId, date) else db.habitLogs().upsert(HabitLogEntity(habitId, date, value))
    }

    suspend fun saveCheckIn(checkIn: DailyCheckIn) = db.checkIns().upsert(checkIn.toEntity())

    suspend fun setCancelled(date: LocalDate, cancelled: Boolean) {
        if (cancelled) db.cancellations().insert(CancellationEntity(date, Instant.now())) else db.cancellations().delete(date)
    }

    /** Ręczny wpis dnia z Fitatu: kcal i białko wystarczą, tłuszcz i węgle opcjonalnie. */
    suspend fun saveNutrition(date: LocalDate, kcal: Int, proteinG: Double, fatG: Double?, carbsG: Double?) {
        db.nutrition().upsert(
            NutritionDayEntity(date, kcal, proteinG, fatG ?: 0.0, carbsG ?: 0.0, Instant.now(), manual = true),
        )
    }

    /** Ręczne ważenie (waga S400 zapisuje tylko do Xiaomi Home), opcjonalnie z pełnym składem ciała ze zrzutu ekranu. */
    suspend fun addWeighIn(
        weightKg: Double,
        bodyFatPct: Double?,
        time: Instant = Instant.now(),
        composition: BodyComposition? = null,
    ) {
        db.weighIns().upsert(
            listOf(
                WeighInEntity(
                    "manual-" + UUID.randomUUID(), time, time.atZone(RytmTime.zone).toLocalDate(), weightKg, bodyFatPct,
                    "com.zacco.rytm", composition,
                ),
            ),
        )
    }

    /**
     * Sen wpisany ręcznie, gdy zegarek go nie zarejestrował: noc kończąca się w dniu [date] (pobudka).
     * Kroki i kalorie dnia zostają; synchronizacja nie nadpisze snu, dopóki wpis istnieje.
     */
    suspend fun saveSleep(date: LocalDate, start: Instant, end: Instant) {
        val min = java.time.Duration.between(start, end).toMinutes().toInt()
        require(min in 30..16 * 60) { "sen 30 min–16 h" }
        val current = db.dailyMetrics().get(date)
        val base = current ?: DailyMetricsEntity(date, null, null, null, null, null, null, Instant.now())
        db.dailyMetrics().upsert(base.copy(sleepMin = min, sleepStart = start, sleepEnd = end, sleepManual = true, updatedAt = Instant.now()))
    }

    /** Usuwa ręczny sen; przy następnej synchronizacji dnia wróci sen z zegarka, jeśli jest. */
    suspend fun clearManualSleep(date: LocalDate) {
        val current = db.dailyMetrics().get(date) ?: return
        if (!current.sleepManual) return
        db.dailyMetrics().upsert(current.copy(sleepMin = null, sleepStart = null, sleepEnd = null, nightRestingHr = null, sleepManual = false, updatedAt = Instant.now()))
    }

    /** „Dziś wolne”: plan przesuwa trening, bez kary. */
    suspend fun setDayOff(date: LocalDate, off: Boolean) = updateNote(date) { it.copy(dayOff = off) }

    /** Alkohol wypity danego dnia (porcje standardowe); liczy się do gotowości następnego dnia. */
    suspend fun setAlcohol(date: LocalDate, drinks: Int) = updateNote(date) { it.copy(alcoholDrinks = drinks.coerceIn(0, 20)) }

    private suspend fun updateNote(date: LocalDate, change: (DayNoteEntity) -> DayNoteEntity) {
        val current = db.dayNotes().get(date) ?: DayNoteEntity(date, dayOff = false, alcoholDrinks = 0, updatedAt = Instant.now())
        val next = change(current).copy(updatedAt = Instant.now())
        if (!next.dayOff && next.alcoholDrinks == 0) db.dayNotes().delete(date) else db.dayNotes().upsert(next)
    }

    suspend fun setRpe(activityId: String, rpe: Int?) = db.activities().setRpe(activityId, rpe)

    /** „Zaloguj inną aktywność”: opis, czas, RPE i „co obciążyło”. */
    suspend fun logOtherActivity(
        description: String,
        start: Instant,
        durationMin: Int,
        rpe: Int?,
        loadedAreas: Set<LoadedArea>,
        type: ActivityType = ActivityType.OTHER,
        kcal: Int? = null,
    ): String {
        val id = "manual-" + UUID.randomUUID()
        db.activities().upsert(
            ActivityEntity(
                id = id,
                type = type,
                start = start,
                date = start.atZone(RytmTime.zone).toLocalDate(),
                durationMin = durationMin,
                rpe = rpe,
                avgHr = null,
                kcal = kcal,
                source = Source.MANUAL,
                origin = null,
                title = null,
                notes = description.trim().ifBlank { null },
                loadedAreas = loadedAreas,
            ),
        )
        return id
    }

    /**
     * Zapisuje trening z dziennikiem serii (z zegarka albo z telefonu). Ten sam id nadpisuje poprzedni zapis,
     * a RPE wpisane później ręcznie zostaje.
     */
    suspend fun saveWorkout(log: WorkoutLog) {
        val old = db.activities().get(log.id)
        val activity = log.toActivity().let { a -> a.copy(rpe = a.rpe ?: old?.rpe) }
        db.activities().upsert(activity.toEntity(origin = OWN_ORIGIN, title = log.title))
        db.workoutSets().replace(
            log.id,
            log.sets.map { WorkoutSetEntity(log.id, it.exercise, it.setIndex, it.reps, it.weightKg, it.rir, it.date) },
        )
    }

    suspend fun deleteWorkout(id: String) {
        db.workoutSets().deleteFor(id)
        db.activities().delete(id)
    }

    private companion object {
        const val OWN_ORIGIN = "com.zacco.rytm"
        const val SETS_LOOKBACK_DAYS = 120L
        const val LOOKBACK_DAYS = 42L
    }
}
