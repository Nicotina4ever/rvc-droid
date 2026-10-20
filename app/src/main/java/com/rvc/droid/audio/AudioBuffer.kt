package com.rvc.droid.audio

/**
 * Immutable data class representing an audio buffer.
 */
data class AudioBuffer(
    val samples: FloatArray,
    val sampleRate: Int,
    val bitDepth: Int,
    val channels: Int,
    val format: AudioFormat
) {
    /**
     * Returns a new AudioBuffer containing a slice of the samples.
     * @param start inclusive start index
     * @param end exclusive end index
     */
    fun slice(start: Int, end: Int): AudioBuffer {
        val slicedSamples = samples.sliceArray(start until end)
        return copy(samples = slicedSamples)
    }

    /**
     * Returns a new AudioBuffer with the other buffer's samples appended to this one.
     */
    fun append(other: AudioBuffer): AudioBuffer {
        require(sampleRate == other.sampleRate) { "Sample rate mismatch: $sampleRate vs ${other.sampleRate}" }
        require(channels == other.channels) { "Channels mismatch: $channels vs ${other.channels}" }
        
        val combinedSamples = FloatArray(samples.size + other.samples.size)
        System.arraycopy(samples, 0, combinedSamples, 0, samples.size)
        System.arraycopy(other.samples, 0, combinedSamples, samples.size, other.samples.size)
        
        return copy(samples = combinedSamples)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioBuffer) return false

        if (!samples.contentEquals(other.samples)) return false
        if (sampleRate != other.sampleRate) return false
        if (bitDepth != other.bitDepth) return false
        if (channels != other.channels) return false
        if (format != other.format) return false

        return true
    }

    override fun hashCode(): Int {
        var result = samples.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + bitDepth
        result = 31 * result + channels
        result = 31 * result + format.hashCode()
        return result
    }
}
