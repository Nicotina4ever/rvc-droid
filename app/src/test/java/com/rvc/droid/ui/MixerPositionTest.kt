package com.rvc.droid.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Validates the native mixer position conversion.
 * The engine timeline is in 48 kHz frames; the UI consumes milliseconds.
 */
class MixerPositionTest {
    private fun framesToMs(frames: Long): Long = (frames * 1000L) / 48000L

    @Test
    fun zeroFramesMapsToZeroMs() {
        assertEquals(0L, framesToMs(0L))
    }

    @Test
    fun oneSecondOfFramesMapsToOneThousandMs() {
        assertEquals(1000L, framesToMs(48_000L))
    }

    @Test
    fun partialFramesTruncateWithoutOverflow() {
        // 47_999 frames < 1s, integer math drops the fraction.
        assertEquals(999L, framesToMs(47_999L))
    }
}
