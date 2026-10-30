package org.trfl.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class ChanThemeTest {
    @Test fun savedSelectionIsStableAndUnknownValuesFallBack() {
        ChanTheme.entries.forEach { assertEquals(it, ChanTheme.fromSaved(it.name)) }
        assertEquals(ChanTheme.Automatic, ChanTheme.fromSaved(null))
        assertEquals(ChanTheme.Automatic, ChanTheme.fromSaved("unknown"))
    }

    @Test fun automaticRetainsBoardPalettes() {
        for (safe in listOf(true, false)) for (dark in listOf(true, false)) {
            assertEquals(chanColors(safe, dark).toString(), chanColors(ChanTheme.Automatic, safe, dark).toString())
        }
    }

    @Test fun explicitThemesIgnoreBoardAndKeepCardsDistinct() {
        ChanTheme.entries.filter { it != ChanTheme.Automatic }.forEach { theme ->
            for (dark in listOf(true, false)) {
                val colors = chanColors(theme, true, dark)
                assertEquals(colors.toString(), chanColors(theme, false, dark).toString())
                assertNotEquals(colors.background, colors.surfaceContainerHigh)
                assertNotEquals(colors.onBackground, colors.background)
            }
        }
    }

    @Test fun classicBackgroundsAndTomorrowDarkMode() {
        assertEquals(Color(0xFFFFFFEE), chanColors(ChanTheme.Futaba, true, false).background)
        assertEquals(Color(0xFFEEF2FF), chanColors(ChanTheme.Burichan, true, false).background)
        assertEquals(Color.White, chanColors(ChanTheme.Photon, true, false).background)
        assertEquals(Color(0xFF1D1F21), chanColors(ChanTheme.Tomorrow, true, false).background)
    }
}
