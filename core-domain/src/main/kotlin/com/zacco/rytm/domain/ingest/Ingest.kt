package com.zacco.rytm.domain.ingest

import com.zacco.rytm.domain.model.Activity
import com.zacco.rytm.domain.time.RytmTime
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Sesja snu z Health Connect sprowadzona do tego, czego potrzebujemy. */
data class SleepSession(
    val start: Instant,
    val end: Instant,
    /** Minuty snu bez etapów „przebudzony”; null, gdy źródło nie podaje etapów. */
    val asleepMin: Int?,
    val origin: String,
)

data class SleepNight(val date: LocalDate, val start: Instant, val end: Instant, val sleepMin: Int)

/**
 * Reguły porządkowania danych z Health Connect, zanim trafią do bazy.
 * Ten sam trening potrafi przyjść z Samsung Health i z innej aplikacji, a sen bywa pocięty na kawałki.
 */
object Ingest {

    /** Kolejność zaufania do źródeł; pierwsze wygrywa przy duplikatach. */
    val preferredOrigins = listOf(
        "com.zacco.rytm",
        "com.sec.android.app.shealth",
        "com.google.android.apps.fitness",
    )

    /**
     * Noc przypisujemy do dnia, w którym się skończyła (strefa Warszawa).
     * Kawałki snu z jednego źródła, które dzieli mniej niż [mergeGap], sklejamy; wygrywa najdłuższy blok.
     * Drzemki w dzień (koniec po 16:00) nie są nocą.
     */
    fun nightFor(date: LocalDate, sessions: List<SleepSession>, mergeGap: Duration = Duration.ofMinutes(90)): SleepNight? {
        val candidates = sessions.filter {
            val end = it.end.atZone(RytmTime.zone)
            end.toLocalDate() == date && end.hour < NAP_AFTER_HOUR
        }
        if (candidates.isEmpty()) return null
        val origin = bestOrigin(candidates.map { it.origin })
        val own = candidates.filter { it.origin == origin }.sortedBy { it.start }

        val blocks = mutableListOf<MutableList<SleepSession>>()
        for (s in own) {
            val last = blocks.lastOrNull()
            if (last != null && Duration.between(last.maxOf { it.end }, s.start) <= mergeGap) last += s else blocks += mutableListOf(s)
        }
        val best = blocks.maxBy { block -> block.sumOf { minutesOf(it) } }
        return SleepNight(
            date = date,
            start = best.minOf { it.start },
            end = best.maxOf { it.end },
            sleepMin = best.sumOf { minutesOf(it) },
        )
    }

    /**
     * Usuwa duplikaty treningów z różnych źródeł: dwie sesje nachodzące na siebie w ponad połowie
     * krótszej z nich to ten sam trening. Zostaje ta z bardziej zaufanego źródła, a ręczne dane
     * (RPE, notatki) zawsze przechodzą na zwycięzcę.
     */
    fun dedupActivities(activities: List<Pair<Activity, String>>): List<Activity> {
        val sorted = activities.sortedWith(compareBy({ originRank(it.second) }, { it.first.start }))
        val kept = mutableListOf<Activity>()
        for ((a, _) in sorted) {
            val dupIdx = kept.indexOfFirst { overlapShare(it, a) > DUPLICATE_OVERLAP }
            if (dupIdx < 0) {
                kept += a
            } else {
                val k = kept[dupIdx]
                kept[dupIdx] = k.copy(
                    rpe = k.rpe ?: a.rpe,
                    avgHr = k.avgHr ?: a.avgHr,
                    kcal = k.kcal ?: a.kcal,
                    notes = k.notes ?: a.notes,
                    loadedAreas = k.loadedAreas.ifEmpty { a.loadedAreas },
                )
            }
        }
        return kept.sortedBy { it.start }
    }

    fun bestOrigin(origins: Collection<String>): String = origins.minBy { originRank(it) }

    private fun originRank(origin: String): Int =
        preferredOrigins.indexOf(origin).let { if (it < 0) preferredOrigins.size else it }

    private fun minutesOf(s: SleepSession): Int = s.asleepMin ?: Duration.between(s.start, s.end).toMinutes().toInt()

    private fun overlapShare(a: Activity, b: Activity): Double {
        val aEnd = a.start.plus(Duration.ofMinutes(a.durationMin.toLong()))
        val bEnd = b.start.plus(Duration.ofMinutes(b.durationMin.toLong()))
        val overlap = Duration.between(maxOf(a.start, b.start), minOf(aEnd, bEnd)).toMinutes()
        val shorter = minOf(a.durationMin, b.durationMin)
        return if (overlap <= 0 || shorter <= 0) 0.0 else overlap.toDouble() / shorter
    }

    private const val NAP_AFTER_HOUR = 16
    private const val DUPLICATE_OVERLAP = 0.5
}
