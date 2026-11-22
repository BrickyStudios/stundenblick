package io.github.brickystudios.stundenblick

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Hallmark · restyle · theme: Broadside (deal 1/3, seed stundenplan-app-sch-ler-untis-client-cd6c·df088994·2026-11-15·r0) · shape: app screen
 * The Vertretungsplan on the school notice board: buff stock, near-black ink, Archivo Narrow small print, Six Caps titles, square corners.
 * waive: the theme allows one colour; status needs two (vermilion = Entfall/Jetzt, ochre = Vertretung/Raum/Zusatz) and subject stripes keep the school's colours.
 */

/** Status colors: the point of the app is that a change jumps out, so these are reserved for changes only. */
@Immutable
data class Signals(val cancelled: Color, val changed: Color, val exam: Color, val extra: Color, val now: Color, val muted: Color, val line: Color)

val LocalSignals = staticCompositionLocalOf { lightSignals }

// light = Broadside "bill", dark = Broadside "inked"; values are the theme's oklch tokens in sRGB
private val lightSignals = Signals(
    cancelled = Color(0xFFC12B09), changed = Color(0xFF8B5500), exam = Color(0xFF15100E), extra = Color(0xFF8B5500),
    now = Color(0xFFC12B09), muted = Color(0xFF584E45), line = Color(0xFFCAC0AC),
)
private val darkSignals = Signals(
    cancelled = Color(0xFFE55033), changed = Color(0xFFD09945), exam = Color(0xFFECE4D0), extra = Color(0xFFD09945),
    now = Color(0xFFE55033), muted = Color(0xFFA09789), line = Color(0xFF302A24),
)

private val light = lightColorScheme(
    primary = Color(0xFF15100E), onPrimary = Color(0xFFF5F0E3), primaryContainer = Color(0xFFD8CDB4), onPrimaryContainer = Color(0xFF15100E),
    secondary = Color(0xFF332C29), onSecondary = Color(0xFFF5F0E3), tertiary = Color(0xFF8B5500), onTertiary = Color(0xFFF5F0E3),
    background = Color(0xFFECE4D0), onBackground = Color(0xFF15100E), surface = Color(0xFFF4EEE0), onSurface = Color(0xFF15100E),
    surfaceVariant = Color(0xFFE2D8C2), onSurfaceVariant = Color(0xFF584E45), surfaceTint = Color(0xFFF4EEE0),
    surfaceContainerLowest = Color(0xFFF4EEE0), surfaceContainerLow = Color(0xFFF4EEE0), surfaceContainer = Color(0xFFF4EEE0),
    surfaceContainerHigh = Color(0xFFECE4D0), surfaceContainerHighest = Color(0xFFE2D8C2),
    outline = Color(0xFFB7AD9A), outlineVariant = Color(0xFFCAC0AC), inverseSurface = Color(0xFF15100E), inverseOnSurface = Color(0xFFECE4D0),
    secondaryContainer = Color(0xFFD8CDB4), onSecondaryContainer = Color(0xFF15100E), error = Color(0xFFC12B09), onError = Color(0xFFF5F0E3),
)
private val dark = darkColorScheme(
    primary = Color(0xFFECE4D0), onPrimary = Color(0xFF130E0B), primaryContainer = Color(0xFF3B342D), onPrimaryContainer = Color(0xFFECE4D0),
    secondary = Color(0xFFCCC3B2), onSecondary = Color(0xFF130E0B), tertiary = Color(0xFFD09945), onTertiary = Color(0xFF130E0B),
    background = Color(0xFF130E0B), onBackground = Color(0xFFECE4D0), surface = Color(0xFF1D1713), onSurface = Color(0xFFECE4D0),
    surfaceVariant = Color(0xFF27201B), onSurfaceVariant = Color(0xFFA09789), surfaceTint = Color(0xFF1D1713),
    surfaceContainerLowest = Color(0xFF130E0B), surfaceContainerLow = Color(0xFF1D1713), surfaceContainer = Color(0xFF1D1713),
    surfaceContainerHigh = Color(0xFF27201B), surfaceContainerHighest = Color(0xFF3B342D),
    outline = Color(0xFF534C43), outlineVariant = Color(0xFF302A24), inverseSurface = Color(0xFFECE4D0), inverseOnSurface = Color(0xFF130E0B),
    secondaryContainer = Color(0xFF3B342D), onSecondaryContainer = Color(0xFFECE4D0), error = Color(0xFFE55033), onError = Color(0xFF130E0B),
)

private val narrow = FontFamily(
    Font(R.font.archivo_narrow_400, FontWeight.Normal), Font(R.font.archivo_narrow_500, FontWeight.Medium),
    Font(R.font.archivo_narrow_600, FontWeight.SemiBold),
)

/** Six Caps: tall poster capitals, only for screen titles; never for labels, buttons or data */
val Poster = FontFamily(Font(R.font.six_caps))

/** a screen title in poster capitals, fitted tight like a bill's head line */
@Composable
fun PosterTitle(text: String, modifier: Modifier = Modifier, size: Int = 56) =
    Text(text.uppercase(), modifier, fontFamily = Poster, fontSize = size.sp, lineHeight = (size * 0.9).sp, letterSpacing = 0.02.em, maxLines = 2)

private val typography = Typography().run {
    fun TextStyle.n() = copy(fontFamily = narrow)
    Typography(
        displayLarge.n(), displayMedium.n(), displaySmall.n(), headlineLarge.n(), headlineMedium.n(), headlineSmall.n(),
        titleLarge.copy(fontFamily = Poster, fontSize = 38.sp, lineHeight = 38.sp, letterSpacing = 0.02.em),
        titleMedium.n(), titleSmall.n(), bodyLarge.n(), bodyMedium.n(), bodySmall.n(), labelLarge.n(), labelMedium.n(), labelSmall.n(),
    )
}

/** square everywhere: a printed bill has no rounded corners */
private val square = RoundedCornerShape(0.dp).let { Shapes(it, it, it, it, it) }

@Composable
fun StundenblickTheme(content: @Composable () -> Unit) {
    val darkMode = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(LocalSignals provides if (darkMode) darkSignals else lightSignals) {
        MaterialTheme(colorScheme = if (darkMode) dark else light, typography = typography, shapes = square, content = content)
    }
}

/** Untis colors are "#ffff80"-style; null or broken means no stripe. */
fun untisColor(hex: String?): Color? = runCatching { hex?.let { Color(android.graphics.Color.parseColor(it)) } }.getOrNull()

/** for schools whose subjects carry no colour: one of these per subject, the same every time */
private val subjectFallback = listOf(0xFFB5502E, 0xFF3D6A9A, 0xFF7B5C9C, 0xFF5B7A3A, 0xFFA87A22, 0xFF9C4762, 0xFF6E6250, 0xFF46577A).map { Color(it) }

/**
 * The stripe colour of a period: its subject's colour from the master data. Not the period's own backColor,
 * which real schools fill with one lesson colour for every period (the yellow #f49f25).
 */
fun Master?.subjectColor(p: Period): Color? {
    val id = p.subjectId ?: return null
    return untisColor(this?.find(Kind.SUBJECT, id)?.color) ?: subjectFallback[id.mod(subjectFallback.size.toLong()).toInt()]
}

/** Small label for a change: "Entfall", "Vertretung", "Klausur" … */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.background(color.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** The tags a period earns, most important first. */
@Composable
fun tagsFor(p: Period): List<Pair<String, Color>> {
    val s = LocalSignals.current
    return buildList {
        if (p.cancelled) add("Entfall" to s.cancelled)
        if (p.isExam) add((p.exam ?: "Klausur") to s.exam)
        if (p.additional) add("Zusatzstunde" to s.extra)
        if (p.of(Kind.TEACHER).any { it.changed }) add("Vertretung" to s.changed)
        if (p.of(Kind.ROOM).any { it.changed }) add("Raumänderung" to s.changed)
        if (p.irregular && isEmpty()) add("Geändert" to s.changed)
    }
}
