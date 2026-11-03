package com.zacco.rytm.domain.profile

import com.zacco.rytm.domain.coach.CoachData
import com.zacco.rytm.domain.coach.CoachEngine
import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.model.DayKind
import com.zacco.rytm.domain.planner.EquipmentKind
import com.zacco.rytm.domain.planner.PlannerConfig
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersonalProfileTest {

    private val today = LocalDate.of(2026, 12, 7) // poniedziałek

    @Test
    fun `targets follow Mifflin St Jeor, activity and goal`() {
        val p = PersonalProfile(sex = Sex.FEMALE, birthYear = 1996, heightCm = 168, weightKg = 65.0, activity = ActivityLevel.LIGHT, goal = Goal.FAT_LOSS)
        // BMR = 650 + 1050 − 150 − 161 = 1389; × 1,4 = 1944,6; −20% (389) = 1556 → 1560
        val t = p.toUserProfile(today).nutrition
        assertEquals(1560, t.kcal)
        assertEquals(130, t.proteinG) // 2,0 g/kg
        assertEquals(52, t.fatG) // max(0,8 g/kg; 25% kcal)
        assertTrue(t.weeklyWeightChangeKg < 0)
    }

    @Test
    fun `custom targets win over the calculation`() {
        val t = ProfilePresets.existingInstall.toUserProfile(today).nutrition
        assertEquals(2300, t.kcal)
        assertEquals(161, t.proteinG)
    }

    @Test
    fun `existing install preset gives the same week as before personalisation`() {
        assertEquals(PlannerConfig().fixedSports, ProfilePresets.existingInstall.plannerConfig().fixedSports)
        val november = LocalDate.of(2026, 11, 2)
        val config = ProfilePresets.existingInstall.plannerConfig()
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY), config.strengthPlan(november).keys)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), config.strengthPlan(today).keys)
    }

    @Test
    fun `friend with football and no equipment gets bodyweight sessions on chosen days`() {
        val p = PersonalProfile(
            goal = Goal.HEALTH,
            equipment = EquipmentKind.NONE,
            strengthDays = listOf(2, 4),
            fixedSports = listOf(FixedSportEntry(3, "Piłka nożna", ActivityType.OTHER)),
            personalTraining = null,
            stepsTarget = 9000,
        )
        val data = CoachData(today = today, profile = p.toUserProfile(today), habits = p.habits())
        val week = CoachEngine().week(data)
        assertEquals("Piłka nożna", week.days[2].plan.title)
        assertEquals(DayKind.FIXED_SPORT, week.days[2].plan.kind)
        assertEquals(DayKind.STRENGTH, week.days[1].plan.kind)
        assertTrue(week.days.flatMap { it.plan.workout?.exercises.orEmpty() }.all { it.weightKg == null })
        assertEquals(9000, p.habits().first { it.id == "activity-steps" }.target)
        assertNull(p.plannerConfig().personalTrainingDay)
    }

    @Test
    fun `profile survives json round trip`() {
        val json = Json { ignoreUnknownKeys = true }
        val p = ProfilePresets.existingInstall.copy(goalNote = "Rekompozycja i lepszy sen")
        assertEquals(p, json.decodeFromString(PersonalProfile.serializer(), json.encodeToString(PersonalProfile.serializer(), p)))
    }
}
