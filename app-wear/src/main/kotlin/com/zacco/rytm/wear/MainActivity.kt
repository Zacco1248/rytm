package com.zacco.rytm.wear

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.MaterialTheme
import com.zacco.rytm.sync.WatchEvent
import com.zacco.rytm.wear.data.WatchStore
import com.zacco.rytm.wear.hrv.HrvScreen
import com.zacco.rytm.wear.ui.CheckInDraft
import com.zacco.rytm.wear.ui.CheckInScreen
import com.zacco.rytm.wear.ui.HabitsScreen
import com.zacco.rytm.wear.ui.HomeScreen
import com.zacco.rytm.wear.ui.OtherActivityScreen
import com.zacco.rytm.wear.ui.RpeScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val screen = mutableStateOf(SCREEN_HOME)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen.value = intent?.getStringExtra(EXTRA_SCREEN) ?: SCREEN_HOME
        setContent {
            MaterialTheme {
                RytmWatchApp(screen)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_SCREEN)?.let { screen.value = it }
    }

    companion object {
        /** Ekran otwierany z kafelka. */
        const val EXTRA_SCREEN = "screen"
        const val SCREEN_HOME = "home"
        const val SCREEN_CHECK_IN = "check_in"
        const val SCREEN_HRV = "hrv"
        const val SCREEN_RPE = "rpe"
        const val SCREEN_HABITS = "habits"
        const val SCREEN_OTHER = "other"
    }
}

/** Prosta nawigacja stanem: kilka ekranów, bez biblioteki nawigacji. */
@Composable
private fun RytmWatchApp(nav: MutableState<String>) {
    val context = LocalContext.current.applicationContext
    val snapshot by WatchStore.observe(context).collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val send: (WatchEvent) -> Unit = { e -> scope.launch { WatchStore.send(context, e) } }
    var draft by remember { mutableStateOf(CheckInDraft()) }
    var rpeTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val go: (String) -> Unit = { nav.value = it }
    val home = { go(MainActivity.SCREEN_HOME) }

    LaunchedEffect(Unit) { WatchStore.pull(context) }

    BackHandler(enabled = nav.value != MainActivity.SCREEN_HOME) {
        go(if (nav.value == MainActivity.SCREEN_HRV) MainActivity.SCREEN_CHECK_IN else MainActivity.SCREEN_HOME)
    }

    val s = snapshot
    when (nav.value) {
        MainActivity.SCREEN_CHECK_IN -> CheckInScreen(
            draft = draft,
            onChange = { draft = it },
            onMeasureHrv = { go(MainActivity.SCREEN_HRV) },
            onSave = {
                send(WatchEvent.CheckIn(WatchStore.today(), draft.energy, draft.mood, draft.sore.map { it.name }, draft.hrv))
                draft = CheckInDraft()
                home()
            },
            onCancel = home,
        )
        MainActivity.SCREEN_HRV -> HrvScreen(
            onUse = { rmssd ->
                draft = draft.copy(hrv = rmssd)
                go(MainActivity.SCREEN_CHECK_IN)
            },
            onBack = { go(MainActivity.SCREEN_CHECK_IN) },
        )
        MainActivity.SCREEN_RPE -> {
            val activity = s?.toRate?.let { list -> list.firstOrNull { it.id == rpeTarget } ?: list.firstOrNull() }
            if (activity == null) {
                LaunchedEffect(Unit) { home() }
            } else {
                RpeScreen(
                    activity = activity,
                    onSave = { rpe ->
                        send(WatchEvent.Rpe(WatchStore.today(), activity.id, rpe))
                        rpeTarget = null
                        home()
                    },
                    onCancel = home,
                )
            }
        }
        MainActivity.SCREEN_HABITS -> HabitsScreen(
            s = s,
            onToggle = { h -> send(WatchEvent.HabitToggle(WatchStore.today(), h.id, !h.done)) },
            onBack = home,
        )
        MainActivity.SCREEN_OTHER -> OtherActivityScreen(
            onSave = { e ->
                send(e)
                home()
            },
            onCancel = home,
        )
        else -> HomeScreen(
            s = s,
            onCheckIn = { go(MainActivity.SCREEN_CHECK_IN) },
            onRate = { a ->
                rpeTarget = a.id
                go(MainActivity.SCREEN_RPE)
            },
            onHabits = { go(MainActivity.SCREEN_HABITS) },
            onOther = { go(MainActivity.SCREEN_OTHER) },
        )
    }
}
