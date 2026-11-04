package com.zacco.rytm.domain.body

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ScaleLayoutTest {

    /** Ekran z 04.11.2026 (szer. ok. 1080 px): dwie kolumny kafelków, linie celowo pomieszane. */
    private val screen = listOf(
        OcrLine("Zacco", 200, 120, 330, 170),
        OcrLine("04/11/2026 08:42", 200, 175, 480, 215),
        OcrLine("79", 370, 330, 610, 560),
        OcrLine("kg", 630, 350, 680, 400),
        OcrLine(",9", 620, 450, 690, 560),
        OcrLine("Spadek o 0,4kg w porównaniu z wagą sprzed 8 dni", 230, 650, 950, 690),
        // prawa kolumna wcześniej niż lewa
        OcrLine("76,3% ↓1,1", 620, 2530, 900, 2600),
        OcrLine("Procent mięśni", 620, 2615, 820, 2650),
        OcrLine("61 kg ↑0,1", 90, 2530, 360, 2600),
        OcrLine("Masa mięśniowa", 90, 2615, 330, 2650),
        OcrLine("47,7kg", 610, 1790, 760, 1850),
        OcrLine("Masa wody", 610, 1860, 760, 1890),
        OcrLine("15,3kg", 610, 1940, 760, 2000),
        OcrLine("Masa tłuszczu", 610, 2010, 800, 2040),
        OcrLine("64,6kg ↑0,1", 90, 4290, 380, 4360),
        OcrLine("Waga ciała bez tkanki", 90, 4375, 400, 4410),
        OcrLine("tłuszczowej", 90, 4415, 260, 4450),
        OcrLine("22,8 ↓0,2", 90, 1260, 330, 1320),
        OcrLine("BMI", 90, 1335, 150, 1365),
        OcrLine("19,1% ↑1,0", 580, 1260, 830, 1320),
        OcrLine("Procentowa zawartość", 580, 1335, 860, 1365),
        OcrLine("tkanki tłuszczowej", 580, 1370, 820, 1400),
        OcrLine("Sugestie dotyczące wagi ciała", 90, 6000, 520, 6040),
        OcrLine("Standardowa waga:", 230, 6120, 500, 6160),
        OcrLine("74,9kg", 230, 6170, 380, 6220),
    )

    @Test
    fun `reads big weight and tiles by position, not by line order`() {
        val r = assertNotNull(ScaleLayout.parse(screen.shuffled(kotlin.random.Random(3))))
        assertEquals(79.9, r.weightKg)
        assertEquals(19.1, r.bodyFatPct)
        assertEquals(22.8, r.composition.bmi)
        assertEquals(61.0, r.composition.muscleKg)
        assertEquals(76.3, r.composition.musclePct)
        assertEquals(47.7, r.composition.waterKg)
        assertEquals(15.3, r.composition.fatKg)
        assertEquals(64.6, r.composition.leanMassKg)
    }

    @Test
    fun `dump and load round trip`() {
        assertEquals(screen, ScaleLayout.load(ScaleLayout.dump(screen)))
    }

    @Test
    fun `suggested weight is never taken as the measurement`() {
        // OCR zgubił „,9” przy dużej wadze: lepiej wziąć sumę mas niż „Standardowa waga 74,9”.
        val text = """
            04/11/2026 08:42
            79
            kg
            15,3kg
            Masa tłuszczu
            64,6kg ↑0,1
            Waga ciała bez tkanki tłuszczowej
            Sugestie dotyczące wagi ciała
            Standardowa waga:
            74,9kg
        """.trimIndent()
        assertEquals(79.9, assertNotNull(ScaleScreenshotParser.parse(text)).weightKg)
    }
}
