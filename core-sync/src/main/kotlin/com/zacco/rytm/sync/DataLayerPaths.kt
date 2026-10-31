package com.zacco.rytm.sync

/** Ścieżki Wearable Data Layer API. Telefon i zegarek muszą mieć ten sam applicationId. */
object DataLayerPaths {
    /** Telefon → zegarek: stan dnia (DataItem nadpisywany przy każdej zmianie). */
    const val SNAPSHOT = "/rytm/snapshot"

    /** Zegarek → telefon: wpisy z zegarka, każdy pod własną ścieżką, kasowane po przetworzeniu. */
    const val EVENT_PREFIX = "/rytm/event/"

    const val KEY_JSON = "json"
}
