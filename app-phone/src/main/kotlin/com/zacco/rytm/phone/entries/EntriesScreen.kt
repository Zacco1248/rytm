package com.zacco.rytm.phone.entries

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zacco.rytm.data.repo.Entry
import com.zacco.rytm.data.repo.EntryKind
import com.zacco.rytm.domain.time.RytmTime
import com.zacco.rytm.phone.app.Format
import com.zacco.rytm.phone.ui.components.CardHeader
import com.zacco.rytm.phone.ui.components.Chip
import com.zacco.rytm.phone.ui.components.ChipTone
import com.zacco.rytm.phone.ui.components.Pill
import com.zacco.rytm.phone.ui.components.RytmButton
import com.zacco.rytm.phone.ui.components.RytmCard
import com.zacco.rytm.phone.ui.components.ScreenHeader
import com.zacco.rytm.phone.ui.components.SmallText
import com.zacco.rytm.phone.ui.theme.Rytm
import java.time.format.DateTimeFormatter

/** Wpisy z ostatnich 30 dni: poprawianie, usuwanie i eksport wszystkiego do pliku. */
@Composable
fun EntriesScreen(
    entries: List<Entry>,
    exportStatus: String?,
    onEdit: (Entry) -> Unit,
    onDelete: (Entry) -> Unit,
    onExport: (android.net.Uri) -> Unit,
    onClose: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) onExport(uri)
    }
    ScreenHeader(sub = "Ostatnie 30 dni", title = "Wpisy") { Pill("Zwiń", false, onClick = onClose) }

    RytmCard {
        CardHeader("Eksport", help = "Zapisuje wszystkie dane Rytmu (treningi, serie, sen, kroki, żywienie, ważenia, check-iny, nawyki, profil) do jednego pliku JSON, np. na Dysk. Bez klucza AI.")
        RytmButton("Eksportuj dane do pliku", Modifier.fillMaxWidth()) {
            export.launch("rytm-" + java.time.LocalDate.now(RytmTime.zone) + ".json")
        }
        exportStatus?.let { SmallText(it) }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill("Wszystko", filter == null) { filter = null }
        Pill("Waga", filter == EntryKind.WEIGH_IN.name) { filter = EntryKind.WEIGH_IN.name }
        Pill("Jedzenie", filter == EntryKind.NUTRITION.name) { filter = EntryKind.NUTRITION.name }
        Pill("Ruch", filter == EntryKind.ACTIVITY.name) { filter = EntryKind.ACTIVITY.name }
    }

    val shown = entries.filter { filter == null || it.kind.name == filter }
    if (shown.isEmpty()) SmallText("Brak wpisów.")
    shown.groupBy { it.date }.forEach { (date, list) ->
        RytmCard {
            CardHeader(Format.dayLong(date))
            list.forEach { e ->
                val key = e.kind.name + e.id
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                e.title + (e.time?.let { " · " + it.atZone(RytmTime.zone).format(HHMM) } ?: ""),
                                style = Rytm.type.bodyStrong, color = Rytm.colors.ink,
                            )
                            SmallText(e.detail + if (e.fromHealthConnect) " · z Health Connect" else "")
                        }
                        if (canEdit(e)) Chip("Popraw", ChipTone.MUTED) { onEdit(e) }
                        Chip("Usuń", ChipTone.MUTED) { confirm = key }
                    }
                    if (confirm == key) {
                        SmallText(
                            "Usunąć ten wpis?" + when {
                                e.hasSets -> " Usunie też zapisane serie."
                                e.fromHealthConnect -> " Wpis z Health Connect może wrócić przy pełnej synchronizacji; usuń go też w aplikacji źródłowej."
                                else -> ""
                            },
                            color = Rytm.colors.warn,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RytmButton("Usuń", Modifier.weight(1f), primary = true) { onDelete(e); confirm = null }
                            RytmButton("Zostaw", Modifier.weight(1f)) { confirm = null }
                        }
                    }
                }
            }
        }
    }
}

private fun canEdit(e: Entry) = e.kind != EntryKind.CHECK_IN

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")
