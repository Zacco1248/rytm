package com.zacco.rytm.phone.app

import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.model.BodyArea
import com.zacco.rytm.domain.model.Intensity
import com.zacco.rytm.domain.model.LoadedArea
import com.zacco.rytm.domain.readiness.ReadinessLevel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Polskie formaty liczb i dat do ekranów. */
object Format {
    private val pl = Locale.forLanguageTag("pl-PL")

    private val months = listOf(
        "stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca",
        "lipca", "sierpnia", "września", "października", "listopada", "grudnia",
    )

    fun dayLong(d: LocalDate): String =
        d.dayOfWeek.getDisplayName(TextStyle.FULL, pl).replaceFirstChar { it.titlecase(pl) } + ", ${d.dayOfMonth} ${months[d.monthValue - 1]}"

    fun dayMonth(d: LocalDate): String = "${d.dayOfMonth} ${months[d.monthValue - 1]}"

    fun range(from: LocalDate, to: LocalDate): String = "${dayMonth(from)} – ${dayMonth(to)}"

    fun weekdayShort(d: DayOfWeek): String = when (d) {
        DayOfWeek.MONDAY -> "pon"
        DayOfWeek.TUESDAY -> "wt"
        DayOfWeek.WEDNESDAY -> "śr"
        DayOfWeek.THURSDAY -> "czw"
        DayOfWeek.FRIDAY -> "pt"
        DayOfWeek.SATURDAY -> "sob"
        DayOfWeek.SUNDAY -> "nd"
    }

    fun minutes(min: Int): String {
        val h = min / 60
        val m = min % 60
        return when {
            h == 0 -> "$m min"
            m == 0 -> "$h h"
            else -> "$h h $m min"
        }
    }

    /** 1 980 (spacja niełamliwa jako separator tysięcy). */
    fun int(v: Int): String = String.format(pl, "%,d", v).replace(' ', ' ').replace(',', ' ')

    fun dec(v: Double, digits: Int = 1): String = String.format(pl, "%.${digits}f", v)

    fun signedKg(v: Double): String = (if (v < 0) "−" else "+") + dec(abs(v), 2)

    fun level(l: ReadinessLevel): String = when (l) {
        ReadinessLevel.HIGH -> "dobra"
        ReadinessLevel.MODERATE -> "umiarkowana"
        ReadinessLevel.LOW -> "niska"
    }

    fun intensity(i: Intensity): String = when (i) {
        Intensity.LOW -> "Lekka"
        Intensity.MODERATE -> "Umiarkowana"
        Intensity.HIGH -> "Mocna"
    }

    fun activity(t: ActivityType): String = when (t) {
        ActivityType.VOLLEYBALL -> "Siatkówka"
        ActivityType.SQUASH -> "Squash"
        ActivityType.STRENGTH -> "Siła"
        ActivityType.PERSONAL_TRAINING -> "Trening personalny"
        ActivityType.MOBILITY -> "Mobilność"
        ActivityType.CARDIO -> "Cardio"
        ActivityType.WALK -> "Spacer"
        ActivityType.OTHER -> "Inna aktywność"
    }

    fun loaded(a: LoadedArea): String = when (a) {
        LoadedArea.BACK -> "Plecy"
        LoadedArea.SHOULDERS -> "Barki"
        LoadedArea.LEGS -> "Nogi"
        LoadedArea.ARMS -> "Ręce"
        LoadedArea.FULL_BODY -> "Całe ciało"
    }

    fun body(a: BodyArea): String = when (a) {
        BodyArea.SHOULDERS -> "Barki"
        BodyArea.ELBOW -> "Łokieć"
        BodyArea.THIGHS -> "Uda"
        BodyArea.KNEES -> "Kolana"
        BodyArea.BACK -> "Plecy"
        BodyArea.CALVES -> "Łydki"
        BodyArea.ANKLES -> "Kostki"
    }

    fun roundInt(v: Double): Int = v.roundToInt()
}
