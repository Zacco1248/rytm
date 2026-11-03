package com.zacco.rytm.domain.readiness

import com.zacco.rytm.domain.load.LoadSummary
import com.zacco.rytm.domain.model.DailyCheckIn
import com.zacco.rytm.domain.model.DailyMetrics
import java.time.LocalDate

/** Jeden dzień okna kalibracji: co z niego weszło do baseline'u. */
data class CalibrationDay(
    val date: LocalDate,
    val sleepMin: Int?,
    val nightRestingHr: Int?,
    val dayRestingHr: Int?,
    val hrv: Boolean,
) {
    /** Dzień liczy się do baseline'u tętna, gdy jest tętno z nocy albo zapasowe z dnia. */
    val counted: Boolean get() = nightRestingHr != null || dayRestingHr != null

    val gap: CalibrationGap?
        get() = when {
            nightRestingHr != null -> null
            dayRestingHr != null -> CalibrationGap.DAY_ONLY
            sleepMin != null -> CalibrationGap.NO_HR
            else -> CalibrationGap.NO_DATA
        }
}

enum class CalibrationGap {
    /** Brak nocy (zegarek na ładowarce), użyto tętna z dnia. */
    DAY_ONLY,
    /** Jest sen, ale bez tętna (inna opaska albo brak uprawnień). */
    NO_HR,
    /** Brak danych z całego dnia. */
    NO_DATA,
}

/** Dlaczego gotowość jest jeszcze w kalibracji i czego brakuje. */
data class CalibrationStatus(
    /** Ostatnie 14 dni przed dziś, od najnowszego. */
    val days: List<CalibrationDay>,
    /** Dni z tętnem w oknie 14 dni (potrzeba [needDays]). */
    val restingHrDays: Int,
    /** Dni z HRV w oknie 14 dni; null, gdy HRV dziś nie ma, więc nie blokuje. */
    val hrvDays: Int?,
    val loadHistoryDays: Int,
    val loadReliable: Boolean,
    val needDays: Int,
) {
    val counted: Int get() = days.count { it.counted }
    val restingHrReady: Boolean get() = restingHrDays >= needDays
    val hrvReady: Boolean get() = hrvDays == null || hrvDays >= needDays

    companion object {
        const val WINDOW_DAYS = 14
        const val LOAD_DAYS = 28

        fun of(
            today: LocalDate,
            metrics: List<DailyMetrics>,
            checkIns: List<DailyCheckIn>,
            load: LoadSummary,
            needDays: Int,
        ): CalibrationStatus {
            val byDate = metrics.associateBy { it.date }
            val hrvDates = checkIns.filter { it.hrvRmssd != null }.map { it.date }.toSet()
            val days = (1..WINDOW_DAYS).map { back ->
                val d = today.minusDays(back.toLong())
                val m = byDate[d]
                CalibrationDay(d, m?.sleepMin, m?.nightRestingHr, m?.dayRestingHr, d in hrvDates)
            }
            // Ta sama zasada co w ReadinessCalculator: bez nocy dziś porównujemy z tętnem z dnia.
            val night = byDate[today]
            val fromDay = night?.nightRestingHr == null && night?.dayRestingHr != null
            val hrDays = days.count { if (fromDay) it.dayRestingHr != null else it.nightRestingHr != null }
            val hrvToday = checkIns.any { it.date == today && it.hrvRmssd != null }
            return CalibrationStatus(
                days = days,
                restingHrDays = hrDays,
                hrvDays = if (hrvToday) days.count { it.hrv } else null,
                loadHistoryDays = load.historyDays,
                loadReliable = load.reliable,
                needDays = needDays,
            )
        }
    }
}
