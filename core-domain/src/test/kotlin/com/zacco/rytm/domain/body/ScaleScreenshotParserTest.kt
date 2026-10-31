package com.zacco.rytm.domain.body

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ScaleScreenshotParserTest {

    /** Tekst jak z OCR ekranu Xiaomi Home (S400), kafelki czytane kolumnami. */
    private val byColumns = """
        Zacco
        03/06/2026 18:39
        78
        kg
        ,7
        Standardowy
        Spadek o 0.8kg w porównaniu z wagą sprzed 17 dni
        Wynik dotyczący ciała
        80
        Punkty
        Twoje zdrowie nie budzi zastrzeżeń.
        22,5 ↓0,2
        BMI
        Standardowy
        18,1% ↓0,1
        Procentowa zawartość tkanki tłuszczowej
        Standardowy
        Skład ciała
        49,0kg
        Masa wody
        14,2kg
        Masa tłuszczu
        3,6kg
        Zawartość minerałów
        kości
        11,1kg
        Masa białka
        60,9kg
        Masa mięśniowa
        Standardowy
        77,4% ↑0,2
        Procent mięśni
        62,3% ↓0,1
        Woda w organizmie
        14,1% ↑0,3
        Białko procentowe
        4,6%
        Minerały kości procentowe
        36,1kg ↓0,2
        Masa mięśni szkieletowych
        5,0
        Ocena tłuszczu trzewnego
        Wysoka
        1763Kcal
        Podstawowa szybkość metabolizmu
        1,1 ↓0,1
        Szacowane proporcje obwodu pasa do bioder
        25 lat
        Wiek biologiczny
        64,5kg
        Waga ciała bez tkanki tłuszczowej
        102 ud./min
        Tętno
        BMI
        Procen towa zawartość tkanki tłus zczowej
    """.trimIndent()

    @Test
    fun `reads all S400 fields from the screenshot text`() {
        val r = assertNotNull(ScaleScreenshotParser.parse(byColumns))
        val c = r.composition

        assertEquals(LocalDateTime.of(2026, 6, 3, 18, 39), r.measuredAt)
        assertEquals(78.7, r.weightKg)
        assertEquals(18.1, r.bodyFatPct)
        assertEquals(22.5, c.bmi)
        assertEquals(80, c.bodyScore)
        assertEquals(49.0, c.waterKg)
        assertEquals(14.2, c.fatKg)
        assertEquals(3.6, c.boneKg)
        assertEquals(11.1, c.proteinKg)
        assertEquals(60.9, c.muscleKg)
        assertEquals(77.4, c.musclePct)
        assertEquals(62.3, c.waterPct)
        assertEquals(14.1, c.proteinPct)
        assertEquals(36.1, c.skeletalMuscleKg)
        assertEquals(5.0, c.visceralFat)
        assertEquals(1763, c.bmrKcal)
        assertEquals(1.1, c.waistHipRatio)
        assertEquals(64.5, c.leanMassKg)
        assertEquals(102, c.heartRate)
        assertEquals(18, r.fieldCount)
    }

    @Test
    fun `two column tiles read row by row still match by unit`() {
        val rows = """
            78,7
            kg
            60,9kg
            77,4% ↑0,2
            Masa mięśniowa
            Procent mięśni
            62,3% ↓0,1
            14,1% ↑0,3
            Woda w organizmie
            Białko procentowe
        """.trimIndent()
        val r = assertNotNull(ScaleScreenshotParser.parse(rows))

        assertEquals(78.7, r.weightKg)
        assertEquals(60.9, r.composition.muscleKg)
        assertEquals(77.4, r.composition.musclePct)
        assertEquals(62.3, r.composition.waterPct)
        assertEquals(14.1, r.composition.proteinPct)
    }

    @Test
    fun `weight falls back to lean mass plus fat mass`() {
        val r = assertNotNull(ScaleScreenshotParser.parse("14,2kg\nMasa tłuszczu\n64,5kg\nWaga ciała bez tkanki tłuszczowej"))
        assertEquals(78.7, r.weightKg)
    }

    @Test
    fun `unrelated text gives nothing`() {
        assertNull(ScaleScreenshotParser.parse("Lista zakupów\nmleko\nchleb"))
    }
}
