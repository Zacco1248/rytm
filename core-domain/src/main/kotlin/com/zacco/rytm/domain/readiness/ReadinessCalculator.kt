package com.zacco.rytm.domain.readiness

import com.zacco.rytm.domain.load.AcwrZone
import com.zacco.rytm.domain.load.LoadSummary
import com.zacco.rytm.domain.model.DailyCheckIn
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/** Wszystko, czego potrzeba do wyniku gotowości na dany dzień. Dzień liczymy od przebudzenia. */
data class ReadinessInput(
    val date: LocalDate,
    val sleepMin: Int?,
    val sleepStart: Instant?,
    /** Pory zaśnięcia z poprzednich (do 7) nocy, do oceny regularności. */
    val previousSleepStarts: List<Instant>,
    val nightRestingHr: Int?,
    /** Tętno spoczynkowe z poprzednich (do 7) nocy. */
    val restingHrBaseline: List<Int>,
    val hrvRmssd: Double?,
    /** Poranne RMSSD z poprzednich (do 7) dni. */
    val hrvBaseline: List<Double>,
    val checkIn: DailyCheckIn?,
    val load: LoadSummary?,
)

data class ReadinessConfig(
    val weights: ReadinessWeights = ReadinessWeights(),
    /** Cel snu z ustawień: 7–8 h. */
    val sleepTargetMin: Int = 420,
    /** Poniżej tylu dni baseline'u wynik jest w trybie kalibracji. */
    val minBaselineDays: Int = 7,
    /** Minimum punktów, by w ogóle porównywać z baseline. */
    val minBaselinePoints: Int = 3,
)

object ReadinessCalculator {

    fun compute(input: ReadinessInput, config: ReadinessConfig = ReadinessConfig()): Readiness {
        val flags = mutableListOf<ReadinessFlag>()
        val components = linkedMapOf<ReadinessComponent, Int>()

        sleepScore(input, config, flags)?.let { components[ReadinessComponent.SLEEP] = it }
        restingHrScore(input, config, flags)?.let { components[ReadinessComponent.RESTING_HR] = it }
        hrvScore(input, config, flags)?.let { components[ReadinessComponent.HRV] = it }
        subjectiveScore(input, flags)?.let { components[ReadinessComponent.SUBJECTIVE] = it }
        loadScore(input, flags)?.let { components[ReadinessComponent.LOAD] = it }

        val totalWeight = components.keys.sumOf { config.weights.of(it) }
        val score = if (totalWeight > 0) {
            (components.entries.sumOf { (c, v) -> config.weights.of(c) * v } / totalWeight).roundToInt()
        } else {
            DEFAULT_SCORE
        }

        val calibrating = input.restingHrBaseline.size < config.minBaselineDays ||
            (input.hrvRmssd != null && input.hrvBaseline.size < config.minBaselineDays) ||
            input.load?.reliable == false

        return Readiness(
            date = input.date,
            score = score.coerceIn(0, 100),
            level = levelOf(score),
            components = components,
            flags = flags,
            calibrating = calibrating,
        )
    }

    fun levelOf(score: Int): ReadinessLevel = when {
        score >= 70 -> ReadinessLevel.HIGH
        score >= 50 -> ReadinessLevel.MODERATE
        else -> ReadinessLevel.LOW
    }

    // --- składowe ---

    internal fun sleepScore(input: ReadinessInput, config: ReadinessConfig, flags: MutableList<ReadinessFlag>): Int? {
        val minutes = input.sleepMin ?: return null
        // 100 przy celu, 0 przy 4 h snu.
        val duration = ((minutes - FLOOR_SLEEP_MIN).toDouble() / (config.sleepTargetMin - FLOOR_SLEEP_MIN) * 100).coerceIn(0.0, 100.0)
        if (minutes < config.sleepTargetMin - 60) flags += ReadinessFlag.SHORT_SLEEP

        val deviation = sleepOnsetDeviationMin(input.sleepStart, input.previousSleepStarts)
        val regularity = deviation?.let {
            // bez kary do 30 min odchyłki, 0 punktów od 150 min
            (100 - (it - 30).coerceAtLeast(0.0) * 100 / 120).coerceIn(0.0, 100.0)
        }
        if (deviation != null && deviation > 90) flags += ReadinessFlag.IRREGULAR_SLEEP

        return (if (regularity == null) duration else duration * 0.75 + regularity * 0.25).roundToInt()
    }

    /** Odchyłka pory zaśnięcia od średniej z poprzednich nocy, w minutach. Liczona od 18:00, żeby północ nie dzieliła średniej. */
    internal fun sleepOnsetDeviationMin(start: Instant?, previous: List<Instant>): Double? {
        if (start == null || previous.size < 3) return null
        fun minutesFromEvening(i: Instant): Double {
            val local = i.atZone(com.zacco.rytm.domain.time.RytmTime.zone).toLocalTime()
            val m = local.hour * 60 + local.minute - 18 * 60
            return (if (m < 0) m + 24 * 60 else m).toDouble()
        }
        val mean = previous.map(::minutesFromEvening).average()
        return abs(minutesFromEvening(start) - mean)
    }

    internal fun restingHrScore(input: ReadinessInput, config: ReadinessConfig, flags: MutableList<ReadinessFlag>): Int? {
        val rhr = input.nightRestingHr ?: return null
        if (input.restingHrBaseline.size < config.minBaselinePoints) return null
        val delta = rhr - input.restingHrBaseline.average()
        if (delta >= 5) flags += ReadinessFlag.RESTING_HR_ELEVATED
        // 75 przy baseline, -10 pkt za każde uderzenie powyżej
        return (75 - 10 * delta).roundToInt().coerceIn(0, 100)
    }

    internal fun hrvScore(input: ReadinessInput, config: ReadinessConfig, flags: MutableList<ReadinessFlag>): Int? {
        val hrv = input.hrvRmssd ?: input.checkIn?.hrvRmssd ?: return null
        if (input.hrvBaseline.size < config.minBaselinePoints) return null
        val ratio = hrv / input.hrvBaseline.average()
        if (ratio < 0.85) flags += ReadinessFlag.HRV_LOW
        // 75 przy baseline, ±1 pkt za każdy 1% różnicy
        return (75 + (ratio - 1) * 100).roundToInt().coerceIn(0, 100)
    }

    internal fun subjectiveScore(input: ReadinessInput, flags: MutableList<ReadinessFlag>): Int? {
        val c = input.checkIn ?: run {
            flags += ReadinessFlag.NO_CHECK_IN
            return null
        }
        if (c.energy <= 2) flags += ReadinessFlag.LOW_ENERGY
        if (c.soreAreas.isNotEmpty()) flags += ReadinessFlag.SORENESS
        val base = ((c.energy + c.mood) / 2.0 - 1) / 4 * 100
        return (base - 10 * c.soreAreas.size.coerceAtMost(3)).roundToInt().coerceIn(0, 100)
    }

    internal fun loadScore(input: ReadinessInput, flags: MutableList<ReadinessFlag>): Int? {
        val load = input.load ?: return null
        val yesterday = load.yesterday.toDouble()
        val reference = load.chronicDailyAvg.takeIf { load.reliable && it > 0 }
        val heaviness = if (reference != null) yesterday / reference else yesterday / ABSOLUTE_REFERENCE_LOAD
        // Wczoraj na poziomie średniej = 90, dwa razy więcej = 50
        var score = (130 - 40 * heaviness).coerceIn(10.0, 100.0)
        if (heaviness >= 2.0) flags += ReadinessFlag.HEAVY_YESTERDAY
        when (load.zone) {
            AcwrZone.HIGH -> { score -= 25; flags += ReadinessFlag.ACWR_HIGH }
            AcwrZone.ELEVATED -> score -= 10
            else -> Unit
        }
        return score.roundToInt().coerceIn(0, 100)
    }

    private const val DEFAULT_SCORE = 60
    private const val FLOOR_SLEEP_MIN = 240

    /** Gdy brak 28 dni historii: „typowy” dzień to ok. 300 sRPE (np. 60 min × RPE 5). */
    private const val ABSOLUTE_REFERENCE_LOAD = 300.0
}
