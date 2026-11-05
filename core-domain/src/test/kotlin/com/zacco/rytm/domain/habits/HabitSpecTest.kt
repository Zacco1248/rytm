package com.zacco.rytm.domain.habits

import com.zacco.rytm.domain.profile.PersonalProfile
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HabitSpecTest {

    @Test
    fun `profile without own list keeps the three default habits`() {
        val p = PersonalProfile(stepsTarget = 7000)
        assertEquals(DefaultHabits.all.map { it.id }, p.habits().map { it.id })
        assertEquals(7000, p.habits().first { it.autoSource == HabitAutoSource.STEPS }.target)
    }

    @Test
    fun `own list can remove defaults and add a habit a few times a week`() {
        val specs = PersonalProfile().habitSpecs().filterNot { it.id == "no-screen-2330" } +
            HabitSpec("run", "Bieg", timesPerWeek = 3)
        val p = PersonalProfile(stepsTarget = 6000, habitList = specs)
        val habits = p.habits()
        assertTrue(habits.none { it.id == "no-screen-2330" })
        assertEquals(HabitSchedule.TimesPerWeek(3), habits.first { it.id == "run" }.schedule)
        // Cel kroków zawsze z profilu, nawet gdy lista jest zapisana.
        assertEquals(6000, habits.first { it.autoSource == HabitAutoSource.STEPS }.target)
    }

    @Test
    fun `empty list means no habits and survives serialization`() {
        val p = PersonalProfile(habitList = emptyList())
        assertTrue(p.habits().isEmpty())
        val back = Json.decodeFromString(PersonalProfile.serializer(), Json.encodeToString(PersonalProfile.serializer(), p))
        assertEquals(emptyList(), back.habitList)
        val old = Json { ignoreUnknownKeys = true }.decodeFromString(PersonalProfile.serializer(), "{}")
        assertEquals(3, old.habits().size)
    }
}
