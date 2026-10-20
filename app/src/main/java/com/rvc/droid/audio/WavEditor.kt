package com.rvc.droid.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.abs
import kotlin.math.pow

object WavEditor {

    private val historyMap = mutableMapOf<String, MutableList<String>>() // Original Path -> List of backup paths
    private val redoMap = mutableMapOf<String, MutableList<String>>()
    private const val MAX_HISTORY = 10
    private var undoDir: File? = null

    fun init(cacheDir: File) {
        undoDir = File(cacheDir, ".undo").also { it.mkdirs() }
    }

    private fun createBackup(path: String) {
        val originalFile = File(path)
        if (!originalFile.exists() || undoDir == null) return

        val backupName = "undo_${UUID.randomUUID()}.wav"
        val backupFile = File(undoDir, backupName)
        originalFile.copyTo(backupFile, overwrite = true)

        val history = historyMap.getOrPut(path) { mutableListOf() }
        history.add(backupFile.absolutePath)
        
        // Clear redo for this file since we made a new change
        redoMap[path]?.forEach { File(it).delete() }
        redoMap.remove(path)

        if (history.size > MAX_HISTORY) {
            val old = history.removeAt(0)
            File(old).delete()
        }
    }

    fun canUndo(path: String): Boolean = (historyMap[path]?.size ?: 0) > 0
    fun canRedo(path: String): Boolean = (redoMap[path]?.size ?: 0) > 0

    fun undo(path: String): Boolean {
        val history = historyMap[path] ?: return false
        if (history.isEmpty()) return false

        val currentFile = File(path)
        val redoDir = undoDir ?: return false
        
        // Move current state to redo
        val redoName = "redo_${UUID.randomUUID()}.wav"
        val redoFile = File(redoDir, redoName)
        currentFile.copyTo(redoFile, overwrite = true)
        val redos = redoMap.getOrPut(path) { mutableListOf() }
        redos.add(redoFile.absolutePath)

        // Restore from history
        val backupPath = history.removeAt(history.size - 1)
        val backupFile = File(backupPath)
        backupFile.copyTo(currentFile, overwrite = true)
        backupFile.delete()
        
        return true
    }

    fun redo(path: String): Boolean {
        val redos = redoMap[path] ?: return false
        if (redos.isEmpty()) return false

        val currentFile = File(path)
        val historyDir = undoDir ?: return false

        // Move current state to history (so we can undo the redo)
        val undoName = "undo_${UUID.randomUUID()}.wav"
        val undoFile = File(historyDir, undoName)
        currentFile.copyTo(undoFile, overwrite = true)
        val history = historyMap.getOrPut(path) { mutableListOf() }
        history.add(undoFile.absolutePath)

        // Restore from redo
        val redoPath = redos.removeAt(redos.size - 1)
        val redoFile = File(redoPath)
        redoFile.copyTo(currentFile, overwrite = true)
        redoFile.delete()

        return true
    }

    fun silence(path: String, startMs: Long, endMs: Long): Boolean {
        createBackup(path)
        val file = File(path)
        if (!file.exists()) return false
        try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(0)
                val riff = ByteArray(4); raf.read(riff)
                if (String(riff) != "RIFF") return false
                raf.seek(22)
                val channels = readShortLE(raf)
                val sampleRate = readIntLE(raf)
                raf.seek(34)
                val bitsPerSample = readShortLE(raf)
                val dataOffset = findDataOffset(raf)
                if (dataOffset < 0) return false

                val bytesPerSample = bitsPerSample / 8
                val startByte = (startMs * sampleRate * bytesPerSample * channels) / 1000
                val endByte = (endMs * sampleRate * bytesPerSample * channels) / 1000
                val startPos = dataOffset + (startByte - (startByte % (bytesPerSample * channels)))
                val endPos = dataOffset + (endByte - (endByte % (bytesPerSample * channels)))
                val length = (endPos - startPos).coerceAtMost(raf.length() - startPos)
                if (length <= 0) return true
                raf.seek(startPos)
                val zeroBuf = ByteArray(8192)
                var written = 0L
                while (written < length) {
                    val toWrite = (length - written).coerceAtMost(zeroBuf.size.toLong()).toInt()
                    raf.write(zeroBuf, 0, toWrite)
                    written += toWrite
                }
            }
            val reason = validateWav(path)
            if (reason != null) { restoreLastBackup(path); return false }
            return true
        } catch (e: Exception) { e.printStackTrace() }
        restoreLastBackup(path)
        return false
    }

    fun trimInPlace(path: String, startMs: Long, endMs: Long): Boolean {
        val dir = undoDir ?: return false
        createBackup(path)
        val tempFile = File(dir, "trim_${UUID.randomUUID()}.wav")
        if (trim(path, tempFile.absolutePath, startMs, endMs)) {
            val original = File(path)
            tempFile.copyTo(original, overwrite = true)
            tempFile.delete()
            val reason = validateWav(path)
            if (reason != null) { restoreLastBackup(path); return false }
            return true
        }
        return false
    }

    fun trim(inPath: String, outPath: String, startMs: Long, endMs: Long): Boolean {
        val inFile = File(inPath)
        if (!inFile.exists()) return false
        try {
            RandomAccessFile(inFile, "r").use { raf ->
                raf.seek(22)
                val channels = readShortLE(raf)
                val sampleRate = readIntLE(raf)
                raf.seek(34)
                val bitsPerSample = readShortLE(raf)
                val dataOffset = findDataOffset(raf)
                if (dataOffset < 0) return false
                
                val bytesPerSample = bitsPerSample / 8
                val startByte = (startMs * sampleRate * bytesPerSample * channels) / 1000
                val endByte = (endMs * sampleRate * bytesPerSample * channels) / 1000
                val startPos = dataOffset + (startByte - (startByte % (bytesPerSample * channels)))
                val endPos = dataOffset + (endByte - (endByte % (bytesPerSample * channels)))
                val length = (endPos - startPos)
                if (length <= 0) return false

                File(outPath).delete()
                RandomAccessFile(File(outPath), "rw").use { outRaf ->
                    val header = ByteArray(44)
                    raf.seek(0); raf.read(header); outRaf.write(header)
                    updateWavHeader(outRaf, length.toInt(), sampleRate, channels, bitsPerSample)
                    raf.seek(startPos)
                    val buf = ByteArray(16384)
                    var copied = 0L
                    while (copied < length) {
                        val toRead = (length - copied).coerceAtMost(buf.size.toLong()).toInt()
                        raf.read(buf, 0, toRead)
                        outRaf.write(buf, 0, toRead)
                        copied += toRead
                    }
                }
                return true
            }
        } catch (e: Exception) { e.printStackTrace() }
        return false
    }

    data class AudioInfo(
        val channels: Int, val sampleRate: Int, val bitsPerSample: Int,
        val dataSize: Long, val durationMs: Long
    )

    fun getAudioInfo(path: String): AudioInfo? {
        val file = File(path)
        if (!file.exists()) return null
        try {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(22)
                val channels = readShortLE(raf).toInt()
                val sampleRate = readIntLE(raf)
                raf.seek(34)
                val bitsPerSample = readShortLE(raf).toInt()
                val dataOffset = findDataOffset(raf)
                if (dataOffset < 0) return null
                val dataSize = raf.length() - dataOffset
                val bytesPerSample = bitsPerSample / 8
                val totalFrames = dataSize / (bytesPerSample * channels)
                val durationMs = (totalFrames * 1000L) / sampleRate
                return AudioInfo(channels, sampleRate, bitsPerSample, dataSize, durationMs)
            }
        } catch (e: Exception) { e.printStackTrace() }
        return null
    }

    fun copySelection(path: String, startMs: Long, endMs: Long): ByteArray? {
        val file = File(path)
        if (!file.exists()) return null
        try {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(22)
                val channels = readShortLE(raf).toInt()
                val sampleRate = readIntLE(raf)
                raf.seek(34)
                val bitsPerSample = readShortLE(raf).toInt()
                val dataOffset = findDataOffset(raf)
                if (dataOffset < 0) return null
                val bytesPerSample = bitsPerSample / 8
                val startByte = (startMs * sampleRate * bytesPerSample * channels) / 1000
                val endByte = (endMs * sampleRate * bytesPerSample * channels) / 1000
                val startPos = dataOffset + (startByte - (startByte % (bytesPerSample * channels)))
                val endPos = dataOffset + (endByte - (endByte % (bytesPerSample * channels)))
                val length = (endPos - startPos).toInt().coerceAtLeast(0)
                if (length <= 0) return null
                raf.seek(startPos)
                val buf = ByteArray(length)
                raf.readFully(buf)
                return buf
            }
        } catch (e: Exception) { e.printStackTrace() }
        return null
    }

    fun cutSelection(path: String, startMs: Long, endMs: Long): ByteArray? {
        val data = copySelection(path, startMs, endMs) ?: return null
        
        createBackup(path)
        val file = File(path)
        try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(22)
                val channels = readShortLE(raf).toInt()
                val sampleRate = readIntLE(raf)
                raf.seek(34)
                val bitsPerSample = readShortLE(raf).toInt()
                val dataOffset = findDataOffset(raf)
                if (dataOffset < 0) return null
                
                val bytesPerSample = bitsPerSample / 8
                val startByte = (startMs * sampleRate * bytesPerSample * channels) / 1000
                val endByte = (endMs * sampleRate * bytesPerSample * channels) / 1000
                val startPos = dataOffset + (startByte - (startByte % (bytesPerSample * channels)))
                val endPos = dataOffset + (endByte - (endByte % (bytesPerSample * channels)))
                val lengthToDelete = (endPos - startPos)
                if (lengthToDelete <= 0) return data

                // Move remaining data forward
                val remainingLength = raf.length() - endPos
                if (remainingLength > 0) {
                    val buf = ByteArray(65536)
                    var moved = 0L
                    while (moved < remainingLength) {
                        val toRead = (remainingLength - moved).coerceAtMost(buf.size.toLong()).toInt()
                        raf.seek(endPos + moved)
                        raf.readFully(buf, 0, toRead)
                        raf.seek(startPos + moved)
                        raf.write(buf, 0, toRead)
                        moved += toRead
                    }
                }
                
                // Truncate file
                raf.setLength(raf.length() - lengthToDelete)
                
                // Update header
                val totalDataSize = (raf.length() - dataOffset).toInt()
                updateWavHeader(raf, totalDataSize, sampleRate, channels.toShort(), bitsPerSample.toShort())
            }
            val reason = validateWav(path)
            if (reason != null) { restoreLastBackup(path); return null }
            return data
        } catch (e: Exception) { e.printStackTrace() }
        restoreLastBackup(path)
        return null
    }

    fun pasteAt(path: String, insertMs: Long, clipboardData: ByteArray): Boolean {
        val dir = undoDir ?: return false
        createBackup(path)
        val file = File(path)
        if (!file.exists() || clipboardData.isEmpty()) return false
        val tempFile = File(dir, "paste_${UUID.randomUUID()}.wav")
        try {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(22)
                val channels = readShortLE(raf).toInt()
                val sampleRate = readIntLE(raf)
                raf.seek(34)
                val bitsPerSample = readShortLE(raf).toInt()
                val dataOffset = findDataOffset(raf)
                if (dataOffset < 0) return false
                val bytesPerSample = bitsPerSample / 8
                val insertByte = (insertMs * sampleRate * bytesPerSample * channels) / 1000
                val insertPos = dataOffset + (insertByte - (insertByte % (bytesPerSample * channels)))

                RandomAccessFile(tempFile, "rw").use { outRaf ->
                    // 1) Write Header
                    val header = ByteArray(44)
                    raf.seek(0); raf.read(header); outRaf.write(header)
                    
                    // 2) Copy pre-insert data
                    raf.seek(dataOffset)
                    val buf = ByteArray(65536)
                    var copied = 0L
                    val preInsertSize = insertPos - dataOffset
                    while (copied < preInsertSize) {
                        val toRead = (preInsertSize - copied).coerceAtMost(buf.size.toLong()).toInt()
                        raf.read(buf, 0, toRead)
                        outRaf.write(buf, 0, toRead)
                        copied += toRead
                    }

                    // 3) Write Clipboard data
                    outRaf.write(clipboardData)

                    // 4) Copy post-insert data
                    raf.seek(insertPos)
                    val remainingSize = raf.length() - insertPos
                    var copiedPost = 0L
                    while (copiedPost < remainingSize) {
                        val toRead = (remainingSize - copiedPost).coerceAtMost(buf.size.toLong()).toInt()
                        raf.read(buf, 0, toRead)
                        outRaf.write(buf, 0, toRead)
                        copiedPost += toRead
                    }

                    // 5) Update Header
                    val totalDataSize = (outRaf.length() - 44).toInt()
                    updateWavHeader(outRaf, totalDataSize, sampleRate, channels.toShort(), bitsPerSample.toShort())
                }
            }
            tempFile.copyTo(file, overwrite = true)
            tempFile.delete()
            val reason = validateWav(path)
            if (reason != null) { restoreLastBackup(path); return false }
            return true
        } catch (e: Exception) { e.printStackTrace(); tempFile.delete() }
        restoreLastBackup(path)
        return false
    }

    fun pasteOverwrite(path: String, insertMs: Long, clipboardData: ByteArray): Boolean {
        createBackup(path)
        val file = File(path)
        if (!file.exists() || clipboardData.isEmpty()) return false
        try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(22)
                val channels = readShortLE(raf).toInt()
                val sampleRate = readIntLE(raf)
                raf.seek(34)
                val bitsPerSample = readShortLE(raf).toInt()
                val dataOffset = findDataOffset(raf)
                if (dataOffset < 0) return false
                val bytesPerSample = bitsPerSample / 8
                val insertByte = (insertMs * sampleRate * bytesPerSample * channels) / 1000
                val insertPos = dataOffset + (insertByte - (insertByte % (bytesPerSample * channels)))
                raf.seek(insertPos)
                raf.write(clipboardData)
                return true
            }
        } catch (e: Exception) { e.printStackTrace() }
        return false
    }

    fun getPeaks(path: String, count: Int): List<Int> {
        val file = File(path)
        if (!file.exists()) return emptyList()
        val peaks = mutableListOf<Int>()
        try {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(22)
                val channels = readShortLE(raf).toInt()
                val sampleRate = readIntLE(raf)
                raf.seek(34)
                val bitsPerSample = readShortLE(raf).toInt()
                val dataOffset = findDataOffset(raf)
                if (dataOffset < 0) return emptyList()
                val dataSize = raf.length() - dataOffset
                val bytesPerSample = bitsPerSample / 8
                val totalFrames = dataSize / (bytesPerSample * channels)
                val step = (totalFrames / count).coerceAtLeast(1)
                val windowSize = 256
                for (i in 0 until count) {
                    val pos = dataOffset + (i * step * bytesPerSample * channels)
                    if (pos >= raf.length()) break
                    raf.seek(pos)
                    
                    var maxAmp = 0
                    // Read a small window to find local peak
                    val windowSamples = Math.min(windowSize, Math.max(1, step.toInt()))
                    for (j in 0 until windowSamples) {
                        if (raf.filePointer >= raf.length()) break
                        val s = if (bitsPerSample == 16) {
                            Math.abs(readShortLE(raf).toInt())
                        } else {
                            Math.abs(raf.read()) * 128
                        }
                        if (s > maxAmp) maxAmp = s
                        // Skip extra channels
                        if (channels > 1) raf.skipBytes((channels - 1) * bytesPerSample)
                    }
                    peaks.add((maxAmp * 100 / 32768).coerceIn(0, 100))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return peaks
    }

    fun saveAs(path: String, outPath: String): Boolean {
        return try {
            File(path).copyTo(File(outPath), overwrite = true)
            true
        } catch (e: Exception) { false }
    }

    data class ExportOptions(
        val outputName: String,
        val startMs: Long = 0L,
        val endMs: Long = -1L,
        val sampleRate: Int = 48000,       // 48000 or 44100
        val bitDepth: Int = 24,            // 16 or 24
        val format: String = "WAV",         // "WAV" or "MP3"
        val mp3Channels: Int = 2,          // 1 (Mono) or 2 (Stereo)
        val mp3BitrateKbps: Int = 320,     // 128, 192, 256, 320
        val normalize: Boolean = true,
        val peakDb: Float = -1.0f          // 0.0 to -6.0 dBFS
    )

    fun exportRegions(
        inputPath: String,
        regions: List<RegionSpec>,
        baseOptions: ExportOptions,
        outDir: File
    ): List<File?> {
        return regions.map { region ->
            val finalName = if (baseOptions.outputName.isBlank()) region.title else "${baseOptions.outputName}_${region.title}"
            val opts = baseOptions.copy(
                outputName = finalName,
                startMs = region.startMs,
                endMs = region.endMs
            )
            try { exportCustomFormat(inputPath, outDir, opts) } catch (e: Exception) { null }
        }
    }

    fun exportCustomFormat(inputPath: String, outDir: File, options: ExportOptions): File? {
        val srcFile = File(inputPath)
        if (!srcFile.exists()) return null

        return try {
            var rawSamples: FloatArray // Mono or multi-channel interleaved float samples in [-1.0, 1.0]
            var srcSr: Int
            var srcChannels: Int

            RandomAccessFile(srcFile, "r").use { raf ->
                raf.seek(22)
                srcChannels = readShortLE(raf).toInt()
                srcSr = readIntLE(raf)
                raf.seek(34)
                val srcBits = readShortLE(raf).toInt()
                val dataOffset = findDataOffset(raf)
                if (dataOffset < 0) return null

                val bytesPerFrame = (srcBits / 8) * srcChannels
                val totalFrames = (raf.length() - dataOffset) / bytesPerFrame

                val startFrame = if (options.startMs > 0) ((options.startMs * srcSr) / 1000).coerceIn(0, totalFrames) else 0L
                val endFrame = if (options.endMs > 0) ((options.endMs * srcSr) / 1000).coerceIn(startFrame + 1, totalFrames) else totalFrames
                val framesToRead = (endFrame - startFrame).toInt()
                if (framesToRead <= 0) return null

                raf.seek(dataOffset + (startFrame * bytesPerFrame))
                val rawFloats = FloatArray(framesToRead)

                if (srcBits == 16) {
                    val buf = ByteArray(framesToRead * 2 * srcChannels)
                    raf.readFully(buf)
                    val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
                    for (i in 0 until framesToRead) {
                        var sum = 0f
                        for (ch in 0 until srcChannels) {
                            sum += bb.short / 32768.0f
                        }
                        rawFloats[i] = sum / srcChannels
                    }
                } else if (srcBits == 24) {
                    val buf = ByteArray(framesToRead * 3 * srcChannels)
                    raf.readFully(buf)
                    for (i in 0 until framesToRead) {
                        var sum = 0f
                        for (ch in 0 until srcChannels) {
                            val idx = (i * srcChannels + ch) * 3
                            val b0 = buf[idx].toInt() and 0xFF
                            val b1 = buf[idx + 1].toInt() and 0xFF
                            val b2 = buf[idx + 2].toInt()
                            val s = (b2 shl 16) or (b1 shl 8) or b0
                            sum += s / 8388608.0f
                        }
                        rawFloats[i] = sum / srcChannels
                    }
                } else {
                    return null
                }
                rawSamples = rawFloats
            }

            // 1) Resample if needed
            val targetSr = options.sampleRate
            val resampled: FloatArray = if (srcSr != targetSr) {
                val ratio = targetSr.toDouble() / srcSr.toDouble()
                val outLen = (rawSamples.size * ratio).toInt()
                val out = FloatArray(outLen)
                for (i in 0 until outLen) {
                    val srcPos = i / ratio
                    val idx = srcPos.toInt()
                    val frac = (srcPos - idx).toFloat()
                    val s0 = rawSamples.getOrElse(idx) { 0f }
                    val s1 = rawSamples.getOrElse(idx + 1) { s0 }
                    out[i] = s0 * (1f - frac) + s1 * frac
                }
                out
            } else {
                rawSamples
            }

            // 2) Normalization to target peak dBFS with noise-floor safeguard and DC offset removal
            if (options.normalize && resampled.isNotEmpty()) {
                var sum = 0.0
                for (s in resampled) sum += s
                val dcOffset = (sum / resampled.size).toFloat()
                if (kotlin.math.abs(dcOffset) > 0.0001f) {
                    for (i in resampled.indices) resampled[i] -= dcOffset
                }

                var maxPeak = 0f
                for (s in resampled) {
                    val a = kotlin.math.abs(s)
                    if (a > maxPeak) maxPeak = a
                }
                val targetLinear = 10.0.pow(options.peakDb.toDouble() / 20.0).toFloat().coerceIn(0.1f, 1.0f)
                // Safeguard: only normalize if signal peak is at least -36 dBFS (~0.015f)
                // and cap maximum boost to +18 dB (gain <= 8.0f) to avoid amplifying background noise
                if (maxPeak >= 0.015f) {
                    val rawGain = targetLinear / maxPeak
                    val gain = rawGain.coerceAtMost(8.0f)
                    for (i in resampled.indices) {
                        resampled[i] = (resampled[i] * gain).coerceIn(-1.0f, 1.0f)
                    }
                }
            }

            // 3) Write output file
            val isMp3 = options.format.equals("MP3", ignoreCase = true)
            // MP3 lato Android richiede un encoder esterno (LAME). Per ora
            // scriviamo un WAV intermedio accanto a un file .mp3cmd.txt che
            // contiene il comando desktop pronto da lanciare.
            val ext = if (isMp3) "wav" else "wav"
            val cleanName = if (options.outputName.endsWith(".$ext", ignoreCase = true)) options.outputName else "${options.outputName}.$ext"
            val outFile = File(outDir, cleanName)

            if (!isMp3) {
                // Write standard WAV (16-bit or 24-bit)
                val outBits = options.bitDepth
                val outChannels = 1
                val bytesPerSample = outBits / 8
                val pcmSize = resampled.size * outChannels * bytesPerSample

                RandomAccessFile(outFile, "rw").use { raf ->
                    raf.setLength(0)
                    raf.write("RIFF".toByteArray())
                    writeIntLE(raf, 36 + pcmSize)
                    raf.write("WAVE".toByteArray())
                    raf.write("fmt ".toByteArray())
                    writeIntLE(raf, 16) // Subchunk1Size
                    writeShortLE(raf, 1) // AudioFormat PCM = 1
                    writeShortLE(raf, outChannels.toShort())
                    writeIntLE(raf, targetSr)
                    writeIntLE(raf, targetSr * outChannels * bytesPerSample)
                    writeShortLE(raf, (outChannels * bytesPerSample).toShort())
                    writeShortLE(raf, outBits.toShort())
                    raf.write("data".toByteArray())
                    writeIntLE(raf, pcmSize)

                    if (outBits == 24) {
                        val outBuf = ByteArray(resampled.size * 3)
                        for (i in resampled.indices) {
                            val v = (resampled[i].coerceIn(-1.0f, 1.0f) * 8388607.0f).toInt()
                            val idx = i * 3
                            outBuf[idx] = (v and 0xFF).toByte()
                            outBuf[idx + 1] = ((v shr 8) and 0xFF).toByte()
                            outBuf[idx + 2] = ((v shr 16) and 0xFF).toByte()
                        }
                        raf.write(outBuf)
                    } else {
                        val outBuf = ByteArray(resampled.size * 2)
                        val bb = ByteBuffer.wrap(outBuf).order(ByteOrder.LITTLE_ENDIAN)
                        for (s in resampled) {
                            val v = (s.coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()
                            bb.putShort(v)
                        }
                        raf.write(outBuf)
                    }
                }
            } else {
                // Encode to high-quality audio file (WAV PCM 16-bit intermediate or direct MediaCodec encoding)
                // Write standard 16-bit PCM for universal compatibility
                val outChannels = options.mp3Channels.coerceIn(1, 2)
                val pcmSize = resampled.size * outChannels * 2
                RandomAccessFile(outFile, "rw").use { raf ->
                    raf.setLength(0)
                    raf.write("RIFF".toByteArray())
                    writeIntLE(raf, 36 + pcmSize)
                    raf.write("WAVE".toByteArray())
                    raf.write("fmt ".toByteArray())
                    writeIntLE(raf, 16)
                    writeShortLE(raf, 1)
                    writeShortLE(raf, outChannels.toShort())
                    writeIntLE(raf, targetSr)
                    writeIntLE(raf, targetSr * outChannels * 2)
                    writeShortLE(raf, (outChannels * 2).toShort())
                    writeShortLE(raf, 16.toShort())
                    raf.write("data".toByteArray())
                    writeIntLE(raf, pcmSize)

                    val outBuf = ByteArray(resampled.size * outChannels * 2)
                    val bb = ByteBuffer.wrap(outBuf).order(ByteOrder.LITTLE_ENDIAN)
                    for (s in resampled) {
                        val v = (s.coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()
                        bb.putShort(v)
                        if (outChannels == 2) bb.putShort(v) // Duplicate for stereo
                    }
                    raf.write(outBuf)
                }

                // Genera accanto un file .mp3cmd.txt con il comando desktop
                // per convertire questo WAV in MP3 con libmp3lame.
                val cmdFile = File(outFile.parentFile, outFile.nameWithoutExtension + ".mp3cmd.txt")
                val pyExe = "K:\\AI\\rvc-portable\\runtime\\python.exe"
                val script = "K:\\AI\\Android\\rvc-portable-android\\tools\\export_mp3.py"
                val bitrateKbps = options.mp3BitrateKbps
                val channels = options.mp3Channels
                val sr = targetSr
                val cmd = buildString {
                    append("@echo off\r\n")
                    append("REM Genera un MP3 dal WAV intermedio prodotto dall'app Android.\r\n")
                    append("REM Esegui questo script sul PC (Windows). Richiede K:\\AI\\rvc-portable\\runtime\\python.exe.\r\n")
                    append("\"").append(pyExe).append("\" \"").append(script).append("\" \"")
                        .append(outFile.absolutePath).append("\" --bitrate ").append(bitrateKbps).append("k")
                        .append(" --ch ").append(channels)
                        .append(" --sr ").append(sr)
                        .append("\r\n")
                    append("pause\r\n")
                }
                cmdFile.writeText(cmd, Charsets.UTF_8)
            }
            outFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun findDataOffset(raf: RandomAccessFile): Long {
        raf.seek(12)
        while (raf.filePointer < raf.length() - 8) {
            val id = ByteArray(4); raf.read(id)
            val size = readIntLE(raf).toLong()
            if (String(id) == "data") return raf.filePointer
            raf.seek(raf.filePointer + size)
        }
        return -1
    }

    /**
     * Sanity-check a WAV file: header present, fmt chunk decodable, data chunk present
     * and at least one frame long. Returns null on success, or a human-readable reason.
     */
    fun validateWav(path: String): String? {
        val f = File(path)
        if (!f.exists() || f.length() < 44) return "file too small or missing"
        return try {
            RandomAccessFile(f, "r").use { raf ->
                raf.seek(0)
                val riff = ByteArray(4); raf.read(riff)
                if (String(riff) != "RIFF") return "missing RIFF"
                raf.seek(8)
                val wave = ByteArray(4); raf.read(wave)
                if (String(wave) != "WAVE") return "missing WAVE"
                raf.seek(22)
                val ch = readShortLE(raf).toInt()
                if (ch < 1 || ch > 2) return "invalid channels=$ch"
                val sr = readIntLE(raf)
                if (sr <= 0 || sr > 384_000) return "invalid sampleRate=$sr"
                raf.seek(34)
                val bits = readShortLE(raf).toInt()
                if (bits !in setOf(8, 16, 24, 32)) return "invalid bits=$bits"
                val dataOff = findDataOffset(raf)
                if (dataOff < 0) return "no data chunk"
                val bytesPerFrame = (bits / 8) * ch
                if (bytesPerFrame <= 0) return "invalid frame size"
                val dataSize = raf.length() - dataOff
                if (dataSize < bytesPerFrame) return "data chunk shorter than one frame"
                null
            }
        } catch (e: Exception) {
            "exception: ${e.message}"
        }
    }

    /** Restores the most recent backup, if any. Returns true on success. */
    private fun restoreLastBackup(path: String): Boolean {
        val history = historyMap[path] ?: return false
        if (history.isEmpty()) return false
        val last = history.removeAt(history.size - 1)
        return try {
            File(last).copyTo(File(path), overwrite = true)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun updateWavHeader(raf: RandomAccessFile, pcmSize: Int, sr: Int, ch: Short, bits: Short) {
        raf.seek(4); writeIntLE(raf, 36 + pcmSize)
        raf.seek(24); writeIntLE(raf, sr)
        writeIntLE(raf, sr * ch * bits / 8)
        raf.seek(32); writeShortLE(raf, (ch * bits / 8).toShort())
        raf.seek(40); writeIntLE(raf, pcmSize)
    }

    private fun readShortLE(raf: RandomAccessFile): Short {
        val b = ByteArray(2); raf.read(b)
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).short
    }
    private fun readIntLE(raf: RandomAccessFile): Int {
        val b = ByteArray(4); raf.read(b)
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int
    }
    private fun writeShortLE(raf: RandomAccessFile, v: Short) {
        raf.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v).array())
    }
    private fun writeIntLE(raf: RandomAccessFile, v: Int) {
        raf.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())
    }

    /**
     * Pulisce i file temporanei accumulati: cache di undo/redo, file scaricati parziali
     * e file intermedi generati durante l'editing o le conversioni.
     * Restituisce il numero totale di byte liberati.
     */
    fun cleanGarbage(context: android.content.Context): Long {
        var freedBytes = 0L

        // 1. Pulisci la cartella undo
        undoDir?.let { dir ->
            if (dir.exists()) {
                dir.listFiles()?.forEach { file ->
                    val len = file.length()
                    if (file.delete()) freedBytes += len
                }
            }
        }
        historyMap.clear()
        redoMap.clear()

        // 2. Pulisci context.cacheDir
        val cache = context.cacheDir
        if (cache != null && cache.exists()) {
            cache.listFiles()?.forEach { file ->
                val len = file.length()
                if (file.deleteRecursively()) freedBytes += len
            }
        }

        // 3. Pulisci file orfani temporanei nella cartella file esterni (TMP_*, *.downloading)
        val extDir = context.getExternalFilesDir(null)
        if (extDir != null && extDir.exists()) {
            extDir.listFiles()?.forEach { file ->
                val name = file.name
                if (name.startsWith("TMP_") || name.endsWith(".downloading") || name.startsWith("TEMP_RVC_")) {
                    val len = file.length()
                    if (file.delete()) freedBytes += len
                }
            }
        }

        return freedBytes
    }
}
