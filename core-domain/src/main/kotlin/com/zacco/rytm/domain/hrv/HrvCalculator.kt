package com.zacco.rytm.domain.hrv

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Liczy RMSSD z krótkiego (około 60 s) zapisu IBI, odrzucając artefakty.
 *
 * Kroki:
 * 1. odrzucenie próbek oznaczonych przez sensor jako błędne,
 * 2. filtr zakresu fizjologicznego (domyślnie 300–2000 ms, czyli 30–200 bpm),
 * 3. filtr lokalnej mediany: odstęp różniący się od mediany sąsiadów o więcej niż [Config.maxDeviationFromLocalMedian]
 *    traktujemy jako artefakt (zgubione lub dodatkowe uderzenie),
 * 4. RMSSD tylko z par kolejnych uderzeń, które obie przeszły filtry. Nie sklejamy różnic przez dziurę po odrzuconej próbce.
 */
object HrvCalculator {

    data class Config(
        val minIbiMs: Int = 300,
        val maxIbiMs: Int = 2000,
        val localMedianRadius: Int = 2,
        val maxDeviationFromLocalMedian: Double = 0.20,
        val minPairs: Int = 30,
        val goodAcceptedRatio: Double = 0.90,
        val acceptableAcceptedRatio: Double = 0.75,
    )

    fun compute(samples: List<IbiSample>, config: Config = Config()): HrvResult {
        val accepted = acceptedMask(samples, config)

        val diffs = ArrayList<Double>()
        for (i in 1 until samples.size) {
            if (accepted[i] && accepted[i - 1]) {
                diffs += (samples[i].ms - samples[i - 1].ms).toDouble()
            }
        }

        val acceptedIbis = samples.filterIndexed { i, _ -> accepted[i] }.map { it.ms }
        val beatsAccepted = acceptedIbis.size
        val ratio = if (samples.isEmpty()) 0.0 else beatsAccepted.toDouble() / samples.size

        val rmssd = if (diffs.size >= config.minPairs) sqrt(diffs.sumOf { it * it } / diffs.size) else null
        val quality = when {
            rmssd == null -> HrvQuality.POOR
            ratio >= config.goodAcceptedRatio -> HrvQuality.GOOD
            ratio >= config.acceptableAcceptedRatio -> HrvQuality.ACCEPTABLE
            else -> HrvQuality.POOR
        }

        return HrvResult(
            rmssdMs = rmssd,
            lnRmssd = rmssd?.takeIf { it > 0 }?.let { ln(it) },
            meanHrBpm = acceptedIbis.takeIf { it.isNotEmpty() }?.let { 60_000.0 / it.average() },
            beatsTotal = samples.size,
            beatsAccepted = beatsAccepted,
            pairsUsed = diffs.size,
            quality = quality,
        )
    }

    internal fun acceptedMask(samples: List<IbiSample>, config: Config): BooleanArray {
        val plausible = BooleanArray(samples.size) { i ->
            val s = samples[i]
            s.sensorValid && s.ms in config.minIbiMs..config.maxIbiMs
        }
        return BooleanArray(samples.size) { i ->
            if (!plausible[i]) return@BooleanArray false
            val neighbours = (i - config.localMedianRadius..i + config.localMedianRadius)
                .filter { it != i && it in samples.indices && plausible[it] }
                .map { samples[it].ms }
            if (neighbours.isEmpty()) return@BooleanArray true
            val median = median(neighbours)
            abs(samples[i].ms - median) <= config.maxDeviationFromLocalMedian * median
        }
    }

    private fun median(values: List<Int>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid].toDouble() else (sorted[mid - 1] + sorted[mid]) / 2.0
    }
}
