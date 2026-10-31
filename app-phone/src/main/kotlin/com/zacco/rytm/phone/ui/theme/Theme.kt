package com.zacco.rytm.phone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zacco.rytm.phone.R

/** Kolory z makiety (tokeny CSS), jasne i ciemne. */
@Immutable
data class RytmColors(
    val bg: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val track: Color,
    val soft: Color,
    val chipMuted: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val good: Color,
    val goodSoft: Color,
    val warn: Color,
    val warnSoft: Color,
    val bad: Color,
    val heart: Color,
    // Rodzaje aktywności (wykres obciążenia, kropki w planie).
    val kindStrength: Color = Color(0xFF2C56E0),
    val kindSport: Color = Color(0xFFC97D07),
    val kindPt: Color = Color(0xFF8A4FD8),
    val kindMobility: Color = Color(0xFF19935F),
    val kindRest: Color = Color(0xFF9AA6B1),
    val kindOther: Color = Color(0xFF7B8794),
)

val LightRytmColors = RytmColors(
    bg = Color(0xFFF5F7F9),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF132029),
    muted = Color(0xFF5A6875),
    line = Color(0xFFDDE3E9),
    track = Color(0xFFEDF0F3),
    soft = Color(0xFFF3F6FB),
    chipMuted = Color(0xFFEEF1F4),
    accent = Color(0xFF2C56E0),
    accentSoft = Color(0xFFE5EBFD),
    onAccent = Color(0xFFFFFFFF),
    good = Color(0xFF19935F),
    goodSoft = Color(0xFFDFF3EA),
    warn = Color(0xFFC97D07),
    warnSoft = Color(0xFFFBF0D9),
    bad = Color(0xFFCF3F3F),
    heart = Color(0xFFE04E5D),
)

val DarkRytmColors = RytmColors(
    bg = Color(0xFF0F1418),
    surface = Color(0xFF182026),
    ink = Color(0xFFE6ECF1),
    muted = Color(0xFF93A1AD),
    line = Color(0xFF27323B),
    track = Color(0xFF26313A),
    soft = Color(0xFF1C2836),
    chipMuted = Color(0xFF222C35),
    accent = Color(0xFF86A2FF),
    accentSoft = Color(0xFF1F2A4A),
    onAccent = Color(0xFF0B1020),
    good = Color(0xFF43C98D),
    goodSoft = Color(0xFF143426),
    warn = Color(0xFFF0B44A),
    warnSoft = Color(0xFF362B13),
    bad = Color(0xFFE86B6B),
    heart = Color(0xFFF07A86),
    kindStrength = Color(0xFF86A2FF),
    kindSport = Color(0xFFF0B44A),
    kindPt = Color(0xFFB98AF0),
    kindMobility = Color(0xFF43C98D),
)

val SoraFamily = FontFamily(
    Font(R.font.sora_medium, FontWeight.Medium),
    Font(R.font.sora_semibold, FontWeight.SemiBold),
    Font(R.font.sora_bold, FontWeight.Bold),
)

val PlexFamily = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
)

/** Style tekstu z makiety: nagłówki Sora, reszta IBM Plex Sans. */
@Immutable
data class RytmType(
    val screenTitle: TextStyle = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.2).sp),
    val planTitle: TextStyle = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold, fontSize = 19.sp),
    val bigNumber: TextStyle = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold, fontSize = 30.sp),
    val ringNumber: TextStyle = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 34.sp),
    val dayNumber: TextStyle = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    val cardTitle: TextStyle = TextStyle(fontFamily = PlexFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.5.sp),
    val body: TextStyle = TextStyle(fontFamily = PlexFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    val bodyStrong: TextStyle = TextStyle(fontFamily = PlexFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp),
    val small: TextStyle = TextStyle(fontFamily = PlexFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    val tiny: TextStyle = TextStyle(fontFamily = PlexFamily, fontWeight = FontWeight.Normal, fontSize = 11.sp),
    val chip: TextStyle = TextStyle(fontFamily = PlexFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
    val button: TextStyle = TextStyle(fontFamily = PlexFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp),
)

val LocalRytmColors = staticCompositionLocalOf { LightRytmColors }
val LocalRytmType = staticCompositionLocalOf { RytmType() }

object Rytm {
    val colors: RytmColors @Composable get() = LocalRytmColors.current
    val type: RytmType @Composable get() = LocalRytmType.current
}

/** Motyw aplikacji. Domyślnie idzie za ustawieniem systemu (jak w makiecie). */
@Composable
fun RytmTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkRytmColors else LightRytmColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft,
            background = c.bg, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
            surfaceVariant = c.chipMuted, onSurfaceVariant = c.muted, outline = c.line, error = c.bad,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft,
            background = c.bg, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
            surfaceVariant = c.chipMuted, onSurfaceVariant = c.muted, outline = c.line, error = c.bad,
        )
    }
    val type = RytmType()
    val base = Typography()
    val typography = Typography(
        bodyLarge = base.bodyLarge.copy(fontFamily = PlexFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = PlexFamily),
        bodySmall = base.bodySmall.copy(fontFamily = PlexFamily),
        labelLarge = base.labelLarge.copy(fontFamily = PlexFamily),
        labelMedium = base.labelMedium.copy(fontFamily = PlexFamily),
        labelSmall = base.labelSmall.copy(fontFamily = PlexFamily),
        titleLarge = base.titleLarge.copy(fontFamily = SoraFamily),
        titleMedium = base.titleMedium.copy(fontFamily = SoraFamily),
        titleSmall = base.titleSmall.copy(fontFamily = PlexFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = SoraFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = SoraFamily),
    )
    CompositionLocalProvider(LocalRytmColors provides c, LocalRytmType provides type) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
