package com.zacco.rytm.domain.model

import java.time.Instant

enum class ActivityType { VOLLEYBALL, SQUASH, STRENGTH, PERSONAL_TRAINING, MOBILITY, CARDIO, RENOVATION, WALK, OTHER }

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
    val notes: String? = null,
) {
    init {
        require(durationMin >= 0) { "durationMin < 0" }
        require(rpe == null || rpe in 1..10) { "rpe poza 1–10" }
    }

    /** sRPE = RPE × minuty. */
    val load: Int? get() = rpe?.let { it * durationMin }

    val isMatch: Boolean get() = type == ActivityType.VOLLEYBALL || type == ActivityType.SQUASH
}
