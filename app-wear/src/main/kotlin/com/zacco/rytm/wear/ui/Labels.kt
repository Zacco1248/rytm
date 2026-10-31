package com.zacco.rytm.wear.ui

import com.zacco.rytm.domain.model.BodyArea
import com.zacco.rytm.domain.model.LoadedArea

object Labels {
    fun intensity(name: String): String = when (name) {
        "LOW" -> "Lekko"
        "HIGH" -> "Mocno"
        else -> "Umiarkowanie"
    }

    fun level(name: String): String = when (name) {
        "HIGH" -> "wysoka"
        "LOW" -> "niska"
        else -> "umiarkowana"
    }

    fun body(a: BodyArea): String = when (a) {
        BodyArea.SHOULDERS -> "Barki"
        BodyArea.ELBOW -> "Łokieć"
        BodyArea.KNEES -> "Kolana"
        BodyArea.BACK -> "Plecy"
        BodyArea.CALVES -> "Łydki"
        BodyArea.ANKLES -> "Kostki"
    }

    fun loaded(a: LoadedArea): String = when (a) {
        LoadedArea.BACK -> "Plecy"
        LoadedArea.SHOULDERS -> "Barki"
        LoadedArea.LEGS -> "Nogi"
        LoadedArea.ARMS -> "Ręce"
        LoadedArea.FULL_BODY -> "Całe ciało"
    }

    fun energy(v: Int): String = when (v) {
        1 -> "bardzo niska"
        2 -> "niska"
        3 -> "średnia"
        4 -> "dobra"
        else -> "świetna"
    }

    fun mood(v: Int): String = when (v) {
        1 -> "zły"
        2 -> "słaby"
        3 -> "w porządku"
        4 -> "dobry"
        else -> "świetny"
    }

    /** Skala Borga CR-10 w skrócie. */
    fun rpe(v: Int): String = when (v) {
        1, 2 -> "bardzo lekko"
        3, 4 -> "lekko"
        5, 6 -> "umiarkowanie"
        7, 8 -> "ciężko"
        9 -> "bardzo ciężko"
        else -> "maksymalnie"
    }

    fun minutes(min: Int): String = if (min < 60) "$min min" else "${min / 60} h" + if (min % 60 != 0) " ${min % 60} min" else ""
}
