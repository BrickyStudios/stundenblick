package io.github.brickystudios.stundenblick

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeTest {
    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test fun darkSubjectColoursAreLightenedUntilTheLettersRead() {
        // dark school colours and the old fallback tones: all must carry dark badge letters at 4.5:1
        listOf(0xFF3D6A9A, 0xFFB5502E, 0xFF000000, 0xFF5B7A3A, 0xFF7B5C9C).forEach {
            assertTrue(contrast(readableOnDark(Color(it)), BadgeInk) >= 4.5)
        }
    }

    @Test fun lightColoursStayAsTheSchoolSetThem() {
        listOf(0xFF6AA0FF, 0xFFFF7B7F, 0xFF9EC5FE, 0xFFFFFF80).forEach { assertEquals(Color(it), readableOnDark(Color(it))) }
    }
}
