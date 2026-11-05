package com.zacco.rytm.phone.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zacco.rytm.phone.ui.theme.Rytm

/** Karta z makiety: tło surface, obramowanie line, promień 18. */
@Composable
fun RytmCard(
    modifier: Modifier = Modifier,
    padding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(16.dp),
    spacing: Dp = 12.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Rytm.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** Nagłówek karty (wersaliki, muted) z opcjonalnym elementem po prawej. */
@Composable
fun CardHeader(title: String, help: String? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    var open by androidx.compose.runtime.saveable.rememberSaveable(title) { androidx.compose.runtime.mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title.uppercase(), style = Rytm.type.cardTitle, color = Rytm.colors.muted)
            if (help != null) HelpDot(open) { open = !open }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
    if (help != null && open) WhyBox(help)
}

/** Mały znak zapytania obok nazwy; po dotknięciu pokazuje wyjaśnienie pojęcia. */
@Composable
fun HelpDot(open: Boolean, onClick: () -> Unit) {
    val c = Rytm.colors
    Box(
        Modifier
            .size(26.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (open) c.accent else c.soft)
            .clickable(onClickLabel = "Wyjaśnienie", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("?", style = Rytm.type.tiny, color = if (open) c.bg else c.ink)
    }
}

/** Nagłówek ekranu: podtytuł nad tytułem, chip po prawej. */
@Composable
fun ScreenHeader(sub: String, title: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(sub, style = Rytm.type.small, color = Rytm.colors.muted)
            Text(title, style = Rytm.type.screenTitle, color = Rytm.colors.ink)
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

enum class ChipTone { ACCENT, GOOD, WARN, BAD, MUTED }

@Composable
fun Chip(text: String, tone: ChipTone = ChipTone.ACCENT, onClick: (() -> Unit)? = null) {
    val c = Rytm.colors
    val (bg, fg) = when (tone) {
        ChipTone.ACCENT -> c.accentSoft to c.accent
        ChipTone.GOOD -> c.goodSoft to c.good
        ChipTone.WARN -> c.warnSoft to c.warn
        ChipTone.BAD -> c.warnSoft to c.bad
        // Tekst jasny jak treść: szary na szarym był w ciemnym motywie nieczytelny.
        ChipTone.MUTED -> c.chipMuted to c.ink
    }
    Text(
        text,
        style = Rytm.type.chip,
        color = fg,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

@Composable
fun RytmButton(text: String, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Rytm.colors
    // Pigułka min. 48 dp: wypełniona akcentem z ciemnym tekstem albo obrys z jasnym tekstem (kontrast WCAG w obu motywach).
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (primary && enabled) c.accent else c.surface)
            .border(1.dp, if (primary && enabled) c.accent else c.outline, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = Rytm.type.button,
            color = when {
                !enabled -> c.muted
                primary -> c.onAccent
                else -> c.ink
            },
            textAlign = TextAlign.Center,
        )
    }
}

/** Okrągły przycisk z ikoną (44 dp), np. „+” w nagłówku. */
@Composable
fun IconCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    primary: Boolean = false,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val c = Rytm.colors
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (primary) c.accent else Color.Transparent)
            .then(if (primary) Modifier else Modifier.border(1.dp, c.outline, CircleShape))
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = tint ?: if (primary) c.onAccent else c.ink, modifier = Modifier.size(22.dp))
    }
}

/** Mały napis nad treścią karty, wersalikami. */
@Composable
fun CardLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = Rytm.type.cardTitle, color = Rytm.colors.muted, modifier = modifier)
}

/** Wiersz do dotknięcia: tytuł, wartość po prawej i strzałka (albo ▴/▾ przy rozwijaniu). */
@Composable
fun TapRow(title: String, value: String? = null, expanded: Boolean? = null, valueColor: Color? = null, onClick: () -> Unit) {
    val c = Rytm.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = Rytm.type.body, color = c.ink, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = Rytm.type.small, color = valueColor ?: c.muted, maxLines = 1)
        val rot = when (expanded) { true -> 270f; false -> 90f; null -> 0f }
        androidx.compose.material3.Icon(
            RytmIcons.chevron, contentDescription = null, tint = c.muted,
            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rot },
        )
    }
}

/** Element listy „Więcej”: wiersz, który po dotknięciu pokazuje pełną kartę pod listą. */
class MoreItem(val title: String, val value: String? = null, val valueColor: Color? = null, val content: @Composable () -> Unit)

/** Progresywne ujawnianie: lista wierszy w jednej karcie, pod nią rozwinięta karta wybranego (jedna naraz). */
@Composable
fun MoreList(items: List<MoreItem>, key: String) {
    var open by androidx.compose.runtime.saveable.rememberSaveable(key) { androidx.compose.runtime.mutableStateOf<String?>(null) }
    if (items.isEmpty()) return
    RytmCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 2.dp), spacing = 0.dp) {
        items.forEachIndexed { i, it ->
            if (i > 0) Divider()
            TapRow(it.title, it.value, expanded = open == it.title, valueColor = it.valueColor) {
                open = if (open == it.title) null else it.title
            }
        }
    }
    items.firstOrNull { it.title == open }?.content?.invoke()
}

/** Dwa przyciski obok siebie, po połowie szerokości. */
@Composable
fun ButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** Poziomy pasek postępu (0–1). */
@Composable
fun Bar(fraction: Float, color: Color, height: Dp = 4.dp, modifier: Modifier = Modifier) {
    val track = Rytm.colors.track
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height))
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(height))
                .background(color),
        )
    }
}

/** Pierścień gotowości. */
@Composable
fun Ring(fraction: Float, color: Color, size: Dp = 112.dp, stroke: Dp = 11.dp, content: @Composable () -> Unit) {
    val track = Rytm.colors.track
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            val inset = w / 2
            val arcSize = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Uzasadnienie z lewym akcentem („why” w makiecie). */
@Composable
fun WhyBox(text: String) {
    val c = Rytm.colors
    Text(
        text,
        style = Rytm.type.small,
        color = c.ink,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
            .background(c.soft)
            .drawBehind { drawRect(c.accent, size = Size(3.dp.toPx(), size.height)) }
            .padding(start = 13.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
    )
}

@Composable
fun SmallText(text: String, modifier: Modifier = Modifier, color: Color = Rytm.colors.muted) {
    Text(text, style = Rytm.type.small, color = color, modifier = modifier)
}

/** Linia oddzielająca wiersze w karcie. */
@Composable
fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Rytm.colors.line))
}

/** Przełącznik w kształcie pigułki (wybór jednej lub wielu opcji). */
@Composable
fun Pill(text: String, on: Boolean, onClick: () -> Unit) {
    val c = Rytm.colors
    Text(
        text,
        style = Rytm.type.small,
        color = if (on) c.bg else c.ink,
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (on) c.ink else c.surface)
            .border(1.dp, if (on) c.ink else c.line, androidx.compose.foundation.shape.CircleShape)
            .clickable(role = androidx.compose.ui.semantics.Role.Checkbox, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
