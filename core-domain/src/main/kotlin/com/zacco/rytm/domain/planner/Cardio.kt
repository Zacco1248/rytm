package com.zacco.rytm.domain.planner

import com.zacco.rytm.domain.model.Intensity

/** Rodzaj sesji wytrzymałościowej z planu. */
enum class CardioKind { RUN, BIKE, SWIM, WALK }

/** Spokojnie (strefa 2), interwały albo dłuższe wyjście. */
enum class CardioType { EASY, INTERVALS, LONG }

/** Sesja cardio z planu: co zrobić, krok po kroku. */
data class CardioSession(
    val kind: CardioKind,
    val type: CardioType,
    val minutes: Int,
    val steps: List<String>,
    val treadmill: Boolean = false,
) {
    val intensity: Intensity
        get() = when (type) {
            CardioType.EASY -> Intensity.LOW
            CardioType.INTERVALS -> Intensity.HIGH
            CardioType.LONG -> Intensity.MODERATE
        }

    val title: String
        get() {
            val what = when (kind) {
                CardioKind.RUN -> if (treadmill) "Bieg na bieżni" else "Bieg"
                CardioKind.BIKE -> "Rower"
                CardioKind.SWIM -> "Pływanie"
                CardioKind.WALK -> if (treadmill) "Marsz na bieżni" else "Szybki marsz"
            }
            val how = when (type) {
                CardioType.EASY -> "spokojnie"
                CardioType.INTERVALS -> "interwały"
                CardioType.LONG -> "dłużej"
            }
            return "$what $how · $minutes min"
        }
}

/**
 * Gotowe sesje cardio. Spokojne to większość (tempo rozmowy), jedna mocniejsza i jedna dłuższa w tygodniu,
 * jak w typowych planach dla amatorów (ok. 80% spokojnie, 20% mocno).
 */
object CardioLibrary {

    /** Kolejność typów sesji w tygodniu: przy 3 i więcej jedna dłuższa (najlepiej w weekend). */
    fun typesFor(n: Int): List<CardioType> = when (n) {
        0 -> emptyList()
        1 -> listOf(CardioType.EASY)
        2 -> listOf(CardioType.EASY, CardioType.INTERVALS)
        else -> listOf(CardioType.EASY, CardioType.INTERVALS) + List(n - 3) { CardioType.EASY } + CardioType.LONG
    }

    fun session(kind: CardioKind, type: CardioType, sessionMinutes: Int, treadmill: Boolean = false): CardioSession {
        val base = sessionMinutes.coerceIn(20, 75)
        val minutes = when (type) {
            CardioType.EASY -> base
            CardioType.INTERVALS -> base.coerceAtMost(45)
            CardioType.LONG -> (base * 1.4).toInt().coerceIn(40, if (kind == CardioKind.BIKE || kind == CardioKind.WALK) 120 else 90)
        }
        val tm = treadmill && (kind == CardioKind.RUN || kind == CardioKind.WALK)
        val steps = when (kind) {
            CardioKind.RUN -> when (type) {
                CardioType.EASY -> listOf(
                    "5 min marszu na rozgrzewkę.",
                    "${minutes - 10} min spokojnego biegu: tempo, przy którym da się rozmawiać.",
                    "Jeśli nie biegasz jeszcze ciągle 20 min: na zmianę 1 min biegu i 1–2 min marszu.",
                    "5 min marszu i rozciąganie łydek.",
                )
                CardioType.INTERVALS -> listOf(
                    "10 min truchtu na rozgrzewkę.",
                    "6 × 1 min szybko (RPE 8, mówisz pojedyncze słowa) / 2 min truchtu albo marszu.",
                    "Reszta czasu spokojnie, na koniec 5 min marszu.",
                )
                CardioType.LONG -> listOf(
                    "$minutes min bardzo spokojnie, wolniej niż zwykle.",
                    "Co tydzień najwyżej o 10% dłużej niż poprzednio.",
                    "Weź wodę, jeśli to ponad godzina.",
                )
            }
            CardioKind.WALK -> when (type) {
                CardioType.EASY -> listOf("$minutes min szybkiego marszu: lekka zadyszka, ale da się mówić.")
                CardioType.INTERVALS -> listOf(
                    "10 min spokojnie.",
                    "5 × 3 min szybciej albo pod górę / 2 min spokojnie.",
                    "Reszta czasu spokojnie.",
                )
                CardioType.LONG -> listOf("$minutes min długiego spaceru, najlepiej w terenie.")
            }
            CardioKind.BIKE -> when (type) {
                CardioType.EASY -> listOf("$minutes min spokojnej jazdy, równa kadencja (ok. 80–90 obr./min).")
                CardioType.INTERVALS -> listOf(
                    "10 min rozgrzewki.",
                    "5 × 3 min mocniej (RPE 7–8) / 3 min luźno.",
                    "Reszta czasu spokojnie.",
                )
                CardioType.LONG -> listOf("$minutes min spokojnej jazdy, coś do picia i jedzenia przy ponad 90 min.")
            }
            CardioKind.SWIM -> when (type) {
                CardioType.EASY -> listOf("200 m rozpływania.", "10 × 50 m spokojnie, 20–30 s przerwy.", "100 m na luzie.")
                CardioType.INTERVALS -> listOf("200 m rozpływania.", "8 × 50 m mocniej, 30–40 s przerwy.", "4 × 100 m spokojnie.", "100 m na luzie.")
                CardioType.LONG -> listOf("$minutes min pływania ciągłego albo w długich odcinkach z krótkimi przerwami.")
            }
        }
        val treadmillTip = when {
            !tm -> emptyList()
            kind == CardioKind.RUN -> listOf("Na bieżni ustaw nachylenie 1%, żeby było jak na zewnątrz.")
            else -> listOf("Na bieżni: 5,5–6,5 km/h i nachylenie 4–8%; im wyżej, tym mocniej.")
        }
        return CardioSession(kind, type, minutes, steps + treadmillTip, tm)
    }

    /** Lżejsza wersja przy niższej gotowości albo przed meczem: interwały zamieniają się w spokojną sesję. */
    fun easier(s: CardioSession, sessionMinutes: Int): CardioSession =
        if (s.type == CardioType.INTERVALS) session(s.kind, CardioType.EASY, sessionMinutes, s.treadmill) else s
}
