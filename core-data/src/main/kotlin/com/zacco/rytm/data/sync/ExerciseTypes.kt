package com.zacco.rytm.data.sync

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.planner.PlannerConfig
import java.time.LocalDate

object ExerciseTypes {
    /**
     * Faza 0: Samsung zapisuje trening personalny jako OTHER_WORKOUT (piątki po 16:00).
     * W dni treningu personalnego traktujemy go więc jako PERSONAL_TRAINING, a nie „inną aktywność”.
     */
    fun toActivityType(exerciseType: Int, date: LocalDate, config: PlannerConfig = PlannerConfig()): ActivityType {
        val base = toActivityType(exerciseType)
        val genericWorkout = exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT ||
            exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_EXERCISE_CLASS ||
            exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_BOOT_CAMP
        return if (genericWorkout && config.hasPersonalTraining(date)) ActivityType.PERSONAL_TRAINING else base
    }

    fun toActivityType(exerciseType: Int): ActivityType = when (exerciseType) {
        ExerciseSessionRecord.EXERCISE_TYPE_VOLLEYBALL -> ActivityType.VOLLEYBALL
        ExerciseSessionRecord.EXERCISE_TYPE_SQUASH -> ActivityType.SQUASH
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
        ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> ActivityType.STRENGTH
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA,
        ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING,
        ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> ActivityType.MOBILITY
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> ActivityType.WALK
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL,
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> ActivityType.CARDIO
        // Zegarek zapisuje ogólny trening jako OTHER_WORKOUT; poza dniem personalnego to najczęściej siła w domu.
        ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
        ExerciseSessionRecord.EXERCISE_TYPE_EXERCISE_CLASS,
        ExerciseSessionRecord.EXERCISE_TYPE_BOOT_CAMP -> ActivityType.STRENGTH
        else -> ActivityType.OTHER
    }
}
