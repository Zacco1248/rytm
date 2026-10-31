package com.zacco.rytm.data.healthconnect

import androidx.health.connect.client.records.ExerciseSessionRecord as E
import androidx.health.connect.client.records.SleepSessionRecord as S

/** Czytelne nazwy stałych Health Connect. Typy spoza listy trafiają do raportu jako TYPE_<liczba>. */
internal fun exerciseTypeName(type: Int): String = when (type) {
    E.EXERCISE_TYPE_SQUASH -> "SQUASH"
    E.EXERCISE_TYPE_VOLLEYBALL -> "VOLLEYBALL"
    E.EXERCISE_TYPE_STRENGTH_TRAINING -> "STRENGTH_TRAINING"
    E.EXERCISE_TYPE_WEIGHTLIFTING -> "WEIGHTLIFTING"
    E.EXERCISE_TYPE_CALISTHENICS -> "CALISTHENICS"
    E.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
    E.EXERCISE_TYPE_BOOT_CAMP -> "BOOT_CAMP"
    E.EXERCISE_TYPE_EXERCISE_CLASS -> "EXERCISE_CLASS"
    E.EXERCISE_TYPE_OTHER_WORKOUT -> "OTHER_WORKOUT"
    E.EXERCISE_TYPE_WALKING -> "WALKING"
    E.EXERCISE_TYPE_RUNNING -> "RUNNING"
    E.EXERCISE_TYPE_BIKING -> "BIKING"
    E.EXERCISE_TYPE_BADMINTON -> "BADMINTON"
    E.EXERCISE_TYPE_TENNIS -> "TENNIS"
    E.EXERCISE_TYPE_YOGA -> "YOGA"
    E.EXERCISE_TYPE_PILATES -> "PILATES"
    E.EXERCISE_TYPE_STRETCHING -> "STRETCHING"
    E.EXERCISE_TYPE_HIKING -> "HIKING"
    E.EXERCISE_TYPE_SWIMMING_POOL -> "SWIMMING_POOL"
    else -> "TYPE_$type"
}

internal fun sleepStageName(stage: Int): String = when (stage) {
    S.STAGE_TYPE_AWAKE -> "AWAKE"
    S.STAGE_TYPE_AWAKE_IN_BED -> "AWAKE_IN_BED"
    S.STAGE_TYPE_OUT_OF_BED -> "OUT_OF_BED"
    S.STAGE_TYPE_SLEEPING -> "SLEEPING"
    S.STAGE_TYPE_LIGHT -> "LIGHT"
    S.STAGE_TYPE_DEEP -> "DEEP"
    S.STAGE_TYPE_REM -> "REM"
    else -> "UNKNOWN"
}
