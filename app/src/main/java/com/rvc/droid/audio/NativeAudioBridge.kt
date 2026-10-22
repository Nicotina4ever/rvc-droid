package com.rvc.droid.audio

import android.util.Log
import androidx.annotation.Keep

object NativeAudioBridge {
    private const val TAG = "NativeAudioBridge"
    private var nativeEnginePtr: Long = 0L
    private var _isInitialized = false
    val isInitialized: Boolean get() = _isInitialized

    // Chiamato via reflection dal JNI (rvc_jni_bridge.cpp: GetMethodID("onProgress", "(F)V")).
    // NON rinominare / rimuovere — il JNI cerca esattamente questo signature.
    @Keep
    fun interface OfflineProgressCallback {
        @Keep
        fun onProgress(progress: Float)
    }

    init {
        try {
            System.loadLibrary("rvc_native")
            Log.i(TAG, "Native library loaded")
        } catch(e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native: ")
        }
    }

    private var logPath: String = ""

    fun initNativeLogging(logFile: String) {
        logPath = logFile
        if (isInitialized) {
            try { nativeSetLogPath(logFile) } catch (_: Exception) {}
        }
    }

    /**
     * Helper: scrive una riga sul file di log nativo (sourroundig try/catch).
     * Usato da validateOnnxArtifact per scrivere il motivo del rifiuto
     * nello stesso posto dove l'utente legge gli errori del C++.
     */
    private fun logToFile(msg: String) {
        if (logPath.isEmpty()) return
        try {
            val ts = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            java.io.File(logPath).appendText("[$ts] [Kotlin] $msg\n")
        } catch (_: Exception) {}
    }

    /**
     * Validazione di un artifact ONNX PRIMA di passarlo al JNI. Ritorna
     * `null` se il file è accettabile, oppure una stringa con il motivo
     * del rifiuto (verrà loggata e il load*() ritornerà false).
     *
     * Regole (volutamente permissive, perché ONNX è un protobuf e il primo
     * byte può essere qualsiasi varint):
     *  - il file deve esistere e avere dimensione > 1 MB
     *  - l'estensione deve essere .onnx
     *  - i primi byte NON devono essere "RIFF" (WAV/AVI/WebM)
     *  - la dimensione non deve superare 2 GB (limite pratico ONNX Runtime)
     *
     * I modelli reali (hubert_base.onnx ~360 MB, Nicola_Spot_Locali.onnx
     * ~110 MB) passano tutti questi check.
     */
    private fun validateOnnxArtifact(path: String, role: String): String? {
        return try {
            val f = java.io.File(path)
            if (!f.exists() || !f.isFile) {
                return "load$role: REJECTED — file non esiste: $path"
            }
            val size = f.length()
            if (size < 1_000_000L) {
                return "load$role: REJECTED — file troppo piccolo (${size} B, atteso > 1 MB): ${f.name}"
            }
            if (size > 2L * 1024 * 1024 * 1024) {
                return "load$role: REJECTED — file troppo grande (${size / 1024 / 1024} MB, max 2 GB): ${f.name}"
            }
            if (!path.lowercase().endsWith(".onnx")) {
                return "load$role: REJECTED — estensione non .onnx (path=$path)"
            }
            f.inputStream().use { input ->
                val head = ByteArray(4)
                val read = input.read(head)
                if (read < 4) {
                    return "load$role: REJECTED — file illeggibile (read=$read): ${f.name}"
                }
                // Rifiuta SOLO magic RIFF (WAV/AVI/WebM). ONNX è protobuf e può
                // iniziare con qualsiasi varint (0x08 per ir_version, 0x00 per
                // ir_version=0, ecc.), quindi non possiamo essere più stringenti.
                if (head[0] == 'R'.code.toByte() && head[1] == 'I'.code.toByte() &&
                    head[2] == 'F'.code.toByte() && head[3] == 'F'.code.toByte()) {
                    return "load$role: REJECTED — magic RIFF (WAV/video) non è un ONNX: ${f.name}"
                }
            }
            null
        } catch (e: Exception) {
            "load$role: REJECTED — errore lettura: ${e.message}"
        }
    }

    fun initialize(): Boolean {
        if (isInitialized) release()
        nativeEnginePtr = nativeCreateEngine()
        _isInitialized = nativeEnginePtr != 0L
        if (_isInitialized && logPath.isNotEmpty()) {
            try { nativeSetLogPath(logPath) } catch (_: Exception) {}
        }
        Log.i(TAG, "Engine init: ptr=$nativeEnginePtr")
        return _isInitialized
    }

    fun release() {
        if (!isInitialized) return
        try { nativeRelease(nativeEnginePtr) } catch(e: Exception) { Log.e(TAG, "Release error: ") }
        nativeEnginePtr = 0L; _isInitialized = false
    }

    // -- AUDIO --
    fun startAudio(enableRVC: Boolean = false) = checkInit { nativeStartAudio(nativeEnginePtr, enableRVC) }
    fun startMonitoring() = checkInit { nativeStartMonitoring(nativeEnginePtr) }
    fun stopAudio() { if(isInitialized) nativeStopAudio(nativeEnginePtr) }
    fun enableRVC(enabled: Boolean) = checkInit { nativeEnableRVC(nativeEnginePtr, enabled) }
    fun isRvcEnabled() = checkInit { nativeIsRvcEnabled(nativeEnginePtr) }
    fun isRunning() = isInitialized && nativeIsRunning(nativeEnginePtr)
    fun isMonitoring() = isInitialized && nativeIsMonitoring(nativeEnginePtr)

    fun setMonitoringGain(gain: Float) {
        if(isInitialized) nativeSetMonitoringGain(nativeEnginePtr, gain)
    }

    fun setInputDevice(deviceId: Int) {
        if(isInitialized) nativeSetInputDevice(nativeEnginePtr, deviceId)
    }

    fun setOutputDevice(deviceId: Int) {
        if(isInitialized) nativeSetOutputDevice(nativeEnginePtr, deviceId)
    }

    // -- MODELS --
    fun loadSynthModel(path: String): Boolean {
        val validation = validateOnnxArtifact(path, "synth")
        if (validation != null) { logToFile(validation); return false }
        return checkInit { nativeLoadSynthModel(nativeEnginePtr, path) }
    }
    fun loadContentVec(path: String): Boolean {
        val validation = validateOnnxArtifact(path, "vec")
        if (validation != null) { logToFile(validation); return false }
        return checkInit { nativeLoadContentVec(nativeEnginePtr, path) }
    }
    fun loadF0Model(path: String): Boolean {
        val validation = validateOnnxArtifact(path, "f0")
        if (validation != null) { logToFile(validation); return false }
        return checkInit { nativeLoadF0Model(nativeEnginePtr, path) }
    }
    fun loadIndex(path: String) = checkInit { nativeLoadIndex(nativeEnginePtr, path) }
    fun getSynthModelName() = if(isInitialized) nativeGetSynthModelName(nativeEnginePtr)?:"" else ""
    fun getVecModelName() = if(isInitialized) nativeGetVecModelName(nativeEnginePtr)?:"" else ""
    fun getIndexName() = if(isInitialized) nativeGetIndexName(nativeEnginePtr)?:"" else ""

    /**
     * True solo se TUTTI e tre i modelli (synth + HuBERT + rmvpe) sono
     * effettivamente caricati nel engine C++. Dopo una conversione
     * `runChain` scarica HuBERT e RMVPE, quindi questo flag torna false.
     * Usare questo per decidere se ricaricare prima di un nuovo run.
     */
    fun areModelsLoaded(): Boolean = if(isInitialized) nativeAreModelsLoaded(nativeEnginePtr) else false

    // -- RVC PARAMS --
    fun setRvcParams(formantShift: Int, f0Method: Int, indexRate: Float = 0.5f, protection: Float = 0.5f, noiseScale: Float = 0.666f) {
        if(isInitialized) nativeSetRvcParams(nativeEnginePtr, formantShift, f0Method, indexRate, protection, noiseScale)
    }

    fun setRmsMixRate(rate: Float) {
        if(isInitialized) nativeSetRmsMixRate(nativeEnginePtr, rate)
    }

    // -- PROCESSING --
    fun processAudio(input: FloatArray, sampleRate: Int, pitchShift: Int = 0): FloatArray {
        checkInit {}
        val out = FloatArray(input.size)
        nativeProcessAudio(nativeEnginePtr, input, sampleRate, pitchShift, out)
        return out
    }

    fun pitchShift(input: FloatArray, sampleRate: Int, semitones: Int): FloatArray {
        checkInit {}
        val out = FloatArray(input.size)
        nativePitchShift(nativeEnginePtr, input, sampleRate, semitones, out)
        return out
    }

    // -- MASTERING --
    fun masterProcess(voice: FloatArray, music: FloatArray): FloatArray {
        checkInit {}
        val out = FloatArray(voice.size)
        nativeMasterProcess(nativeEnginePtr, voice, music, out)
        return out
    }

    data class EQBand(val freq: Float, val gainDB: Float, val q: Float)
    fun setVoiceEQ(bands: List<EQBand>) {
        checkInit {}
        val freqs = FloatArray(4) { if(it<bands.size) bands[it].freq else 1000f }
        val gains = FloatArray(4) { if(it<bands.size) bands[it].gainDB else 0f }
        val qs = FloatArray(4) { if(it<bands.size) bands[it].q else 1f }
        nativeSetVoiceEQ(nativeEnginePtr, freqs, gains, qs)
    }

    fun setMusicEQ(bands: List<EQBand>) {
        checkInit {}
        val freqs = FloatArray(4) { if(it<bands.size) bands[it].freq else 1000f }
        val gains = FloatArray(4) { if(it<bands.size) bands[it].gainDB else 0f }
        val qs = FloatArray(4) { if(it<bands.size) bands[it].q else 1f }
        nativeSetMusicEQ(nativeEnginePtr, freqs, gains, qs)
    }

    fun setSideChain(threshDB: Float, ratio: Float, attackMs: Float, releaseMs: Float, makeupDB: Float) {
        checkInit { nativeSetSideChain(nativeEnginePtr, threshDB, ratio, attackMs, releaseMs, makeupDB) }
    }

    fun setLimiter(threshDB: Float, releaseMs: Float) {
        checkInit { nativeSetLimiter(nativeEnginePtr, threshDB, releaseMs) }
    }

    fun setVolumes(voiceDB: Float, musicDB: Float, masterDB: Float) {
        checkInit { nativeSetVolume(nativeEnginePtr, voiceDB, musicDB, masterDB) }
    }

    data class LufsReading(
        val momentary: Float, val shortTerm: Float,
        val integrated: Float, val truePeak: Float, val range: Float
    )

    fun getLufs(): LufsReading {
        if(!isInitialized) return LufsReading(-70f,-70f,-70f,-70f,0f)
        val d = nativeGetLufs(nativeEnginePtr) ?: return LufsReading(-70f,-70f,-70f,-70f,0f)
        return LufsReading(d[0], d[1], d[2], d[3], d[4])
    }

    
    data class InputLevelReading(
        val left: Float, val right: Float,
        val peakLeft: Float, val peakRight: Float,
        val clipping: Boolean
    )
    fun masteringReset() { if(isInitialized) nativeMasteringReset(nativeEnginePtr) }

    // -- NATIVE METHODS --
    
    // -- NOISE GATE --
    fun setNoiseGate(enabled: Boolean, threshold: Float, attackMs: Float, holdMs: Float, releaseMs: Float) {
        checkInit { nativeSetNoiseGate(nativeEnginePtr, enabled, threshold, attackMs, holdMs, releaseMs) }
    }

    // -- WAVEFORM PEAKS --
    fun getWaveformPeaks(): FloatArray {
        if(!isInitialized) return FloatArray(4096)
        return nativeGetWaveformPeaks(nativeEnginePtr) ?: FloatArray(4096)
    }
    fun getWaveformTotalPeaks(): Int {
        return if(isInitialized) nativeGetWaveformTotalPeaks(nativeEnginePtr) else 0
    }
    fun getWaveformWritePos(): Int {
        return if(isInitialized) nativeGetWaveformWritePos(nativeEnginePtr) else 0
    }

    // -- BACKING TRACK --
    fun loadBackingTrack(path: String): Boolean {
        if(!isInitialized) return false
        return nativeLoadBackingTrack(nativeEnginePtr, path)
    }

    // -- OFFLINE PROCESSOR --
    fun processOffline(inPath: String, outPath: String, pitchShift: Int, onProgress: ((Float) -> Unit)? = null): Boolean {
        if(!isInitialized) return false
        val callback = onProgress?.let { OfflineProgressCallback(it) }
        return nativeProcessOffline(nativeEnginePtr, inPath, outPath, pitchShift, callback)
    }
    fun setBackingTrackGain(gain: Float) {
        if(isInitialized) nativeSetBackingTrackGain(nativeEnginePtr, gain)
    }
    fun setBackingTrackPosition(frames: Long) {
        if(isInitialized) nativeSetBackingTrackPosition(nativeEnginePtr, frames)
    }
    fun isBackingLoaded(): Boolean {
        return isInitialized && nativeIsBackingLoaded(nativeEnginePtr)
    }
    fun getBackingTrackGain(): Float {
        return if(isInitialized) nativeGetBackingTrackGain(nativeEnginePtr) else 0.5f
    }

    // -- GR METERS --
    fun getCompressorGR(): Float {
        return if(isInitialized) nativeGetCompressorGR(nativeEnginePtr) else 0f
    }

    fun getLimiterGR(): Float {
        return if(isInitialized) nativeGetLimiterGR(nativeEnginePtr) else 0f
    }

    fun getNoiseGateGR(): Float {
        return if(isInitialized) nativeGetNoiseGateGR(nativeEnginePtr) else 0f
    }

    // -- INPUT LEVEL --
    fun getInputLevel(): InputLevelReading {
        if(!isInitialized) return InputLevelReading(-80f, -80f, -80f, -80f, false)
        val d = nativeGetInputLevel(nativeEnginePtr) ?: return InputLevelReading(-80f, -80f, -80f, -80f, false)
        return InputLevelReading(d[0], d[1], d[2], d[3], d[4] > 0.5f)
    }

    // -- RECORDING --
    fun startRecording(path: String, format: String, includeRVC: Boolean, includeMastering: Boolean): Boolean {
        if(!isInitialized) return false
        return nativeStartRecording(nativeEnginePtr, path, format, includeRVC, includeMastering)
    }
    fun stopRecording() {
        if(isInitialized) nativeStopRecording(nativeEnginePtr)
    }
    fun isRecording(): Boolean {
        return isInitialized && nativeIsRecording(nativeEnginePtr)
    }
    fun getRecordingDurationSec(): Float {
        return if(isInitialized) nativeGetRecordingDuration(nativeEnginePtr) else 0f
    }

    // -- REVERB --
    fun setReverbMix(mix: Float) {
        if(isInitialized) nativeSetReverbMix(nativeEnginePtr, mix)
    }
    fun setReverbRoomSize(sz: Float) {
        if(isInitialized) nativeSetReverbRoomSize(nativeEnginePtr, sz)
    }
    fun setReverbDamping(damp: Float) {
        if(isInitialized) nativeSetReverbDamping(nativeEnginePtr, damp)
    }
    fun getReverbMix(): Float = if(isInitialized) nativeGetReverbMix(nativeEnginePtr) else 0f
    fun getReverbRoomSize(): Float = if(isInitialized) nativeGetReverbRoomSize(nativeEnginePtr) else 0.5f
    fun getReverbDamping(): Float = if(isInitialized) nativeGetReverbDamping(nativeEnginePtr) else 0.5f

    // -- TRACK MIXER --
    fun addTrack(name: String = ""): Int = checkInit { nativeAddTrack(nativeEnginePtr, name) }
    fun removeTrack(index: Int) { if(isInitialized) nativeRemoveTrack(nativeEnginePtr, index) }
    fun getTrackCount(): Int = if(isInitialized) nativeGetTrackCount(nativeEnginePtr) else 0
    fun getMixerPositionMs(): Long = if(isInitialized) nativeGetMixerPositionMs(nativeEnginePtr) else 0L
    fun getMixerDurationMs(): Long = if(isInitialized) nativeGetMixerDurationMs(nativeEnginePtr) else 0L
    fun setTrackVolume(index: Int, volume: Float) { if(isInitialized) nativeSetTrackVolume(nativeEnginePtr, index, volume) }
    fun setTrackPan(index: Int, pan: Float) { if(isInitialized) nativeSetTrackPan(nativeEnginePtr, index, pan) }
    fun setTrackMute(index: Int, muted: Boolean) { if(isInitialized) nativeSetTrackMute(nativeEnginePtr, index, muted) }
    fun setTrackSolo(index: Int, solo: Boolean) { if(isInitialized) nativeSetTrackSolo(nativeEnginePtr, index, solo) }
    fun setTrackOffset(index: Int, offsetFrames: Long) { if(isInitialized) nativeSetTrackOffset(nativeEnginePtr, index, offsetFrames) }
    /**
     * Imposta il ducking auto-livellamento per la traccia `index`. Il segnale
     * della traccia target (index-1, quella "superiore" nella timeline) viene
     * usato come sidechain: quando supera la soglia, la traccia `index` viene
     * attenuata di `amount` (0..1). attack/release 50/200 ms di default.
     */
    fun setTrackDuck(index: Int, amount: Float) { if(isInitialized) nativeSetTrackDuck(nativeEnginePtr, index, amount) }
    fun importTrack(path: String, name: String = ""): Boolean = checkInit { nativeImportTrack(nativeEnginePtr, path, name) }
    /** Importa il WAV nella traccia esistente (armata), senza crearne una nuova. */
    fun importTrackAt(index: Int, path: String, name: String = ""): Boolean =
        checkInit { nativeImportTrackAt(nativeEnginePtr, index, path, name) }
    fun resetMixerPosition() { if(isInitialized) nativeResetMixerPosition(nativeEnginePtr) }
    fun setMixerPositionMs(ms: Long) { if(isInitialized) nativeSetMixerPosition(nativeEnginePtr, ms * 48) }
    fun clearTracks() { if(isInitialized) nativeClearTracks(nativeEnginePtr) }

    fun renderMixer(outPath: String): Boolean {
        return if(isInitialized) nativeRenderMixer(nativeEnginePtr, outPath) else false
    }

    /** Esporta il mixdown in WAV stereo 16-bit applicando il pan per traccia. */
    fun renderMixerStereo(outPath: String, sampleRate: Int = 48000): Boolean {
        return if(isInitialized) nativeRenderMixerStereo(nativeEnginePtr, outPath, sampleRate) else false
    }

    private external fun nativeSetLogPath(path: String)
    private external fun nativeCreateEngine(): Long
    private external fun nativeRelease(ptr: Long)
    private external fun nativeStartAudio(ptr: Long, rvc: Boolean): Boolean
    private external fun nativeStartMonitoring(ptr: Long): Boolean
    private external fun nativeStopAudio(ptr: Long)
    private external fun nativeIsMonitoring(ptr: Long): Boolean
    private external fun nativeSetMonitoringGain(ptr: Long, gain: Float)
    private external fun nativeSetInputDevice(ptr: Long, deviceId: Int)
    private external fun nativeSetOutputDevice(ptr: Long, deviceId: Int)
    private external fun nativeEnableRVC(ptr: Long, enable: Boolean)
    private external fun nativeIsRvcEnabled(ptr: Long): Boolean
    private external fun nativeIsRunning(ptr: Long): Boolean
    private external fun nativeSetRvcParams(ptr: Long, formantShift: Int, f0Method: Int, indexRate: Float, protection: Float, noiseScale: Float)
    private external fun nativeSetRmsMixRate(ptr: Long, rate: Float)
    private external fun nativeLoadSynthModel(ptr: Long, path: String): Boolean
    private external fun nativeLoadContentVec(ptr: Long, path: String): Boolean
    private external fun nativeLoadF0Model(ptr: Long, path: String): Boolean
    private external fun nativeLoadIndex(ptr: Long, path: String): Boolean
    private external fun nativeGetSynthModelName(ptr: Long): String?
    private external fun nativeGetVecModelName(ptr: Long): String?
    private external fun nativeGetIndexName(ptr: Long): String?
    private external fun nativeAreModelsLoaded(ptr: Long): Boolean
    private external fun nativeProcessAudio(ptr: Long, input: FloatArray, sr: Int, ps: Int, out: FloatArray)
    private external fun nativePitchShift(ptr: Long, input: FloatArray, sr: Int, semis: Int, out: FloatArray)
    private external fun nativeMasterProcess(ptr: Long, voice: FloatArray, music: FloatArray, out: FloatArray)
    private external fun nativeSetVoiceEQ(ptr: Long, freqs: FloatArray, gains: FloatArray, qs: FloatArray)
    private external fun nativeSetMusicEQ(ptr: Long, freqs: FloatArray, gains: FloatArray, qs: FloatArray)
    private external fun nativeSetSideChain(ptr: Long, thresh: Float, ratio: Float, attack: Float, release: Float, makeup: Float)
    private external fun nativeSetLimiter(ptr: Long, thresh: Float, releaseMs: Float)
    private external fun nativeSetVolume(ptr: Long, voiceDB: Float, musicDB: Float, masterDB: Float)
    private external fun nativeGetLufs(ptr: Long): FloatArray?
    private external fun nativeSetNoiseGate(ptr: Long, enabled: Boolean, threshold: Float, attackMs: Float, holdMs: Float, releaseMs: Float)
    private external fun nativeGetCompressorGR(ptr: Long): Float
    private external fun nativeGetLimiterGR(ptr: Long): Float
    private external fun nativeGetNoiseGateGR(ptr: Long): Float
    private external fun nativeGetInputLevel(ptr: Long): FloatArray?
    private external fun nativeStartRecording(ptr: Long, path: String, format: String, rvc: Boolean, mastering: Boolean): Boolean
    private external fun nativeStopRecording(ptr: Long)
    private external fun nativeIsRecording(ptr: Long): Boolean
    private external fun nativeMasteringReset(ptr: Long)
    private external fun nativeSetReverbMix(ptr: Long, mix: Float)
    private external fun nativeSetReverbRoomSize(ptr: Long, sz: Float)
    private external fun nativeSetReverbDamping(ptr: Long, damp: Float)
    private external fun nativeGetReverbMix(ptr: Long): Float
    private external fun nativeGetReverbRoomSize(ptr: Long): Float
    private external fun nativeGetReverbDamping(ptr: Long): Float
    private external fun nativeGetRecordingDuration(ptr: Long): Float
    private external fun nativeGetWaveformPeaks(ptr: Long): FloatArray?
    private external fun nativeGetWaveformTotalPeaks(ptr: Long): Int
    private external fun nativeGetWaveformWritePos(ptr: Long): Int
    private external fun nativeLoadBackingTrack(ptr: Long, path: String): Boolean
    private external fun nativeSetBackingTrackGain(ptr: Long, gain: Float)
    private external fun nativeSetBackingTrackPosition(ptr: Long, pos: Long)
    private external fun nativeIsBackingLoaded(ptr: Long): Boolean
    private external fun nativeGetBackingTrackGain(ptr: Long): Float
    private external fun nativeProcessOffline(ptr: Long, inPath: String, outPath: String, pitchShift: Int, callback: OfflineProgressCallback?): Boolean
    private external fun nativeAddTrack(ptr: Long, name: String): Int
    private external fun nativeRemoveTrack(ptr: Long, index: Int)
    private external fun nativeGetTrackCount(ptr: Long): Int
    private external fun nativeGetMixerPositionMs(ptr: Long): Long
    private external fun nativeGetMixerDurationMs(ptr: Long): Long
    private external fun nativeSetTrackVolume(ptr: Long, index: Int, volume: Float)
    private external fun nativeSetTrackPan(ptr: Long, index: Int, pan: Float)
    private external fun nativeSetTrackMute(ptr: Long, index: Int, muted: Boolean)
    private external fun nativeSetTrackSolo(ptr: Long, index: Int, solo: Boolean)
    private external fun nativeSetTrackOffset(ptr: Long, index: Int, offset: Long)
    private external fun nativeSetTrackDuck(ptr: Long, index: Int, amount: Float)
    private external fun nativeImportTrack(ptr: Long, path: String, name: String): Boolean
    private external fun nativeImportTrackAt(ptr: Long, index: Int, path: String, name: String): Boolean
    private external fun nativeResetMixerPosition(ptr: Long)
    private external fun nativeSetMixerPosition(ptr: Long, frames: Long)
    private external fun nativeClearTracks(ptr: Long)
    private external fun nativeRenderMixer(ptr: Long, outPath: String): Boolean
    private external fun nativeRenderMixerStereo(ptr: Long, outPath: String, sampleRate: Int): Boolean

    private inline fun <T> checkInit(block: () -> T): T {
        if(!_isInitialized) initialize()
        check(_isInitialized && nativeEnginePtr != 0L) { "NativeAudioBridge not initialized" }
        return block()
    }
}

