package com.zacco.rytm.phone.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
fun RytmCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Rytm.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** Nagłówek karty (wersaliki, muted) z opcjonalnym elementem po prawej. */
@Composable
fun CardHeader(title: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title.uppercase(), style = Rytm.type.cardTitle, color = Rytm.colors.muted)
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
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
        ChipTone.MUTED -> c.chipMuted to c.muted
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
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (primary) c.accent else c.surface)
            .border(1.dp, if (primary) c.accent else c.line, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
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
