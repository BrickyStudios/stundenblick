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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Status colors: the point of the app is that a change jumps out, so these are reserved for changes only. */
@Immutable
data class Signals(val cancelled: Color, val changed: Color, val exam: Color, val extra: Color, val now: Color, val muted: Color, val line: Color)

val LocalSignals = staticCompositionLocalOf { lightSignals }

private val lightSignals = Signals(
    cancelled = Color(0xFFD23B3B), changed = Color(0xFFD9730A), exam = Color(0xFF4352E0), extra = Color(0xFF1C7FD6),
    now = Color(0xFFD23B3B), muted = Color(0xFF6B6F7B), line = Color(0xFFE3E0D8),
)
private val darkSignals = Signals(
    cancelled = Color(0xFFFF6B6B), changed = Color(0xFFFFB04D), exam = Color(0xFF8E99FF), extra = Color(0xFF6CB8FF),
    now = Color(0xFFFF6B6B), muted = Color(0xFF9A9EAB), line = Color(0xFF2A2E38),
)

private val light = lightColorScheme(
    primary = Color(0xFF4352E0), onPrimary = Color.White, primaryContainer = Color(0xFFE2E5FF), onPrimaryContainer = Color(0xFF1A2380),
    background = Color(0xFFF5F4F0), onBackground = Color(0xFF17181D), surface = Color(0xFFFFFFFF), onSurface = Color(0xFF17181D),
    surfaceVariant = Color(0xFFECEAE4), onSurfaceVariant = Color(0xFF5B5F6A), surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0EEE9), surfaceContainerLow = Color(0xFFFAF9F6), outline = Color(0xFFD5D2C9), outlineVariant = Color(0xFFE3E0D8),
    secondaryContainer = Color(0xFFE2E5FF), onSecondaryContainer = Color(0xFF1A2380), error = Color(0xFFD23B3B),
)
private val dark = darkColorScheme(
    primary = Color(0xFF8E99FF), onPrimary = Color(0xFF0E1250), primaryContainer = Color(0xFF2A3290), onPrimaryContainer = Color(0xFFE2E5FF),
    background = Color(0xFF0F1116), onBackground = Color(0xFFECEDF2), surface = Color(0xFF171A21), onSurface = Color(0xFFECEDF2),
    surfaceVariant = Color(0xFF20242D), onSurfaceVariant = Color(0xFFA3A7B4), surfaceContainer = Color(0xFF171A21),
    surfaceContainerHigh = Color(0xFF20242D), surfaceContainerLow = Color(0xFF13151B), outline = Color(0xFF353A46), outlineVariant = Color(0xFF2A2E38),
    secondaryContainer = Color(0xFF2A3290), onSecondaryContainer = Color(0xFFE2E5FF), error = Color(0xFFFF6B6B),
)

@Composable
fun StundenblickTheme(content: @Composable () -> Unit) {
    val darkMode = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(LocalSignals provides if (darkMode) darkSignals else lightSignals) {
        MaterialTheme(colorScheme = if (darkMode) dark else light, content = content)
    }
}

/** Untis colors are "#ffff80"-style; null or broken means no stripe. */
fun untisColor(hex: String?): Color? = runCatching { hex?.let { Color(android.graphics.Color.parseColor(it)) } }.getOrNull()

/** Small label for a change: "Entfall", "Vertretung", "Klausur" … */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
