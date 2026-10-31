package com.zacco.rytm.domain.diagnostics

import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DumpAnalyzerTest {

    private val t0: Instant = Instant.parse("2026-10-04T22:00:00Z") // poniedziałek, 00:00 w Warszawie
    private val samsung = "com.sec.android.app.shealth"
    private val fitatu = "com.fitatu.tracker"

    private fun minutes(m: Long): Instant = t0.plus(Duration.ofMinutes(m))

    private fun dump(
        hr: List<HrSampleDump> = emptyList(),
        sleep: List<SleepSessionDump> = emptyList(),
        exercise: List<ExerciseDump> = emptyList(),
        nutrition: List<NutritionDump> = emptyList(),
        measurements: List<MeasurementDump> = emptyList(),
        counts: List<RecordCount> = emptyList(),
    ) = HealthDump(t0, t0.plus(Duration.ofDays(30)), t0, emptyList(), counts, hr, sleep, exercise, nutrition, measurements)

    private fun exercise(start: Long, durationMin: Long, origin: String, type: String) =
        ExerciseDump(minutes(start), minutes(start + durationMin), origin, null, 0, type, null, null)

    @Test
    fun `heart rate every 10 minutes is detected`() {
        val hr = List(100) { HrSampleDump(minutes(it * 10L), 60, samsung) }
        val sleep = listOf(SleepSessionDump(minutes(0), minutes(480), samsung, null, emptyList()))

        val summary = DumpAnalyzer.analyzeHr(samsung, hr, sleep)

        assertEquals(10.0, summary.medianIntervalMin)
        assertEquals(1.0, summary.shareAround10Min)
        assertEquals(6.0, summary.samplesPerSleepHour!!, 1e-9)
        val row = DumpAnalyzer.coverage(dump(hr = hr, sleep = sleep)).first { it.name == "Tętno co ~10 min" }
        assertEquals(CoverageStatus.OK, row.status)
    }

    @Test
    fun `gaps longer than an hour do not distort sampling interval`() {
        val hr = List(10) { HrSampleDump(minutes(it * 10L), 60, samsung) } +
            List(10) { HrSampleDump(minutes(600 + it * 10L), 60, samsung) }

        assertEquals(10.0, DumpAnalyzer.analyzeHr(samsung, hr, emptyList()).medianIntervalMin)
    }

    @Test
    fun `sleep stages are counted only when real stages exist`() {
        val stages = listOf(SleepStageDump(minutes(0), minutes(60), "LIGHT"), SleepStageDump(minutes(60), minutes(90), "DEEP"))
        val sleep = listOf(
            SleepSessionDump(minutes(0), minutes(420), samsung, null, stages),
            SleepSessionDump(minutes(1440), minutes(1860), samsung, null, listOf(SleepStageDump(minutes(1440), minutes(1860), "SLEEPING"))),
        )

        val summary = DumpAnalyzer.analyzeSleep(sleep)

        assertEquals(1, summary.withStages)
        assertEquals(listOf("DEEP", "LIGHT", "SLEEPING"), summary.stageTypes)
    }

    @Test
    fun `exercise types are grouped with weekdays in Warsaw time`() {
        // t0 + 1 dzień = wtorek, + 2 dni = środa
        val sessions = listOf(
            exercise(1440 + 18 * 60, 90, samsung, "VOLLEYBALL"),
            exercise(2880 + 19 * 60, 60, samsung, "SQUASH"),
            exercise(8 * 1440 + 18 * 60, 90, samsung, "VOLLEYBALL"),
        )

        val summary = DumpAnalyzer.analyzeExercise(sessions)

        val volleyball = summary.first { it.typeName == "VOLLEYBALL" }
        assertEquals(2, volleyball.count)
        assertEquals(mapOf(DayOfWeek.TUESDAY to 2), volleyball.weekdays)
        assertEquals(90.0, volleyball.medianDurationMin)
    }

    @Test
    fun `overlapping sessions from different origins are flagged as duplicates`() {
        val sessions = listOf(
            exercise(600, 60, samsung, "SQUASH"),
            exercise(605, 50, fitatu, "OTHER_WORKOUT"),
            exercise(700, 30, samsung, "WALKING"),
        )

        val duplicates = DumpAnalyzer.findExerciseDuplicates(sessions)

        assertEquals(1, duplicates.size)
        assertEquals("SQUASH", duplicates.single().first.typeName)
    }

    @Test
    fun `nutrition duplicates need similar kcal and different origins`() {
        fun meal(start: Long, origin: String, kcal: Double) =
            NutritionDump(minutes(start), minutes(start + 1), origin, null, 0, kcal, 30.0, 10.0, 50.0)
        val records = listOf(
            meal(480, fitatu, 600.0),
            meal(490, samsung, 620.0),
            meal(500, samsung, 300.0),
            meal(720, fitatu, 600.0),
        )

        val duplicates = DumpAnalyzer.findNutritionDuplicates(records)

        assertEquals(1, duplicates.size)
    }

    @Test
    fun `missing weight points to Xiaomi sync and missing hrv is expected`() {
        val coverage = DumpAnalyzer.coverage(dump())

        val weight = coverage.first { it.name == "Waga" }
        assertEquals(CoverageStatus.MISSING, weight.status)
        assertTrue("Xiaomi" in weight.details)
        assertTrue("Sensor SDK" in coverage.first { it.name == "HRV w HC" }.details)
    }

    @Test
    fun `report renders and dump survives json round trip`() {
        val d = dump(
            hr = List(5) { HrSampleDump(minutes(it * 10L), 60, samsung) },
            exercise = listOf(exercise(600, 60, samsung, "SQUASH")),
            measurements = listOf(MeasurementDump(MeasurementKind.WEIGHT, minutes(10), "com.xiaomi.smarthome", 80.2)),
            counts = listOf(RecordCount("WeightRecord", "com.xiaomi.smarthome", 1, minutes(10), minutes(10))),
        )

        val markdown = ReportRenderer.render(DumpAnalyzer.analyze(d))

        assertTrue("## Co mamy / czego brak" in markdown)
        assertTrue("| Waga | com.xiaomi.smarthome | 1 | 80.2 kg |" in markdown)
        assertEquals(d, HealthDump.fromJson(d.toJson()))
    }
}
