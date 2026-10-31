package com.zacco.rytm.phone.diagnostics

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File

@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { viewModel.refresh() }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Rytm, faza 0", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Zrzut danych z Health Connect: co faktycznie zapisują Samsung Health, Fitatu i Xiaomi Home.",
            style = MaterialTheme.typography.bodyMedium,
        )

        StatusCard(state)

        when (state.sdkStatus) {
            SdkStatus.UPDATE_REQUIRED -> Button(onClick = { context.openHealthConnectInStore() }) {
                Text("Zainstaluj / zaktualizuj Health Connect")
            }
            SdkStatus.AVAILABLE -> {
                Button(onClick = { permissionLauncher.launch(viewModel.permissions) }) {
                    Text(if (state.allReadGranted) "Sprawdź uprawnienia ponownie" else "Nadaj uprawnienia")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 90).forEach { days ->
                        FilterChip(
                            selected = state.days == days,
                            onClick = { viewModel.setDays(days) },
                            label = { Text("$days dni") },
                            enabled = days == 30 || state.historyGranted,
                        )
                    }
                }
                Button(onClick = viewModel::dump, enabled = state.grantedCount > 0 && !state.isRunning) {
                    Text(state.progress ?: "Zrzuć dane z ${state.days} dni")
                }
            }
            else -> Unit
        }

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (state.files.isNotEmpty()) {
            OutlinedButton(onClick = { context.shareFiles(state.files) }) {
                Text("Udostępnij raport i zrzut JSON")
            }
            Text(
                "Pliki: ${state.files.first().parent}",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        state.reportMarkdown?.let { markdown ->
            Card(modifier = Modifier.fillMaxWidth()) {
                SelectionContainer {
                    Text(
                        text = markdown,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(state: DiagnosticsUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                when (state.sdkStatus) {
                    SdkStatus.CHECKING -> "Sprawdzam Health Connect…"
                    SdkStatus.AVAILABLE -> "Health Connect: dostępny"
                    SdkStatus.UPDATE_REQUIRED -> "Health Connect: wymaga instalacji lub aktualizacji"
                    SdkStatus.UNAVAILABLE -> "Health Connect: niedostępny na tym urządzeniu"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.sdkStatus == SdkStatus.AVAILABLE) {
                Text("Uprawnienia do odczytu: ${state.grantedCount}/${state.requiredCount}")
                Text("Historia > 30 dni: ${if (state.historyGranted) "tak" else "nie"}")
                Text("Odczyt w tle: ${if (state.backgroundGranted) "tak" else "nie"}")
            }
        }
    }
}

private fun Context.openHealthConnectInStore() {
    val uri = "market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding".toUri()
    try {
        startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.vending"))
    } catch (_: ActivityNotFoundException) {
        startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata".toUri()))
    }
}

private fun Context.shareFiles(files: List<File>) {
    val uris = ArrayList(files.map { FileProvider.getUriForFile(this, "$packageName.files", it) })
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "*/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        putExtra(Intent.EXTRA_SUBJECT, "Rytm: raport fazy 0")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, "Udostępnij raport"))
}
