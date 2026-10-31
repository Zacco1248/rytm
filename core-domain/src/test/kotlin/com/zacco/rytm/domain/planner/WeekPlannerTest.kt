package com.zacco.rytm.domain.planner

import com.zacco.rytm.domain.load.AcwrZone
import com.zacco.rytm.domain.load.LoadSummary
import com.zacco.rytm.domain.model.Activity
import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.model.DayKind
import com.zacco.rytm.domain.model.Intensity
import com.zacco.rytm.domain.model.LoadedArea
import com.zacco.rytm.domain.model.Source
import com.zacco.rytm.domain.model.StrengthFocus
import com.zacco.rytm.domain.readiness.Readiness
import com.zacco.rytm.domain.readiness.ReadinessCalculator
import com.zacco.rytm.domain.time.RytmTime
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeekPlannerTest {

    private val planner = WeekPlanner()
    private val novemberMonday = LocalDate.of(2026, 11, 2)
    private val decemberMonday = LocalDate.of(2026, 12, 7)

    private fun readiness(date: LocalDate, score: Int) =
        Readiness(date, score, ReadinessCalculator.levelOf(score), emptyMap(), emptyList(), calibrating = false)

    private fun activity(
        date: LocalDate,
        type: ActivityType,
        minutes: Int,
        rpe: Int? = null,
        notes: String? = null,
        areas: Set<LoadedArea> = emptySet(),
    ) = Activity(
        "$date-$type", type, date.atTime(LocalTime.of(10, 0)).atZone(RytmTime.zone).toInstant(),
        minutes, rpe, null, null, Source.MANUAL, notes, areas,
    )

    private fun List<com.zacco.rytm.domain.model.PlannedDay>.on(day: DayOfWeek) = first { it.date.dayOfWeek == day }

    @Test
    fun `november template follows section 3_1`() {
        val week = planner.planWeek(novemberMonday, PlanningContext(today = novemberMonday))

        assertEquals(DayKind.STRENGTH, week.on(DayOfWeek.MONDAY).kind)
        assertEquals(StrengthFocus.UPPER, week.on(DayOfWeek.MONDAY).focus) // przed wtorkową siatkówką
        assertEquals("Siatkówka", week.on(DayOfWeek.TUESDAY).title)
        assertEquals("Squash", week.on(DayOfWeek.WEDNESDAY).title)
        assertEquals(DayKind.PERSONAL_TRAINING, week.on(DayOfWeek.FRIDAY).kind)
        assertEquals(StrengthFocus.FULL_BODY, week.on(DayOfWeek.SATURDAY).focus)
        assertEquals(DayKind.REST, week.on(DayOfWeek.SUNDAY).kind)
    }

    @Test
    fun `from december friday is strength B after thursday volleyball so it gets lighter`() {
        val week = planner.planWeek(decemberMonday, PlanningContext(today = decemberMonday))
        val friday = week.on(DayOfWeek.FRIDAY)

        assertEquals(DayKind.STRENGTH, friday.kind)
        // reguła 2: dzień po meczu lekka góra
        assertEquals(StrengthFocus.UPPER, friday.focus)
        assertEquals(Intensity.LOW, friday.intensity)
        // tydzień nadal ma co najmniej dwie sesje siłowe
        assertTrue(week.count { it.kind == DayKind.STRENGTH } >= 2)
    }

    @Test
    fun `cancelled match becomes strength when the week lacks strength`() {
        val tuesday = decemberMonday.plusDays(1)
        val week = planner.planWeek(
            decemberMonday,
            PlanningContext(today = tuesday, cancelled = setOf(tuesday)),
        )
        val replaced = week.on(DayOfWeek.TUESDAY)

        assertEquals(DayKind.STRENGTH, replaced.kind)
        // jutro squash → bez ciężkich nóg
        assertEquals(StrengthFocus.UPPER, replaced.focus)
    }

    @Test
    fun `cancelled match becomes mobility when strength is covered and load is not low`() {
        val wednesday = novemberMonday.plusDays(2)
        val week = planner.planWeek(
            novemberMonday,
            PlanningContext(
                today = wednesday,
                cancelled = setOf(wednesday),
                activities = listOf(activity(novemberMonday, ActivityType.STRENGTH, 45, 7)),
            ),
        )
        assertEquals(DayKind.MOBILITY, week.on(DayOfWeek.WEDNESDAY).kind)
    }

    @Test
    fun `low readiness replaces strength with mobility but keeps fixed sport with warning`() {
        val monday = planner.planDay(novemberMonday, PlanningContext(today = novemberMonday, readiness = readiness(novemberMonday, 45)))
        assertEquals(DayKind.MOBILITY, monday.kind)

        val tuesday = novemberMonday.plusDays(1)
        val match = planner.planDay(tuesday, PlanningContext(today = tuesday, readiness = readiness(tuesday, 45)))
        assertEquals(DayKind.FIXED_SPORT, match.kind)
        assertTrue(match.warnings.isNotEmpty())
    }

    @Test
    fun `very low readiness means rest`() {
        val day = planner.planDay(novemberMonday, PlanningContext(today = novemberMonday, readiness = readiness(novemberMonday, 20)))
        assertEquals(DayKind.REST, day.kind)
    }

    @Test
    fun `high acwr forces a lighter day`() {
        val load = LoadSummary(novemberMonday, 0, 0, 3000, 1500, 2.0, AcwrZone.HIGH, true)
        val day = planner.planDay(novemberMonday, PlanningContext(today = novemberMonday, load = load))
        assertEquals(Intensity.LOW, day.intensity)
    }

    @Test
    fun `heavy other activity yesterday warns with its description`() {
        val saturday = novemberMonday.plusDays(5)
        val day = planner.planDay(
            saturday,
            PlanningContext(today = saturday, activities = listOf(activity(saturday.minusDays(1), ActivityType.OTHER, 240, notes = "remont: noszenie płyt"))),
        )
        assertTrue(day.warnings.any { "remont: noszenie płyt" in it })
    }

    @Test
    fun `short other activity does not trigger the rule`() {
        val saturday = novemberMonday.plusDays(5)
        val day = planner.planDay(
            saturday,
            PlanningContext(today = saturday, activities = listOf(activity(saturday.minusDays(1), ActivityType.OTHER, 30, rpe = 4))),
        )
        assertTrue(day.warnings.isEmpty())
    }

    @Test
    fun `week always has a rest day`() {
        val sunday = novemberMonday.plusDays(6)
        val week = planner.planWeek(novemberMonday, PlanningContext(today = novemberMonday))
        assertEquals(1, week.count { it.kind == DayKind.REST })
        assertEquals(sunday, week.single { it.kind == DayKind.REST }.date)
    }

    @Test
    fun `loaded back moves strength to lower body when no match tomorrow`() {
        // grudzień: sobota to dzień elastyczny, więc bierzemy poniedziałek po ciężkiej niedzieli
        val monday = decemberMonday
        val day = planner.planDay(
            monday,
            PlanningContext(today = monday, activities = listOf(activity(monday.minusDays(1), ActivityType.OTHER, 180, 6, "remont", setOf(LoadedArea.BACK)))),
        )
        // jutro siatkówka, więc dół odpada: lekka sesja
        assertEquals(Intensity.LOW, day.intensity)
        assertTrue(day.warnings.single().contains("odciąż plecy"))
    }

    @Test
    fun `loaded legs switch full body to upper`() {
        val saturday = novemberMonday.plusDays(5)
        val day = planner.planDay(
            saturday,
            PlanningContext(today = saturday, activities = listOf(activity(saturday.minusDays(1), ActivityType.OTHER, 150, 6, "przeprowadzka", setOf(LoadedArea.LEGS)))),
        )
        assertEquals(StrengthFocus.UPPER, day.focus)
    }

    @Test
    fun `loaded shoulders switch full body to lower when no match tomorrow`() {
        val saturday = novemberMonday.plusDays(5)
        val day = planner.planDay(
            saturday,
            PlanningContext(today = saturday, activities = listOf(activity(saturday.minusDays(1), ActivityType.OTHER, 150, 6, "malowanie sufitu", setOf(LoadedArea.SHOULDERS)))),
        )
        assertEquals(StrengthFocus.LOWER, day.focus)
    }

    @Test
    fun `full body load turns strength into mobility`() {
        val saturday = novemberMonday.plusDays(5)
        val day = planner.planDay(
            saturday,
            PlanningContext(today = saturday, activities = listOf(activity(saturday.minusDays(1), ActivityType.OTHER, 240, 7, "remont", setOf(LoadedArea.FULL_BODY)))),
        )
        assertEquals(DayKind.MOBILITY, day.kind)
    }

    @Test
    fun `renovation preselects back and shoulders`() {
        assertEquals(setOf(LoadedArea.BACK, LoadedArea.SHOULDERS), LoadedArea.defaultsFor("Remont łazienki"))
        assertTrue(LoadedArea.defaultsFor("ogród").isEmpty())
    }
}
