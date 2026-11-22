package io.github.brickystudios.stundenblick

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * Abfahrt: the plan as a departure board. Ink surfaces, Geist for the interface, JetBrains Mono for times, rooms,
 * codes and the minute count. Signal yellow marks only today, now, progress, the active tab and changes.
 * The signature is the subject as a line badge: a block in the subject's colour with dark mono letters.
 * Always dark; the phone's light mode is ignored on purpose.
 */

/** Status colors and the ramp roles Material has no slot for. Yellow is reserved for now and for changes. */
@Immutable
data class Signals(
    val cancelled: Color, val changed: Color, val exam: Color, val extra: Color, val now: Color,
    val muted: Color, val line: Color, val accent: Color, val onAccent: Color, val raised: Color, val nav: Color,
)

private val signal = Color(0xFFF2C230)
private val text1 = Color(0xFFEEF0F5)
private val text2 = Color(0xFFA3A9B8)
private val signals = Signals(
    cancelled = Color(0xFFFF6B6B), changed = signal, exam = text1, extra = signal, now = signal,
    muted = Color(0xFF7A8296), line = text1.copy(alpha = 0.08f), accent = signal, onAccent = Color(0xFF14120A),
    raised = Color(0xFF1A1E28), nav = Color(0xFF07080D),
)

val LocalSignals = staticCompositionLocalOf { signals }

/** dark letters on a subject badge */
val BadgeInk = Color(0xFF0B0D14)

// the primary button is off-white with dark text; the yellow stays for now and for changes
private val scheme = darkColorScheme(
    primary = text1, onPrimary = BadgeInk, primaryContainer = signals.raised, onPrimaryContainer = text1,
    inversePrimary = Color(0xFF7A5C00),
    secondary = text2, onSecondary = BadgeInk, secondaryContainer = Color(0xFF2A2F3B), onSecondaryContainer = text1,
    tertiary = signal, onTertiary = signals.onAccent,
    background = Color(0xFF0B0D14), onBackground = text1, surface = Color(0xFF11141C), onSurface = text1,
    surfaceVariant = signals.raised, onSurfaceVariant = text2, surfaceTint = Color(0xFF11141C),
    surfaceContainerLowest = signals.nav, surfaceContainerLow = Color(0xFF11141C), surfaceContainer = Color(0xFF11141C),
    surfaceContainerHigh = signals.raised, surfaceContainerHighest = Color(0xFF232836),
    outline = Color(0xFF5C6475), outlineVariant = signals.line, inverseSurface = text1, inverseOnSurface = BadgeInk,
    error = signals.cancelled, onError = BadgeInk,
)

private val geist = FontFamily(Font(R.font.geist_400, FontWeight.Normal), Font(R.font.geist_500, FontWeight.Medium))

/** JetBrains Mono: times, rooms, teacher codes, badges and the minute count */
val Mono = FontFamily(
    Font(R.font.jetbrains_mono_400, FontWeight.Normal), Font(R.font.jetbrains_mono_500, FontWeight.Medium),
    Font(R.font.jetbrains_mono_600, FontWeight.SemiBold),
)

private val typography = Typography().run {
    // line heights relative to the size, so a Text with its own fontSize never inherits a fixed 24sp line
    fun TextStyle.g(size: Int, line: Double, weight: FontWeight? = fontWeight) =
        copy(fontFamily = geist, fontSize = size.sp, lineHeight = line.em, fontWeight = weight, letterSpacing = 0.sp, fontFeatureSettings = "tnum")
    Typography(
        displayLarge.g(44, 1.1), displayMedium.g(36, 1.15), displaySmall.g(32, 1.15),
        headlineLarge.g(30, 1.2), headlineMedium.g(28, 1.2), headlineSmall.g(24, 1.25),
        titleLarge.g(28, 1.2, FontWeight.Medium).copy(letterSpacing = (-0.01).em), titleMedium.g(16, 1.4), titleSmall.g(14, 1.4),
        bodyLarge.g(15, 1.45), bodyMedium.g(14, 1.43), bodySmall.g(13, 1.38),
        labelLarge.g(14, 1.43), labelMedium.g(12, 1.33), labelSmall.g(11, 1.45),
    )
}

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(6.dp), medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp), extraLarge = RoundedCornerShape(16.dp),
)

/** buttons and text fields */
val ControlShape = RoundedCornerShape(8.dp)

/** the one easing curve, for everything that moves */
val Ease = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

@Composable
fun StundenblickTheme(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalSignals provides signals) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
    }

/** a screen title, 28sp */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier) = Text(text, modifier, style = MaterialTheme.typography.titleLarge)

/** the small grey line above a card's content */
@Composable
fun Label(text: String, modifier: Modifier = Modifier) = Text(text, modifier, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** A card: one surface step above the page, a hairline, 12 corners, 16 inside. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Column(
        Modifier.fillMaxWidth().clip(shape).then(modifier).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, LocalSignals.current.line, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** Untis colors are "#ffff80"-style; null or broken means none. */
fun untisColor(hex: String?): Color? = runCatching { hex?.let { Color(android.graphics.Color.parseColor(it)) } }.getOrNull()

/** WCAG relative luminance */
internal fun luminance(c: Color): Double {
    fun lin(v: Float) = if (v <= 0.03928f) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    return 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
}

/** A subject colour as badge fill on ink: lightened toward white until the dark badge letters reach 4.5:1. */
fun readableOnDark(c: Color): Color {
    var x = c.copy(alpha = 1f)
    while ((luminance(x) + 0.05) / (luminance(BadgeInk) + 0.05) < 4.5)
        x = Color(x.red + (1 - x.red) * 0.1f, x.green + (1 - x.green) * 0.1f, x.blue + (1 - x.blue) * 0.1f)
    return x
}

/** for schools whose subjects carry no colour: one of these per subject, the same every time */
private val subjectFallback = listOf(0xFF6AA0FF, 0xFFFF7B7F, 0xFFFFBE5C, 0xFF74C97A, 0xFFB392FF, 0xFFC9A27E, 0xFFE8D35A, 0xFFA8B0BD, 0xFFFF8FC7, 0xFF6FD0F0).map { Color(it) }

/**
 * A subject's badge colour from the master data. Not the period's own backColor, which real schools fill with
 * one lesson colour for every period (the yellow #f49f25).
 */
fun Master?.subjectColor(id: Long): Color =
    readableOnDark(untisColor(this?.find(Kind.SUBJECT, id)?.color) ?: subjectFallback[id.mod(subjectFallback.size.toLong()).toInt()])

/** The signature: a subject as a line badge, like the line number on a departure board. */
@Composable
fun SubjectBadge(name: String, color: Color, modifier: Modifier = Modifier, cancelled: Boolean = false, small: Boolean = false) {
    val s = LocalSignals.current
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier.height(if (small) 20.dp else 24.dp).widthIn(min = if (small) 0.dp else 36.dp)
            .then(if (cancelled) Modifier.border(1.5.dp, s.cancelled, shape) else Modifier.background(color, shape))
            .padding(horizontal = if (small) 4.dp else 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = if (small) 12.sp else 13.sp, lineHeight = if (small) 12.sp else 13.sp,
            color = if (cancelled) s.cancelled else BadgeInk, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
            textDecoration = if (cancelled) TextDecoration.LineThrough else null,
        )
    }
}

/** the badge of a subject by id, or nothing */
@Composable
fun SubjectBadge(id: Long?, master: Master?, modifier: Modifier = Modifier, cancelled: Boolean = false, small: Boolean = false) {
    if (id == null) return
    SubjectBadge(master?.find(Kind.SUBJECT, id)?.name ?: "?", master.subjectColor(id), modifier, cancelled, small)
}

/** Small label for a change: "Entfall", "Vertretung", "Klausur" … */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(text, color = color, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
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
