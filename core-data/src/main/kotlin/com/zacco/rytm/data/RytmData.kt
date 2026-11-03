package com.zacco.rytm.data

import android.content.Context
import com.zacco.rytm.data.ai.AiCoach
import com.zacco.rytm.data.db.RytmDatabase
import com.zacco.rytm.data.profile.ProfileStore
import com.zacco.rytm.domain.planner.PlannerConfig
import com.zacco.rytm.domain.profile.PersonalProfile
import com.zacco.rytm.data.repo.CoachRepository
import com.zacco.rytm.data.repo.RoomUsageStore

/**
 * Ręczne DI: jedna baza i repozytoria na proces. Przy tej skali prostsze niż Hilt
 * (mniej generowanego kodu i wtyczek w buildzie); łatwo przejść na Hilt później.
 */
class RytmData private constructor(context: Context) {
    private val appContext = context.applicationContext
    val db: RytmDatabase = RytmDatabase.create(context)
    val profiles = ProfileStore(context, db)
    val dayOverrides = com.zacco.rytm.data.repo.DayOverrideStore(appContext)
    val coach = CoachRepository(db, profiles, dayOverrides)
    val usageStore = RoomUsageStore(db.llmUsage())
    val ai = AiCoach(appContext, coach, usageStore, dayOverrides)
    val phoneWorkout = com.zacco.rytm.data.workout.PhoneWorkoutStore(appContext)

    /** Tydzień z profilu (do mapowania treningów z Health Connect). */
    fun plannerConfig(): PlannerConfig = (profiles.current() ?: PersonalProfile()).plannerConfig()

    companion object {
        @Volatile private var instance: RytmData? = null

        fun get(context: Context): RytmData =
            instance ?: synchronized(this) { instance ?: RytmData(context.applicationContext).also { instance = it } }
    }
}
