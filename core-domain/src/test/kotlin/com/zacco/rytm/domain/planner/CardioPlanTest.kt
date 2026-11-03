package com.zacco.rytm.domain.planner

import com.zacco.rytm.domain.model.DayKind
import com.zacco.rytm.domain.model.Intensity
import com.zacco.rytm.domain.profile.ActivityChoice
import com.zacco.rytm.domain.profile.Goal
import com.zacco.rytm.domain.profile.PersonalProfile
import com.zacco.rytm.domain.readiness.Readiness
import com.zacco.rytm.domain.readiness.ReadinessLevel
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CardioPlanTest {
    private val monday = LocalDate.of(2026, 11, 2)

    private fun plan(p: PersonalProfile, ctx: PlanningContext = PlanningContext(today = monday)) =
        WeekPlanner(p.plannerConfig(), ExerciseLibrary(p.toUserProfile(monday).equipment, p.goal)).planWeek(monday, ctx)

    @Test
    fun runnerWithoutStrengthGetsOnlyRuns() {
        val p = PersonalProfile(activities = listOf(ActivityChoice.RUN), goal = Goal.ENDURANCE, strengthDays = listOf(2, 4, 6), strengthPerWeek = 3)
        val week = plan(p)
        assertEquals(0, week.count { it.kind == DayKind.STRENGTH })
        val runs = week.filter { it.kind == DayKind.CARDIO }
        assertEquals(3, runs.size)
        assertEquals(listOf(CardioType.EASY, CardioType.INTERVALS, CardioType.LONG), runs.map { it.cardio!!.type })
        assertTrue(runs.all { it.cardio!!.kind == CardioKind.RUN })
    }

    @Test
    fun mixedSplitsSessions() {
        val p = PersonalProfile(activities = listOf(ActivityChoice.STRENGTH_HOME, ActivityChoice.BIKE), goal = Goal.RECOMP, strengthDays = listOf(1, 3, 5), strengthPerWeek = 3)
        assertEquals(2 to 1, p.sessionSplit())
        val week = plan(p)
        assertEquals(2, week.count { it.kind == DayKind.STRENGTH })
        assertEquals(1, week.count { it.kind == DayKind.CARDIO && it.cardio!!.kind == CardioKind.BIKE })
    }

    @Test
    fun walkingAlongsideStrengthDoesNotTakeSession() {
        val p = PersonalProfile(activities = listOf(ActivityChoice.STRENGTH_HOME, ActivityChoice.WALK), strengthPerWeek = 3)
        assertEquals(3 to 0, p.sessionSplit())
    }

    @Test
    fun treadmillWalkHasTip() {
        val s = CardioLibrary.session(CardioKind.WALK, CardioType.EASY, 45, treadmill = true)
        assertTrue(s.title.contains("bieżni"))
        assertTrue(s.steps.any { it.contains("nachylenie") })
    }

    @Test
    fun lowerReadinessTurnsIntervalsEasy() {
        val p = PersonalProfile(activities = listOf(ActivityChoice.RUN), goal = Goal.ENDURANCE, strengthDays = listOf(1, 3, 6), strengthPerWeek = 2)
        val wednesday = monday.plusDays(2)
        val readiness = Readiness(wednesday, 60, ReadinessLevel.MODERATE, emptyMap(), emptyList(), calibrating = false)
        val day = plan(p, PlanningContext(today = wednesday, readiness = readiness)).first { it.date == wednesday }
        assertEquals(DayKind.CARDIO, day.kind)
        assertEquals(CardioType.EASY, day.cardio!!.type)
        assertTrue(day.intensity < Intensity.HIGH)
    }

    @Test
    fun oldProfileKeepsStrength() {
        val p = PersonalProfile(strengthPerWeek = 2)
        assertTrue(p.wantsStrength)
        assertEquals(2 to 0, p.sessionSplit())
    }
}
