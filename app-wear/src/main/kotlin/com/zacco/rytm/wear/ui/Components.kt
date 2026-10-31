package com.zacco.rytm.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

/** Przewijana kolumna na okrągły ekran. Pokrętło/gest przewija jak każdy scroll. */
@Composable
fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
fun Title(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)

@Composable
fun Body(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)

@Composable
fun Label(text: String) =
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

@Composable
fun WideButton(text: String, onClick: () -> Unit, secondary: String? = null, tonal: Boolean = false) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (tonal) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.buttonColors(),
        secondaryLabel = secondary?.let { s -> { Text(s, maxLines = 1) } },
        label = { Text(text, maxLines = 2) },
    )
}

/** − wartość + ; duże pola dotyku zamiast suwaka, bo suwak na małym ekranie łatwo przestawić przy przewijaniu. */
@Composable
fun Stepper(value: Int, range: IntRange, onChange: (Int) -> Unit, step: Int = 1, display: (Int) -> String = { it.toString() }) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RoundKey("−", enabled = value - step >= range.first) { onChange(value - step) }
        Text(display(value), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        RoundKey("+", enabled = value + step <= range.last) { onChange(value + step) }
    }
}

@Composable
private fun RoundKey(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (enabled) colors.surfaceContainer else colors.surfaceContainerLow)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.titleLarge, color = if (enabled) colors.onSurface else colors.onSurfaceVariant)
    }
}

/** Przełączniki w dwóch kolumnach (np. „co boli”). */
@Composable
fun <T> ToggleGrid(items: List<T>, selected: Set<T>, label: (T) -> String, onToggle: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { item ->
                    ToggleChip(label(item), item in selected, Modifier.weight(1f)) { onToggle(item) }
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun ToggleChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) colors.primary else colors.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.onPrimary else colors.onSurface,
            maxLines = 1,
        )
    }
}
