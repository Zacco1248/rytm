package com.zacco.rytm.data.healthconnect

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.zacco.rytm.domain.diagnostics.ExerciseDump
import com.zacco.rytm.domain.diagnostics.HealthDump
import com.zacco.rytm.domain.diagnostics.HrSampleDump
import com.zacco.rytm.domain.diagnostics.MeasurementDump
import com.zacco.rytm.domain.diagnostics.MeasurementKind
import com.zacco.rytm.domain.diagnostics.NutritionDump
import com.zacco.rytm.domain.diagnostics.RecordCount
import com.zacco.rytm.domain.diagnostics.SleepSessionDump
import com.zacco.rytm.domain.diagnostics.SleepStageDump
import java.time.Instant
import kotlin.reflect.KClass

/** Czyta wszystkie rekordy z zadanego okna i mapuje je na [HealthDump] (faza 0). */
class HealthConnectDumper(private val client: HealthConnectClient) {

    suspend fun dump(from: Instant, to: Instant, onProgress: (String) -> Unit = {}): HealthDump {
        val granted = client.permissionController.getGrantedPermissions()
        val counts = mutableListOf<RecordCount>()
        val errors = mutableListOf<String>()
        val records = mutableMapOf<KClass<out Record>, List<Record>>()

        for (type in HealthConnectPermissions.recordTypes) {
            val name = type.simpleName ?: type.toString()
            onProgress(name)
            try {
                val list = readAll(type, from, to)
                records[type] = list
                list.groupBy { it.metadata.dataOrigin.packageName }.forEach { (origin, recs) ->
                    val times = recs.mapNotNull { it.startTime() }
                    counts += RecordCount(name, origin, recs.size, times.minOrNull(), times.maxOrNull())
                }
            } catch (e: SecurityException) {
                errors += "$name: brak uprawnienia"
            } catch (e: Exception) {
                errors += "$name: ${e::class.simpleName}: ${e.message}"
            }
        }

        fun <T : Record> typed(type: KClass<T>): List<T> =
            @Suppress("UNCHECKED_CAST") (records[type] as List<T>?).orEmpty()

        return HealthDump(
            from = from,
            to = to,
            createdAt = Instant.now(),
            grantedPermissions = granted.sorted(),
            recordCounts = counts,
            heartRate = typed(HeartRateRecord::class).flatMap { r ->
                r.samples.map { HrSampleDump(it.time, it.beatsPerMinute, r.origin()) }
            },
            sleep = typed(SleepSessionRecord::class).map { r ->
                SleepSessionDump(
                    start = r.startTime,
                    end = r.endTime,
                    origin = r.origin(),
                    device = r.metadata.deviceName(),
                    stages = r.stages.map { SleepStageDump(it.startTime, it.endTime, sleepStageName(it.stage)) },
                )
            },
            exercise = typed(ExerciseSessionRecord::class).map { r ->
                ExerciseDump(
                    start = r.startTime,
                    end = r.endTime,
                    origin = r.origin(),
                    device = r.metadata.deviceName(),
                    exerciseType = r.exerciseType,
                    typeName = exerciseTypeName(r.exerciseType),
                    title = r.title,
                    recordingMethod = r.metadata.recordingMethod,
                )
            },
            nutrition = typed(NutritionRecord::class).map { r ->
                NutritionDump(
                    start = r.startTime,
                    end = r.endTime,
                    origin = r.origin(),
                    name = r.name,
                    mealType = r.mealType,
                    kcal = r.energy?.inKilocalories,
                    proteinG = r.protein?.inGrams,
                    fatG = r.totalFat?.inGrams,
                    carbsG = r.totalCarbohydrate?.inGrams,
                )
            },
            measurements = typed(WeightRecord::class).map { MeasurementDump(MeasurementKind.WEIGHT, it.time, it.origin(), it.weight.inKilograms) } +
                typed(BodyFatRecord::class).map { MeasurementDump(MeasurementKind.BODY_FAT, it.time, it.origin(), it.percentage.value) } +
                typed(Vo2MaxRecord::class).map { MeasurementDump(MeasurementKind.VO2_MAX, it.time, it.origin(), it.vo2MillilitersPerMinuteKilogram) } +
                typed(RestingHeartRateRecord::class).map { MeasurementDump(MeasurementKind.RESTING_HR, it.time, it.origin(), it.beatsPerMinute.toDouble()) } +
                typed(HeartRateVariabilityRmssdRecord::class).map { MeasurementDump(MeasurementKind.HRV_RMSSD, it.time, it.origin(), it.heartRateVariabilityMillis) },
            errors = errors,
        )
    }

    private suspend fun <T : Record> readAll(type: KClass<T>, from: Instant, to: Instant): List<T> {
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

    private fun Record.origin(): String = metadata.dataOrigin.packageName

    // InstantaneousRecord i IntervalRecord są w Health Connect wewnętrzne, więc wyliczamy typy z HealthConnectPermissions.
    private fun Record.startTime(): Instant? = when (this) {
        is HeartRateRecord -> startTime
        is SleepSessionRecord -> startTime
        is ExerciseSessionRecord -> startTime
        is StepsRecord -> startTime
        is DistanceRecord -> startTime
        is TotalCaloriesBurnedRecord -> startTime
        is ActiveCaloriesBurnedRecord -> startTime
        is NutritionRecord -> startTime
        is RestingHeartRateRecord -> time
        is HeartRateVariabilityRmssdRecord -> time
        is BasalMetabolicRateRecord -> time
        is Vo2MaxRecord -> time
        is OxygenSaturationRecord -> time
        is WeightRecord -> time
        is BodyFatRecord -> time
        is HeightRecord -> time
        else -> null
    }

    private fun Metadata.deviceName(): String? =
        device?.let { listOfNotNull(it.manufacturer, it.model).joinToString(" ").ifBlank { null } }

    private companion object {
        const val PAGE_SIZE = 5000
    }
}
