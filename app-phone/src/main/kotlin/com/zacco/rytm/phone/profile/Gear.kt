package com.zacco.rytm.phone.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.zacco.rytm.domain.exercise.Muscle
import com.zacco.rytm.domain.profile.ActivityLevel
import com.zacco.rytm.domain.profile.PersonalProfile
import com.zacco.rytm.phone.app.Format
import com.zacco.rytm.phone.ui.components.Chip
import com.zacco.rytm.phone.ui.components.ChipTone
import com.zacco.rytm.phone.ui.components.Pill
import com.zacco.rytm.phone.ui.components.SmallText
import com.zacco.rytm.phone.ui.components.WhyBox
import com.zacco.rytm.phone.ui.theme.Rytm

/**
 * „Co masz?”: jedna lista sprzętu zamiast osobnych przełączników. Główny rodzaj sprzętu do planu
 * wylicza [PersonalProfile.withGear] (siłownia, potem hantle, potem gumy).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GearChecklist(p: PersonalProfile, onChange: (PersonalProfile) -> Unit) {
    SmallText("Co masz? Zaznacz wszystko, z czego możesz korzystać.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill("Hantle", p.hasDumbbells) { onChange(p.withGear(dumbbells = !p.hasDumbbells)) }
        Pill("Gumy oporowe", p.hasBands) { onChange(p.withGear(bands = !p.hasBands)) }
        Pill("Drążek", p.pullUpBar) { onChange(p.copy(pullUpBar = !p.pullUpBar)) }
        Pill("Karnet na siłownię", p.hasGym) { onChange(p.withGear(gym = !p.hasGym)) }
        Pill("Bieżnia", p.treadmill) { onChange(p.copy(treadmill = !p.treadmill)) }
        Pill("Rower stacjonarny", p.indoorBike) { onChange(p.copy(indoorBike = !p.indoorBike)) }
    }
    val none = !p.hasDumbbells && !p.hasBands && !p.pullUpBar && !p.hasGym
    if (none) SmallText("Bez sprzętu też się da: plan oprze się na masie ciała.")
    if (p.pullUpBar && !p.hasGym) SmallText("Z drążkiem plan doda podciąganie (albo negatywy, jeśli jeszcze nie dajesz rady) i unoszenie kolan w zwisie.")
}

/** Partie do skupienia: sesja dostaje dodatkowe ćwiczenie albo serię (maks. 2 partie na sesję). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FocusPicker(p: PersonalProfile, onChange: (PersonalProfile) -> Unit) {
    SmallText("Na czym chcesz się skupić? Opcjonalnie; sesja dostanie na to dodatkowe ćwiczenie albo serię.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Muscle.entries.forEach { m ->
            val on = m in p.focus
            Pill(Format.muscle(m), on) { onChange(p.copy(focus = if (on) p.focus - m else p.focus + m)) }
        }
    }
}

/** Poziom ruchu poza treningami z wyjaśnieniem pod „?”. */
@Composable
internal fun ActivityLevelPicker(p: PersonalProfile, onChange: (PersonalProfile) -> Unit) {
    var help by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ruch poza treningami", style = Rytm.type.bodyStrong, color = Rytm.colors.ink)
        Chip(if (help) "?  ▴" else "?", ChipTone.MUTED) { help = !help }
    }
    Pills(
        listOf("Siedząca praca" to ActivityLevel.SEDENTARY, "Trochę ruchu" to ActivityLevel.LIGHT, "Dużo chodzenia" to ActivityLevel.MODERATE, "Praca fizyczna" to ActivityLevel.ACTIVE),
        p.activity,
    ) { onChange(p.copy(activity = it)) }
    if (help) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WhyBox(
                "Chodzi tylko o zwykły dzień: praca, dojazdy, dom, spacery. Treningi liczą się osobno: kcal z zegarka (albo szacunek z RPE) " +
                    "doliczam do celu jedzenia w dniu treningu. Siedząca praca i 2–3 treningi w tygodniu to nadal „Siedząca praca”.",
            )
            SmallText("Siedząca praca: biurko, auto, do 5 tys. kroków.")
            SmallText("Trochę ruchu: biurko, ale spacery albo pies, ok. 5–8 tys. kroków.")
            SmallText("Dużo chodzenia: praca na nogach (sklep, nauczyciel), 8–12 tys. kroków.")
            SmallText("Praca fizyczna: budowa, magazyn, noszenie, ponad 12 tys. kroków.")
        }
    }
}
