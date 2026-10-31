package com.zacco.rytm.data.watch

import android.content.Context
import android.util.Log
import com.zacco.rytm.data.RytmData
import com.zacco.rytm.domain.coach.CoachData
import com.zacco.rytm.domain.coach.CoachEngine
import com.zacco.rytm.domain.coach.TodaySnapshot
import com.zacco.rytm.domain.habits.HabitKind
import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.model.BodyArea
import com.zacco.rytm.domain.model.DailyCheckIn
import com.zacco.rytm.domain.model.LoadedArea
import com.zacco.rytm.domain.time.RytmTime
import com.zacco.rytm.sync.DataLayer
import com.zacco.rytm.sync.WatchActivity
import com.zacco.rytm.sync.WatchCodec
import com.zacco.rytm.sync.WatchEvent
import com.zacco.rytm.sync.WatchHabit
import com.zacco.rytm.sync.WatchSnapshot
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/**
 * Telefon ↔ zegarek. Telefon liczy wszystko i wysyła gotowy [WatchSnapshot];
 * zegarek odsyła tylko wpisy ([WatchEvent]), które tu zapisujemy do bazy.
 */
object WatchBridge {

    private const val TAG = "RytmWatch"
    private const val PREFS = "watch-bridge"
    private const val KEY_LAST = "last-hash"

    /** Wysyła stan dnia, jeśli zmienił się od ostatniej wysyłki. Bez sparowanego zegarka po prostu nic nie robi. */
    suspend fun publish(context: Context, data: CoachData? = null) {
        val d = data ?: RytmData.get(context).coach.observe(LocalDate.now(RytmTime.zone)).first()
        val snapshot = snapshotOf(d, CoachEngine().today(d), System.currentTimeMillis())
        val hash = WatchCodec.encode(snapshot.copy(generatedAt = 0)).contentHashCode()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_LAST, 0) == hash) return
        runCatching { DataLayer.publishSnapshot(context, snapshot) }
            .onSuccess { prefs.edit().putInt(KEY_LAST, hash).apply() }
            .onFailure { Log.i(TAG, "Nie wysłano na zegarek: ${it.message}") }
    }

    /** Zapisuje wpis z zegarka. */
    suspend fun apply(context: Context, event: WatchEvent) {
        val repo = RytmData.get(context).coach
        val date = LocalDate.parse(event.date)
        when (event) {
            is WatchEvent.HabitToggle -> {
                val habit = CoachData(today = date).habits.firstOrNull { it.id == event.habitId } ?: return
                if (habit.autoSource != null) return
                val value = when {
                    !event.done -> null
                    habit.kind == HabitKind.YES_NO -> 1
                    else -> habit.target ?: 1
                }
                repo.setHabit(habit.id, date, value)
            }
            is WatchEvent.CheckIn -> {
                val existing = repo.observe(date).first().checkIns.firstOrNull { it.date == date }
                repo.saveCheckIn(
                    DailyCheckIn(
                        date = date,
                        energy = event.energy.coerceIn(1, 5),
                        mood = event.mood.coerceIn(1, 5),
                        soreAreas = event.sore.mapNotNull { name -> BodyArea.entries.firstOrNull { it.name == name } }.toSet(),
                        hrvRmssd = event.hrvRmssd ?: existing?.hrvRmssd,
                    ),
                )
            }
            is WatchEvent.Rpe -> repo.setRpe(event.activityId, event.rpe.coerceIn(1, 10))
            is WatchEvent.OtherActivity -> {
                val minutes = event.durationMin.coerceIn(1, 600)
                val end = Instant.ofEpochMilli(event.endedAt)
                repo.logOtherActivity(
                    description = event.description,
                    start = end.minus(Duration.ofMinutes(minutes.toLong())),
                    durationMin = minutes,
                    rpe = event.rpe?.coerceIn(1, 10),
                    loadedAreas = event.areas.mapNotNull { name -> LoadedArea.entries.firstOrNull { it.name == name } }.toSet(),
                )
            }
        }
    }

    fun snapshotOf(data: CoachData, today: TodaySnapshot, now: Long): WatchSnapshot {
        val yesterday = data.today.minusDays(1)
        val toRate = data.activities
            .filter { it.rpe == null && it.type != ActivityType.WALK }
            .filter { it.start.atZone(RytmTime.zone).toLocalDate().let { d -> d == data.today || d == yesterday } }
            .sortedByDescending { it.start }
            .take(3)
            .map { WatchActivity(it.id, it.notes ?: label(it.type), it.durationMin) }
        return WatchSnapshot(
            date = data.today.toString(),
            readinessScore = today.readiness.score,
            readinessLevel = today.readiness.level.name,
            calibrating = today.readiness.calibrating,
            planTitle = today.plan.title,
            planIntensity = today.plan.intensity.name,
            planWhy = today.plan.rationale.firstOrNull(),
            planWarning = today.plan.warnings.firstOrNull(),
            bedtime = "%02d:%02d".format(Locale.ROOT, today.bedtime.hour, today.bedtime.minute),
            habits = today.habits.map { s ->
                val detail = when {
                    s.habit.autoSource != null -> "${s.value ?: 0} / ${s.habit.target ?: 0}"
                    s.streak > 0 -> "seria ${s.streak}"
                    else -> ""
                }
                WatchHabit(s.habit.id, s.habit.name, s.done, s.habit.autoSource != null, detail)
            },
            toRate = toRate,
            checkInDone = today.checkIn != null,
            generatedAt = now,
        )
    }

    private fun label(t: ActivityType): String = when (t) {
        ActivityType.VOLLEYBALL -> "Siatkówka"
        ActivityType.SQUASH -> "Squash"
        ActivityType.STRENGTH -> "Siła"
        ActivityType.PERSONAL_TRAINING -> "Trening personalny"
        ActivityType.MOBILITY -> "Mobilność"
        ActivityType.CARDIO -> "Cardio"
        ActivityType.WALK -> "Spacer"
        ActivityType.OTHER -> "Inna aktywność"
    }
}
