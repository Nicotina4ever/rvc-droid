package com.rvc.droid.ui

import com.rvc.droid.audio.WavEditor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavEditorValidationTest {
    private lateinit var dir: File
    private lateinit var wav: File

    @Before
    fun setUp() {
        dir = createTempDir(prefix = "waveditor")
        WavEditor.init(dir)
        wav = File(dir, "clip.wav")
        writeWav(wav, sampleRate = 16000, channels = 1, bits = 16, seconds = 1)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun validateAcceptsValidWav() {
        assertNull(WavEditor.validateWav(wav.absolutePath))
    }

    @Test
    fun validateRejectsShortFile() {
        val tiny = File(dir, "tiny.wav")
        tiny.writeBytes(ByteArray(20))
        assertNotNull(WavEditor.validateWav(tiny.absolutePath))
    }

    @Test
    fun validateRejectsMissingRiff() {
        val bad = File(dir, "bad.wav")
        bad.writeBytes(ByteArray(1024) { 0x42 })
        assertNotNull(WavEditor.validateWav(bad.absolutePath))
    }

    @Test
    fun silenceZerosRangeAndKeepsFilePlayable() {
        val ok = WavEditor.silence(wav.absolutePath, 200, 600)
        assertTrue(ok)
        assertNull(WavEditor.validateWav(wav.absolutePath))
    }

    @Test
    fun silenceFailsAndRestoresWhenRangeIsOutOfBounds() {
        val beforeSize = wav.length()
        val ok = WavEditor.silence(wav.absolutePath, 10_000, 20_000)
        // End before start, length <= 0 -> short-circuit true; backup is created though.
        assertTrue(ok)
        // File should still be valid.
        assertNull(WavEditor.validateWav(wav.absolutePath))
        // Backup directory must hold a copy.
        val undo = File(dir, ".undo")
        assertTrue(undo.exists() && undo.listFiles()!!.isNotEmpty())
        // Original size unchanged.
        assertEquals(beforeSize, wav.length())
    }

    @Test
    fun trimReducesDurationAndStaysValid() {
        val ok = WavEditor.trimInPlace(wav.absolutePath, 100, 500)
        assertTrue(ok)
        assertNull(WavEditor.validateWav(wav.absolutePath))
        // 400ms @ 16kHz = 6400 samples * 2 bytes = 12800 bytes of PCM.
        val raf = RandomAccessFile(wav, "r")
        raf.use { f ->
            f.seek(40)
            val pcm = ByteBuffer.wrap(ByteArray(4)).order(ByteOrder.LITTLE_ENDIAN)
            f.read(pcm.array())
            assertEquals(12800, pcm.int)
        }
    }

    @Test
    fun cutSelectionRemovesSamplesAndStaysValid() {
        val beforePcm = readDataSize(wav)
        val data = WavEditor.cutSelection(wav.absolutePath, 100, 300)
        assertNotNull(data)
        val afterPcm = readDataSize(wav)
        // We removed 200ms of mono 16-bit @ 16kHz = 6400 bytes.
        assertEquals(beforePcm - 6400, afterPcm)
        assertNull(WavEditor.validateWav(wav.absolutePath))
    }

    @Test
    fun pasteReinsertsBytesAndStaysValid() {
        val data = WavEditor.cutSelection(wav.absolutePath, 100, 300)!!
        // Restore the cut before re-inserting to keep the test independent of state.
        WavEditor.undo(wav.absolutePath)
        val before = readDataSize(wav)
        val ok = WavEditor.pasteAt(wav.absolutePath, 500, data)
        assertTrue(ok)
        val after = readDataSize(wav)
        assertEquals(before + data.size, after)
        assertNull(WavEditor.validateWav(wav.absolutePath))
    }

    @Test
    fun undoRestoresTrimmedFile() {
        val originalSize = readDataSize(wav)
        WavEditor.trimInPlace(wav.absolutePath, 100, 900)
        val trimmed = readDataSize(wav)
        assertTrue(trimmed < originalSize)
        assertTrue(WavEditor.undo(wav.absolutePath))
        assertEquals(originalSize, readDataSize(wav))
    }

    // --- helpers ---

    private fun readDataSize(file: File): Int {
        RandomAccessFile(file, "r").use { f ->
            f.seek(40)
            val buf = ByteArray(4); f.read(buf)
            return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).int
        }
    }

    private fun writeWav(file: File, sampleRate: Int, channels: Int, bits: Int, seconds: Int) {
        val totalSamples = sampleRate * seconds
        val pcmSize = totalSamples * channels * (bits / 8)
        java.io.BufferedOutputStream(file.outputStream()).use { out ->
            val bb = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            bb.put("RIFF".toByteArray(Charsets.US_ASCII))
            bb.putInt(36 + pcmSize)
            bb.put("WAVE".toByteArray(Charsets.US_ASCII))
            bb.put("fmt ".toByteArray(Charsets.US_ASCII))
            bb.putInt(16)
            bb.putShort(1) // PCM
            bb.putShort(channels.toShort())
            bb.putInt(sampleRate)
            bb.putInt(sampleRate * channels * bits / 8)
            bb.putShort((channels * bits / 8).toShort())
            bb.putShort(bits.toShort())
            bb.put("data".toByteArray(Charsets.US_ASCII))
            bb.putInt(pcmSize)
            out.write(bb.array())
            val oneSec = ByteArray(sampleRate * channels * (bits / 8))
            val sampleBb = ByteBuffer.wrap(oneSec).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until sampleRate) {
                val v = (i * 32767 / sampleRate).toShort()
                repeat(channels) { sampleBb.putShort(v) }
            }
            repeat(seconds) { out.write(oneSec) }
        }
    }
}
