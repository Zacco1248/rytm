package com.zacco.rytm.domain.goals

import com.zacco.rytm.domain.model.BodyMetrics
import com.zacco.rytm.domain.workout.SetLog
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoalsTest {
    private val today = LocalDate.of(2026, 12, 20)

    private fun pullUps(vararg reps: Int) = reps.mapIndexed { i, r ->
        SetLog("w$i", "Podciąganie na drążku", 1, r, null, 0, today.minusDays(7L * (reps.size - 1 - i)))
    }

    @Test
    fun `reps goal forecasts a date from the weekly trend and counts milestones`() {
        val goal = UserGoal("g", GoalKind.REPS, "Podciąganie na drążku", 10.0, createdOn = today.minusDays(21).toString(), start = 2.0)
        val p = Goals.progress(goal, today, emptyList(), pullUps(2, 3, 4, 6))
        assertEquals(6.0, p.current)
        assertEquals(0.5, p.share, 1e-9)
        assertEquals(2, p.milestonesReached)
        val f = assertNotNull(p.forecast)
        assertTrue(f.isAfter(today) && f.isBefore(today.plusDays(60)))
    }

    @Test
    fun `weight loss goal goes down and is done when reached`() {
        val weights = (0L until 28L).map { BodyMetrics(today.minusDays(27 - it), 82.0 - 0.1 * it, null) }
        val goal = UserGoal("w", GoalKind.WEIGHT, target = 78.0, createdOn = today.minusDays(27).toString())
        val p = Goals.progress(goal, today, weights, emptyList())
        assertTrue(p.share > 0.4 && p.share < 0.8)
        assertNotNull(p.forecast)
        val done = Goals.progress(goal.copy(target = 80.0), today, weights, emptyList())
        assertTrue(done.done)
        assertEquals(4, done.milestonesReached)
    }

    @Test
    fun `no progress means no forecast`() {
        val goal = UserGoal("g", GoalKind.REPS, "Podciąganie na drążku", 10.0, createdOn = today.minusDays(21).toString())
        val p = Goals.progress(goal, today, emptyList(), pullUps(5, 5, 4))
        assertNull(p.forecast)
        assertTrue(p.note.contains("brak prognozy"))
        val empty = Goals.progress(goal, today, emptyList(), emptyList())
        assertNull(empty.current)
    }

    @Test
    fun `flat belly tracks body fat down and custom goal uses manual share`() {
        val w = (0..5).map { i -> BodyMetrics(today.minusDays(7L * (5 - i)), 80.0, 20.0 - i * 0.5) }
        val belly = UserGoal("b", GoalKind.FAT_PCT, null, 15.0, createdOn = today.minusDays(35).toString(), label = "Płaski brzuch")
        val p = Goals.progress(belly, today, w, emptyList())
        assertEquals(17.5, p.current)
        assertTrue(p.share in 0.4..0.6, p.share.toString())
        assertNotNull(p.forecast)
        assertTrue(belly.title.startsWith("Płaski brzuch"))

        val own = UserGoal("c", GoalKind.CUSTOM, null, 1.0, createdOn = today.toString(), label = "Przebiec 10 km", manualShare = 0.5)
        val c = Goals.progress(own, today, emptyList(), emptyList())
        assertEquals(0.5, c.share)
        assertEquals(2, c.milestonesReached)
        assertEquals("Przebiec 10 km", own.title)
    }
}
