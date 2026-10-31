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

    private data class Field(val key: String, val unit: Unit)

    private val fields = mapOf(
        "fatPct" to Field("procentowa zawartosc tkanki", Unit.PCT),
        "bmi" to Field("bmi", Unit.PLAIN),
        "score" to Field("punkt", Unit.PLAIN),
        "waterKg" to Field("masa wody", Unit.KG),
        "fatKg" to Field("masa tluszczu", Unit.KG),
        "boneKg" to Field("zawartosc mineralow kosci", Unit.KG),
        "proteinKg" to Field("masa bialka", Unit.KG),
        "muscleKg" to Field("masa miesniowa", Unit.KG),
        "musclePct" to Field("procent miesni", Unit.PCT),
        "waterPct" to Field("woda w organizmie", Unit.PCT),
        "proteinPct" to Field("bialko procentowe", Unit.PCT),
        "skeletalKg" to Field("miesni szkieletowych", Unit.KG),
        "visceral" to Field("tluszczu trzewnego", Unit.PLAIN),
        "bmr" to Field("metabolizmu", Unit.KCAL),
        "whr" to Field("obwodu pasa", Unit.PLAIN),
        "leanKg" to Field("bez tkanki", Unit.KG),
        "hr" to Field("tetno", Unit.PLAIN),
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

        val weight = weight(lines) ?: run {
            // Zapasowo: masa beztłuszczowa + masa tłuszczu.
            val lean = values["leanKg"]
            val fat = values["fatKg"]
            if (lean != null && fat != null) round1(lean + fat) else null
        }

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
            return m.groupValues[1].replace(',', '.').toDoubleOrNull()
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

    /** Duża liczba na górze ekranu: „78,7” albo rozbita przez OCR na „78” i „,7”. */
    private fun weight(lines: List<String>): Double? {
        val top = lines.take(12)
        top.forEach { l ->
            Regex("""^(\d{2,3})[,.](\d)\s*(kg)?$""", RegexOption.IGNORE_CASE).find(l)?.let { m ->
                return "${m.groupValues[1]}.${m.groupValues[2]}".toDouble().takeIf { it in 30.0..250.0 }
            }
        }
        top.forEachIndexed { i, l ->
            val whole = Regex("""^(\d{2,3})\s*(kg)?$""", RegexOption.IGNORE_CASE).find(l)?.groupValues?.get(1) ?: return@forEachIndexed
            val frac = top.drop(i + 1).take(3).firstNotNullOfOrNull { Regex("""^(kg\s*)?[,.](\d)\s*(kg)?$""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(2) }
            if (frac != null) return "$whole.$frac".toDouble().takeIf { it in 30.0..250.0 }
        }
        return null
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
}
