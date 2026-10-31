package com.zacco.rytm.domain.heart

import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

data class HrSample(val time: Instant, val bpm: Int)

/**
 * Tętno spoczynkowe w nocy: najniższa średnia z okna 30 minut w czasie snu (sekcja 3.4).
 * Przy próbkach co 10 min okno ma 3–4 próbki; wymagamy co najmniej [minSamplesPerWindow].
 */
object NightRestingHr {

    fun compute(
        samples: List<HrSample>,
        sleepStart: Instant,
        sleepEnd: Instant,
        window: Duration = Duration.ofMinutes(30),
        minSamplesPerWindow: Int = 3,
    ): Int? {
        val night = samples.filter { it.time >= sleepStart && it.time < sleepEnd && it.bpm in 25..220 }
            .sortedBy { it.time }
        var best: Double? = null
        var startIdx = 0
        for (endIdx in night.indices) {
            while (Duration.between(night[startIdx].time, night[endIdx].time) >= window) startIdx++
            val count = endIdx - startIdx + 1
            if (count >= minSamplesPerWindow) {
                val avg = (startIdx..endIdx).sumOf { night[it].bpm }.toDouble() / count
                if (best == null || avg < best) best = avg
            }
        }
        return best?.roundToInt()
    }
}

/**
 * Tętno spoczynkowe z dnia, gdy brak nocy (zegarek ładowany w nocy). Najniższa średnia z 30 minut,
 * pomijając treningi i godzinę po nich. Przy gęstym tętnie (ok. 1 próbka/min) wymagamy 15 próbek w oknie,
 * żeby pojedyncze odczyty nie dawały zaniżonego wyniku.
 */
object DayRestingHr {

    fun compute(
        samples: List<HrSample>,
        dayStart: Instant,
        dayEnd: Instant,
        workouts: List<Pair<Instant, Instant>>,
        recovery: Duration = Duration.ofMinutes(60),
        minSamplesPerWindow: Int = 15,
    ): Int? {
        val calm = samples.filter { s ->
            workouts.none { (start, end) -> s.time >= start && s.time < end.plus(recovery) }
        }
        return NightRestingHr.compute(calm, dayStart, dayEnd, minSamplesPerWindow = minSamplesPerWindow)
    }
}
