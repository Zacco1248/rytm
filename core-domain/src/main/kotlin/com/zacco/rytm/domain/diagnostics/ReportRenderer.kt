package com.zacco.rytm.domain.diagnostics

import com.zacco.rytm.domain.time.RytmTime
import java.time.DayOfWeek
import java.time.Duration
import java.time.format.DateTimeFormatter

/** Raport fazy 0 w Markdown, po polsku. */
object ReportRenderer {

    private val dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(RytmTime.zone)
    private val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(RytmTime.zone)

    fun render(report: Phase0Report): String = buildString {
        appendLine("# Rytm, faza 0: raport danych z Health Connect")
        appendLine()
        appendLine("Zakres: ${date.format(report.from)} – ${date.format(report.to)} (strefa ${RytmTime.zone})")
        appendLine()

        appendLine("## Co mamy / czego brak")
        appendLine()
        appendLine("| Dane | Status | Szczegóły |")
        appendLine("|---|---|---|")
        report.coverage.forEach { appendLine("| ${it.name} | ${it.status.symbol} | ${it.details.escape()} |") }
        appendLine()

        appendLine("## Tętno")
        appendLine()
        if (report.heartRate.isEmpty()) {
            appendLine("Brak próbek tętna.")
        } else {
            appendLine("| Źródło | Próbki | Dni | Mediana odstępu | Odstępy 8–12 min | Odstępy < 2 min | Próbki / h snu |")
            appendLine("|---|---|---|---|---|---|---|")
            report.heartRate.forEach {
                appendLine(
                    "| ${it.origin} | ${it.samples} | ${it.days} | ${it.medianIntervalMin?.let { m -> "${m.format1()} min" } ?: "–"} " +
                        "| ${it.shareAround10Min.pct()} | ${it.shareUnder2Min.pct()} | ${it.samplesPerSleepHour?.format1() ?: "–"} |",
                )
            }
        }
        appendLine()

        appendLine("## Sen")
        appendLine()
        with(report.sleep) {
            appendLine("- Sesje: $sessions (źródła: ${origins.joinToString().ifEmpty { "brak" }})")
            appendLine("- Z etapami: $withStages, typy etapów: ${stageTypes.joinToString().ifEmpty { "brak" }}")
            appendLine("- Mediana długości: ${medianDurationMin?.let { formatMinutes(it) } ?: "–"}")
        }
        appendLine()

        appendLine("## Treningi")
        appendLine()
        if (report.exercise.isEmpty()) {
            appendLine("Brak sesji treningowych.")
        } else {
            appendLine("| Źródło | Typ HC | Tytuł | Liczba | Dni tygodnia | Mediana czasu |")
            appendLine("|---|---|---|---|---|---|")
            report.exercise.forEach {
                appendLine(
                    "| ${it.origin} | ${it.typeName} | ${it.title?.escape() ?: "–"} | ${it.count} " +
                        "| ${it.weekdays.entries.joinToString { (d, c) -> "${d.pl()}×$c" }} " +
                        "| ${it.medianDurationMin?.let { m -> formatMinutes(m) } ?: "–"} |",
                )
            }
            appendLine()
            appendLine("Sprawdź: czy squash, siatkówka i trening personalny mają rozróżnialne typy lub tytuły.")
        }
        appendLine()
        appendDuplicates(
            "Możliwe duplikaty treningów (różne źródła, ≥50% pokrycia)",
            report.exerciseDuplicates.map { (a, b) ->
                "${dateTime.format(a.start)}: ${a.typeName} (${a.origin}) ↔ ${b.typeName} (${b.origin})"
            },
        )

        appendLine("## Żywienie")
        appendLine()
        if (report.nutrition.isEmpty()) {
            appendLine("Brak rekordów żywienia. Sprawdź synchronizację Fitatu → Health Connect.")
        } else {
            appendLine("| Źródło | Rekordy | Dni | Śr. kcal/dzień | Śr. białko/dzień | Z nazwą posiłku |")
            appendLine("|---|---|---|---|---|---|")
            report.nutrition.forEach {
                appendLine(
                    "| ${it.origin} | ${it.records} | ${it.days} | ${it.avgDailyKcal?.toInt() ?: "–"} " +
                        "| ${it.avgDailyProteinG?.let { p -> "${p.toInt()} g" } ?: "–"} | ${it.namedRecordsShare.pct()} |",
                )
            }
        }
        appendLine()
        appendDuplicates(
            "Możliwe duplikaty żywienia (różne źródła, ±30 min, kcal ±10%)",
            report.nutritionDuplicates.map { (a, b) ->
                "${dateTime.format(a.start)}: ${a.kcal?.toInt()} kcal (${a.origin}) ↔ ${b.kcal?.toInt()} kcal (${b.origin})"
            },
        )

        appendLine("## Pomiary ciała i wydolności")
        appendLine()
        if (report.measurements.isEmpty()) {
            appendLine("Brak pomiarów (waga, tkanka tłuszczowa, VO2max).")
        } else {
            appendLine("| Pomiar | Źródło | Liczba | Ostatni | Kiedy |")
            appendLine("|---|---|---|---|---|")
            report.measurements.forEach {
                appendLine("| ${it.kind.label} | ${it.origin} | ${it.count} | ${it.latestValue.format1()} ${it.kind.unit} | ${dateTime.format(it.latestTime)} |")
            }
        }
        appendLine()

        appendLine("## Wszystkie typy rekordów")
        appendLine()
        appendLine("| Typ | Źródło | Liczba | Od | Do |")
        appendLine("|---|---|---|---|---|")
        report.recordCounts.forEach {
            appendLine("| ${it.type} | ${it.origin} | ${it.count} | ${it.first?.let(dateTime::format) ?: "–"} | ${it.last?.let(dateTime::format) ?: "–"} |")
        }
        appendLine()

        if (report.errors.isNotEmpty()) {
            appendLine("## Błędy odczytu")
            appendLine()
            report.errors.forEach { appendLine("- ${it.escape()}") }
            appendLine()
        }
    }

    private fun StringBuilder.appendDuplicates(title: String, lines: List<String>) {
        appendLine("### $title")
        appendLine()
        if (lines.isEmpty()) {
            appendLine("Brak.")
        } else {
            lines.take(MAX_LISTED).forEach { appendLine("- $it") }
            if (lines.size > MAX_LISTED) appendLine("- … i ${lines.size - MAX_LISTED} więcej")
        }
        appendLine()
    }

    private const val MAX_LISTED = 20

    private fun Double?.pct(): String = this?.let { "${(it * 100).toInt()}%" } ?: "–"

    private fun String.escape(): String = replace("|", "\\|").replace("\n", " ")

    private fun formatMinutes(min: Double): String {
        val d = Duration.ofMinutes(min.toLong())
        return if (d.toHours() > 0) "${d.toHours()} h ${d.toMinutesPart()} min" else "${d.toMinutes()} min"
    }

    private fun DayOfWeek.pl(): String = when (this) {
        DayOfWeek.MONDAY -> "pn"
        DayOfWeek.TUESDAY -> "wt"
        DayOfWeek.WEDNESDAY -> "śr"
        DayOfWeek.THURSDAY -> "cz"
        DayOfWeek.FRIDAY -> "pt"
        DayOfWeek.SATURDAY -> "sb"
        DayOfWeek.SUNDAY -> "nd"
    }
}
