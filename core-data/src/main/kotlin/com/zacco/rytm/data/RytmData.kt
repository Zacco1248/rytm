package com.zacco.rytm.data

import android.content.Context
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
    val db: RytmDatabase = RytmDatabase.create(context)
    val profiles = ProfileStore(context, db)
    val coach = CoachRepository(db, profiles)
    val usageStore = RoomUsageStore(db.llmUsage())

    /** Tydzień z profilu (do mapowania treningów z Health Connect). */
    fun plannerConfig(): PlannerConfig = (profiles.current() ?: PersonalProfile()).plannerConfig()

    companion object {
        @Volatile private var instance: RytmData? = null

        fun get(context: Context): RytmData =
            instance ?: synchronized(this) { instance ?: RytmData(context.applicationContext).also { instance = it } }
    }
}
