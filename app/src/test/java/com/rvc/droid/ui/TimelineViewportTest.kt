package com.rvc.droid.ui

import com.rvc.droid.ui.components.TimelineViewport
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineViewportTest {
    @Test
    fun zoomOneShowsBaseWindow() {
        assertEquals(60_000L, TimelineViewport().visibleDurationMs)
    }

    @Test
    fun zoomTwoHalvesVisibleWindow() {
        assertEquals(30_000L, TimelineViewport(zoom = 2f).visibleDurationMs)
    }

    @Test
    fun zoomClampedBetweenHalfAndEight() {
        assertEquals(120_000L, TimelineViewport(zoom = 0.1f).visibleDurationMs)
        assertEquals(7_500L, TimelineViewport(zoom = 9f).visibleDurationMs)
    }

    @Test
    fun pxPerMsScalesWithWidth() {
        val v = TimelineViewport(zoom = 2f)
        // 30_000 ms visible at zoom 2x.
        val pxPerMs = v.pxPerMs(600f)
        assertEquals(0.02f, pxPerMs, 0.0001f)
    }
}
