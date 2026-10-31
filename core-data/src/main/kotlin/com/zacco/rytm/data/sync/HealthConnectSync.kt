package com.zacco.rytm.data.sync

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.zacco.rytm.data.db.ActivityEntity
import com.zacco.rytm.data.db.DailyMetricsEntity
import com.zacco.rytm.data.db.NutritionDayEntity
import com.zacco.rytm.data.db.RytmDatabase
import com.zacco.rytm.data.db.SyncStateEntity
import com.zacco.rytm.data.db.WeighInEntity
import com.zacco.rytm.domain.heart.HrSample
import com.zacco.rytm.domain.heart.NightRestingHr
import com.zacco.rytm.domain.ingest.Ingest
import com.zacco.rytm.domain.ingest.SleepSession
import com.zacco.rytm.domain.model.Source
import com.zacco.rytm.domain.time.RytmTime
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.reflect.KClass

/**
 * Synchronizacja Health Connect → Room.
 *
 * Pierwszy raz: token zmian, potem import ostatnich dni (30, a z uprawnieniem do historii 60).
 * Kolejne razy: Changes API mówi, które dni się zmieniły, i te dni przeliczamy od nowa z HC.
 * Agregaty (kroki, kcal, żywienie) liczy Health Connect, który sam usuwa duplikaty wg priorytetu źródeł.
 */
class HealthConnectSync(
    private val client: HealthConnectClient,
    private val db: RytmDatabase,
    private val now: () -> Instant = Instant::now,
) {

    data class Result(val daysUpdated: Int, val fullImport: Boolean)

    suspend fun sync(): Result {
        val granted = client.permissionController.getGrantedPermissions()
        val types = recordTypes.filter { HealthPermission.getReadPermission(it) in granted }.toSet()
        if (types.isEmpty()) throw SecurityException("Brak uprawnień do Health Connect")

        val state = db.syncState()
        val token = state.get(KEY_TOKEN)?.takeIf { state.get(KEY_TYPES) == typesKey(types) }
        val today = today()

        val result = if (token == null) {
            fullImport(types, granted, today)
        } else {
            incremental(token, types, granted, today)
        }
        state.put(SyncStateEntity(KEY_LAST_SYNC, now().toEpochMilli().toString()))
        return result
    }

    private suspend fun fullImport(types: Set<KClass<out Record>>, granted: Set<String>, today: LocalDate): Result {
        // Token przed importem, żeby nie zgubić zmian, które wpadną w trakcie.
        val newToken = client.getChangesToken(ChangesTokenRequest(recordTypes = types))
        val days = if (HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in granted) HISTORY_DAYS else DEFAULT_DAYS
        val dates = (0L until days).map { today.minusDays(it) }.toSet()
        refreshDays(dates, types)
        saveToken(newToken, types)
        return Result(dates.size, fullImport = true)
    }

    private suspend fun incremental(
        startToken: String,
        types: Set<KClass<out Record>>,
        granted: Set<String>,
        today: LocalDate,
    ): Result {
        val dates = mutableSetOf<LocalDate>()
        var token = startToken
        do {
            val response = client.getChanges(token)
            if (response.changesTokenExpired) return fullImport(types, granted, today)
            for (change in response.changes) {
                when (change) {
                    is UpsertionChange -> dates += affectedDates(change.record)
                    is DeletionChange -> {
                        // Usunięcie podaje tylko id: aktywności i ważenia kasujemy po id, resztę przeliczamy za ostatni tydzień.
                        db.activities().delete(change.recordId)
                        db.weighIns().delete(change.recordId)
                        dates += (0L until DELETION_LOOKBACK_DAYS).map { today.minusDays(it) }
                    }
                }
            }
            token = response.nextChangesToken
        } while (response.hasMore)

        // Dzisiejsze kroki zmieniają się ciągle, więc dziś przeliczamy zawsze.
        dates += today
        refreshDays(dates.filter { !it.isAfter(today) }.toSet(), types)
        saveToken(token, types)
        return Result(dates.size, fullImport = false)
    }

    private suspend fun refreshDays(dates: Set<LocalDate>, types: Set<KClass<out Record>>) {
        for (date in dates.sorted()) {
            refreshMetrics(date, types)
            if (NutritionRecord::class in types) refreshNutrition(date)
            if (ExerciseSessionRecord::class in types) refreshActivities(date, types)
            if (WeightRecord::class in types || BodyFatRecord::class in types) refreshWeighIns(date, types)
        }
    }

    // --- dzień ---

    private suspend fun refreshMetrics(date: LocalDate, types: Set<KClass<out Record>>) {
        val (start, end) = dayBounds(date)
        val night = if (SleepSessionRecord::class in types) {
            // Noc kończąca się danego dnia zaczyna się dzień wcześniej.
            val sessions = read(SleepSessionRecord::class, start.minus(Duration.ofHours(12)), end).map { it.toSleepSession() }
            Ingest.nightFor(date, sessions)
        } else {
            null
        }
        val restingHr = if (night != null && HeartRateRecord::class in types) {
            val samples = read(HeartRateRecord::class, night.start, night.end)
                .flatMap { r -> r.samples.map { HrSample(it.time, it.beatsPerMinute.toInt()) } }
            NightRestingHr.compute(samples, night.start, night.end)
        } else {
            null
        }
        val aggregates = buildSet<AggregateMetric<*>> {
            if (StepsRecord::class in types) add(StepsRecord.COUNT_TOTAL)
            if (TotalCaloriesBurnedRecord::class in types) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
        }
        val agg = if (aggregates.isNotEmpty()) {
            client.aggregate(AggregateRequest(metrics = aggregates, timeRangeFilter = TimeRangeFilter.between(start, end)))
        } else {
            null
        }
        db.dailyMetrics().upsert(
            DailyMetricsEntity(
                date = date,
                sleepMin = night?.sleepMin,
                sleepStart = night?.start,
                sleepEnd = night?.end,
                nightRestingHr = restingHr,
                steps = agg?.get(StepsRecord.COUNT_TOTAL)?.toInt(),
                totalKcal = agg?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories?.toInt(),
                updatedAt = now(),
            ),
        )
    }

    private suspend fun refreshNutrition(date: LocalDate) {
        val (start, end) = dayBounds(date)
        val agg = client.aggregate(
            AggregateRequest(
                metrics = setOf(
                    NutritionRecord.ENERGY_TOTAL,
                    NutritionRecord.PROTEIN_TOTAL,
                    NutritionRecord.TOTAL_FAT_TOTAL,
                    NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL,
                ),
                timeRangeFilter = TimeRangeFilter.between(start, end),
            ),
        )
        val kcal = agg[NutritionRecord.ENERGY_TOTAL]?.inKilocalories
        if (kcal == null || kcal <= 0.0) {
            db.nutrition().delete(date)
            return
        }
        db.nutrition().upsert(
            NutritionDayEntity(
                date = date,
                kcal = kcal.toInt(),
                proteinG = agg[NutritionRecord.PROTEIN_TOTAL]?.inGrams ?: 0.0,
                fatG = agg[NutritionRecord.TOTAL_FAT_TOTAL]?.inGrams ?: 0.0,
                carbsG = agg[NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL]?.inGrams ?: 0.0,
                updatedAt = now(),
            ),
        )
    }

    private suspend fun refreshActivities(date: LocalDate, types: Set<KClass<out Record>>) {
        val (start, end) = dayBounds(date)
        val sessions = read(ExerciseSessionRecord::class, start, end)
            .filter { it.startTime.atZone(RytmTime.zone).toLocalDate() == date }
        val fresh = sessions.map { it.metadata.id }.toSet()
        // Sesje, które zniknęły z HC dla tego dnia (np. przesunięte), usuwamy.
        db.activities().healthConnectIdsOn(date).filter { it !in fresh }.forEach { db.activities().delete(it) }

        for (s in sessions) {
            val avgHr = if (HeartRateRecord::class in types) {
                read(HeartRateRecord::class, s.startTime, s.endTime)
                    .flatMap { r -> r.samples.filter { it.time >= s.startTime && it.time < s.endTime } }
                    .map { it.beatsPerMinute }
                    .takeIf { it.isNotEmpty() }
                    ?.average()?.toInt()
            } else {
                null
            }
            db.activities().upsertFromHealthConnect(
                ActivityEntity(
                    id = s.metadata.id,
                    type = ExerciseTypes.toActivityType(s.exerciseType, date),
                    start = s.startTime,
                    date = date,
                    durationMin = Duration.between(s.startTime, s.endTime).toMinutes().toInt().coerceAtLeast(0),
                    rpe = null,
                    avgHr = avgHr,
                    kcal = null,
                    source = Source.HEALTH_CONNECT,
                    origin = s.metadata.dataOrigin.packageName,
                    title = s.title,
                    notes = null,
                    loadedAreas = emptySet(),
                ),
            )
        }
    }

    private suspend fun refreshWeighIns(date: LocalDate, types: Set<KClass<out Record>>) {
        val (start, end) = dayBounds(date)
        val items = buildList {
            if (WeightRecord::class in types) {
                read(WeightRecord::class, start, end).forEach {
                    add(WeighInEntity(it.metadata.id, it.time, date, it.weight.inKilograms, null, it.metadata.dataOrigin.packageName))
                }
            }
            if (BodyFatRecord::class in types) {
                read(BodyFatRecord::class, start, end).forEach {
                    add(WeighInEntity(it.metadata.id, it.time, date, null, it.percentage.value, it.metadata.dataOrigin.packageName))
                }
            }
        }
        if (items.isNotEmpty()) db.weighIns().upsert(items)
    }

    // --- pomocnicze ---

    private fun affectedDates(record: Record): Set<LocalDate> {
        fun d(i: Instant) = i.atZone(RytmTime.zone).toLocalDate()
        return when (record) {
            // Sen i tętno w nocy wpływają na dzień zakończenia.
            is SleepSessionRecord -> setOf(d(record.endTime))
            is HeartRateRecord -> setOf(d(record.startTime), d(record.endTime))
            is ExerciseSessionRecord -> setOf(d(record.startTime))
            is StepsRecord -> setOf(d(record.startTime))
            is TotalCaloriesBurnedRecord -> setOf(d(record.startTime))
            is NutritionRecord -> setOf(d(record.startTime))
            is WeightRecord -> setOf(d(record.time))
            is BodyFatRecord -> setOf(d(record.time))
            else -> emptySet()
        }
    }

    private suspend fun <T : Record> read(type: KClass<T>, from: Instant, to: Instant): List<T> {
        val result = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                    pageSize = PAGE_SIZE,
                    pageToken = pageToken,
                ),
            )
            result += response.records
            pageToken = response.pageToken
        } while (pageToken != null)
        return result
    }

    private fun SleepSessionRecord.toSleepSession(): SleepSession {
        val asleep = stages.takeIf { it.isNotEmpty() }?.filter { it.stage !in AWAKE_STAGES }
            ?.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }?.toInt()
        return SleepSession(startTime, endTime, asleep, metadata.dataOrigin.packageName)
    }

    private fun dayBounds(date: LocalDate): Pair<Instant, Instant> =
        date.atStartOfDay(RytmTime.zone).toInstant() to date.plusDays(1).atStartOfDay(RytmTime.zone).toInstant()

    private fun today(): LocalDate = now().atZone(RytmTime.zone).toLocalDate()

    private suspend fun saveToken(token: String, types: Set<KClass<out Record>>) {
        db.syncState().put(SyncStateEntity(KEY_TOKEN, token))
        db.syncState().put(SyncStateEntity(KEY_TYPES, typesKey(types)))
    }

    private fun typesKey(types: Set<KClass<out Record>>) = types.mapNotNull { it.simpleName }.sorted().joinToString(",")

    companion object {
        const val KEY_TOKEN = "hc_changes_token"
        const val KEY_TYPES = "hc_changes_types"
        const val KEY_LAST_SYNC = "hc_last_sync"

        /** Typy, które synchronizujemy do bazy. Zmiana zestawu (np. nowe uprawnienie) wymusza pełny import. */
        val recordTypes: List<KClass<out Record>> = listOf(
            SleepSessionRecord::class,
            HeartRateRecord::class,
            ExerciseSessionRecord::class,
            StepsRecord::class,
            TotalCaloriesBurnedRecord::class,
            NutritionRecord::class,
            WeightRecord::class,
            BodyFatRecord::class,
        )

        private const val DEFAULT_DAYS = 30L
        private const val HISTORY_DAYS = 60L
        private const val DELETION_LOOKBACK_DAYS = 7L
        private const val PAGE_SIZE = 5000

        private val AWAKE_STAGES = setOf(
            SleepSessionRecord.STAGE_TYPE_AWAKE,
            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
        )
    }
}
