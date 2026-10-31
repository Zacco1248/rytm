package com.zacco.rytm.phone.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zacco.rytm.phone.body.BodyScreen
import com.zacco.rytm.phone.diagnostics.DiagnosticsScreen
import com.zacco.rytm.phone.today.CheckInSheet
import com.zacco.rytm.phone.today.HabitsCard
import com.zacco.rytm.phone.today.NutritionSheet
import com.zacco.rytm.phone.today.RpeSheet
import com.zacco.rytm.phone.today.OtherActivitySheet
import com.zacco.rytm.phone.today.TodayScreen
import com.zacco.rytm.phone.ui.components.CardHeader
import com.zacco.rytm.phone.ui.components.RytmCard
import com.zacco.rytm.phone.ui.components.RytmIcons
import com.zacco.rytm.phone.ui.components.ScreenHeader
import com.zacco.rytm.phone.ui.components.SmallText
import com.zacco.rytm.phone.ui.components.WhyBox
import com.zacco.rytm.phone.ui.theme.Rytm
import com.zacco.rytm.phone.week.WeekScreen
import java.time.format.DateTimeFormatter

enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Dziś", RytmIcons.today),
    WEEK("Tydzień", RytmIcons.week),
    BODY("Sylwetka", RytmIcons.body),
    HABITS("Nawyki", RytmIcons.habits),
    EVENING("Wieczór", RytmIcons.evening),
}

private enum class Sheet { NONE, CHECK_IN, OTHER_ACTIVITY, NUTRITION, RPE }

@Composable
fun RytmRoot(vm: CoachViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var sheet by rememberSaveable { mutableStateOf(Sheet.NONE) }
    var diagnostics by rememberSaveable { mutableStateOf(false) }
    var rpeDate by rememberSaveable { mutableStateOf<String?>(null) }

    val state by vm.state.collectAsStateWithLifecycle()
    val hc by vm.hc.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        vm.refreshHealthConnect()
    }
    // Po powrocie do aplikacji (np. z ustawień Health Connect) sprawdzamy uprawnienia i dociągamy dane.
    LifecycleResumeEffect(Unit) {
        vm.refreshHealthConnect()
        onPauseOrDispose { }
    }

    val c = Rytm.colors
    if (diagnostics) {
        BackHandler { diagnostics = false }
        Box(Modifier.fillMaxSize().background(c.bg)) { DiagnosticsScreen() }
        return
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val s = state
            if (s == null) {
                SmallText("Wczytuję…", Modifier.padding(top = 24.dp))
            } else {
                when (tab) {
                    Tab.TODAY -> TodayScreen(
                        s = s.today,
                        hc = hc,
                        sync = sync,
                        onConnect = { permissionLauncher.launch(vm.permissions) },
                        onSync = vm::syncNow,
                        onProfile = { diagnostics = true },
                        onToggleHabit = { vm.toggleHabit(it.habit, it.done) },
                        onCheckIn = { sheet = Sheet.CHECK_IN },
                        onOtherActivity = { sheet = Sheet.OTHER_ACTIVITY },
                        onNutrition = { sheet = Sheet.NUTRITION },
                    )
                    Tab.WEEK -> WeekScreen(s, vm::toggleCancelled) { day ->
                        rpeDate = day.date.toString()
                        sheet = Sheet.RPE
                    }
                    Tab.BODY -> BodyScreen(s.body, s.today.nutritionTargets.kcal)
                    Tab.HABITS -> HabitsTab(s, onToggle = { vm.toggleHabit(it.habit, it.done) })
                    Tab.EVENING -> EveningTab(s)
                }
            }
        }
        TabBar(tab) { tab = it }
    }

    when (sheet) {
        Sheet.CHECK_IN -> CheckInSheet(state?.today?.checkIn, onDismiss = { sheet = Sheet.NONE }, onSave = vm::saveCheckIn)
        Sheet.OTHER_ACTIVITY -> OtherActivitySheet(onDismiss = { sheet = Sheet.NONE }, onSave = vm::logOtherActivity)
        Sheet.NUTRITION -> NutritionSheet(state?.today?.nutrition, onDismiss = { sheet = Sheet.NONE }, onSave = vm::saveNutrition)
        Sheet.RPE -> {
            val acts = state?.week?.days?.firstOrNull { it.date.toString() == rpeDate }?.activities
                ?.filter { it.type != com.zacco.rytm.domain.model.ActivityType.WALK }.orEmpty()
            if (acts.isNotEmpty()) RpeSheet(acts, onDismiss = { sheet = Sheet.NONE }, onSave = vm::setRpe)
        }
        Sheet.NONE -> Unit
    }
}

@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val c = Rytm.colors
    Column(Modifier.fillMaxWidth().background(c.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 8.dp)) {
            Tab.entries.forEach { t ->
                val on = t == selected
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(role = Role.Tab) { onSelect(t) }
                        .semantics { this.selected = on }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(if (on) c.accentSoft else c.surface)
                            .padding(horizontal = 14.dp),
                    ) {
                        Icon(t.icon, contentDescription = null, tint = if (on) c.accent else c.muted, modifier = Modifier.width(22.dp).height(22.dp))
                    }
                    Text(
                        t.label,
                        style = Rytm.type.tiny.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
                        color = if (on) c.ink else c.muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitsTab(s: CoachUiState, onToggle: (com.zacco.rytm.domain.coach.HabitStatus) -> Unit) {
    ScreenHeader(sub = "Tydzień od poniedziałku", title = "Nawyki")
    HabitsCard(s.today.habits, onToggle, showStreak = true)
    RytmCard {
        CardHeader("Sen") { SmallText("cel: zaśnięcie 24–01, 7–8 h") }
        val n = s.today.night
        SmallText(
            if (n?.sleepStart != null && n.sleepMin != null) {
                "Ostatnia noc: zaśnięcie " + n.sleepStart!!.atZone(com.zacco.rytm.domain.time.RytmTime.zone).format(DateTimeFormatter.ofPattern("HH:mm")) +
                    ", " + Format.minutes(n.sleepMin!!) + "."
            } else {
                "Brak snu z ostatniej nocy w Health Connect."
            },
        )
    }
    SmallText("Dodawanie własnych nawyków i kafelek na zegarku: faza 2.")
}

@Composable
private fun EveningTab(s: CoachUiState) {
    val t = s.today
    ScreenHeader(sub = Format.dayLong(t.date), title = "Wieczór")
    RytmCard {
        CardHeader("Pora snu")
        Text(t.bedtime.format(DateTimeFormatter.ofPattern("HH:mm")), style = Rytm.type.bigNumber, color = Rytm.colors.ink)
        SmallText("Jutro: ${t.tomorrow.title.lowercase()} (${Format.intensity(t.tomorrow.intensity).lowercase()}).")
        if (t.bedtime != com.zacco.rytm.domain.profile.SleepTarget().onsetFrom) {
            WhyBox("Pół godziny wcześniej niż zwykle: niska gotowość, ciężki dzień albo mocny trening jutro.")
        }
    }
    RytmCard {
        CardHeader("Przegląd dnia")
        SmallText("Wieczorna analiza AI (Haiku 5.5) pojawi się w fazie 4. Do tego czasu plan i porę snu ustala silnik reguł.")
    }
}
