package com.zacco.rytm.domain.diagnostics

import com.zacco.rytm.domain.time.RytmTime
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Analiza zrzutu z fazy 0: co mamy, czego brak, jak wyglądają dane. */
object DumpAnalyzer {

    fun analyze(dump: HealthDump): Phase0Report = Phase0Report(
        from = dump.from,
        to = dump.to,
        heartRate = dump.heartRate.groupBy { it.origin }.map { (origin, s) -> analyzeHr(origin, s, dump.sleep) },
        sleep = analyzeSleep(dump.sleep),
        exercise = analyzeExercise(dump.exercise),
        exerciseDuplicates = findExerciseDuplicates(dump.exercise),
        nutrition = dump.nutrition.groupBy { it.origin }.map { (origin, n) -> analyzeNutrition(origin, n) },
        nutritionDuplicates = findNutritionDuplicates(dump.nutrition),
        measurements = dump.measurements.groupBy { it.kind to it.origin }.map { (key, m) ->
            val latest = m.maxBy { it.time }
            MeasurementSummary(key.first, key.second, m.size, latest.value, latest.time)
        }.sortedWith(compareBy({ it.kind }, { it.origin })),
        recordCounts = dump.recordCounts.sortedWith(compareBy({ it.type }, { it.origin })),
        coverage = coverage(dump),
        errors = dump.errors,
    )

    // --- tętno ---

    internal fun analyzeHr(origin: String, samples: List<HrSampleDump>, sleep: List<SleepSessionDump>): HrSummary {
        val sorted = samples.sortedBy { it.time }
        val intervalsMin = sorted.zipWithNext { a, b -> Duration.between(a.time, b.time).seconds / 60.0 }
            .filter { it > 0 }
        // Przerwy dłuższe niż 60 min to raczej zdjęty zegarek, nie częstotliwość próbkowania.
        val regular = intervalsMin.filter { it <= 60.0 }
        val around10 = regular.count { it in 8.0..12.0 }
        val sleepSamples = sorted.filter { s -> sleep.any { s.time >= it.start && s.time < it.end } }
        val sleepHours = sleep.sumOf { Duration.between(it.start, it.end).toMinutes() } / 60.0
        return HrSummary(
            origin = origin,
            samples = sorted.size,
            days = sorted.map { it.time.atZone(RytmTime.zone).toLocalDate() }.distinct().size,
            medianIntervalMin = regular.medianOrNull(),
            shareAround10Min = if (regular.isEmpty()) null else around10.toDouble() / regular.size,
            shareUnder2Min = if (regular.isEmpty()) null else regular.count { it < 2.0 }.toDouble() / regular.size,
            samplesPerSleepHour = if (sleepHours > 0) sleepSamples.size / sleepHours else null,
        )
    }

    // --- sen ---

    internal fun analyzeSleep(sessions: List<SleepSessionDump>): SleepSummary {
        val durations = sessions.map { Duration.between(it.start, it.end).toMinutes().toDouble() }
        return SleepSummary(
            sessions = sessions.size,
            origins = sessions.map { it.origin }.distinct().sorted(),
            withStages = sessions.count { s -> s.stages.any { it.stage !in NON_STAGE_TYPES } },
            stageTypes = sessions.flatMap { s -> s.stages.map { it.stage } }.distinct().sorted(),
            medianDurationMin = durations.medianOrNull(),
        )
    }

    private val NON_STAGE_TYPES = setOf("UNKNOWN", "SLEEPING")

    // --- treningi ---

    internal fun analyzeExercise(sessions: List<ExerciseDump>): List<ExerciseTypeSummary> =
        sessions.groupBy { Triple(it.origin, it.typeName, it.title) }
            .map { (key, list) ->
                ExerciseTypeSummary(
                    origin = key.first,
                    typeName = key.second,
                    title = key.third,
                    count = list.size,
                    weekdays = list.map { it.start.atZone(RytmTime.zone).dayOfWeek }.groupingBy { it }.eachCount()
                        .toSortedMap(),
                    medianDurationMin = list.map { Duration.between(it.start, it.end).toMinutes().toDouble() }
                        .medianOrNull(),
                )
            }
            .sortedWith(compareByDescending<ExerciseTypeSummary> { it.count }.thenBy { it.origin })

    /** Pary treningów z różnych źródeł, które pokrywają się w co najmniej połowie krótszego z nich. */
    internal fun findExerciseDuplicates(sessions: List<ExerciseDump>): List<Pair<ExerciseDump, ExerciseDump>> {
        val sorted = sessions.sortedBy { it.start }
        val result = mutableListOf<Pair<ExerciseDump, ExerciseDump>>()
        for (i in sorted.indices) {
            for (j in i + 1 until sorted.size) {
                val a = sorted[i]
                val b = sorted[j]
                if (b.start >= a.end) break
                if (a.origin == b.origin) continue
                if (overlapRatio(a.start, a.end, b.start, b.end) >= 0.5) result += a to b
            }
        }
        return result
    }

    // --- żywienie ---

    internal fun analyzeNutrition(origin: String, records: List<NutritionDump>): NutritionSummary {
        val byDay = records.groupBy { it.start.atZone(RytmTime.zone).toLocalDate() }
        return NutritionSummary(
            origin = origin,
            records = records.size,
            days = byDay.size,
            avgDailyKcal = byDay.values.map { day -> day.sumOf { it.kcal ?: 0.0 } }.averageOrNull(),
            avgDailyProteinG = byDay.values.map { day -> day.sumOf { it.proteinG ?: 0.0 } }.averageOrNull(),
            namedRecordsShare = if (records.isEmpty()) 0.0 else records.count { !it.name.isNullOrBlank() }.toDouble() / records.size,
        )
    }

    /** Wpisy z różnych źródeł w tym samym oknie czasu i z podobnymi kaloriami (±10%). */
    internal fun findNutritionDuplicates(records: List<NutritionDump>): List<Pair<NutritionDump, NutritionDump>> {
        val result = mutableListOf<Pair<NutritionDump, NutritionDump>>()
        val sorted = records.sortedBy { it.start }
        for (i in sorted.indices) {
            for (j in i + 1 until sorted.size) {
                val a = sorted[i]
                val b = sorted[j]
                if (Duration.between(a.start, b.start).toMinutes() > 30) break
                if (a.origin == b.origin) continue
                val ka = a.kcal ?: continue
                val kb = b.kcal ?: continue
                if (ka > 0 && abs(ka - kb) / ka <= 0.10) result += a to b
            }
        }
        return result
    }

    // --- tabela „co mamy / czego brak” ---

    internal fun coverage(dump: HealthDump): List<CoverageRow> {
        val countsByType = dump.recordCounts.groupBy { it.type }.mapValues { (_, v) -> v.sumOf { it.count } }
        fun count(type: String) = countsByType[type] ?: 0
        fun originsOf(type: String) = dump.recordCounts.filter { it.type == type && it.count > 0 }.map { it.origin }.distinct()
        fun measurements(kind: MeasurementKind) = dump.measurements.filter { it.kind == kind }

        val sleepSummary = analyzeSleep(dump.sleep)
        val hr = dump.heartRate.groupBy { it.origin }.map { (o, s) -> analyzeHr(o, s, dump.sleep) }
        val bestHr = hr.maxByOrNull { it.samples }
        val days = max(1, Duration.between(dump.from, dump.to).toDays().toInt())

        return listOf(
            row("Sen", count("SleepSessionRecord"), originsOf("SleepSessionRecord"),
                note = sleepSummary.medianDurationMin?.let { "mediana ${it.toInt()} min" }),
            CoverageRow(
                "Etapy snu",
                when {
                    sleepSummary.sessions == 0 -> CoverageStatus.MISSING
                    sleepSummary.withStages * 2 >= sleepSummary.sessions -> CoverageStatus.OK
                    sleepSummary.withStages > 0 -> CoverageStatus.PARTIAL
                    else -> CoverageStatus.MISSING
                },
                "${sleepSummary.withStages}/${sleepSummary.sessions} nocy, typy: ${sleepSummary.stageTypes.joinToString().ifEmpty { "brak" }}",
            ),
            CoverageRow(
                "Tętno co ~10 min",
                when {
                    bestHr == null -> CoverageStatus.MISSING
                    (bestHr.medianIntervalMin ?: Double.MAX_VALUE) <= 12.0 -> CoverageStatus.OK
                    else -> CoverageStatus.PARTIAL
                },
                bestHr?.let { "${it.origin}: mediana odstępu ${it.medianIntervalMin?.format1() ?: "?"} min, ${it.samples} próbek" }
                    ?: "brak próbek",
            ),
            CoverageRow(
                "Tętno w nocy (do RHR)",
                when (val perHour = bestHr?.samplesPerSleepHour) {
                    null -> CoverageStatus.MISSING
                    else -> if (perHour >= 4.0) CoverageStatus.OK else CoverageStatus.PARTIAL
                },
                bestHr?.samplesPerSleepHour?.let { "${it.format1()} próbek na godzinę snu (min. ~4 do 30-min średniej)" }
                    ?: "brak danych",
            ),
            row("Tętno spoczynkowe w HC", measurements(MeasurementKind.RESTING_HR).size,
                measurements(MeasurementKind.RESTING_HR).map { it.origin }.distinct(),
                missingNote = "zgodnie z oczekiwaniem: liczymy sami z HR w oknie snu"),
            row("HRV w HC", measurements(MeasurementKind.HRV_RMSSD).size,
                measurements(MeasurementKind.HRV_RMSSD).map { it.origin }.distinct(),
                missingNote = "zgodnie z oczekiwaniem: poranny pomiar na zegarku (Sensor SDK)"),
            row("Treningi", count("ExerciseSessionRecord"), originsOf("ExerciseSessionRecord"),
                note = dump.exercise.map { it.typeName }.distinct().sorted().joinToString().ifEmpty { null }),
            row("Kroki", count("StepsRecord"), originsOf("StepsRecord")),
            row("Kalorie całkowite", count("TotalCaloriesBurnedRecord"), originsOf("TotalCaloriesBurnedRecord")),
            row("Żywienie", count("NutritionRecord"), originsOf("NutritionRecord"),
                note = "${dump.nutrition.map { it.start.atZone(RytmTime.zone).toLocalDate() }.distinct().size}/$days dni"),
            row("Waga", measurements(MeasurementKind.WEIGHT).size,
                measurements(MeasurementKind.WEIGHT).map { it.origin }.distinct(),
                missingNote = "sprawdź synchronizację Xiaomi Home → Health Connect"),
            row("Tkanka tłuszczowa", measurements(MeasurementKind.BODY_FAT).size,
                measurements(MeasurementKind.BODY_FAT).map { it.origin }.distinct()),
            row("VO2max", measurements(MeasurementKind.VO2_MAX).size,
                measurements(MeasurementKind.VO2_MAX).map { it.origin }.distinct()),
        )
    }

    private fun row(
        name: String,
        count: Int,
        origins: List<String>,
        note: String? = null,
        missingNote: String? = null,
    ) = CoverageRow(
        name = name,
        status = if (count > 0) CoverageStatus.OK else CoverageStatus.MISSING,
        details = if (count > 0) {
            listOfNotNull("$count rek.", origins.joinToString().ifEmpty { null }, note).joinToString(", ")
        } else {
            missingNote ?: "brak"
        },
    )

    private fun overlapRatio(aStart: Instant, aEnd: Instant, bStart: Instant, bEnd: Instant): Double {
        val overlap = Duration.between(maxOf(aStart, bStart), minOf(aEnd, bEnd)).seconds
        val shorter = min(Duration.between(aStart, aEnd).seconds, Duration.between(bStart, bEnd).seconds)
        return if (shorter <= 0) 0.0 else overlap.toDouble() / shorter
    }

    private fun List<Double>.medianOrNull(): Double? {
        if (isEmpty()) return null
        val s = sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
}

internal fun Double.format1(): String = String.format(java.util.Locale.ROOT, "%.1f", this)

data class Phase0Report(
    val from: Instant,
    val to: Instant,
    val heartRate: List<HrSummary>,
    val sleep: SleepSummary,
    val exercise: List<ExerciseTypeSummary>,
    val exerciseDuplicates: List<Pair<ExerciseDump, ExerciseDump>>,
    val nutrition: List<NutritionSummary>,
    val nutritionDuplicates: List<Pair<NutritionDump, NutritionDump>>,
    val measurements: List<MeasurementSummary>,
    val recordCounts: List<RecordCount>,
    val coverage: List<CoverageRow>,
    val errors: List<String>,
)

data class HrSummary(
    val origin: String,
    val samples: Int,
    val days: Int,
    val medianIntervalMin: Double?,
    val shareAround10Min: Double?,
    val shareUnder2Min: Double?,
    val samplesPerSleepHour: Double?,
)

data class SleepSummary(
    val sessions: Int,
    val origins: List<String>,
    val withStages: Int,
    val stageTypes: List<String>,
    val medianDurationMin: Double?,
)

data class ExerciseTypeSummary(
    val origin: String,
    val typeName: String,
    val title: String?,
    val count: Int,
    val weekdays: Map<DayOfWeek, Int>,
    val medianDurationMin: Double?,
)

data class NutritionSummary(
    val origin: String,
    val records: Int,
    val days: Int,
    val avgDailyKcal: Double?,
    val avgDailyProteinG: Double?,
    val namedRecordsShare: Double,
)

data class MeasurementSummary(
    val kind: MeasurementKind,
    val origin: String,
    val count: Int,
    val latestValue: Double,
    val latestTime: Instant,
)

data class CoverageRow(val name: String, val status: CoverageStatus, val details: String)

enum class CoverageStatus(val symbol: String) { OK("✅"), PARTIAL("⚠️"), MISSING("❌") }

