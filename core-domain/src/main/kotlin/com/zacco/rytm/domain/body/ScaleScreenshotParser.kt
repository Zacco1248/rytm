package com.zacco.rytm.domain.body

import com.zacco.rytm.domain.model.BodyComposition
import java.text.Normalizer
import java.time.LocalDateTime

/** Wynik odczytu zrzutu ekranu z wagi; użytkownik zatwierdza go przed zapisem. */
data class ScaleReading(
    val measuredAt: LocalDateTime?,
    val weightKg: Double?,
    val bodyFatPct: Double?,
    val composition: BodyComposition,
) {
    /** Ile pól udało się odczytać (do komunikatu „odczytano 15 pomiarów”). */
    val fieldCount: Int
        get() = with(composition) {
            listOf(
                weightKg, bodyFatPct, muscleKg, musclePct, skeletalMuscleKg, waterKg, waterPct, fatKg, boneKg,
                proteinKg, proteinPct, visceralFat, bmrKcal, leanMassKg, waistHipRatio, bmi, bodyScore, heartRate,
            ).count { it != null }
        }
}

/**
 * Odczyt ekranu pomiaru z Xiaomi Home (waga Xiaomi S400) z tekstu rozpoznanego przez OCR.
 * Rozwiązanie tymczasowe, dopóki pomiary nie przychodzą same (np. przez ScaleLauncher do Health Connect).
 *
 * OCR zwraca linie w różnej kolejności (zwłaszcza przy dwóch kolumnach kafelków), więc dla każdej etykiety
 * szukamy najbliższej wcześniejszej linii z liczbą w oczekiwanej jednostce (kg, %, kcal, bez jednostki).
 */
object ScaleScreenshotParser {

    private enum class Unit { KG, PCT, KCAL, PLAIN }

    /** [range]: wartości poza zakresem odrzucamy (OCR potrafi przypisać liczbę z sąsiedniego kafelka). */
    private data class Field(val key: String, val unit: Unit, val range: ClosedFloatingPointRange<Double> = 0.0..10_000.0)

    private val fields = mapOf(
        "fatPct" to Field("procentowa zawartosc tkanki", Unit.PCT, 2.0..70.0),
        "bmi" to Field("bmi", Unit.PLAIN, 10.0..70.0),
        "score" to Field("punkt", Unit.PLAIN, 0.0..100.0),
        "waterKg" to Field("masa wody", Unit.KG, 10.0..150.0),
        "fatKg" to Field("masa tluszczu", Unit.KG, 1.0..150.0),
        "boneKg" to Field("zawartosc mineralow kosci", Unit.KG, 0.5..10.0),
        "proteinKg" to Field("masa bialka", Unit.KG, 2.0..40.0),
        "muscleKg" to Field("masa miesniowa", Unit.KG, 15.0..150.0),
        "musclePct" to Field("procent miesni", Unit.PCT, 20.0..95.0),
        "waterPct" to Field("woda w organizmie", Unit.PCT, 30.0..80.0),
        "proteinPct" to Field("bialko procentowe", Unit.PCT, 5.0..30.0),
        "skeletalKg" to Field("miesni szkieletowych", Unit.KG, 10.0..90.0),
        "visceral" to Field("tluszczu trzewnego", Unit.PLAIN, 1.0..60.0),
        "bmr" to Field("metabolizmu", Unit.KCAL, 800.0..4000.0),
        "whr" to Field("obwodu pasa", Unit.PLAIN, 0.5..1.6),
        "leanKg" to Field("bez tkanki", Unit.KG, 20.0..200.0),
        "hr" to Field("tetno", Unit.PLAIN, 30.0..220.0),
    )

    private val number = """(\d{1,4}(?:[.,]\d{1,2})?)"""
    private val kg = Regex("""$number\s*kg""", RegexOption.IGNORE_CASE)
    private val pct = Regex("""$number\s*%""")
    private val kcal = Regex("""$number\s*kcal""", RegexOption.IGNORE_CASE)
    private val plain = Regex("""^\s*$number(?![.,\d])(?!\s*(?:kg|%|kcal))""", RegexOption.IGNORE_CASE)
    private val dateTime = Regex("""(\d{2})[/.](\d{2})[/.](\d{4})\s+(\d{1,2}):(\d{2})""")

    fun parse(text: String): ScaleReading? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null
        val norm = lines.map(::normalize)
        val values = mutableMapOf<String, Double>()

        for ((name, field) in fields) {
            val idx = indexOfLabel(norm, field.key) ?: continue
            valueNear(lines, norm, idx, field)?.let { values[name] = it }
        }

        // Masa beztłuszczowa + masa tłuszczu = waga; służy do sprawdzenia odczytu dużej liczby na górze.
        val lean = values["leanKg"]
        val fat = values["fatKg"]
        val sum = if (lean != null && fat != null) round1(lean + fat) else null
        val taken = KG_FIELDS.mapNotNull { values[it] }.toSet()
        val weight = weight(lines, taken, sum) ?: sum

        val composition = BodyComposition(
            muscleKg = values["muscleKg"],
            musclePct = values["musclePct"],
            skeletalMuscleKg = values["skeletalKg"],
            waterKg = values["waterKg"],
            waterPct = values["waterPct"],
            fatKg = values["fatKg"],
            boneKg = values["boneKg"],
            proteinKg = values["proteinKg"],
            proteinPct = values["proteinPct"],
            visceralFat = values["visceral"],
            bmrKcal = values["bmr"]?.toInt(),
            leanMassKg = values["leanKg"],
            waistHipRatio = values["whr"],
            bmi = values["bmi"],
            bodyScore = values["score"]?.toInt(),
            heartRate = values["hr"]?.toInt()?.takeIf { it in 30..220 },
        )
        if (weight == null && values.isEmpty()) return null
        return ScaleReading(measuredAt(text), weight, values["fatPct"], composition)
    }

    /** Etykieta może być złamana na dwie linie, więc sprawdzamy też linię z następną. */
    private fun indexOfLabel(norm: List<String>, key: String): Int? {
        norm.forEachIndexed { i, line ->
            if (key == "bmi") {
                if (line == "bmi" || line.startsWith("bmi ")) return i
            } else if (line.contains(key) || (i + 1 < norm.size && "$line ${norm[i + 1]}".contains(key) && !norm[i + 1].contains(key))) {
                return i
            }
        }
        return null
    }

    private fun isLabel(norm: List<String>, i: Int): Boolean =
        i in norm.indices && fields.values.any { f -> if (f.key == "bmi") norm[i] == "bmi" else norm[i].contains(f.key) }

    private fun valueNear(lines: List<String>, norm: List<String>, idx: Int, field: Field): Double? {
        fun read(i: Int): Double? {
            if (i !in lines.indices) return null
            val m = when (field.unit) {
                Unit.KG -> kg.find(lines[i])
                Unit.PCT -> pct.find(lines[i])
                Unit.KCAL -> kcal.find(lines[i])
                Unit.PLAIN -> plain.find(lines[i])
            } ?: return null
            return m.groupValues[1].replace(',', '.').toDoubleOrNull()?.takeIf { it in field.range }
        }

        // Dwa kafelki w wierszu dają układ „wartość A, wartość B, etykieta A, etykieta B”:
        // dla k-tej etykiety z ciągu etykiet bierzemy k-tą wartość z ciągu tuż nad nim.
        if (isLabel(norm, idx)) {
            var start = idx
            while (isLabel(norm, start - 1)) start--
            var end = idx
            while (isLabel(norm, end + 1)) end++
            val runLength = end - start + 1
            if (runLength > 1) read(start - runLength + (idx - start))?.let { return it }
        }

        // Ta sama linia („80 Punkty”), potem najbliższa wyżej z liczbą w tej jednostce.
        val candidates = listOf(idx) + (idx - 1 downTo (idx - 4).coerceAtLeast(0))
        for (i in candidates) {
            if (i != idx && isLabel(norm, i)) continue
            read(i)?.let { return it }
        }
        return null
    }

    /**
     * Duża liczba na górze ekranu: „78,7” albo rozbita przez OCR na „78” i „,7”. Kafelki składu ciała też mają
     * liczby w kg (np. masa mięśniowa 60,9 kg), więc pomijamy wartości przypisane już do pól, a gdy znamy
     * masę beztłuszczową i tłuszcz, bierzemy kandydata zgodnego z ich sumą (±1,5 kg).
     */
    private fun weight(lines: List<String>, taken: Set<Double>, sum: Double?): Double? {
        val candidates = mutableListOf<Double>()
        val split = Regex("""^(\d{2,3})\s*(kg)?$""", RegexOption.IGNORE_CASE)
        val frac = Regex("""^(kg\s*)?[,.](\d)\s*(kg)?$""", RegexOption.IGNORE_CASE)
        lines.forEachIndexed { i, l ->
            val whole = split.find(l)?.groupValues?.get(1) ?: return@forEachIndexed
            lines.drop(i + 1).take(3).firstNotNullOfOrNull { frac.find(it)?.groupValues?.get(2) }
                ?.let { candidates += "$whole.$it".toDouble() }
        }
        lines.forEach { l ->
            Regex("""^(\d{2,3})[,.](\d)\s*(kg)?$""", RegexOption.IGNORE_CASE).find(l)?.let { m ->
                candidates += "${m.groupValues[1]}.${m.groupValues[2]}".toDouble()
            }
        }
        val plausible = candidates.filter { it in 30.0..250.0 && it !in taken }
        if (sum != null) return plausible.firstOrNull { kotlin.math.abs(it - sum) <= 1.5 }
        return plausible.firstOrNull()
    }

    private fun measuredAt(text: String): LocalDateTime? = dateTime.find(text)?.let { m ->
        val (d, mo, y, h, mi) = m.destructured
        runCatching { LocalDateTime.of(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt()) }.getOrNull()
    }

    private fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase().replace('ł', 'l'), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun round1(v: Double) = Math.round(v * 10) / 10.0

    private val KG_FIELDS = listOf("waterKg", "fatKg", "boneKg", "proteinKg", "muscleKg", "skeletalKg", "leanKg")
}
