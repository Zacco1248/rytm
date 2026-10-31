package com.zacco.rytm.domain.workout

import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.model.PlannedExercise
import com.zacco.rytm.domain.model.Source
import com.zacco.rytm.domain.model.StrengthWorkout
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutRunTest {
    private val workout = StrengthWorkout(
        "Góra",
        listOf(
            PlannedExercise("Wyciskanie", 2, 12..15, 6.0, "3-1-1", 75),
            PlannedExercise("Pompki", 1, 8..15, null, null, 60),
        ),
    )
    private val t0 = 1_800_000_000_000L
    private fun run() = WorkoutRun.start("w1", workout, ActivityType.STRENGTH, 1.0, t0)

    @Test
    fun `logging a set starts the rest and moves to the next set`() {
        val r = run().log(14, 6.0, 2, t0 + 60_000)
        assertEquals(2, r.setNo)
        assertTrue(r.isResting(t0 + 100_000))
        assertEquals(35, r.restLeftSec(t0 + 100_000))
        assertFalse(r.isResting(t0 + 136_000))
    }

    @Test
    fun `suggestions follow the previous set`() {
        val r = run()
        assertEquals(15, r.suggestedReps())
        assertEquals(6.0, r.suggestedWeight())
        val r2 = r.log(13, 7.0, null, t0)
        assertEquals(13, r2.suggestedReps())
        assertEquals(7.0, r2.suggestedWeight())
    }

    @Test
    fun `last set of the workout finishes without rest`() {
        val r = run().log(15, 6.0, null, t0).log(15, 6.0, null, t0 + 1).log(12, null, null, t0 + 2)
        assertTrue(r.isFinished)
        assertNull(r.restUntilMs)
        assertEquals(3, r.sets.size)
    }

    @Test
    fun `skipping an exercise moves on and remembers it`() {
        val r = run().skipExercise()
        assertEquals("Pompki", r.current?.name)
        assertEquals(listOf("Wyciskanie"), r.skipped)
    }

    @Test
    fun `extra set extends the current exercise`() {
        val r = run().addSet()
        assertEquals(3, r.current?.sets)
    }

    @Test
    fun `log becomes a strength activity with sets`() {
        val r = run().log(15, 6.0, null, t0 + 60_000).log(0, 6.0, null, t0 + 120_000)
        val log = r.toLog(t0 + 40 * 60_000, rpe = 7, avgHr = 110, kcal = 220, source = Source.WATCH_APP)
        assertEquals(1, log.sets.size)
        assertEquals(40, log.durationMin)
        assertEquals(90.0, log.volumeKg)
        val a = log.toActivity()
        assertEquals(ActivityType.STRENGTH, a.type)
        assertEquals(280, a.load)
        assertEquals(220, a.kcal)
    }

    @Test
    fun `state survives json round trip`() {
        val r = run().log(15, 6.0, 1, t0)
        val json = Json.encodeToString(WorkoutRun.serializer(), r)
        assertEquals(r, Json.decodeFromString(WorkoutRun.serializer(), json))
    }
}
