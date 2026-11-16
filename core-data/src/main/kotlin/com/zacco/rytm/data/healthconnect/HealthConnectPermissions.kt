package com.zacco.rytm.data.healthconnect

import androidx.health.connect.client.permission.HealthPermission
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
import kotlin.reflect.KClass

object HealthConnectPermissions {

    /** Typy rekordów sprawdzane w fazie 0. Muszą się zgadzać z uprawnieniami w AndroidManifest.xml aplikacji. */
    val recordTypes: List<KClass<out Record>> = listOf(
        SleepSessionRecord::class,
        HeartRateRecord::class,
        RestingHeartRateRecord::class,
        HeartRateVariabilityRmssdRecord::class,
        ExerciseSessionRecord::class,
        StepsRecord::class,
        DistanceRecord::class,
        TotalCaloriesBurnedRecord::class,
        ActiveCaloriesBurnedRecord::class,
        BasalMetabolicRateRecord::class,
        Vo2MaxRecord::class,
        OxygenSaturationRecord::class,
        NutritionRecord::class,
        WeightRecord::class,
        BodyFatRecord::class,
        HeightRecord::class,
    )

    val read: Set<String> = recordTypes.map { HealthPermission.getReadPermission(it) }.toSet()

    /** Historia starsza niż 30 dni i odczyt w tle. Nie każda wersja Health Connect je obsługuje: raport pokaże, czy przyznane. */
    val extra: Set<String> = setOf(
        HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY,
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
    )

    val all: Set<String> = read + extra

    /** Zgody ważne dla Rytmu, po polsku. Pozostałe typy są tylko do diagnostyki. */
    private val important: List<Pair<String, String>> = listOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class) to "sen",
        HealthPermission.getReadPermission(HeartRateRecord::class) to "tętno",
        HealthPermission.getReadPermission(ExerciseSessionRecord::class) to "treningi",
        HealthPermission.getReadPermission(StepsRecord::class) to "kroki",
        HealthPermission.getReadPermission(NutritionRecord::class) to "żywienie (Fitatu)",
        HealthPermission.getReadPermission(WeightRecord::class) to "waga",
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND to "odczyt w tle",
    )

    fun missingLabels(granted: Set<String>): List<String> = important.filter { it.first !in granted }.map { it.second }
}
