package com.zacco.rytm.domain.body

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Linia z OCR z położeniem na obrazie (piksele, oś Y w dół). */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val height get() = bottom - top
    val centerY get() = (top + bottom) / 2
}

/**
 * Odczyt ekranu Xiaomi Home po położeniu: wartość kafelka stoi tuż nad etykietą i zaczyna się w tej samej
 * kolumnie. Kolejność linii z OCR nie ma wtedy znaczenia (dwie kolumny kafelków, długie zrzuty cięte na kawałki).
 * Pola, których nie da się dopasować, uzupełnia odczyt z samego tekstu ([ScaleScreenshotParser.parse]).
 */
object ScaleLayout {

    /** Zapis linii do schowka i z powrotem: „lewa;góra;prawa;dół;tekst” (do zgłaszania błędów odczytu). */
    fun dump(lines: List<OcrLine>): String = lines.joinToString("\n") { "${it.left};${it.top};${it.right};${it.bottom};${it.text}" }

    fun load(dump: String): List<OcrLine> = dump.lines().mapNotNull { l ->
        val p = l.trim().split(';', limit = 5)
        if (p.size < 5) return@mapNotNull null
        val n = p.take(4).map { it.trim().toIntOrNull() ?: return@mapNotNull null }
        OcrLine(p[4], n[0], n[1], n[2], n[3])
    }

    /** Linie w kolejności czytania: kolumnami w obrębie wiersza kafelków (lewa, potem prawa), z góry na dół. */
    fun readingOrder(lines: List<OcrLine>): List<OcrLine> = lines.sortedWith(compareBy<OcrLine> { it.top }.thenBy { it.left })

    fun parse(raw: List<OcrLine>): ScaleReading? {
        if (raw.isEmpty()) return null
        val lines = mergeFragments(dedupe(raw))
        val width = max(1, lines.maxOf { it.right } - lines.minOf { it.left })

        // Najpierw tekst w kolejności „kafelek po kafelku”: ten sam parser co dla zwykłego tekstu.
        val byText = ScaleScreenshotParser.parse(tileOrder(lines, width).joinToString("\n") { it.text })

        // Potem dopasowanie po położeniu, które wygrywa, gdy coś znajdzie.
        val values = mutableMapOf<String, Double>()
        val norm = lines.map { normalize(it.text) }
        for ((name, field) in ScaleScreenshotParser.fieldSpecs) {
            val label = findLabel(lines, norm, field.key) ?: continue
            valueFor(lines, norm, label, field, width)?.let { values[name] = it }
        }
        val weight = bigWeight(lines) ?: byText?.weightKg
        if (byText == null && values.isEmpty() && weight == null) return null
        val base = byText ?: ScaleReading(null, null, null, com.zacco.rytm.domain.model.BodyComposition())
        val c = base.composition
        return base.copy(
            weightKg = weight,
            bodyFatPct = values["fatPct"] ?: base.bodyFatPct,
            composition = c.copy(
                muscleKg = values["muscleKg"] ?: c.muscleKg,
                musclePct = values["musclePct"] ?: c.musclePct,
                skeletalMuscleKg = values["skeletalKg"] ?: c.skeletalMuscleKg,
                waterKg = values["waterKg"] ?: c.waterKg,
                waterPct = values["waterPct"] ?: c.waterPct,
                fatKg = values["fatKg"] ?: c.fatKg,
                boneKg = values["boneKg"] ?: c.boneKg,
                proteinKg = values["proteinKg"] ?: c.proteinKg,
                proteinPct = values["proteinPct"] ?: c.proteinPct,
                visceralFat = values["visceral"] ?: c.visceralFat,
                bmrKcal = values["bmr"]?.toInt() ?: c.bmrKcal,
                leanMassKg = values["leanKg"] ?: c.leanMassKg,
                waistHipRatio = values["whr"] ?: c.waistHipRatio,
                bmi = values["bmi"] ?: c.bmi,
                bodyScore = values["score"]?.toInt() ?: c.bodyScore,
                heartRate = values["hr"]?.toInt()?.takeIf { it in 30..220 } ?: c.heartRate,
            ),
        )
    }

    /** Zachodzące na siebie kawałki długiego zrzutu dają te same linie dwa razy. */
    private fun dedupe(lines: List<OcrLine>): List<OcrLine> {
        val out = mutableListOf<OcrLine>()
        for (l in lines.sortedBy { it.top }) {
            if (out.none { it.text == l.text && abs(it.top - l.top) <= max(8, l.height) && abs(it.left - l.left) <= max(8, l.height) }) out += l
        }
        return out
    }

    /**
     * Jednostki w indeksie górnym („kg”, „%”) i część dziesiętna dużej wagi („,7”) bywają osobnymi liniami.
     * Doklejamy je do linii z lewej, jeśli leżą w tym samym wierszu tuż obok.
     */
    private fun mergeFragments(lines: List<OcrLine>): List<OcrLine> {
        val frag = Regex("""^\s*(kg|%|kcal|[,.]\d|ud\./min)\s*$""", RegexOption.IGNORE_CASE)
        val result = lines.toMutableList()
        val fragments = result.filter { frag.matches(it.text) }.sortedBy { it.left }
        for (f in fragments) {
            val host = result.filter { it !== f && !frag.matches(it.text) && it.right <= f.left + f.height && f.left - it.right < 2 * max(it.height, f.height) }
                .filter { f.centerY in it.top..it.bottom || it.centerY in f.top..f.bottom }
                .maxByOrNull { it.right } ?: continue
            val merged = OcrLine(host.text + " " + f.text.trim(), host.left, min(host.top, f.top), max(host.right, f.right), max(host.bottom, f.bottom))
            result[result.indexOf(host)] = merged
            result.remove(f)
        }
        return result
    }

    /** Kolejność „kafelkami”: linie z jednej kolumny wiersza razem, żeby wartość stała tuż nad etykietą. */
    private fun tileOrder(lines: List<OcrLine>, width: Int): List<OcrLine> {
        val minLeft = lines.minOf { it.left }
        val half = minLeft + width / 2
        return lines.sortedWith(compareBy<OcrLine> { it.top / (width / 2) }.thenBy { if (it.left < half) 0 else 1 }.thenBy { it.top })
    }

    private fun findLabel(lines: List<OcrLine>, norm: List<String>, key: String): Int? {
        norm.forEachIndexed { i, n ->
            if (key == "bmi") {
                if (n == "bmi" || n.startsWith("bmi ")) return i
            } else if (n.contains(key)) {
                return i
            }
        }
        // Etykieta złamana na dwie linie w tym samym kafelku.
        lines.forEachIndexed { i, a ->
            val j = lines.indices.filter { it != i }.minByOrNull { k ->
                val b = lines[k]
                if (b.top >= a.bottom - a.height / 2 && b.top - a.bottom < a.height && abs(b.left - a.left) < 2 * a.height) b.top - a.bottom else Int.MAX_VALUE
            } ?: return@forEachIndexed
            val b = lines[j]
            if (b.top >= a.bottom - a.height / 2 && b.top - a.bottom < a.height && abs(b.left - a.left) < 2 * a.height &&
                "${norm[i]} ${norm[j]}".contains(key)
            ) return i
        }
        return null
    }

    private fun isAnyLabel(n: String) = ScaleScreenshotParser.fieldSpecs.values.any { f -> if (f.key == "bmi") n == "bmi" else n.contains(f.key) }

    private fun valueFor(lines: List<OcrLine>, norm: List<String>, idx: Int, field: ScaleScreenshotParser.Field, width: Int): Double? {
        val label = lines[idx]
        // Ta sama linia albo ta sama wysokość tuż z lewej („80 Punkty”).
        ScaleScreenshotParser.read(label.text, field)?.let { return it }
        val sameRow = lines.indices.filter { i ->
            i != idx && lines[i].right <= label.left + label.height && label.left - lines[i].right < 3 * label.height &&
                abs(lines[i].centerY - label.centerY) < label.height
        }
        sameRow.firstNotNullOfOrNull { ScaleScreenshotParser.read(lines[it].text, field) }?.let { return it }
        // Nad etykietą, w tej samej kolumnie, najbliżej.
        val maxGap = 4 * label.height
        return lines.indices
            .filter { i ->
                val v = lines[i]
                i != idx && !isAnyLabel(norm[i]) && v.bottom <= label.top + label.height / 2 && label.top - v.bottom <= maxGap &&
                    abs(v.left - label.left) <= max(2 * label.height, width / 12)
            }
            .sortedBy { label.top - lines[it].bottom }
            .firstNotNullOfOrNull { ScaleScreenshotParser.read(lines[it].text, field) }
    }

    /** Duża waga na górze: najwyższa (największa czcionka) liczba 30–250 w górnej części ekranu. */
    private fun bigWeight(lines: List<OcrLine>): Double? {
        val re = Regex("""^(\d{2,3})(?:\s*kg)?\s*[,.]\s*(\d)(?:\s*kg)?$""", RegexOption.IGNORE_CASE)
        val reSplit = Regex("""^(\d{2,3})\s*(?:kg)?\s*[,.]?\s*(\d)?\s*(?:kg)?$""", RegexOption.IGNORE_CASE)
        val top = lines.minOf { it.top }
        val tallest = lines.maxOf { it.height }
        return lines.filter { it.height >= tallest * 0.7 && it.top - top < 12 * tallest }
            .sortedByDescending { it.height }
            .firstNotNullOfOrNull { l ->
                val m = re.find(l.text.trim()) ?: reSplit.find(l.text.trim())?.takeIf { it.groupValues[2].isNotEmpty() } ?: return@firstNotNullOfOrNull null
                "${m.groupValues[1]}.${m.groupValues[2]}".toDouble().takeIf { it in 30.0..250.0 }
            }
    }

    private fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase().replace('ł', 'l'), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
}
