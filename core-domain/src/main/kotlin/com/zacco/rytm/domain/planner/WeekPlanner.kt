package com.zacco.rytm.domain.planner

import com.zacco.rytm.domain.load.AcwrZone
import com.zacco.rytm.domain.load.LoadSummary
import com.zacco.rytm.domain.model.Activity
import com.zacco.rytm.domain.model.ActivityType
import com.zacco.rytm.domain.model.DayKind
import com.zacco.rytm.domain.model.Intensity
import com.zacco.rytm.domain.model.LoadedArea
import com.zacco.rytm.domain.model.PlannedDay
import com.zacco.rytm.domain.model.StrengthFocus
import com.zacco.rytm.domain.readiness.Readiness
import com.zacco.rytm.domain.readiness.ReadinessLevel
import com.zacco.rytm.domain.time.RytmTime
import java.time.DayOfWeek
import java.time.LocalDate

/** Stan, na którym planujemy. Gotowość znamy tylko na dziś, więc reguły gotowości dotyczą wyłącznie [today]. */
data class PlanningContext(
    val today: LocalDate,
    val readiness: Readiness? = null,
    val load: LoadSummary? = null,
    /** Aktywności z bieżącego i poprzedniego tygodnia (faktycznie wykonane). */
    val activities: List<Activity> = emptyList(),
    /** Dni, w które stały sport lub trening personalny wypadł („mecz odwołany”). */
    val cancelled: Set<LocalDate> = emptySet(),
    /** Dni oznaczone „dziś wolne”: odpoczynek, a brakujące sesje siłowe przechodzą na kolejne dni tygodnia. */
    val daysOff: Set<LocalDate> = emptySet(),
)

/**
 * Regułowy planer tygodnia (sekcje 3.1 i 3.2). Źródło prawdy dla planu; LLM tylko go tłumaczy.
 */
class WeekPlanner(
    private val config: PlannerConfig = PlannerConfig(),
    private val exercises: ExerciseLibrary = ExerciseLibrary(),
) {

    fun planWeek(anyDayOfWeek: LocalDate, context: PlanningContext): List<PlannedDay> {
        val monday = anyDayOfWeek.with(DayOfWeek.MONDAY)
        val dates = (0L until 7L).map { monday.plusDays(it) }

        var days = dates.map { template(it, context) }
        days = ensureStrengthMinimum(days, context)
        days = days.map { adjust(it, days, context) }
        // Ćwiczenia na końcu, gdy znany jest ostateczny rodzaj, nacisk i intensywność dnia.
        return ensureRestDay(days).map { it.copy(workout = exercises.workoutFor(it)) }
    }

    fun planDay(date: LocalDate, context: PlanningContext): PlannedDay = planWeek(date, context).first { it.date == date }

    // --- szablon (3.1) ---

    internal fun template(date: LocalDate, context: PlanningContext): PlannedDay {
        if (date in context.daysOff) {
            return rest(date, "Wolne", "Dzień wolny. Brakującą siłę plan przesuwa na kolejne dni tygodnia.")
        }
        val cancelled = date in context.cancelled
        config.fixedSports[date.dayOfWeek]?.let { sport ->
            return if (cancelled) replacement(date, "${sport.pl()} odwołana") else
                PlannedDay(date, DayKind.FIXED_SPORT, sport.pl(), Intensity.HIGH, rationale = listOf("Stały punkt tygodnia."))
        }
        if (config.hasPersonalTraining(date)) {
            return if (cancelled) replacement(date, "Trening personalny odwołany") else
                PlannedDay(date, DayKind.PERSONAL_TRAINING, "Trening personalny", Intensity.HIGH, rationale = listOf("Grupowy trening personalny."))
        }
        return when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> strength(date, StrengthFocus.UPPER)
            DayOfWeek.FRIDAY -> strength(date, StrengthFocus.LOWER)
            DayOfWeek.SATURDAY ->
                if (config.personalTrainingUntil?.let { !date.isAfter(it) } == true) {
                    strength(date, StrengthFocus.FULL_BODY)
                } else {
                    PlannedDay(date, DayKind.FLEX, "Dowolna aktywność", Intensity.MODERATE, rationale = listOf("Dzień elastyczny."))
                }
            DayOfWeek.SUNDAY -> rest(date, "Odpoczynek: mobilność 20–30 min i spacer", "Dzień regeneracji.")
            else -> PlannedDay(date, DayKind.FLEX, "Dowolna aktywność", Intensity.LOW)
        }
    }

    /** Reguła 3: mecz wypadł, więc zamiennik zależny od tygodniowego obciążenia i liczby sesji siłowych. */
    private fun replacement(date: LocalDate, reason: String): PlannedDay =
        PlannedDay(date, DayKind.FLEX, "Zamiennik", Intensity.MODERATE, rationale = listOf("$reason: dzień do zaplanowania."))

    // --- reguły tygodniowe ---

    /** Reguła 7: min. 2 sesje siłowe (w tym personalne); zamienniki i dni elastyczne idą najpierw na siłę. */
    internal fun ensureStrengthMinimum(days: List<PlannedDay>, context: PlanningContext): List<PlannedDay> {
        val result = days.toMutableList()
        val doneStrength = days.count { day ->
            day.date < context.today && context.activities.any {
                it.type in STRENGTH_TYPES && it.start.atZone(RytmTime.zone).toLocalDate() == day.date
            }
        }
        fun planned() = result.count { it.isStrengthLike() && it.date >= context.today } + doneStrength

        for (i in result.indices) {
            if (planned() >= config.minStrengthSessionsPerWeek) break
            val day = result[i]
            if (day.kind == DayKind.FLEX && day.date >= context.today) {
                result[i] = strength(day.date, StrengthFocus.FULL_BODY).withRationale(
                    "Za mało sesji siłowych w tym tygodniu: siła ma pierwszeństwo przed dodatkowym cardio.",
                )
            }
        }
        // Pozostałe zamienniki: przy niskim obciążeniu interwały, w innym wypadku mobilność.
        return result.map { day ->
            if (day.kind == DayKind.FLEX && day.title == "Zamiennik") {
                if (context.load?.zone == AcwrZone.LOW) {
                    day.copy(title = "Interwały z hantlami 20–25 min", intensity = Intensity.MODERATE)
                        .withRationale("Tygodniowe obciążenie niskie.")
                } else {
                    day.copy(kind = DayKind.MOBILITY, title = "Mobilność 30 min", intensity = Intensity.LOW)
                }
            } else {
                day
            }
        }
    }

    /** Reguła 6: min. 1 dzień pełnego odpoczynku. */
    internal fun ensureRestDay(days: List<PlannedDay>): List<PlannedDay> {
        if (days.any { it.kind == DayKind.REST }) return days
        val candidate = days.lastOrNull { it.kind == DayKind.MOBILITY || it.kind == DayKind.FLEX } ?: return days
        return days.map {
            if (it == candidate) rest(it.date, "Odpoczynek", "W tygodniu musi być co najmniej jeden dzień pełnego odpoczynku.") else it
        }
    }

    // --- reguły dzienne ---

    internal fun adjust(day: PlannedDay, week: List<PlannedDay>, context: PlanningContext): PlannedDay {
        var d = day
        val tomorrow = planFor(day.date.plusDays(1), week, context)
        val yesterdayMatch = hadMatch(day.date.minusDays(1), week, context)

        // Reguła 1: bez ciężkich nóg dzień przed siatkówką lub squashem.
        if (d.kind == DayKind.STRENGTH && d.focus != StrengthFocus.UPPER && tomorrow?.kind == DayKind.FIXED_SPORT) {
            d = d.copy(title = strengthTitle(StrengthFocus.UPPER), focus = StrengthFocus.UPPER)
                .withRationale("Jutro ${tomorrow.title.lowercase()}: bez ciężkich nóg.")
        }

        // Reguła 2: dzień po meczu mobilność albo lekka góra.
        if (yesterdayMatch && d.kind == DayKind.STRENGTH) {
            d = d.copy(title = "Lekka góra + mobilność", focus = StrengthFocus.UPPER, intensity = Intensity.LOW)
                .withRationale("Wczoraj mecz: lżejszy dzień.")
        }

        // Reguła 4: po ciężkiej „innej aktywności” (np. remoncie) odciążamy partie z pola „co obciążyło”.
        heavyOtherActivity(day.date.minusDays(1), context)?.let { heavy ->
            if (d.kind in TRAINING_KINDS) d = unloadAfter(heavy, d, tomorrow)
        }

        if (day.date == context.today) d = applyReadiness(d, context)
        return d
    }

    /** Reguła 5 i ACWR: lżejsza wersja albo odpoczynek; stałe sporty zostają z ostrzeżeniem. */
    private fun applyReadiness(day: PlannedDay, context: PlanningContext): PlannedDay {
        var d = day
        val readiness = context.readiness
        val acwrHigh = context.load?.zone == AcwrZone.HIGH

        if (d.kind == DayKind.FIXED_SPORT || d.kind == DayKind.PERSONAL_TRAINING) {
            if (readiness?.level == ReadinessLevel.LOW) d = d.withWarning("Niska gotowość (${readiness.score}): graj spokojniej, słuchaj ciała.")
            if (acwrHigh) d = d.withWarning("Wysokie obciążenie tygodnia (ACWR > 1,5).")
            return d
        }
        if (d.kind !in TRAINING_KINDS) return d

        when {
            readiness != null && readiness.score < config.restBelowReadiness ->
                d = rest(d.date, "Odpoczynek", "Bardzo niska gotowość (${readiness.score}): dziś regeneracja.")
            readiness?.level == ReadinessLevel.LOW ->
                d = d.copy(kind = DayKind.MOBILITY, title = "Mobilność 20–30 min", intensity = Intensity.LOW, focus = null)
                    .withRationale("Niska gotowość (${readiness.score}): trening zastąpiony mobilnością.")
            acwrHigh ->
                d = d.copy(intensity = Intensity.LOW).withRationale("ACWR powyżej 1,5: wymuszony lżejszy dzień.")
            readiness?.level == ReadinessLevel.MODERATE && d.intensity == Intensity.HIGH ->
                d = d.copy(intensity = Intensity.MODERATE).withRationale("Umiarkowana gotowość: bez maksymalnego wysiłku.")
        }
        return d
    }

    private fun planFor(date: LocalDate, week: List<PlannedDay>, context: PlanningContext): PlannedDay? =
        week.firstOrNull { it.date == date } ?: template(date, context)

    private fun hadMatch(date: LocalDate, week: List<PlannedDay>, context: PlanningContext): Boolean {
        val actual = context.activities.filter { it.start.atZone(RytmTime.zone).toLocalDate() == date }
        if (date < context.today) return actual.any { it.isMatch }
        return planFor(date, week, context)?.kind == DayKind.FIXED_SPORT
    }

    private fun unloadAfter(heavy: Activity, day: PlannedDay, tomorrow: PlannedDay?): PlannedDay {
        val what = heavy.notes?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        val areas = heavy.loadedAreas
        var d = day.copy(intensity = minOf(day.intensity, Intensity.MODERATE))
        if (areas.isEmpty()) {
            return d.withWarning("Wczoraj ciężka aktywność$what: dziś bez dużego obciążenia partii, które pracowały.")
        }
        d = d.withWarning("Wczoraj ciężka aktywność$what: odciąż ${areas.joinToString { it.pl() }}.")
        if (d.kind != DayKind.STRENGTH) {
            return if (LoadedArea.FULL_BODY in areas) d.copy(intensity = Intensity.LOW) else d
        }
        val upperLoaded = LoadedArea.BACK in areas || LoadedArea.SHOULDERS in areas || LoadedArea.ARMS in areas
        val legsLoaded = LoadedArea.LEGS in areas
        return when {
            LoadedArea.FULL_BODY in areas || (upperLoaded && legsLoaded) ->
                d.copy(kind = DayKind.MOBILITY, title = "Mobilność 30 min", intensity = Intensity.LOW, focus = null)
                    .withRationale("Wczoraj obciążone całe ciało: zamiast siły mobilność.")
            legsLoaded && d.focus != StrengthFocus.UPPER ->
                d.copy(title = strengthTitle(StrengthFocus.UPPER), focus = StrengthFocus.UPPER)
                    .withRationale("Nogi obciążone wczoraj: dziś góra.")
            upperLoaded && d.focus != StrengthFocus.LOWER ->
                if (tomorrow?.kind == DayKind.FIXED_SPORT) {
                    // Dół odpada przez jutrzejszy mecz, więc zostaje lekka sesja.
                    d.copy(intensity = Intensity.LOW).withRationale("Góra obciążona, a jutro mecz: lekka sesja.")
                } else {
                    d.copy(title = strengthTitle(StrengthFocus.LOWER), focus = StrengthFocus.LOWER)
                        .withRationale("Góra obciążona wczoraj: dziś dół.")
                }
            else -> d
        }
    }

    private fun LoadedArea.pl(): String = when (this) {
        LoadedArea.BACK -> "plecy"
        LoadedArea.SHOULDERS -> "barki"
        LoadedArea.LEGS -> "nogi"
        LoadedArea.ARMS -> "ręce"
        LoadedArea.FULL_BODY -> "całe ciało"
    }

    private fun heavyOtherActivity(date: LocalDate, context: PlanningContext): Activity? =
        context.activities.firstOrNull {
            it.type == ActivityType.OTHER &&
                it.start.atZone(RytmTime.zone).toLocalDate() == date &&
                (it.durationMin >= config.heavyOtherActivityMinutes || (it.load ?: 0) >= config.heavyOtherActivityLoad)
        }

    private fun strength(date: LocalDate, focus: StrengthFocus) =
        PlannedDay(date, DayKind.STRENGTH, strengthTitle(focus), Intensity.MODERATE, focus = focus)

    private fun rest(date: LocalDate, title: String, why: String) =
        PlannedDay(date, DayKind.REST, title, Intensity.LOW, rationale = listOf(why))

    private fun strengthTitle(focus: StrengthFocus) = when (focus) {
        StrengthFocus.UPPER -> "Siła A: góra + core"
        StrengthFocus.LOWER -> "Siła B: dół + core"
        StrengthFocus.FULL_BODY -> "Siła C: całe ciało"
    }

    private fun PlannedDay.isStrengthLike() = kind == DayKind.STRENGTH || kind == DayKind.PERSONAL_TRAINING

    private fun PlannedDay.withRationale(text: String) = copy(rationale = listOf(text) + rationale)

    private fun PlannedDay.withWarning(text: String) = copy(warnings = warnings + text)

    private fun ActivityType.pl(): String = when (this) {
        ActivityType.VOLLEYBALL -> "Siatkówka"
        ActivityType.SQUASH -> "Squash"
        else -> name.lowercase().replaceFirstChar { it.uppercase() }
    }

    private companion object {
        val TRAINING_KINDS = setOf(DayKind.STRENGTH, DayKind.FLEX, DayKind.MOBILITY)
        val STRENGTH_TYPES = setOf(ActivityType.STRENGTH, ActivityType.PERSONAL_TRAINING)
    }
}
