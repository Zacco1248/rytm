package com.zacco.rytm.domain.hrv

import org.junit.jupiter.api.Test
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HrvCalculatorTest {

    /** Naprzemienne 800/820 ms: każda różnica ma 20 ms, więc RMSSD = 20. */
    private fun alternating(n: Int) = List(n) { IbiSample(if (it % 2 == 0) 800 else 820) }

    @Test
    fun `clean signal gives exact rmssd`() {
        val result = HrvCalculator.compute(alternating(70))

        assertEquals(20.0, result.rmssdMs!!, 1e-9)
        assertEquals(ln(20.0), result.lnRmssd!!, 1e-9)
        assertEquals(69, result.pairsUsed)
        assertEquals(HrvQuality.GOOD, result.quality)
        assertEquals(60_000.0 / 810.0, result.meanHrBpm!!, 1e-9)
    }

    @Test
    fun `missed beat artefact is rejected and does not bridge the gap`() {
        val samples = alternating(70).toMutableList()
        samples[30] = IbiSample(1620) // zgubione uderzenie: dwa odstępy zlane w jeden

        val result = HrvCalculator.compute(samples)

        assertEquals(69, result.beatsAccepted)
        assertEquals(67, result.pairsUsed) // znikają obie pary sąsiadujące z artefaktem
        assertEquals(20.0, result.rmssdMs!!, 1e-9)
    }

    @Test
    fun `sensor flagged and out of range samples are rejected`() {
        val samples = alternating(70).toMutableList()
        samples[10] = IbiSample(810, sensorValid = false)
        samples[40] = IbiSample(250)

        val result = HrvCalculator.compute(samples)

        assertEquals(68, result.beatsAccepted)
        assertEquals(20.0, result.rmssdMs!!, 1e-9)
    }

    @Test
    fun `too few pairs gives no rmssd and poor quality`() {
        val result = HrvCalculator.compute(alternating(20))

        assertNull(result.rmssdMs)
        assertEquals(HrvQuality.POOR, result.quality)
    }

    @Test
    fun `many artefacts lower quality`() {
        val samples = alternating(80).mapIndexed { i, s -> if (i % 6 == 0) IbiSample(s.ms, sensorValid = false) else s }

        val result = HrvCalculator.compute(samples)

        assertNotNull(result.rmssdMs)
        assertEquals(HrvQuality.ACCEPTABLE, result.quality)
    }

    @Test
    fun `rmssd matches manual computation for irregular series`() {
        val ibis = listOf(812, 790, 805, 830, 815, 798, 802, 825, 840, 818)
        val samples = (ibis + ibis + ibis + ibis).map { IbiSample(it) }
        val diffs = samples.zipWithNext { a, b -> (b.ms - a.ms).toDouble() }
        val expected = sqrt(diffs.sumOf { it * it } / diffs.size)

        val result = HrvCalculator.compute(samples)

        assertEquals(expected, result.rmssdMs!!, 1e-9)
    }
}
