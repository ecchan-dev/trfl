package org.trfl.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PredictivePageTest {
    @Test fun missingPreviewNeverExposesWindow() {
        // Includes process restoration, theme changes and navigation before capture.
        for (progress in listOf(0f, 0.1f, 0.5f, 1f)) {
            assertEquals(0f, predictivePageProgress(false, progress))
        }
    }

    @Test fun availablePreviewTracksGestureAndCancellation() {
        for (progress in listOf(0f, 0.5f, 1f, 0.5f, 0f)) {
            assertEquals(progress, predictivePageProgress(true, progress))
        }
        assertEquals(0f, predictivePageProgress(true, -1f))
        assertEquals(1f, predictivePageProgress(true, 2f))
    }
}
