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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zacco.rytm.phone.body.BodyScreen
import com.zacco.rytm.phone.diagnostics.DiagnosticsScreen
import com.zacco.rytm.phone.evening.AiUi
import com.zacco.rytm.phone.evening.EveningScreen
import com.zacco.rytm.phone.onboarding.OnboardingWizard
import com.zacco.rytm.phone.profile.ProfileScreen
import com.zacco.rytm.phone.today.AlcoholSheet
import com.zacco.rytm.phone.today.CheckInSheet
import com.zacco.rytm.phone.today.HabitsCard
import com.zacco.rytm.phone.today.NutritionSheet
import com.zacco.rytm.phone.today.RpeSheet
import com.zacco.rytm.phone.today.SleepSheet
import com.zacco.rytm.phone.today.ActivitySheet
import com.zacco.rytm.phone.today.PhoneWorkoutScreen
import com.zacco.rytm.phone.chat.ChatScreen
import com.zacco.rytm.phone.entries.EntriesScreen
import com.zacco.rytm.data.repo.EntryKind
import com.zacco.rytm.phone.today.QuickAddSheet
import com.zacco.rytm.phone.today.QuickEntry
import com.zacco.rytm.phone.today.TodayScreen
import com.zacco.rytm.phone.today.WeighInSheet
import com.zacco.rytm.phone.today.WorkoutLogSheet
import com.zacco.rytm.phone.ui.components.Chip
import com.zacco.rytm.phone.ui.components.ChipTone
import com.zacco.rytm.phone.ui.components.CardHeader
import com.zacco.rytm.phone.ui.components.RytmCard
import com.zacco.rytm.phone.ui.components.RytmIcons
import com.zacco.rytm.phone.ui.components.ScreenHeader
import com.zacco.rytm.phone.ui.components.SmallText
import com.zacco.rytm.phone.ui.theme.Rytm
import com.zacco.rytm.phone.week.WeekScreen
import com.zacco.rytm.domain.time.RytmTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Dziś", RytmIcons.today),
    WEEK("Tydzień", RytmIcons.week),
    BODY("Sylwetka", RytmIcons.body),
    HABITS("Nawyki", RytmIcons.habits),
    EVENING("Wieczór", RytmIcons.evening),
}

private enum class Sheet { NONE, QUICK_ADD, CHECK_IN, ACTIVITY, NUTRITION, RPE, WEIGH_IN, ALCOHOL, WORKOUT, SLEEP }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RytmRoot(vm: CoachViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var sheet by rememberSaveable { mutableStateOf(Sheet.NONE) }
    var diagnostics by rememberSaveable { mutableStateOf(false) }
    var profileOpen by rememberSaveable { mutableStateOf(false) }
    // Na starcie kreator krok po kroku; „Wolę jeden formularz” przełącza na pełny profil.
    var fullForm by rememberSaveable { mutableStateOf(false) }
    var workoutOpen by rememberSaveable { mutableStateOf(true) }
    var chatOpen by rememberSaveable { mutableStateOf(false) }
    var entriesOpen by rememberSaveable { mutableStateOf(false) }
    var replaceWeighIn by rememberSaveable { mutableStateOf<String?>(null) }
    var rpeDate by rememberSaveable { mutableStateOf<String?>(null) }
    // Dzień wybrany w arkuszu „+”; dotyczy każdego wpisu.
    var entryDate by rememberSaveable { mutableStateOf<String?>(null) }

    val state by vm.state.collectAsStateWithLifecycle()
    val hc by vm.hc.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val profileReady by vm.profileReady.collectAsStateWithLifecycle()
    val aiSettings by vm.aiSettings.collectAsStateWithLifecycle()
    val aiHasKey by vm.aiHasKey.collectAsStateWithLifecycle()
    val aiReviews by vm.aiReviews.collectAsStateWithLifecycle()
    val aiRunning by vm.aiRunning.collectAsStateWithLifecycle()
    val morningNotes by vm.morningNotes.collectAsStateWithLifecycle()
    val weeklyReports by vm.weeklyReports.collectAsStateWithLifecycle()
    val weeklyRunning by vm.weeklyRunning.collectAsStateWithLifecycle()
    val monthlyReports by vm.monthlyReports.collectAsStateWithLifecycle()
    val monthlyRunning by vm.monthlyRunning.collectAsStateWithLifecycle()
    val aiSpent by vm.aiSpent.collectAsStateWithLifecycle()
    val phoneRun by vm.phoneRun.collectAsStateWithLifecycle()
    val chat by vm.chat.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val exportStatus by vm.exportStatus.collectAsStateWithLifecycle()
    val chatRunning by vm.chatRunning.collectAsStateWithLifecycle()

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
    // Kreator przy pierwszym uruchomieniu albo edycja profilu.
    val onboarding = profileReady && profile == null
    if (onboarding || profileOpen) {
        if (profileOpen) BackHandler { profileOpen = false }
        Column(
            Modifier
                .fillMaxSize()
                .background(c.bg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onboarding && !fullForm) {
                OnboardingWizard(
                    initial = null,
                    onTweak = vm::tweakPlan,
                    onFullForm = { fullForm = true },
                    onFinish = { vm.saveProfile(it) },
                    hasAiKey = aiHasKey,
                    onSaveAiKey = { provider, key ->
                        vm.saveAiSettings(aiSettings.copy(provider = provider))
                        vm.saveAiKey(provider, key)
                    },
                )
            } else {
                ProfileScreen(
                    initial = profile,
                    onboarding = onboarding,
                    onSave = {
                        vm.saveProfile(it)
                        profileOpen = false
                    },
                    onDiagnostics = if (onboarding) null else ({ profileOpen = false; diagnostics = true }),
                )
            }
        }
        return
    }

    phoneRun?.takeIf { workoutOpen }?.let { run ->
        BackHandler { workoutOpen = false }
        Column(
            Modifier
                .fillMaxSize()
                .background(c.bg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PhoneWorkoutScreen(
                run = run,
                onUpdate = vm::updatePhoneRun,
                onFinish = vm::finishPhoneWorkout,
                onCancel = vm::cancelPhoneWorkout,
                onClose = { workoutOpen = false },
            )
        }
        return
    }

    if (entriesOpen) {
        BackHandler { entriesOpen = false }
        Column(
            Modifier
                .fillMaxSize()
                .background(c.bg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EntriesScreen(
                entries = entries,
                exportStatus = exportStatus,
                onEdit = { e ->
                    entriesOpen = false
                    entryDate = e.date.toString()
                    when (e.kind) {
                        EntryKind.WEIGH_IN -> {
                            // Stare ważenie usuwamy dopiero po zapisaniu nowego.
                            replaceWeighIn = e.id
                            sheet = Sheet.WEIGH_IN
                        }
                        EntryKind.NUTRITION -> sheet = Sheet.NUTRITION
                        EntryKind.ALCOHOL -> sheet = Sheet.ALCOHOL
                        EntryKind.ACTIVITY -> { rpeDate = e.date.toString(); sheet = Sheet.RPE }
                        EntryKind.CHECK_IN -> sheet = Sheet.CHECK_IN
                        EntryKind.SLEEP -> sheet = Sheet.SLEEP
                    }
                },
                onDelete = vm::deleteEntry,
                onExport = vm::exportTo,
                onClose = { entriesOpen = false },
            )
        }
        return
    }

    state?.takeIf { chatOpen }?.let { st ->
        BackHandler { chatOpen = false }
        val chatScroll = rememberScrollState()
        // Nowa wiadomość: przewiń na dół, do odpowiedzi i pola tekstu.
        androidx.compose.runtime.LaunchedEffect(chat.size, chatRunning) { chatScroll.animateScrollTo(chatScroll.maxValue) }
        Column(
            Modifier
                .fillMaxSize()
                .background(c.bg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(chatScroll)
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChatScreen(
                messages = chat,
                plan = st.today.plan,
                planChanged = st.today.planChanged,
                running = chatRunning,
                hasKey = aiHasKey,
                onSend = vm::sendChat,
                onResetPlan = vm::resetTodayPlan,
                onClear = vm::clearChat,
                onClose = { chatOpen = false },
            )
        }
        return
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        // Pociągnij w dół: synchronizacja z Health Connect od razu (np. zaraz po spacerze).
        PullToRefreshBox(
            isRefreshing = sync.running,
            onRefresh = vm::syncNow,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .padding(bottom = 84.dp),
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
                        onProfile = { profileOpen = true },
                        onEntries = { entriesOpen = true },
                        onToggleHabit = { vm.toggleHabit(it.habit, it.done) },
                        onCheckIn = { sheet = Sheet.CHECK_IN },
                        onNutrition = { entryDate = null; sheet = Sheet.NUTRITION },
                        onDayOff = vm::toggleDayOff,
                        onAdd = { entryDate = null; sheet = Sheet.ACTIVITY },
                        onLogWorkout = { sheet = Sheet.WORKOUT },
                        phoneRunActive = phoneRun != null,
                        onPhoneWorkout = {
                            vm.startPhoneWorkout()
                            workoutOpen = true
                        },
                        morning = morningNotes.firstOrNull { it.date == s.today.date.toString() }?.note,
                        onCoach = { chatOpen = true },
                        onResetPlan = vm::resetTodayPlan,
                        onAddSleep = { entryDate = null; sheet = Sheet.SLEEP },
                    )
                    Tab.WEEK -> WeekScreen(
                        s,
                        vm::toggleCancelled,
                        weekly = weeklyReports.firstOrNull(),
                        weeklyRunning = weeklyRunning,
                        onRunWeekly = vm::runWeekly,
                    ) { day ->
                        rpeDate = day.date.toString()
                        sheet = Sheet.RPE
                    }
                    Tab.BODY -> BodyScreen(
                        s.body,
                        s.today.nutritionTargets.kcal - s.today.trainingKcal,
                        monthly = monthlyReports.firstOrNull(),
                        monthlyRunning = monthlyRunning,
                        onRunMonthly = vm::runMonthly,
                        onAddWeighIn = { entryDate = null; sheet = Sheet.WEIGH_IN },
                        onResetKcal = vm::resetKcalAdjust,
                        onAddGoal = vm::addGoal,
                        onRemoveGoal = vm::removeGoal,
                    )
                    Tab.HABITS -> HabitsTab(s, onToggle = { vm.toggleHabit(it.habit, it.done) }, onAddSleep = { entryDate = null; sheet = Sheet.SLEEP })
                    Tab.EVENING -> EveningScreen(
                        s = s,
                        ai = AiUi(
                            settings = aiSettings,
                            hasKey = aiHasKey,
                            review = aiReviews.firstOrNull { it.date == s.today.date.toString() },
                            running = aiRunning,
                            spentUsd = aiSpent,
                        ),
                        onRun = vm::runEvening,
                        onSaveSettings = vm::saveAiSettings,
                        onSaveKey = vm::saveAiKey,
                        onClearKey = vm::clearAiKey,
                    )
                }
            }
        }
            if (state != null && tab != Tab.EVENING) {
                AddButton(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                    entryDate = null
                    sheet = Sheet.QUICK_ADD
                }
            }
        }
        TabBar(tab) { tab = it }
    }

    val today = state?.today?.date ?: LocalDate.now(RytmTime.zone)
    val date = entryDate?.let(LocalDate::parse) ?: today

    when (sheet) {
        Sheet.QUICK_ADD -> QuickAddSheet(
            today = today,
            date = date,
            hasWorkout = state?.today?.plan?.workout != null,
            onDate = { entryDate = it.toString() },
            onDismiss = { sheet = Sheet.NONE },
        ) { entry ->
            sheet = when (entry) {
                QuickEntry.ACTIVITY -> Sheet.ACTIVITY
                QuickEntry.WORKOUT -> Sheet.WORKOUT
                QuickEntry.NUTRITION -> Sheet.NUTRITION
                QuickEntry.WEIGH_IN -> Sheet.WEIGH_IN
                QuickEntry.ALCOHOL -> Sheet.ALCOHOL
                QuickEntry.CHECK_IN -> Sheet.CHECK_IN
                QuickEntry.SLEEP -> Sheet.SLEEP
            }
        }
        Sheet.CHECK_IN -> CheckInSheet(state?.today?.checkIn, onDismiss = { sheet = Sheet.NONE }, onSave = vm::saveCheckIn)
        Sheet.ACTIVITY -> ActivitySheet(date, onDismiss = { sheet = Sheet.NONE }, onSave = vm::logActivity)
        Sheet.NUTRITION -> state?.let { s ->
            NutritionSheet(s.today.date, s.recentNutrition, onDismiss = { sheet = Sheet.NONE }, onSave = vm::saveNutrition, initial = date)
        }
        Sheet.ALCOHOL -> state?.let { s ->
            AlcoholSheet(date, s.recentAlcohol[date] ?: 0, onDismiss = { sheet = Sheet.NONE }, onSave = vm::setAlcohol)
        }
        Sheet.RPE -> {
            val acts = state?.week?.days?.firstOrNull { it.date.toString() == rpeDate }?.activities
                ?.filter { it.type != com.zacco.rytm.domain.model.ActivityType.WALK }.orEmpty()
            if (acts.isNotEmpty()) RpeSheet(acts, onDismiss = { sheet = Sheet.NONE }, onSave = vm::setRpe)
        }
        Sheet.WORKOUT -> state?.today?.plan?.workout?.let { w ->
            WorkoutLogSheet(w, onDismiss = { sheet = Sheet.NONE }, onSave = vm::saveManualWorkout)
        }
        Sheet.WEIGH_IN -> WeighInSheet(date, onDismiss = { sheet = Sheet.NONE; replaceWeighIn = null }) { kg, fat, time, comp ->
            replaceWeighIn?.let { vm.deleteWeighIn(it) }
            replaceWeighIn = null
            vm.addWeighIn(kg, fat, time, comp, date)
        }
        Sheet.SLEEP -> {
            // Do poprawki: ręczny wpis z listy albo sen z zegarka z dzisiejszej nocy.
            val manual = entries.firstOrNull { it.kind == EntryKind.SLEEP && it.date == date }
            val night = state?.today?.night?.takeIf { it.date == date }
            SleepSheet(
                date,
                initialStart = manual?.time ?: night?.sleepStart,
                initialEnd = manual?.end ?: night?.sleepEnd,
                onDismiss = { sheet = Sheet.NONE },
                onSave = vm::saveSleep,
            )
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
private fun HabitsTab(s: CoachUiState, onToggle: (com.zacco.rytm.domain.coach.HabitStatus) -> Unit, onAddSleep: () -> Unit) {
    ScreenHeader(sub = "Tydzień od poniedziałku", title = "Nawyki")
    HabitsCard(s.today.habits, onToggle, showStreak = true)
    RytmCard {
        CardHeader("Sen") { SmallText("cel: zaśnięcie 24–01, 7–8 h") }
        val n = s.today.night
        SmallText(
            if (n?.sleepStart != null && n.sleepMin != null) {
                "Ostatnia noc: zaśnięcie " + n.sleepStart!!.atZone(com.zacco.rytm.domain.time.RytmTime.zone).format(DateTimeFormatter.ofPattern("HH:mm")) +
                    ", " + Format.minutes(n.sleepMin!!) + (if (n.sleepManual) " (wpisane ręcznie)." else ".")
            } else {
                "Brak snu z ostatniej nocy w Health Connect."
            },
        )
        Chip(if (n?.sleepMin == null) "Wpisz sen" else "Popraw sen", ChipTone.ACCENT, onClick = onAddSleep)
    }
    SmallText("Dodawanie własnych nawyków i kafelek na zegarku: faza 2.")
}


/** Pływający przycisk „+”: jedno miejsce na wszystkie wpisy. */
@Composable
private fun AddButton(modifier: Modifier, onClick: () -> Unit) {
    val c = Rytm.colors
    Box(
        modifier
            .size(56.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(c.accent)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .semantics { contentDescription = "Dodaj wpis" },
        contentAlignment = Alignment.Center,
    ) {
        Text("+", style = Rytm.type.screenTitle.copy(fontSize = 28.sp), color = c.onAccent)
    }
}
