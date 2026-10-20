package com.rvc.droid.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

import android.os.Handler
import android.os.Looper

object WavPlayer {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var audioTrack: AudioTrack? = null
    private var playThread: Thread? = null
    @Volatile private var isPlaying = false
    @Volatile private var playPath: String = ""
    @Volatile private var playStartMs: Long = 0
    @Volatile private var playEndMs: Long = 0
    @Volatile private var playProgressMs: Long = 0
    @Volatile private var playDurationMs: Long = 0

    var onProgressChanged: ((Long, Long) -> Unit)? = null  // (currentMs, totalMs)
    var onPlaybackComplete: (() -> Unit)? = null

    fun isPlaying(): Boolean = isPlaying

    fun getCurrentPositionMs(): Long = playProgressMs

    fun getDurationMs(): Long = playDurationMs

    fun play(path: String, startMs: Long = 0, endMs: Long = -1) {
        stop()
        val file = File(path)
        if (!file.exists()) return

        var raf: RandomAccessFile? = null
        try {
            raf = RandomAccessFile(file, "r")
            raf.seek(22)
            val channels = raf.readShortLE().toInt()
            val sampleRate = raf.readIntLE()
            raf.seek(34)
            val bitsPerSample = raf.readShortLE().toInt()
            val dataOffset = findDataOffset(raf)
            if (dataOffset < 0) { try { raf.close() } catch (_: Exception) {}; return }

            val bytesPerSample = bitsPerSample / 8
            val dataSize = (raf.length() - dataOffset).toInt()
            val totalFrames = dataSize / (bytesPerSample * channels)
            val totalMs = (totalFrames * 1000L) / sampleRate

            val actualEndMs = if (endMs < 0 || endMs > totalMs) totalMs else endMs
            val startFrame = (startMs * sampleRate / 1000).toInt()
            val endFrame = (actualEndMs * sampleRate / 1000).toInt()
            val frameCount = endFrame - startFrame
            if (frameCount <= 0) { try { raf.close() } catch (_: Exception) {}; return }

            val startByte = startFrame * bytesPerSample * channels
            val byteCount = frameCount * bytesPerSample * channels

            val channelConfig = if (channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
            val audioFormat = when (bitsPerSample) {
                16 -> AudioFormat.ENCODING_PCM_16BIT
                8 -> AudioFormat.ENCODING_PCM_8BIT
                else -> AudioFormat.ENCODING_PCM_16BIT
            }

            val minBuf = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = minBuf.coerceAtLeast(65536)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .setEncoding(audioFormat)
                    .build())
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            playPath = path
            playStartMs = startMs
            playEndMs = actualEndMs
            playDurationMs = totalMs
            isPlaying = true
            playProgressMs = startMs

            val fileRaf = raf
            playThread = thread(isDaemon = true) {
                try {
                    audioTrack?.play()
                    fileRaf.seek(dataOffset + startByte)
                    val buf = ByteArray(8192)
                    var bytesWritten = 0
                    val framesPerMs = sampleRate / 1000.0

                    while (isPlaying && bytesWritten < byteCount) {
                        val remaining = byteCount - bytesWritten
                        val toRead = buf.size.coerceAtMost(remaining).toInt()
                        val read = fileRaf.read(buf, 0, toRead)
                        if (read <= 0) break

                        val result = audioTrack?.write(buf, 0, read) ?: -1
                        if (result < 0) break
                        bytesWritten += read

                        val framesWritten = bytesWritten / (bytesPerSample * channels)
                        val curMs = startMs + (framesWritten / framesPerMs).toLong()
                        playProgressMs = curMs
                        mainHandler.post {
                            onProgressChanged?.invoke(curMs, totalMs)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    val wasPlaying = isPlaying
                    stop()
                    try { fileRaf.close() } catch (_: Exception) {}
                    if (wasPlaying) {
                        mainHandler.post {
                            onPlaybackComplete?.invoke()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try { raf?.close() } catch (_: Exception) {}
            stop()
        }
    }

    fun stop() {
        isPlaying = false
        try {
            playThread?.join(200)
        } catch (_: InterruptedException) {}
        playThread = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
        playProgressMs = 0
    }

    fun seekTo(ms: Long) {
        playProgressMs = ms.coerceIn(0, playDurationMs)
        onProgressChanged?.invoke(playProgressMs, playDurationMs)
    }

    private fun findDataOffset(raf: RandomAccessFile): Long {
        raf.seek(12)
        while (raf.filePointer < raf.length() - 8) {
            val id = ByteArray(4); raf.read(id)
            val size = raf.readIntLE().toLong()
            if (String(id) == "data") return raf.filePointer
            raf.seek(raf.filePointer + size)
        }
        return -1
    }

    private fun RandomAccessFile.readShortLE(): Short {
        val b = ByteArray(2); read(b)
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).short
    }

    private fun RandomAccessFile.readIntLE(): Int {
        val b = ByteArray(4); read(b)
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int
    }
}
