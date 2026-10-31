package com.zacco.rytm.domain.model

import java.time.Instant

enum class ActivityType { VOLLEYBALL, SQUASH, STRENGTH, PERSONAL_TRAINING, MOBILITY, CARDIO, WALK, OTHER }

/** Co obciążyła „inna aktywność” (pole opcjonalne przy ręcznym logowaniu). */
enum class LoadedArea {
    BACK, SHOULDERS, LEGS, ARMS, FULL_BODY;

    companion object {
        /** Domyślne zaznaczenie w formularzu na podstawie opisu: „remont” → plecy i barki. */
        fun defaultsFor(description: String?): Set<LoadedArea> = when {
            description == null -> emptySet()
            description.contains("remont", ignoreCase = true) -> setOf(BACK, SHOULDERS)
            else -> emptySet()
        }
    }
}

enum class Source { HEALTH_CONNECT, WATCH_APP, MANUAL }

data class Activity(
    val id: String,
    val type: ActivityType,
    val start: Instant,
    val durationMin: Int,
    /** 1–10, podawane po treningu; null, gdy jeszcze brak. */
    val rpe: Int?,
    val avgHr: Int?,
    val kcal: Int?,
    val source: Source,
    /** Opis, zwłaszcza dla „innej aktywności” logowanej ręcznie (np. „remont: noszenie płyt”). */
    val notes: String? = null,
    val loadedAreas: Set<LoadedArea> = emptySet(),
) {
    init {
        require(durationMin >= 0) { "durationMin < 0" }
        require(rpe == null || rpe in 1..10) { "rpe poza 1–10" }
    }

    /** sRPE = RPE × minuty. */
    val load: Int? get() = rpe?.let { it * durationMin }

    val isMatch: Boolean get() = type == ActivityType.VOLLEYBALL || type == ActivityType.SQUASH
}
