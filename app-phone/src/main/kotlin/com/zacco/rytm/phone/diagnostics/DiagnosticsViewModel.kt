package com.zacco.rytm.phone.diagnostics

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zacco.rytm.data.healthconnect.HealthConnectDumper
import com.zacco.rytm.data.healthconnect.HealthConnectPermissions
import com.zacco.rytm.domain.diagnostics.DumpAnalyzer
import com.zacco.rytm.domain.diagnostics.ReportRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class DiagnosticsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    val permissions: Set<String> = HealthConnectPermissions.all

    private val client: HealthConnectClient? get() =
        if (sdkStatus() == SdkStatus.AVAILABLE) HealthConnectClient.getOrCreate(getApplication()) else null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val status = sdkStatus()
            val granted = client?.permissionController?.getGrantedPermissions().orEmpty()
            _uiState.update {
                it.copy(
                    sdkStatus = status,
                    grantedCount = granted.count { p -> p in HealthConnectPermissions.read },
                    requiredCount = HealthConnectPermissions.read.size,
                    historyGranted = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in granted,
                    backgroundGranted = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in granted,
                )
            }
        }
    }

    fun setDays(days: Int) = _uiState.update { it.copy(days = days) }

    fun dump() {
        val hc = client ?: return
        if (_uiState.value.isRunning) return
        viewModelScope.launch {
            _uiState.update { it.copy(progress = "Start", error = null) }
            try {
                val to = Instant.now()
                val from = to.minus(Duration.ofDays(_uiState.value.days.toLong()))
                val dump = HealthConnectDumper(hc).dump(from, to) { type ->
                    _uiState.update { it.copy(progress = "Czytam $type…") }
                }
                _uiState.update { it.copy(progress = "Analiza…") }
                val (markdown, files) = withContext(Dispatchers.Default) {
                    val md = ReportRenderer.render(DumpAnalyzer.analyze(dump))
                    md to save(dump.toJson(), md)
                }
                _uiState.update { it.copy(progress = null, reportMarkdown = markdown, files = files) }
            } catch (e: Exception) {
                _uiState.update { it.copy(progress = null, error = "${e::class.simpleName}: ${e.message}") }
            }
        }
    }

    private suspend fun save(json: String, markdown: String): List<File> = withContext(Dispatchers.IO) {
        val dir = File(getApplication<Application>().getExternalFilesDir(null), "phase0").apply { mkdirs() }
        val stamp = FILE_STAMP.format(Instant.now())
        listOf(
            File(dir, "rytm-hc-dump-$stamp.json").apply { writeText(json) },
            File(dir, "rytm-hc-raport-$stamp.md").apply { writeText(markdown) },
        )
    }

    private fun sdkStatus(): SdkStatus = when (HealthConnectClient.getSdkStatus(getApplication())) {
        HealthConnectClient.SDK_AVAILABLE -> SdkStatus.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> SdkStatus.UPDATE_REQUIRED
        else -> SdkStatus.UNAVAILABLE
    }

    private companion object {
        val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").withZone(ZoneOffset.UTC)
    }
}
