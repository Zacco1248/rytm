package com.zacco.rytm.domain.sleep

import com.zacco.rytm.domain.time.RytmTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Sen wpisany ręcznie: godziny z pola tekstowego i okno nocy kończącej się w dniu pobudki. */
object ManualSleep {
    /** „23:45”, „2345”, „7” → godzina; null, gdy nie da się odczytać. */
    fun parseTime(text: String): LocalTime? {
        val d = text.filter(Char::isDigit)
        val (h, m) = when (d.length) {
            1, 2 -> d.toInt() to 0
            3 -> d.substring(0, 1).toInt() to d.substring(1).toInt()
            4 -> d.substring(0, 2).toInt() to d.substring(2).toInt()
            else -> return null
        }
        if (h !in 0..23 || m !in 0..59) return null
        return LocalTime.of(h, m)
    }

    /** Okno snu kończącego się w dniu [date]: zaśnięcie później niż pobudka na zegarze to poprzedni wieczór. */
    fun window(date: LocalDate, onset: LocalTime?, wake: LocalTime?): Pair<Instant, Instant>? {
        if (onset == null || wake == null) return null
        val end = date.atTime(wake).atZone(RytmTime.zone).toInstant()
        val startDay = if (onset.isAfter(wake)) date.minusDays(1) else date
        val start = startDay.atTime(onset).atZone(RytmTime.zone).toInstant()
        return if (start.isBefore(end)) start to end else null
    }
}
