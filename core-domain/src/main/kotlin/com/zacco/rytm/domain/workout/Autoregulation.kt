package com.zacco.rytm.domain.workout

import kotlinx.serialization.Serializable

/**
 * Jak ciężko poszła seria, w prostych słowach. Zapisujemy jako zapas powtórzeń (RIR),
 * bo z niego liczy też [Progression].
 */
enum class Effort(val rir: Int, val label: String, val short: String) {
    EASY(4, "Lekko", "Lekko"),
    GOOD(2, "W sam raz", "OK"),
    HARD(1, "Ciężko", "Ciężko"),
    MAX(0, "Do upadku", "Max");

    companion object {
        fun of(rir: Int?): Effort? = when {
            rir == null -> null
            rir >= 4 -> EASY
            rir >= 2 -> GOOD
            rir == 1 -> HARD
            else -> MAX
        }

        const val HELP = "Po każdej serii zaznacz, jak poszło. Lekko: w zapasie zostały 4 powtórzenia albo więcej. " +
            "W sam raz: 2–3 w zapasie. Ciężko: jeszcze 1. Do upadku: więcej się nie dało. " +
            "Rytm od razu dobiera ciężar i powtórzenia na następną serię, a z całego treningu ciężar na kolejny raz."
    }
}

/** Podpowiedź po serii: co zrobić w następnej. */
@Serializable
data class SetAdvice(
    /** Ćwiczenie, którego dotyczy podpowiedź. */
    val exercise: String,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val message: String,
    /** Po ostatniej serii poszło lekko: można dołożyć serię. */
    val offerExtraSet: Boolean = false,
    /** Dwie serie z rzędu do upadku poniżej zakresu: lepiej skończyć ćwiczenie. */
    val offerEndExercise: Boolean = false,
)

/**
 * Autoregulacja w trakcie treningu. Po serii z oceną trudności dobiera ciężar i powtórzenia na następną serię
 * tego samego ćwiczenia i podpowiada, czy dołożyć albo odpuścić serię.
 */
object Autoregulation {

    fun afterSet(
        ex: RunExercise,
        done: RunSet,
        previous: List<RunSet>,
        stepKg: Double,
        maxKg: Double?,
        lastSetOfExercise: Boolean,
    ): SetAdvice? {
        val effort = Effort.of(done.rir) ?: return null
        val w = done.weightKg
        val r = done.reps
        val seconds = ex.name.contains("sekundy")
        val unit = if (seconds) "s" else "powt."
        val belowRange = r < ex.repsMin

        if (lastSetOfExercise) {
            return when {
                effort == Effort.EASY && r >= ex.repsMax -> SetAdvice(
                    ex.name, w, r,
                    "Ostatnia seria poszła lekko. Możesz dołożyć jeszcze jedną; następnym razem Rytm doda ciężar.",
                    offerExtraSet = true,
                )
                effort == Effort.MAX && belowRange -> SetAdvice(ex.name, w, null, "Do upadku poniżej zakresu: następnym razem Rytm zmniejszy ciężar.")
                else -> null
            }
        }

        val prevSame = previous.lastOrNull { it.exercise == ex.name && it.setIndex == done.setIndex - 1 }
        val twiceFailed = effort == Effort.MAX && belowRange && prevSame != null &&
            Effort.of(prevSame.rir) == Effort.MAX && prevSame.reps < ex.repsMin

        if (w == null) {
            // Masa ciała albo czas: zmieniamy tylko cel powtórzeń (sekund).
            val step = if (seconds) 10 else 2
            return when (effort) {
                Effort.EASY -> SetAdvice(ex.name, null, r + step, "Lekko: w następnej serii celuj w ${r + step} $unit" + if (seconds) "." else ", wolniej w dół.")
                Effort.GOOD -> SetAdvice(ex.name, null, maxOf(r, ex.repsMin), "W sam raz: powtórz ${maxOf(r, ex.repsMin)} $unit.")
                Effort.HARD -> SetAdvice(ex.name, null, r, "Ciężko: zostań przy $r $unit, dbaj o technikę.")
                Effort.MAX -> SetAdvice(
                    ex.name, null, maxOf(1, r - if (seconds) 5 else 1),
                    if (twiceFailed) "Drugi raz do upadku: możesz skończyć to ćwiczenie." else "Do upadku: w następnej zostaw 1 w zapasie, dłuższa przerwa pomoże.",
                    offerEndExercise = twiceFailed,
                )
            }
        }

        return when (effort) {
            Effort.EASY -> {
                val up = w + stepKg
                if (maxKg != null && up > maxKg + EPS) {
                    val reps = r + 2
                    SetAdvice(ex.name, w, reps, "Lekko, a to maks. sprzętu: zrób $reps $unit i opuszczaj wolniej (3–4 s).")
                } else if (r >= ex.repsMin) {
                    SetAdvice(ex.name, up, maxOf(ex.repsMin, minOf(r, ex.repsMax) - 2), "Lekko: następna seria ${Progression.kg(up)} kg.")
                } else {
                    SetAdvice(ex.name, w, ex.repsMin, "Lekko, ale mało powtórzeń: zostań przy ${Progression.kg(w)} kg i zrób ${ex.repsMin}.")
                }
            }
            Effort.GOOD -> SetAdvice(ex.name, w, maxOf(r, ex.repsMin), "W sam raz: zostań przy ${Progression.kg(w)} kg.")
            Effort.HARD -> if (belowRange) {
                val down = maxOf(stepKg, w - stepKg)
                SetAdvice(ex.name, down, ex.repsMin, "Ciężko i poniżej zakresu: następna seria ${Progression.kg(down)} kg.")
            } else {
                SetAdvice(ex.name, w, r, "Ciężko, ale w zakresie: zostań przy ${Progression.kg(w)} kg.")
            }
            Effort.MAX -> {
                val down = if (belowRange) maxOf(stepKg, w - stepKg) else w
                val msg = when {
                    twiceFailed -> "Drugi raz do upadku: zejdź na ${Progression.kg(down)} kg albo skończ to ćwiczenie."
                    belowRange -> "Do upadku poniżej zakresu: następna seria ${Progression.kg(down)} kg."
                    else -> "Do upadku: zostań przy ${Progression.kg(w)} kg i zostaw 1 powtórzenie w zapasie."
                }
                SetAdvice(ex.name, down, if (belowRange) ex.repsMin else maxOf(1, r - 1), msg, offerEndExercise = twiceFailed)
            }
        }
    }

    private const val EPS = 1e-6
}
