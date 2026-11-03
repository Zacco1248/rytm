package com.zacco.rytm.wear.hrv

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.zacco.rytm.domain.hrv.HrvQuality
import java.util.Locale

/** Wear OS 6 (API 36) zastępuje BODY_SENSORS uprawnieniem health.READ_HEART_RATE. */
private val heartRatePermission: String =
    if (Build.VERSION.SDK_INT >= 36) "android.permission.health.READ_HEART_RATE" else Manifest.permission.BODY_SENSORS

@Composable
fun HrvScreen(
    /** Wywoływane przyciskiem „Dodaj do check-inu” po udanym pomiarze. */
    onUse: ((Double) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    viewModel: HrvViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.start()
    }
    val onStart = {
        if (ContextCompat.checkSelfPermission(context, heartRatePermission) == PackageManager.PERMISSION_GRANTED) {
            viewModel.start()
        } else {
            permissionLauncher.launch(heartRatePermission)
        }
    }

    val measuring = state is HrvUiState.Connecting || state is HrvUiState.WarmingUp || state is HrvUiState.Measuring
    val view = LocalView.current
    DisposableEffect(measuring) {
        view.keepScreenOn = measuring
        onDispose { view.keepScreenOn = false }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        when (val s = state) {
            HrvUiState.Idle -> {
                Title("HRV, 60 s")
                Body("Usiądź, rozluźnij rękę i oddychaj spokojnie.")
                Button(onClick = onStart, label = { Text("Start") })
                onBack?.let { back -> Button(onClick = back, label = { Text("Wróć") }) }
            }
            HrvUiState.Connecting -> Title("Łączę z sensorem…")
            is HrvUiState.WarmingUp -> {
                Title("Przygotowanie ${s.secondsLeft} s")
                Body("Tętno: ${s.hrBpm ?: "–"}")
            }
            is HrvUiState.Measuring -> {
                Text("${s.secondsLeft}", style = MaterialTheme.typography.displayLarge)
                Body("Tętno: ${s.hrBpm ?: "–"} · uderzenia: ${s.beats}")
                Button(onClick = viewModel::reset, label = { Text("Przerwij") })
            }
            is HrvUiState.Done -> {
                val r = s.result
                Title(r.rmssdMs?.let { "RMSSD ${it.fmt()} ms" } ?: "Za mało danych")
                Body("ln ${r.lnRmssd?.fmt(2) ?: "–"} · HR ${r.meanHrBpm?.fmt(0) ?: "–"}")
                Body("Uderzenia ${r.beatsAccepted}/${r.beatsTotal}, pary ${r.pairsUsed}")
                Body("Jakość: ${r.quality.label()}")
                val rmssd = r.rmssdMs
                if (onUse != null && rmssd != null && r.quality != HrvQuality.POOR) {
                    Button(onClick = { onUse(rmssd) }, label = { Text("Dodaj do check-inu") })
                }
                Button(onClick = viewModel::reset, label = { Text("Jeszcze raz") })
            }
            is HrvUiState.Error -> {
                Title("Błąd")
                Body(s.message)
                Button(onClick = viewModel::reset, label = { Text("OK") })
            }
            is HrvUiState.Unavailable -> {
                Title("Brak Sensor SDK")
                Body(s.reason)
                onBack?.let { back -> Button(onClick = back, label = { Text("Wróć") }) }
            }
        }
    }
}

@Composable
private fun Title(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)

@Composable
private fun Body(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)

private fun Double.fmt(decimals: Int = 1): String = String.format(Locale.ROOT, "%.${decimals}f", this)

private fun HrvQuality.label(): String = when (this) {
    HrvQuality.GOOD -> "dobra"
    HrvQuality.ACCEPTABLE -> "akceptowalna"
    HrvQuality.POOR -> "słaba"
}
