package com.zacco.rytm.domain.workout

import com.zacco.rytm.domain.nutrition.StrengthTrend
import java.time.LocalDate

/** Ostatnia sesja ćwiczenia i poprzednia, do karty „Siła”. */
data class ExerciseProgress(
    val exercise: String,
    val lastDate: LocalDate,
    val last: String,
    val previous: String?,
    /** Ostatnio lepiej niż poprzednio (wyższy szacowany wynik). */
    val improved: Boolean?,
)

/** Postępy z dziennika serii: lista ćwiczeń i trend siły do raportu żywienia. */
object StrengthHistory {

    fun latest(sets: List<SetLog>, limit: Int = 8): List<ExerciseProgress> =
        sets.groupBy { it.exercise }.map { (name, own) ->
            val sessions = own.groupBy { it.date }.toSortedMap()
            val dates = sessions.keys.toList()
            val last = sessions.getValue(dates.last())
            val prev = dates.getOrNull(dates.size - 2)?.let { sessions.getValue(it) }
            ExerciseProgress(
                exercise = name,
                lastDate = dates.last(),
                last = Progression.summary(last.sortedBy { it.setIndex }),
                previous = prev?.let { Progression.summary(it.sortedBy { s -> s.setIndex }) },
                improved = prev?.let { score(last) > score(it) * 1.001 },
            )
        }.sortedByDescending { it.lastDate }.take(limit)

    /**
     * Ostatnie 2 tygodnie kontra 2–6 tygodni wcześniej, dla ćwiczeń zrobionych w obu oknach.
     * Mediana zmian powyżej +2% to wzrost, poniżej −3% spadek. Za mało danych: nieznany.
     */
    fun trend(sets: List<SetLog>, today: LocalDate): StrengthTrend {
        val recentFrom = today.minusDays(14)
        val priorFrom = today.minusDays(42)
        val ratios = sets.groupBy { it.exercise }.mapNotNull { (_, own) ->
            val recent = own.filter { it.date > recentFrom && it.date <= today }
            val prior = own.filter { it.date > priorFrom && it.date <= recentFrom }
            if (recent.isEmpty() || prior.isEmpty()) null else best(recent) / best(prior)
        }.sorted()
        if (ratios.size < MIN_EXERCISES) return StrengthTrend.UNKNOWN
        val median = ratios[ratios.size / 2]
        return when {
            median > 1.02 -> StrengthTrend.UP
            median < 0.97 -> StrengthTrend.DOWN
            else -> StrengthTrend.FLAT
        }
    }

    /** Szacunek Epleya dla serii z ciężarem, same powtórzenia przy masie ciała. */
    private fun setScore(s: SetLog): Double = s.weightKg?.let { it * (1 + s.reps / 30.0) } ?: s.reps.toDouble()

    private fun best(sets: List<SetLog>): Double = sets.maxOf { setScore(it) }

    /** Sesja: najlepsza seria plus odrobina za łączną pracę, żeby dodatkowa seria też była postępem. */
    private fun score(sets: List<SetLog>): Double = best(sets) + sets.sumOf { setScore(it) } * 0.01

    private const val MIN_EXERCISES = 2
}
