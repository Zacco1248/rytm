package com.zacco.rytm.data.db

import androidx.room.TypeConverter
import com.zacco.rytm.domain.model.BodyArea
import com.zacco.rytm.domain.model.BodyComposition
import com.zacco.rytm.domain.model.LoadedArea
import java.time.Instant
import java.time.LocalDate
import org.json.JSONObject

/** Daty jako tekst ISO (sortuje się poprawnie), chwile jako milisekundy, zbiory enumów jako CSV. */
class Converters {
    @TypeConverter fun dateToString(d: LocalDate?): String? = d?.toString()
    @TypeConverter fun stringToDate(s: String?): LocalDate? = s?.let(LocalDate::parse)

    @TypeConverter fun instantToLong(i: Instant?): Long? = i?.toEpochMilli()
    @TypeConverter fun longToInstant(l: Long?): Instant? = l?.let(Instant::ofEpochMilli)

    @TypeConverter fun loadedToString(s: Set<LoadedArea>): String = s.joinToString(",") { it.name }
    @TypeConverter fun stringToLoaded(s: String): Set<LoadedArea> =
        s.split(',').filter { it.isNotBlank() }.mapNotNull { runCatching { LoadedArea.valueOf(it) }.getOrNull() }.toSet()

    @TypeConverter fun bodyToString(s: Set<BodyArea>): String = s.joinToString(",") { it.name }
    @TypeConverter fun stringToBody(s: String): Set<BodyArea> =
        s.split(',').filter { it.isNotBlank() }.mapNotNull { runCatching { BodyArea.valueOf(it) }.getOrNull() }.toSet()

    @TypeConverter fun compositionToString(c: BodyComposition?): String? = c?.takeUnless { it.isEmpty }?.let { b ->
        JSONObject().apply {
            fun put(k: String, v: Number?) { if (v != null) put(k, v) }
            put("muscleKg", b.muscleKg); put("musclePct", b.musclePct); put("skeletalMuscleKg", b.skeletalMuscleKg)
            put("waterKg", b.waterKg); put("waterPct", b.waterPct); put("fatKg", b.fatKg); put("boneKg", b.boneKg)
            put("proteinKg", b.proteinKg); put("proteinPct", b.proteinPct); put("visceralFat", b.visceralFat)
            put("bmrKcal", b.bmrKcal); put("leanMassKg", b.leanMassKg); put("waistHipRatio", b.waistHipRatio)
            put("bmi", b.bmi); put("bodyScore", b.bodyScore); put("heartRate", b.heartRate)
        }.toString()
    }

    @TypeConverter fun stringToComposition(s: String?): BodyComposition? = s?.let {
        runCatching {
            val o = JSONObject(it)
            fun d(k: String): Double? = if (o.has(k)) o.getDouble(k) else null
            fun i(k: String): Int? = if (o.has(k)) o.getInt(k) else null
            BodyComposition(
                muscleKg = d("muscleKg"), musclePct = d("musclePct"), skeletalMuscleKg = d("skeletalMuscleKg"),
                waterKg = d("waterKg"), waterPct = d("waterPct"), fatKg = d("fatKg"), boneKg = d("boneKg"),
                proteinKg = d("proteinKg"), proteinPct = d("proteinPct"), visceralFat = d("visceralFat"),
                bmrKcal = i("bmrKcal"), leanMassKg = d("leanMassKg"), waistHipRatio = d("waistHipRatio"),
                bmi = d("bmi"), bodyScore = i("bodyScore"), heartRate = i("heartRate"),
            )
        }.getOrNull()
    }
}
