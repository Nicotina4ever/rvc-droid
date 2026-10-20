package com.rvc.droid.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Basic unit tests for the immutable {@link AudioBuffer} data class.
 * These tests verify that the copy/slice/append helpers preserve sample data
 * and metadata correctly.
 */
class AudioBufferTest {

    private val sampleRate = 44100
    private val bitDepth = 16
    private val channels = 1
    private val format = AudioFormat.WAV

    @Test
    fun `copy creates identical buffer`() {
        val original = AudioBuffer(
            samples = floatArrayOf(0.1f, -0.2f, 0.3f),
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            channels = channels,
            format = format
        )
        val copy = original.copy()
        assertArrayEquals(original.samples, copy.samples, 0.0f)
        assertEquals(original.sampleRate, copy.sampleRate)
        assertEquals(original.bitDepth, copy.bitDepth)
        assertEquals(original.channels, copy.channels)
        assertEquals(original.format, copy.format)
    }

    @Test
    fun `slice returns correct subset`() {
        val buffer = AudioBuffer(
            samples = floatArrayOf(0.0f, 0.1f, 0.2f, 0.3f, 0.4f),
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            channels = channels,
            format = format
        )
        val sliced = buffer.slice(1, 4) // should contain indices 1,2,3
        assertArrayEquals(floatArrayOf(0.1f, 0.2f, 0.3f), sliced.samples, 0.0f)
        assertEquals(sampleRate, sliced.sampleRate)
    }

    @Test
    fun `append concatenates samples`() {
        val a = AudioBuffer(
            samples = floatArrayOf(0.1f, 0.2f),
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            channels = channels,
            format = format
        )
        val b = AudioBuffer(
            samples = floatArrayOf(0.3f, 0.4f),
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            channels = channels,
            format = format
        )
        val combined = a.append(b)
        assertArrayEquals(floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f), combined.samples, 0.0f)
        assertEquals(sampleRate, combined.sampleRate)
    }
}
