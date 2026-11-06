package com.zacco.rytm.domain.time

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

object RytmTime {
    /** Strefa, w której liczymy dni i tygodnie (konwencja projektu, sekcja 12). */
    val zone: ZoneId = ZoneId.of("Europe/Warsaw")

    /** Dzień w aplikacji kończy się o 4:00, nie o północy: po 24 ekran Dziś pokazuje jeszcze mijający dzień. */
    const val DAY_END_HOUR = 4

    /** „Dziś” z punktu widzenia osoby: do 3:59 to jeszcze poprzedni dzień kalendarzowy. */
    fun appDay(now: ZonedDateTime = ZonedDateTime.now(zone)): LocalDate =
        now.withZoneSameInstant(zone).minusHours(DAY_END_HOUR.toLong()).toLocalDate()
}
