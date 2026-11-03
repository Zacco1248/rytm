package com.zacco.rytm.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Co telefon wysyła na zegarek. Daty i godziny jako tekst ISO, żeby DTO nie zależały od wersji java.time na zegarku.
 * Zegarek tylko wyświetla; wszystko liczy telefon.
 */
@Serializable
data class WatchSnapshot(
    val date: String,
    val readinessScore: Int,
    /** HIGH / MODERATE / LOW */
    val readinessLevel: String,
    val calibrating: Boolean,
    val planTitle: String,
    /** LOW / MODERATE / HIGH */
    val planIntensity: String,
    val planWhy: String?,
    val planWarning: String?,
    val bedtime: String,
    val habits: List<WatchHabit>,
    /** Dzisiejsze i wczorajsze treningi bez RPE, do szybkiej oceny. */
    val toRate: List<WatchActivity>,
    val checkInDone: Boolean,
    val generatedAt: Long,
    /** Dzisiejsza sesja z ćwiczeniami i ciężarem z progresji; null w dni bez treningu do zrobienia. */
    val workout: WatchWorkout? = null,
    /** Skrót wieczornej analizy (rekomendacja na jutro), gdy już jest. */
    val evening: String? = null,
)

@Serializable
data class WatchWorkout(
    val title: String,
    /** Nazwa ActivityType: STRENGTH, MOBILITY, CARDIO. */
    val type: String,
    val exercises: List<WatchExercise>,
    /** Skok ciężaru przy zmianie na zegarku. */
    val stepKg: Double = 1.0,
    /** Dziś już zapisano serie tego dnia. */
    val doneToday: Boolean = false,
)

@Serializable
data class WatchExercise(
    val name: String,
    val sets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val weightKg: Double? = null,
    val tempo: String? = null,
    val restSec: Int = 60,
    val note: String? = null,
)

@Serializable
data class WatchSet(
    val exercise: String,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double? = null,
    val rir: Int? = null,
)

@Serializable
data class WatchHabit(
    val id: String,
    val name: String,
    val done: Boolean,
    /** Automatyczne (kroki) nie dają się odhaczyć ręcznie. */
    val auto: Boolean,
    val detail: String,
)

@Serializable
data class WatchActivity(val id: String, val title: String, val durationMin: Int)

/** Wpisy z zegarka. */
@Serializable
sealed interface WatchEvent {
    val date: String

    @Serializable
    @SerialName("habit")
    data class HabitToggle(override val date: String, val habitId: String, val done: Boolean) : WatchEvent

    @Serializable
    @SerialName("check_in")
    data class CheckIn(
        override val date: String,
        val energy: Int,
        val mood: Int,
        /** Nazwy BodyArea. */
        val sore: List<String> = emptyList(),
        val hrvRmssd: Double? = null,
    ) : WatchEvent

    @Serializable
    @SerialName("rpe")
    data class Rpe(override val date: String, val activityId: String, val rpe: Int) : WatchEvent

    @Serializable
    @SerialName("other_activity")
    data class OtherActivity(
        override val date: String,
        val description: String,
        val durationMin: Int,
        val rpe: Int?,
        /** Nazwy LoadedArea. */
        val areas: List<String>,
        /** Koniec aktywności (epoch ms); domyślnie chwila wysłania. */
        val endedAt: Long,
    ) : WatchEvent

    @Serializable
    @SerialName("workout")
    data class WorkoutDone(
        override val date: String,
        /** Stały id treningu: ponowne wysłanie nadpisuje zamiast dublować. */
        val id: String,
        val title: String,
        val type: String,
        val startedAt: Long,
        val endedAt: Long,
        val rpe: Int? = null,
        val avgHr: Int? = null,
        val kcal: Int? = null,
        val sets: List<WatchSet> = emptyList(),
    ) : WatchEvent
}

object WatchCodec {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(s: WatchSnapshot): ByteArray = json.encodeToString(WatchSnapshot.serializer(), s).encodeToByteArray()
    fun decodeSnapshot(b: ByteArray): WatchSnapshot = json.decodeFromString(WatchSnapshot.serializer(), b.decodeToString())

    fun encode(e: WatchEvent): ByteArray = json.encodeToString(WatchEvent.serializer(), e).encodeToByteArray()
    fun decodeEvent(b: ByteArray): WatchEvent = json.decodeFromString(WatchEvent.serializer(), b.decodeToString())
}
