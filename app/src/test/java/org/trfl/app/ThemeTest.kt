package org.trfl.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class ThemeTest {
    @Test fun darkPalettesAreDistinctAndReadable() {
        assertNotEquals(chanColors(true, true).background, chanColors(false, true).background)
        for (safe in listOf(true, false)) {
            val colors = chanColors(safe, true)
            assertTrue(colors.background.luminance() < 0.05f)
            for ((text, background) in listOf(colors.onSurface to colors.surface, colors.onSurfaceVariant to colors.surfaceContainerLow, colors.primary to colors.surfaceContainerLow, colors.onPrimary to colors.primary)) {
                val contrast = (maxOf(text.luminance(), background.luminance()) + 0.05f) / (minOf(text.luminance(), background.luminance()) + 0.05f)
                assertTrue("Text contrast must meet WCAG AA: $contrast", contrast >= 4.5f)
            }
        }
    }

    @Test fun sfwUsesYotsubaBlue() {
        val colors = chanColors(true)
        assertEquals(Color(0xFFEEF2FF), colors.background)
        assertEquals(Color(0xFFD6DAF0), colors.surfaceContainerLow)
        assertEquals(colors.background, colors.surface)
    }
    @Test fun nsfwUsesYotsubaCream() {
        val colors = chanColors(false)
        assertEquals(Color(0xFFFFFFEE), colors.background)
        assertEquals(Color(0xFFF0E0D6), colors.surfaceContainerLow)
        assertNotEquals(chanColors(true).background, colors.background)
    }
}
