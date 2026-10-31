package com.zacco.rytm.data.sync

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.zacco.rytm.domain.model.ActivityType

object ExerciseTypes {
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
        else -> ActivityType.OTHER
    }
}
