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
}

object WatchCodec {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(s: WatchSnapshot): ByteArray = json.encodeToString(WatchSnapshot.serializer(), s).encodeToByteArray()
    fun decodeSnapshot(b: ByteArray): WatchSnapshot = json.decodeFromString(WatchSnapshot.serializer(), b.decodeToString())

    fun encode(e: WatchEvent): ByteArray = json.encodeToString(WatchEvent.serializer(), e).encodeToByteArray()
    fun decodeEvent(b: ByteArray): WatchEvent = json.decodeFromString(WatchEvent.serializer(), b.decodeToString())
}
