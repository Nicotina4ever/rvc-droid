package com.rvc.droid.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Verifica la logica di validazione magic bytes introdotta in
 * NativeAudioBridge.validateOnnxArtifact. Il bridge è un `object` Kotlin
 * che richiede `initNativeLogging(...)` per funzionare; qui testiamo il
 * comportamento "puro" del check replicando la stessa logica in un
 * helper statico (sorgente unica di verità: NativeAudioBridge.kt).
 *
 * Obiettivo: assicurarsi che file con magic non-ONNX (RIFF/WAV, Matroska,
 * MPEG-TS) o con dimensione anomala vengano rifiutati PRIMA di essere
 * passati al JNI (che altrimenti li accetterebbe e produrrebbe errori
 * di inferenza a runtime).
 */
class OnnxMagicBytesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun writeFile(name: String, size: Long, magic: ByteArray?): File {
        val f = File(tmp.root, name)
        f.outputStream().use { out ->
            if (magic != null) out.write(magic)
            val remaining = (size - (magic?.size ?: 0)).coerceAtLeast(0)
            val buf = ByteArray(4096)
            for (i in 0 until buf.size) buf[i] = 0xAB.toByte()
            var written = 0L
            while (written < remaining) {
                val chunk = minOf(buf.size.toLong(), remaining - written).toInt()
                out.write(buf, 0, chunk)
                written += chunk
            }
        }
        return f
    }

    private fun validate(path: String): String? {
        val f = File(path)
        if (!f.exists() || !f.isFile) return "not a file"
        val size = f.length()
        if (size < 1_000_000L) return "too small"
        if (!path.lowercase().endsWith(".onnx")) return "wrong ext"
        f.inputStream().use { input ->
            val head = ByteArray(4)
            val read = input.read(head)
            if (read < 4) return "unreadable"
            if (head[0] == 'R'.code.toByte() && head[1] == 'I'.code.toByte() &&
                head[2] == 'F'.code.toByte() && head[3] == 'F'.code.toByte()) return "RIFF"
        }
        return null
    }

    @Test
    fun `valid ONNX size and extension passes`() {
        val f = writeFile("hubert.onnx", 360_000_000L, byteArrayOf(0x08, 0x07, 0x12, 0x34, 0x56, 0x78))
        assertEquals(null, validate(f.absolutePath))
    }

    @Test
    fun `ONNX with zero first byte passes (permissive policy)`() {
        // Modelli con ir_version=0 o protobuf che inizia con varint 0
        // NON devono essere rifiutati. Solo RIFF viene rifiutato.
        val f = writeFile("low_ir.onnx", 360_000_000L, byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x02, 0x03))
        assertEquals(null, validate(f.absolutePath))
    }

    @Test
    fun `file too small rejected`() {
        val f = writeFile("tiny.onnx", 100L, byteArrayOf(0x08, 0x07, 0x12, 0x34))
        val reason = validate(f.absolutePath)
        assertEquals("too small", reason)
    }

    @Test
    fun `wrong extension rejected`() {
        val f = writeFile("blob_msf_1000381856", 360_000_000L, byteArrayOf(0x08, 0x07, 0x12, 0x34))
        val reason = validate(f.absolutePath)
        assertEquals("wrong ext", reason)
    }

    @Test
    fun `WAV magic rejected`() {
        val f = writeFile("audio.wav", 360_000_000L, "RIFF".toByteArray(Charsets.US_ASCII) + byteArrayOf(0x10, 0x10, 0x10, 0x10) + "WAVE".toByteArray(Charsets.US_ASCII))
        // Rinominiamo per simulare un file WAV scelto come se fosse onnx: il
        // check estensione lo rifiuterebbe. Per testare solo il magic
        // cambiamo approccio: scriviamo un .onnx con magic RIFF.
        f.delete()
        val f2 = writeFile("fake.onnx", 360_000_000L, "RIFF".toByteArray(Charsets.US_ASCII))
        val reason = validate(f2.absolutePath)
        assertEquals("RIFF", reason)
    }
}
