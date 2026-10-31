package com.zacco.rytm.domain.time

import java.time.ZoneId

object RytmTime {
    /** Strefa, w której liczymy dni i tygodnie (konwencja projektu, sekcja 12). */
    val zone: ZoneId = ZoneId.of("Europe/Warsaw")
}
