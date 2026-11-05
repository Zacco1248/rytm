package com.zacco.rytm.domain.goals

import com.zacco.rytm.domain.model.BodyMetrics
import com.zacco.rytm.domain.nutrition.NutritionAnalyzer
import com.zacco.rytm.domain.workout.Progression
import com.zacco.rytm.domain.workout.SetLog
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil

/**
 * WEIGHT: waga (średnia 7 dni), REPS: powtórzenia w jednej serii ćwiczenia, LIFT: ciężar w ćwiczeniu,
 * FAT_PCT: tłuszcz z ważeń (np. „płaski brzuch”), CARDIO_MIN: najdłuższe cardio bez przerwy (np. bieg),
 * CUSTOM: własny cel tekstem, postęp zaznaczany ręcznie.
 */
enum class GoalKind { WEIGHT, REPS, LIFT, FAT_PCT, CARDIO_MIN, CUSTOM }

/** Cel osoby, np. „10 podciągnięć do marca” albo „78 kg”. */
@Serializable
data class UserGoal(
    val id: String,
    val kind: GoalKind,
    /** Ćwiczenie dla REPS i LIFT. */
    val exercise: String? = null,
    val target: Double,
    /** Termin (ISO), opcjonalnie. */
    val deadline: String? = null,
    val createdOn: String,
    /** Wartość na starcie (do kamieni milowych); null: pierwsza znana. */
    val start: Double? = null,
    /** Ile kamieni milowych już świętowaliśmy powiadomieniem (0–4). */
    val celebrated: Int = 0,
    /** Własna nazwa (cel własny albo z listy, np. „Płaski brzuch”). */
    val label: String? = null,
    /** CUSTOM: postęp zaznaczony ręcznie, 0–1. */
    val manualShare: Double = 0.0,
) {
    val title: String get() = label?.takeIf { it.isNotBlank() }?.let { l ->
        when (kind) {
            GoalKind.FAT_PCT -> "$l (${Progression.kg(target)}% tłuszczu)"
            GoalKind.CARDIO_MIN -> "$l (${target.toInt()} min)"
            else -> l
        }
    } ?: when (kind) {
        GoalKind.FAT_PCT -> "Tłuszcz ${Progression.kg(target)}%"
        GoalKind.CARDIO_MIN -> "${exercise ?: "Cardio"} ${target.toInt()} min bez przerwy"
        GoalKind.CUSTOM -> "Cel własny"
        GoalKind.WEIGHT -> "Waga ${Progression.kg(target)} kg"
        GoalKind.REPS -> "${target.toInt()} × ${exercise.orEmpty()}"
        GoalKind.LIFT -> "${exercise.orEmpty()}: ${Progression.kg(target)} kg"
    }
}

data class GoalProgress(
    val goal: UserGoal,
    val start: Double?,
    val current: Double?,
    /** 0–1 */
    val share: Double,
    /** Osiągnięte kamienie milowe: 25, 50, 75, 100% drogi. */
    val milestonesReached: Int,
    val milestones: List<Double>,
    val done: Boolean,
    /** Kiedy w obecnym tempie; null, gdy brak prognozy. */
    val forecast: LocalDate?,
    /** Krótki opis tempa i prognozy po polsku. */
    val note: String,
)

/** Postęp celów z danych: waga z ważeń, powtórzenia i ciężary z dziennika serii; prognoza z trendu 6 tygodni. */
object Goals {
    private const val TREND_DAYS = 42L
    private const val MILESTONES = 4

    fun progress(
        goal: UserGoal,
        today: LocalDate,
        weighIns: List<BodyMetrics>,
        sets: List<SetLog>,
        activities: List<com.zacco.rytm.domain.model.Activity> = emptyList(),
    ): GoalProgress {
        if (goal.kind == GoalKind.CUSTOM) {
            val share = goal.manualShare.coerceIn(0.0, 1.0)
            val done = share >= 1.0 - 1e-6
            val reached = (share * MILESTONES + 1e-6).toInt().coerceIn(0, MILESTONES)
            return GoalProgress(goal, null, null, share, reached, emptyList(), done, null, if (done) "Cel osiągnięty!" else "Postęp zaznaczasz sam.")
        }
        val created = runCatching { LocalDate.parse(goal.createdOn) }.getOrDefault(today)
        val series: List<Pair<LocalDate, Double>> = when (goal.kind) {
            GoalKind.WEIGHT -> weighIns.filter { it.weightKg != null }.map { it.date }.distinct().sorted()
                .mapNotNull { d -> NutritionAnalyzer.weightAvg7(weighIns, d)?.let { d to it } }
            GoalKind.REPS -> bestPerDay(sets, goal.exercise) { it.reps.toDouble() }
            GoalKind.LIFT -> bestPerDay(sets, goal.exercise) { it.weightKg ?: 0.0 }.filter { it.second > 0 }
            GoalKind.FAT_PCT -> weighIns.filter { it.bodyFatPct != null }.groupBy { it.date }.toSortedMap()
                .map { (d, list) -> d to list.mapNotNull { it.bodyFatPct }.average() }
            GoalKind.CARDIO_MIN -> activities.filter { it.type == com.zacco.rytm.domain.model.ActivityType.CARDIO }
                .groupBy { it.start.atZone(com.zacco.rytm.domain.time.RytmTime.zone).toLocalDate() }.toSortedMap()
                .map { (d, list) -> d to list.maxOf { it.durationMin }.toDouble() }
            GoalKind.CUSTOM -> emptyList()
        }
        val current = series.lastOrNull()?.second
        val start = goal.start ?: series.lastOrNull { it.first <= created }?.second ?: series.firstOrNull()?.second
        val up = start == null || goal.target >= start
        val done = current != null && if (up) current >= goal.target - 1e-6 else current <= goal.target + 1e-6
        val total = start?.let { goal.target - it } ?: 0.0
        val share = when {
            done -> 1.0
            start == null || current == null || abs(total) < 1e-9 -> 0.0
            else -> ((current - start) / total).coerceIn(0.0, 1.0)
        }
        val milestones = start?.let { s -> (1..MILESTONES).map { s + total * it / MILESTONES } }.orEmpty()
        val reached = if (done) MILESTONES else (share * MILESTONES + 1e-6).toInt().coerceIn(0, MILESTONES)

        val recent = series.filter { it.first >= today.minusDays(TREND_DAYS) }
        val slope = slopePerDay(recent)
        val forecast = when {
            done || current == null || slope == null -> null
            (up && slope <= 0) || (!up && slope >= 0) -> null
            else -> today.plusDays(ceil((goal.target - current) / slope).toLong().coerceIn(1, 3650))
        }
        val deadline = goal.deadline?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val note = when {
            done -> "Cel osiągnięty!"
            current == null -> "Brak danych: " + when (goal.kind) {
                GoalKind.WEIGHT -> "dodaj ważenie."
                GoalKind.FAT_PCT -> "dodaj ważenie z % tłuszczu."
                GoalKind.CARDIO_MIN -> "zapisz cardio (bieg, rower) z zegarka albo ręcznie."
                else -> "zapisz trening z tym ćwiczeniem."
            }
            forecast == null -> "W obecnym tempie brak prognozy" + if (recent.size < 2) " (za mało pomiarów)." else ": wynik stoi albo idzie w drugą stronę."
            deadline == null -> "W tym tempie ok. ${pl(forecast)}."
            !forecast.isAfter(deadline) -> "W tym tempie ok. ${pl(forecast)}, przed terminem (${pl(deadline)})."
            else -> "W tym tempie ok. ${pl(forecast)}, po terminie (${pl(deadline)}): ${daysLate(forecast, deadline)} dni za późno."
        }
        return GoalProgress(goal, start, current, share, reached, milestones, done, forecast, note)
    }

    private fun bestPerDay(sets: List<SetLog>, exercise: String?, value: (SetLog) -> Double): List<Pair<LocalDate, Double>> {
        val key = exercise?.substringBefore('(')?.trim()?.lowercase() ?: return emptyList()
        return sets.filter { it.exercise.substringBefore('(').trim().lowercase() == key }
            .groupBy { it.date }.toSortedMap()
            .map { (d, list) -> d to list.maxOf(value) }
    }

    /** Nachylenie prostej (najmniejsze kwadraty) w jednostkach na dzień; null przy mniej niż 2 punktach. */
    fun slopePerDay(points: List<Pair<LocalDate, Double>>): Double? {
        if (points.size < 2) return null
        val t0 = points.first().first
        val xs = points.map { ChronoUnit.DAYS.between(t0, it.first).toDouble() }
        val ys = points.map { it.second }
        val mx = xs.average()
        val my = ys.average()
        val den = xs.sumOf { (it - mx) * (it - mx) }
        if (den < 1e-9) return null
        return xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / den
    }

    private fun daysLate(f: LocalDate, d: LocalDate) = ChronoUnit.DAYS.between(d, f)

    private val MONTHS = listOf("stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca", "lipca", "sierpnia", "września", "października", "listopada", "grudnia")

    fun pl(d: LocalDate): String = "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}" + if (d.year != LocalDate.now().year) " ${d.year}" else ""
}
