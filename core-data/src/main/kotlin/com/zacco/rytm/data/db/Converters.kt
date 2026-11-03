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
        val o = JSONObject()
        // Uwaga: lokalna funkcja o nazwie „put” wołała samą siebie (StackOverflowError przy zapisie ważenia ze zrzutu).
        fun opt(k: String, v: Number?) { if (v != null) o.put(k, v) }
        opt("muscleKg", b.muscleKg); opt("musclePct", b.musclePct); opt("skeletalMuscleKg", b.skeletalMuscleKg)
        opt("waterKg", b.waterKg); opt("waterPct", b.waterPct); opt("fatKg", b.fatKg); opt("boneKg", b.boneKg)
        opt("proteinKg", b.proteinKg); opt("proteinPct", b.proteinPct); opt("visceralFat", b.visceralFat)
        opt("bmrKcal", b.bmrKcal); opt("leanMassKg", b.leanMassKg); opt("waistHipRatio", b.waistHipRatio)
        opt("bmi", b.bmi); opt("bodyScore", b.bodyScore); opt("heartRate", b.heartRate)
        o.toString()
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
