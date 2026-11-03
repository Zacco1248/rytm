package com.zacco.rytm.ai.chat

import com.zacco.rytm.domain.coach.CoachData
import com.zacco.rytm.domain.coach.CoachEngine
import com.zacco.rytm.domain.coach.OverrideExercise
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoachChatTest {
    private val day = LocalDate.of(2026, 11, 4)
    private val data = CoachData(today = day)
    private val ctx = ChatContext.from(data, CoachEngine().today(data), CoachEngine().week(data), "18:00")

    @Test
    fun replaceTodayIsClampedToLimitsAndEquipment() {
        val reply = ChatReply(
            "Krótsza sesja.",
            ChatPlanChange(
                action = "REPLACE_TODAY", title = "20 minut", kind = "STRENGTH", intensity = "MODERATE",
                exercises = listOf(
                    OverrideExercise("Przysiad goblet", 12, 20, 10, 40.0, 900),
                    OverrideExercise("  ", 3, 10, 12),
                ),
                reason = "mało czasu",
            ),
        )
        val r = CoachChatService.toResult(reply, ctx, 1L)
        val o = assertNotNull(r.override)
        val e = o.exercises.single()
        assertEquals(6, e.sets)
        assertEquals(20, e.repsMax)
        assertEquals(ctx.dumbbellSingleMaxKg, e.weightKg)
        assertEquals(300, e.restSec)
        assertEquals(day.toString(), o.date)
    }

    @Test
    fun noneAndInvalidChangesKeepThePlan() {
        assertNull(CoachChatService.toResult(ChatReply("Ok."), ctx, 1L).override)
        val bad = ChatReply("x", ChatPlanChange(action = "REPLACE_TODAY", kind = "DANCE"))
        assertNull(CoachChatService.toResult(bad, ctx, 1L).override)
        val empty = ChatReply("x", ChatPlanChange(action = "REPLACE_TODAY", kind = "STRENGTH", exercises = emptyList()))
        assertNull(CoachChatService.toResult(empty, ctx, 1L).override)
    }

    @Test
    fun restNeedsNoExercisesAndResetOnlyWhenChanged() {
        val rest = CoachChatService.toResult(ChatReply("Odpocznij.", ChatPlanChange(action = "REPLACE_TODAY", kind = "REST")), ctx, 1L)
        assertEquals("REST", rest.override!!.kind)
        assertFalse(CoachChatService.toResult(ChatReply("x", ChatPlanChange(action = "RESET_TODAY")), ctx, 1L).reset)
        assertTrue(CoachChatService.toResult(ChatReply("x", ChatPlanChange(action = "RESET_TODAY")), ctx.copy(todayChanged = true), 1L).reset)
    }

    @Test
    fun replyParsesWithMissingChange() {
        val r = Json { ignoreUnknownKeys = true }.decodeFromString(ChatReply.serializer(), """{"reply":"Hej"}""")
        assertEquals("NONE", r.change.action)
        assertTrue(ctx.knownExercises.isNotEmpty())
    }
}
