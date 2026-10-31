@file:UseSerializers(InstantSerializer::class)

package com.zacco.rytm.domain.diagnostics

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Surowy zrzut danych z Health Connect na potrzeby fazy 0.
 * Niezależny od Androida: mapowanie rekordów HC robi `core-data`.
 */
@Serializable
data class HealthDump(
    val from: Instant,
    val to: Instant,
    val createdAt: Instant,
    val grantedPermissions: List<String>,
    val recordCounts: List<RecordCount>,
    val heartRate: List<HrSampleDump> = emptyList(),
    val sleep: List<SleepSessionDump> = emptyList(),
    val exercise: List<ExerciseDump> = emptyList(),
    val nutrition: List<NutritionDump> = emptyList(),
    val measurements: List<MeasurementDump> = emptyList(),
    val errors: List<String> = emptyList(),
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { prettyPrint = true; encodeDefaults = true }
        fun fromJson(text: String): HealthDump = json.decodeFromString(serializer(), text)
    }
}

/** Liczba rekordów danego typu z jednego źródła (pakietu aplikacji). */
@Serializable
data class RecordCount(
    val type: String,
    val origin: String,
    val count: Int,
    val first: Instant?,
    val last: Instant?,
)

@Serializable
data class HrSampleDump(val time: Instant, val bpm: Long, val origin: String)

@Serializable
data class SleepSessionDump(
    val start: Instant,
    val end: Instant,
    val origin: String,
    val device: String?,
    val stages: List<SleepStageDump>,
)

@Serializable
data class SleepStageDump(val start: Instant, val end: Instant, val stage: String)

@Serializable
data class ExerciseDump(
    val start: Instant,
    val end: Instant,
    val origin: String,
    val device: String?,
    val exerciseType: Int,
    val typeName: String,
    val title: String?,
    val recordingMethod: Int?,
)

@Serializable
data class NutritionDump(
    val start: Instant,
    val end: Instant,
    val origin: String,
    val name: String?,
    val mealType: Int,
    val kcal: Double?,
    val proteinG: Double?,
    val fatG: Double?,
    val carbsG: Double?,
)

@Serializable
data class MeasurementDump(val kind: MeasurementKind, val time: Instant, val origin: String, val value: Double)

@Serializable
enum class MeasurementKind(val label: String, val unit: String) {
    WEIGHT("Waga", "kg"),
    BODY_FAT("Tkanka tłuszczowa", "%"),
    VO2_MAX("VO2max", "ml/kg/min"),
    RESTING_HR("Tętno spoczynkowe (HC)", "bpm"),
    HRV_RMSSD("HRV RMSSD (HC)", "ms"),
}
