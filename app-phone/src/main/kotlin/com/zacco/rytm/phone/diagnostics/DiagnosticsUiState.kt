package com.zacco.rytm.phone.diagnostics

import java.io.File

data class DiagnosticsUiState(
    val sdkStatus: SdkStatus = SdkStatus.CHECKING,
    val grantedCount: Int = 0,
    val requiredCount: Int = 0,
    val historyGranted: Boolean = false,
    val backgroundGranted: Boolean = false,
    val days: Int = DEFAULT_DAYS,
    val progress: String? = null,
    val reportMarkdown: String? = null,
    val files: List<File> = emptyList(),
    val error: String? = null,
) {
    val isRunning: Boolean get() = progress != null
    val allReadGranted: Boolean get() = requiredCount > 0 && grantedCount >= requiredCount

    companion object {
        const val DEFAULT_DAYS = 30
    }
}

enum class SdkStatus { CHECKING, AVAILABLE, UPDATE_REQUIRED, UNAVAILABLE }
