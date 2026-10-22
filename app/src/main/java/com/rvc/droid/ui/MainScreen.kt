package com.rvc.droid.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rvc.droid.audio.NativeAudioBridge
import com.rvc.droid.audio.WavEditor
import com.rvc.droid.ui.components.*
import com.rvc.droid.ui.editor.EditorView
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.rvc.droid.ui.rvc.RvcView
import com.rvc.droid.ui.rvc.ModelArtifactDiscovery
import com.rvc.droid.ui.project.ProjectManager
import com.rvc.droid.ui.project.ProjectState
import com.rvc.droid.ui.project.RecordingClip
import com.rvc.droid.ui.project.MixerTrackState
import com.rvc.droid.ui.studio.StudioWorkspace
import com.rvc.droid.ui.plugins.PluginRackView
import com.rvc.droid.ui.video.VideoPreviewView
import com.rvc.droid.ui.video.VideoWorkspace
import com.rvc.droid.ui.prompter.PrompterScreen
import com.rvc.droid.ui.cloud.ProjectSyncView
import com.rvc.droid.ui.cloud.RemoteControlView
import com.rvc.droid.cloud.PcStatusParser
import com.rvc.droid.cloud.RemotePcStatus
import com.rvc.droid.ui.project.ProjectBundle
import com.rvc.droid.cloud.RemoteEntry
import com.rvc.droid.ui.theme.RVCColors
import com.rvc.droid.cloud.DropboxClient
import com.rvc.droid.cloud.DropboxConfig
import com.rvc.droid.cloud.RemoteRvcModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(hasPermission: Boolean = true) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val bridge = remember { NativeAudioBridge }
    var modelsDir by remember { mutableStateOf(
        ctx.getSharedPreferences("rvc_prefs", android.content.Context.MODE_PRIVATE)
            .getString("models_dir", "") ?: ""
    ) }
    fun persistModelsDir(path: String) {
        ctx.getSharedPreferences("rvc_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putString("models_dir", path).apply()
    }
    fun persistModelsSafUri(uri: String) {
        ctx.getSharedPreferences("rvc_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putString("models_saf_uri", uri).apply()
    }
    fun savedModelsSafUri(): String =
        ctx.getSharedPreferences("rvc_prefs", android.content.Context.MODE_PRIVATE)
            .getString("models_saf_uri", "") ?: ""
    val prefs = remember { ctx.getSharedPreferences("rvc_prefs", android.content.Context.MODE_PRIVATE) }
    fun persistModel(key: String, path: String) {
        prefs.edit().putString(key, path).apply()
    }
    fun savedModel(key: String): String =
        prefs.getString(key, "") ?: ""

    var isRemoteRvcMode by remember {
        mutableStateOf(prefs.getBoolean("remote_mode_enabled", true))
    }
    var dropboxToken by remember {
        mutableStateOf(prefs.getString("dropbox_token", "") ?: "")
    }
    var dropboxAppKey by remember {
        mutableStateOf(prefs.getString("dropbox_app_key", "") ?: "")
    }
    var dropboxInputDir by remember {
        mutableStateOf(prefs.getString("dropbox_input_dir", "/TestRVC/AudioIn") ?: "/TestRVC/AudioIn")
    }
    var dropboxOutputDir by remember {
        mutableStateOf(prefs.getString("dropbox_output_dir", "/TestRVC/AudioOut") ?: "/TestRVC/AudioOut")
    }
    var dropboxConfigFile by remember {
        mutableStateOf(prefs.getString("dropbox_config_file", "/TestRVC/config.json") ?: "/TestRVC/config.json")
    }
    var dropboxModelsFile by remember {
        mutableStateOf(prefs.getString("dropbox_models_file", "/TestRVC/modelli.txt") ?: "/TestRVC/modelli.txt")
    }
    var autoMuteOriginal by remember {
        mutableStateOf(prefs.getBoolean("auto_mute_original", true))
    }
    var remoteModels by remember { mutableStateOf<List<RemoteRvcModel>>(emptyList()) }
    var selectedRemoteModel by remember { mutableStateOf<RemoteRvcModel?>(null) }
    var dropboxConnectionStatus by remember { mutableStateOf("") }
    var remoteProgressText by remember { mutableStateOf("") }
    var remoteProgress by remember { mutableFloatStateOf(-1f) }
    var freedStorageStatus by remember { mutableStateOf("") }

    fun persistDropbox(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
    fun persistDropboxBool(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    var currentTab by remember { mutableStateOf(DAWTab.STUDIO) }
    var selectedClip by remember { mutableStateOf<RecordingClip?>(null) }
    var rvcInputClip by remember { mutableStateOf<RecordingClip?>(null) }
    var lastRvcOutputClip by remember { mutableStateOf<RecordingClip?>(null) }
    var recordings by remember { mutableStateOf<List<RecordingClip>>(emptyList()) }
    var mixerTracks by remember { mutableStateOf<List<com.rvc.droid.ui.project.MixerTrackState>>(emptyList()) }
    var armedTrackIndex by remember { mutableStateOf<Int?>(null) }
    var showStudioVideo by remember { mutableStateOf(false) }
    var showStudioPluginRack by remember { mutableStateOf(false) }

    var isRunning by remember { mutableStateOf(false) }
    var recordingStartedAudio by remember { mutableStateOf(false) }
    var isRvcEnabled by remember { mutableStateOf(false) }
    var synthModel by remember { mutableStateOf("") }
    var vecModel by remember { mutableStateOf("") }
    var loadedSynth by remember { mutableStateOf(false) }
    var loadedVec by remember { mutableStateOf(false) }
    var loadedF0 by remember { mutableStateOf(false) }
    var loadedIndex by remember { mutableStateOf(false) }
    var pitchShift by remember { mutableIntStateOf(0) }
    var formantShift by remember { mutableIntStateOf(0) }
    var f0Method by remember { mutableStateOf("rmvpe") }
    var indexRate by remember { mutableFloatStateOf(0.5f) }
    var protection by remember { mutableFloatStateOf(0.5f) }
    var noiseScale by remember { mutableFloatStateOf(0.666f) }
    var inputLevel by remember { mutableStateOf(InputLevel()) }
    var voiceBands by remember { mutableStateOf(List(4) { EQBandState(freq = 200f * (it + 1) * 2.5f, gainDB = 0f) }) }
    var musicBands by remember { mutableStateOf(List(4) { EQBandState(freq = 100f * (it + 1) * 3f, gainDB = 0f) }) }
    var compThreshold by remember { mutableFloatStateOf(-20f) }
    var compRatio by remember { mutableFloatStateOf(4f) }
    var compAttack by remember { mutableFloatStateOf(5f) }
    var compRelease by remember { mutableFloatStateOf(50f) }
    var compMakeup by remember { mutableFloatStateOf(0f) }
    var compGR by remember { mutableFloatStateOf(0f) }
    var limiterThreshold by remember { mutableFloatStateOf(-0.5f) }
    var limiterRelease by remember { mutableFloatStateOf(60f) }
    var limiterGR by remember { mutableFloatStateOf(0f) }
    var gateState by remember { mutableStateOf(NoiseGateState()) }
    var gateGR by remember { mutableFloatStateOf(0f) }
    var voiceVol by remember { mutableFloatStateOf(0f) }
    var musicVol by remember { mutableFloatStateOf(0f) }
    var masterVol by remember { mutableFloatStateOf(0f) }
    var momentary by remember { mutableFloatStateOf(-70f) }
    var shortTerm by remember { mutableFloatStateOf(-70f) }
    var integrated by remember { mutableFloatStateOf(-70f) }
    var truePeak by remember { mutableFloatStateOf(-70f) }

    var currentProjectName by remember { mutableStateOf("Untitled") }
    var currentProjectFile by remember { mutableStateOf<File?>(null) }

    var waveformPeaks by remember { mutableStateOf(FloatArray(4096)) }
    var waveformProgress by remember { mutableFloatStateOf(0f) }
    var backingPosition by remember { mutableFloatStateOf(0f) }
    var offlineProgress by remember { mutableFloatStateOf(-1f) }
    var backingLoaded by remember { mutableStateOf(false) }
    var backingGain by remember { mutableFloatStateOf(0.5f) }
    var timelinePositionMs by remember { mutableLongStateOf(0L) }
    var timelinePlaying by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var lastSavedAt by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    var recentProjects by remember { mutableStateOf<List<ProjectInfo>>(emptyList()) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showRegionExportDialog by remember { mutableStateOf(false) }
    var exportSourceClip by remember { mutableStateOf<RecordingClip?>(null) }
    var selectedTimelineClipId by remember { mutableStateOf<String?>(null) }
    var selectedTrackIdx by remember { mutableIntStateOf(0) }
    var showRenameClipDialog by remember { mutableStateOf(false) }
    var renameClipName by remember { mutableStateOf("") }

    val timelineDurationMs = mixerTracks.maxOfOrNull { it.offsetMs + it.durationMs }?.coerceAtLeast(1L) ?: 60000L

    LaunchedEffect(timelinePlaying, timelineDurationMs) {
        // Prefer the native mixer position when the engine is running, otherwise fall back
        // to a wall clock so the UI remains usable without the engine.
        var sawNative = false
        while (timelinePlaying) {
            if (bridge.isInitialized) {
                val nativeMs = bridge.getMixerPositionMs()
                if (nativeMs > 0L) {
                    sawNative = true
                    timelinePositionMs = nativeMs.coerceAtMost(timelineDurationMs)
                }
            }
            if (!sawNative) {
                timelinePositionMs = (timelinePositionMs + 33L).coerceAtMost(timelineDurationMs)
            }
            if (timelinePositionMs >= timelineDurationMs) timelinePlaying = false
            kotlinx.coroutines.delay(33L)
        }
    }
    var showSaveAsDialog by remember { mutableStateOf(false) }
    var saveAsName by remember { mutableStateOf("") }
    var exportState by remember { mutableStateOf(ExportState()) }
    var pendingExportFile by remember { mutableStateOf<File?>(null) }
    var monitoringGain by remember { mutableFloatStateOf(0.7f) }
    var reverbMix by remember { mutableFloatStateOf(0f) }
    var reverbRoomSize by remember { mutableFloatStateOf(0.5f) }
    var reverbDamping by remember { mutableFloatStateOf(0.5f) }
    var editorClipboard by remember { mutableStateOf<ByteArray?>(null) }
    var videoPath by remember { mutableStateOf<String?>(null) }
    var videoIsPlaying by remember { mutableStateOf(false) }
    var videoPositionMs by remember { mutableLongStateOf(0L) }

    // Teleprompter state
    var teleprompterText by remember { mutableStateOf("") }
    var teleprompterFontSize by remember { mutableIntStateOf(24) }
    var teleprompterScrollSpeed by remember { mutableFloatStateOf(0.3f) }
    var showTeleprompter by remember { mutableStateOf(true) }

    // Cloud / progetti condivisi (bundle .rvcdaw.zip su Dropbox)
    var cloudSection by remember { mutableIntStateOf(0) }
    var remoteBundles by remember { mutableStateOf<List<RemoteEntry>>(emptyList()) }
    var bundleListing by remember { mutableStateOf(false) }
    var bundleBusy by remember { mutableStateOf(false) }
    var bundleProgress by remember { mutableFloatStateOf(0f) }
    var bundleStatus by remember { mutableStateOf("") }
    var bundleListingLoaded by remember { mutableStateOf(false) }

    // Controllo remoto PC (heartbeat + config)
    var pcStatus by remember { mutableStateOf<RemotePcStatus?>(null) }
    var pcStatusLoading by remember { mutableStateOf(false) }
    var pcStatusError by remember { mutableStateOf("") }
    var remoteConfigBusy by remember { mutableStateOf(false) }
    var remoteConfigStatus by remember { mutableStateOf("") }

    var showClipSelectorForTrack by remember { mutableStateOf(-2) }
    var targetTrackLaneForAdd by remember { mutableIntStateOf(-1) }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var inputDevices by remember { mutableStateOf<List<AudioDeviceInfo>>(emptyList()) }
    var outputDevices by remember { mutableStateOf<List<AudioDeviceInfo>>(emptyList()) }
    var selectedInputDevice by remember { mutableStateOf<Int>(-1) }
    var selectedOutputDevice by remember { mutableStateOf<Int>(-1) }

    var isApplyingRvc by remember { mutableStateOf(false) }

    fun getDeviceTypeString(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Microfono Integrato"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Altoparlante Integrato"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Capsula Auricolare"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Cuffie con Microfono"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Cuffie Cablate"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Audio Bluetooth (A2DP)"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Auricolare Bluetooth (SCO)"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "Scheda Audio USB"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "Accessorio USB"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "Cuffie USB"
        else -> "Dispositivo Esterno"
    }

    fun isUsableInputDevice(type: Int): Boolean = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET -> true
        else -> false
    }

    fun isUsableOutputDevice(type: Int): Boolean = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET -> true
        else -> false
    }

    fun refreshDevices() {
        try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val rawInputs = am.getDevices(AudioManager.GET_DEVICES_INPUTS)
            inputDevices = rawInputs.filter { isUsableInputDevice(it.type) }
                .distinctBy { it.type to it.productName.toString() }
                .ifEmpty { rawInputs.toList() }

            val rawOutputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            outputDevices = rawOutputs.filter { isUsableOutputDevice(it.type) }
                .distinctBy { it.type to it.productName.toString() }
                .ifEmpty { rawOutputs.toList() }
        } catch (_: Exception) {}
    }

    fun getDeviceDisplayName(dev: AudioDeviceInfo): String {
        val name = dev.productName.toString().trim()
        val typeStr = getDeviceTypeString(dev.type)
        return if (name.isNotEmpty() && !name.equals("null", ignoreCase = true) && !name.equals(typeStr, ignoreCase = true)) {
            "$name ($typeStr)"
        } else {
            typeStr
        }
    }

    fun showStatus(message: String, isError: Boolean = false) {
        statusMessage = message
        statusIsError = isError
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun openExportedFile(path: String) {
        val file = File(path)
        if (!file.exists()) {
            showStatus("Exported file is no longer available", true)
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(file), "audio/wav")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(intent)
            showExportDialog = false
            exportState = ExportState()
        } catch (e: Exception) {
            showStatus("No app available to open the WAV file", true)
        }
    }

    fun getDurationMs(file: File): Long {
        return try {
            val info = WavEditor.getAudioInfo(file.absolutePath)
            info?.durationMs ?: (file.length() / 176)
        } catch (_: Exception) {
            file.length() / 176
        }
    }

    fun loadStateFromProject(ps: ProjectState) {
        isRvcEnabled = ps.isRvcEnabled; pitchShift = ps.pitchShift
        synthModel = ps.synthModelPath; vecModel = ps.vecModelPath
        gateState = ps.noiseGate; voiceVol = ps.voiceVolume; musicVol = ps.musicVolume; masterVol = ps.masterVolume
        reverbMix = ps.reverbMix; reverbRoomSize = ps.reverbRoomSize; reverbDamping = ps.reverbDamping
        monitoringGain = ps.monitoringGain
        voiceBands = ps.voiceBands; musicBands = ps.musicBands
        compThreshold = ps.compThreshold; compRatio = ps.compRatio
        compAttack = ps.compAttack; compRelease = ps.compRelease; compMakeup = ps.compMakeup
        limiterThreshold = ps.limiterThreshold; limiterRelease = ps.limiterRelease
        
        // Resolve relative paths
        recordings = ps.recordings.map { clip ->
            val absPath = if (clip.path.startsWith("INTERNAL:")) {
                ctx.filesDir.absolutePath + clip.path.substring(9)
            } else clip.path
            clip.copy(path = absPath)
        }
        mixerTracks = ps.mixerTracks.map { track ->
            val absPath = if (track.clipPath.startsWith("INTERNAL:")) {
                ctx.filesDir.absolutePath + track.clipPath.substring(9)
            } else track.clipPath
            track.copy(clipPath = absPath)
        }
        videoPath = ps.videoPath?.let {
            if (it.startsWith("INTERNAL:")) ctx.filesDir.absolutePath + it.substring(9) else it
        }
        
        lastSavedAt = ps.updatedAt.takeIf { it.isNotBlank() } ?: ps.createdAt.takeIf { it.isNotBlank() }
    }

    fun currentProjectState(name: String = currentProjectName): ProjectState = ProjectState(
        name = name,
        createdAt = currentProjectFile?.let { ProjectManager.loadProject(it)?.createdAt }
            ?: SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.US).format(Date()),
        updatedAt = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.US).format(Date()),
        isRvcEnabled = isRvcEnabled, pitchShift = pitchShift,
        synthModelPath = synthModel, vecModelPath = vecModel,
        noiseGate = gateState, voiceVolume = voiceVol, musicVolume = musicVol, masterVolume = masterVol,
        reverbMix = reverbMix, reverbRoomSize = reverbRoomSize, reverbDamping = reverbDamping,
        monitoringGain = monitoringGain,
        voiceBands = voiceBands, musicBands = musicBands,
        compThreshold = compThreshold, compRatio = compRatio,
        compAttack = compAttack, compRelease = compRelease, compMakeup = compMakeup,
        limiterThreshold = limiterThreshold, limiterRelease = limiterRelease,
        recordings = recordings.map { clip ->
            // Make paths relative to internal filesDir if possible to survive path changes
            val relPath = if (clip.path.startsWith(ctx.filesDir.absolutePath)) {
                "INTERNAL:" + clip.path.substring(ctx.filesDir.absolutePath.length)
            } else clip.path
            clip.copy(path = relPath)
        },
        mixerTracks = mixerTracks.map { track ->
            val relPath = if (track.clipPath.startsWith(ctx.filesDir.absolutePath)) {
                "INTERNAL:" + track.clipPath.substring(ctx.filesDir.absolutePath.length)
            } else track.clipPath
            track.copy(clipPath = relPath)
        },
        videoPath = videoPath?.let {
            if (it.startsWith(ctx.filesDir.absolutePath)) "INTERNAL:" + it.substring(ctx.filesDir.absolutePath.length) else it
        }
    )

    fun persistProject(file: File? = currentProjectFile, showMessage: Boolean = false): Boolean {
        val targetFile = file ?: File(ProjectManager.getProjectsDir(), "${currentProjectName}.rvcp")
        targetFile.parentFile?.mkdirs()
        val ok = ProjectManager.saveProject(currentProjectState(), targetFile)
        if (ok) {
            currentProjectFile = targetFile
            lastSavedAt = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            recentProjects = ProjectManager.listProjects().map { f ->
                val s = ProjectManager.loadProject(f)
                ProjectInfo(name = s?.name ?: f.nameWithoutExtension, path = f.absolutePath, date = s?.updatedAt?.takeIf { it.isNotBlank() } ?: s?.createdAt ?: "", size = "${f.length() / 1024}KB")
            }
            if (showMessage) showStatus("Project saved")
        }
        return ok
    }

    fun saveProject() { persistProject(showMessage = true) }

    fun loadProjectByPath(path: String) {
        val file = File(path)
        val state = ProjectManager.loadProject(file)
        if (state != null) {
            loadStateFromProject(state)
            currentProjectFile = file
            currentProjectName = state.name
            lastSavedAt = state.updatedAt.takeIf { it.isNotBlank() } ?: state.createdAt.takeIf { it.isNotBlank() }
            showStatus("Project loaded: ${state.name}")
        }
    }

    fun newProject() {
        currentProjectName = ProjectManager.defaultProjectName()
        currentProjectFile = null
        isRvcEnabled = false; pitchShift = 0; synthModel = ""; vecModel = ""
        loadedSynth = false; loadedVec = false; loadedF0 = false; loadedIndex = false
        voiceBands = List(4) { EQBandState(freq = 200f * (it + 1) * 2.5f, gainDB = 0f) }
        musicBands = List(4) { EQBandState(freq = 100f * (it + 1) * 3f, gainDB = 0f) }
        compThreshold = -20f; compRatio = 4f; compAttack = 5f; compRelease = 50f; compMakeup = 6f
        limiterThreshold = -1f; limiterRelease = 10f
        gateState = NoiseGateState(); voiceVol = 0f; musicVol = 0f; masterVol = 0f
        recordings = emptyList()
        videoPath = null
        lastSavedAt = null
        showStatus("New project ready")
    }

    LaunchedEffect(
        currentProjectFile, currentProjectName, recordings,
        videoPath, isRvcEnabled, pitchShift, synthModel, vecModel,
        gateState, voiceVol, musicVol, masterVol,
        reverbMix, reverbRoomSize, reverbDamping, monitoringGain,
        voiceBands, musicBands,
        compThreshold, compRatio, compAttack, compRelease, compMakeup,
        limiterThreshold, limiterRelease
    ) {
        if (currentProjectFile == null) return@LaunchedEffect
        delay(1200)
        persistProject(currentProjectFile, false)
    }

    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            delay(4000)
            statusMessage = null
        }
    }

    fun refreshRecordings() {
        val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val clips = (dir.listFiles { f -> f.extension.equals("wav", true) } ?: emptyArray())
            .map {
                RecordingClip(name = it.name, path = it.absolutePath, durationMs = getDurationMs(it))
            } + (ctx.filesDir.listFiles { f -> f.extension.equals("wav", true) } ?: emptyArray())
            .map {
                RecordingClip(name = it.name, path = it.absolutePath, durationMs = getDurationMs(it))
            }
        recordings = clips.distinctBy { it.path }.sortedByDescending { it.createdAt }
    }

    fun diag(msg: String) {
        try {
            val dir = File(android.os.Environment.getExternalStorageDirectory(), "Modelli")
            if (!dir.exists()) dir.mkdirs()
            File(dir, "native.log").appendText("[$msg]\n")
        } catch (_: Exception) {}
    }

    fun tryAutoLoadContentVec(): Boolean {
        if (loadedVec) return true
        val searchPaths = ModelArtifactDiscovery.searchDirectories(ctx.getExternalFilesDir(null), ctx.filesDir, modelsDir)

        for (dir in searchPaths) {
            if (!dir.exists()) continue
            val vecFiles = dir.listFiles { f ->
                val name = f.name.lowercase()
                name.endsWith(".onnx") &&
                    (name.contains("hubert") || name.contains("vec") || name.contains("content"))
            }
            if (!vecFiles.isNullOrEmpty()) {
                val found = vecFiles.firstOrNull { it.name.lowercase().contains("hubert") } ?: vecFiles[0]
                val ok = bridge.loadContentVec(found.absolutePath)
                diag("autoload vec: ${found.name} → $ok")
                if (ok) {
                    vecModel = found.name
                    loadedVec = true
                    showStatus("Auto-loaded ContentVec: ${found.name}")
                    return true
                }
            }
        }
        return false
    }

    fun reportAutoloadStatus() {
        val missing = mutableListOf<String>()
        if (!loadedSynth) missing.add("modello voce ONNX (es. Nicola-Spot.onnx)")
        if (!loadedVec) missing.add("HuBERT ONNX (hubert_base.onnx)")
        if (!loadedF0) missing.add("rmvpe.onnx (con rmvpe_melfb.bin accanto)")
        if (!loadedIndex) missing.add("index FAISS (.fbin/.index, opzionale ma consigliato)")
        if (missing.isEmpty()) {
            showStatus("Modelli RVC pronti (voce + hubert + f0 + index)")
        } else {
            showStatus("Autoload: manca ${missing.joinToString("; ")}", true)
        }
    }

    fun tryAutoLoadF0(): Boolean {
        if (loadedF0) return true
        val searchPaths = ModelArtifactDiscovery.searchDirectories(ctx.getExternalFilesDir(null), ctx.filesDir, modelsDir)

        for (dir in searchPaths) {
            if (!dir.exists()) continue
            val f0Files = dir.listFiles { f -> f.name.lowercase().equals("rmvpe.onnx") }
            if (!f0Files.isNullOrEmpty()) {
                val found = f0Files[0]
                val ok = bridge.loadF0Model(found.absolutePath)
                diag("autoload f0: ${found.name} → $ok")
                if (ok) {
                    loadedF0 = true
                    showStatus("Auto-loaded F0: ${found.name}")
                    return true
                }
            }
        }
        return false
    }

    fun tryAutoLoadRvcArtifacts(): Boolean {
        val searchPaths = ModelArtifactDiscovery.searchDirectories(ctx.getExternalFilesDir(null), ctx.filesDir, modelsDir)
        val artifacts = ModelArtifactDiscovery.discover(searchPaths)
        diag("--- autoload synth: cartella=$modelsDir ---")
        for (dir in searchPaths) {
            val files = if (dir.exists()) (dir.listFiles() ?: emptyArray()).map { it.name } else emptyList()
            diag("percorso ${dir.absolutePath}: esiste=${dir.exists()} file=${files.size} ${files.joinToString(",") { it }.take(400)}")
        }

        val synthFile = artifacts.synth
        diag("scelto synth: ${synthFile?.absolutePath ?: "NESSUNO"}")

        // 0) Prova prima a ricaricare i modelli salvati in rvc_prefs (persistenza tra avvii).
        val savedSynthPath = savedModel("synth_path")
        val savedVecPath = savedModel("vec_path")
        val savedF0Path = savedModel("f0_path")
        val savedIndexPath = savedModel("index_path")

        // Synth: preferisci il path salvato se esiste ancora.
        val synthToLoad = synthFile
            ?: savedSynthPath.takeIf { java.io.File(it).isFile }?.let { java.io.File(it) }
        if (synthToLoad != null && !loadedSynth) {
            val ok = bridge.loadSynthModel(synthToLoad.absolutePath)
            diag("loadSynthModel(${synthToLoad.name}): $ok")
            if (ok) {
                synthModel = bridge.getSynthModelName()
                loadedSynth = true
                persistModel("synth_path", synthToLoad.absolutePath)
                showStatus("Auto-loaded synth model: ${synthToLoad.name}")
            }
        }
        val vecToLoad = artifacts.contentVec
            ?: savedVecPath.takeIf { java.io.File(it).isFile }?.let { java.io.File(it) }
        if (!loadedVec && vecToLoad != null) {
            val ok = bridge.loadContentVec(vecToLoad.absolutePath)
            diag("autoload vec: ${vecToLoad.name} -> $ok")
            if (ok) {
                vecModel = vecToLoad.name
                loadedVec = true
                persistModel("vec_path", vecToLoad.absolutePath)
            }
        }
        val f0ToLoad = artifacts.f0
            ?: savedF0Path.takeIf { java.io.File(it).isFile }?.let { java.io.File(it) }
        if (!loadedF0 && f0ToLoad != null) {
            val ok = bridge.loadF0Model(f0ToLoad.absolutePath)
            diag("autoload f0: ${f0ToLoad.name} -> $ok")
            if (ok) {
                loadedF0 = true
                persistModel("f0_path", f0ToLoad.absolutePath)
            }
        }
        val indexToLoad = artifacts.index
            ?: savedIndexPath.takeIf { java.io.File(it).isFile }?.let { java.io.File(it) }
        if (!loadedIndex && indexToLoad != null) {
            val indexOk = bridge.loadIndex(indexToLoad.absolutePath)
            diag("autoload index: ${indexToLoad.name} -> $indexOk")
            if (indexOk) {
                loadedIndex = true
                persistModel("index_path", indexToLoad.absolutePath)
                showStatus("Auto-loaded index: ${indexToLoad.name}")
            } else if (indexToLoad.extension.equals("index", ignoreCase = true)) {
                showStatus(
                    "Index .index (faiss) NON supportato. Converti in .fbin con tools/export_faiss_bin.py",
                    true
                )
            }
        }
        return loadedSynth && loadedVec && loadedF0
    }

    LaunchedEffect(currentTab) {
        if (currentTab == DAWTab.EDITOR || currentTab == DAWTab.CLOUD || currentTab == DAWTab.STUDIO) {
            refreshRecordings()
        }
        if (currentTab == DAWTab.STUDIO || currentTab == DAWTab.MIXER || currentTab == DAWTab.PROMPTER) {
            if (bridge.isInitialized && !isRunning && !bridge.isMonitoring()) {
                bridge.startMonitoring()
            }
        }
    }

    fun cleanGarbage() {
        val freed = WavEditor.cleanGarbage(ctx)
        val mb = freed.toDouble() / (1024.0 * 1024.0)
        freedStorageStatus = "Pulizia completata: liberati %.1f MB".format(mb)
        showStatus(freedStorageStatus)
    }

    fun syncDropboxConfig(onDone: (() -> Unit)? = null) {
        if (dropboxToken.isBlank()) {
            dropboxConnectionStatus = "Inserisci il token Dropbox per sincronizzare"
            return
        }
        scope.launch {
            dropboxConnectionStatus = "Sincronizzazione in corso..."
            withContext(Dispatchers.IO) {
                // 1. Leggi config.json
                val cfgRes = DropboxClient.downloadText(dropboxToken, dropboxConfigFile)
                cfgRes.onSuccess { cfgText ->
                    try {
                        val json = org.json.JSONObject(cfgText)
                        pitchShift = json.optInt("pitch", pitchShift)
                        indexRate = json.optDouble("index_rate", indexRate.toDouble()).toFloat()
                        protection = json.optDouble("protect", protection.toDouble()).toFloat()
                        val m = json.optString("method", f0Method)
                        if (m.isNotBlank()) f0Method = m
                        val mPath = json.optString("model_path", "")
                        val iPath = json.optString("index_path", "")
                        if (mPath.isNotBlank()) {
                            val name = mPath.substringAfterLast("/").substringAfterLast("\\").removeSuffix(".pth")
                            val fallback = RemoteRvcModel(name = name, modelPath = mPath, indexPath = iPath)
                            if (remoteModels.none { it.name == name || it.modelPath == mPath }) {
                                remoteModels = listOf(fallback) + remoteModels
                            }
                            if (selectedRemoteModel == null) {
                                selectedRemoteModel = fallback
                            }
                        }
                    } catch (e: Exception) {
                        com.rvc.droid.RVCCoreApplication.logToFile("syncDropboxConfig json error: ${e.message}")
                    }
                }

                // 2. Leggi modelli.txt
                val modelsRes = DropboxClient.downloadText(dropboxToken, dropboxModelsFile)
                modelsRes.onSuccess { txt ->
                    val parsed = DropboxClient.parseModels(txt)
                    if (parsed.isNotEmpty()) {
                        remoteModels = parsed
                        if (selectedRemoteModel == null || !parsed.any { it.name == selectedRemoteModel?.name }) {
                            selectedRemoteModel = parsed.firstOrNull()
                        }
                    }
                }

                dropboxConnectionStatus = "Sincronizzato (${remoteModels.size} modelli disponibili)"
            }
            onDone?.invoke()
        }
    }

    fun testDropboxConnection() {
        if (dropboxToken.isBlank()) {
            dropboxConnectionStatus = "Token non inserito"
            return
        }
        scope.launch {
            dropboxConnectionStatus = "Verifica account Dropbox..."
            withContext(Dispatchers.IO) {
                val res = DropboxClient.testConnection(dropboxToken)
                res.fold(
                    onSuccess = { name ->
                        dropboxConnectionStatus = "Connesso come: $name"
                        syncDropboxConfig()
                    },
                    onFailure = { err ->
                        dropboxConnectionStatus = "Errore connessione: ${err.message}"
                    }
                )
            }
        }
    }

    fun applyRemoteRvc(
        clipToConvert: RecordingClip,
        trackIndexToMute: Int? = null,
        targetOffsetMs: Long? = null,
        onFinished: ((File) -> Unit)? = null
    ) {
        if (dropboxToken.isBlank()) {
            showStatus("Inserisci prima il token Dropbox in RVC", true)
            return
        }
        if (remoteProgress in 0f..0.99f) {
            showStatus("Conversione già in corso...", true)
            return
        }

        scope.launch {
            remoteProgress = 0.05f
            remoteProgressText = "1/4: Aggiornamento config.json su Dropbox..."

            val resultFile = withContext(Dispatchers.IO) {
                try {
                    val curModel = selectedRemoteModel
                    val cfgJson = org.json.JSONObject().apply {
                        put("input_dir", dropboxInputDir)
                        put("output_dir", dropboxOutputDir)
                        put("model_path", curModel?.modelPath ?: "")
                        put("index_path", curModel?.indexPath ?: "")
                        put("pitch", pitchShift)
                        put("method", f0Method)
                        put("protect", protection.toDouble())
                        put("index_rate", indexRate.toDouble())
                    }
                    val cfgUpload = DropboxClient.uploadText(dropboxToken, dropboxConfigFile, cfgJson.toString(4))
                    if (cfgUpload.isFailure) {
                        showStatus("Errore config.json: ${cfgUpload.exceptionOrNull()?.message}", true)
                        return@withContext null
                    }

                    // 2. Upload file
                    val srcFile = File(clipToConvert.path)
                    if (!srcFile.exists()) {
                        showStatus("File sorgente non trovato", true)
                        return@withContext null
                    }
                    val cleanBaseName = clipToConvert.name.replace(" ", "_").removeSuffix(".wav")
                    val inWavName = "${cleanBaseName}.wav"
                    val inPath = DropboxConfig(dropboxToken, inputDir = dropboxInputDir).normalizePath(dropboxInputDir, inWavName)
                    val outPath = DropboxConfig(dropboxToken, outputDir = dropboxOutputDir).normalizePath(dropboxOutputDir, inWavName)

                    remoteProgress = 0.15f
                    remoteProgressText = "2/4: Caricamento $inWavName su Dropbox..."

                    // Cancella eventuale vecchio output
                    DropboxClient.deleteFile(dropboxToken, outPath)

                    val uploadRes = DropboxClient.uploadFile(dropboxToken, inPath, srcFile) { p ->
                        remoteProgress = 0.15f + (p * 0.25f)
                    }
                    if (uploadRes.isFailure) {
                        showStatus("Errore upload WAV: ${uploadRes.exceptionOrNull()?.message}", true)
                        return@withContext null
                    }

                    // 3. Polling attesa conversione
                    remoteProgress = 0.45f
                    remoteProgressText = "3/4: Elaborazione RVC su PC in corso..."
                    val startTime = System.currentTimeMillis()
                    var found = false
                    while (System.currentTimeMillis() - startTime < 180_000L) { // max 3 minuti
                        kotlinx.coroutines.delay(2000L)
                        val elapsed = (System.currentTimeMillis() - startTime) / 1000
                        remoteProgressText = "3/4: In elaborazione sul PC... (${elapsed}s)"
                        val check = DropboxClient.checkFileExists(dropboxToken, outPath)
                        if (check.isSuccess && check.getOrDefault(false)) {
                            kotlinx.coroutines.delay(1000L)
                            found = true
                            break
                        }
                    }

                    if (!found) {
                        showStatus("Timeout: Nessun file in AudioOut dopo 3 minuti", true)
                        return@withContext null
                    }

                    // 4. Download file
                    remoteProgress = 0.85f
                    remoteProgressText = "4/4: Download del WAV convertito..."
                    val outDir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
                    val localOut = File(outDir, "RVC_${System.currentTimeMillis()}_$inWavName")
                    val dlRes = DropboxClient.downloadFile(dropboxToken, outPath, localOut) { p ->
                        remoteProgress = 0.85f + (p * 0.14f)
                    }

                    if (dlRes.isFailure || !localOut.exists() || localOut.length() < 44L) {
                        showStatus("Errore download file convertito", true)
                        return@withContext null
                    }

                    DropboxClient.deleteFile(dropboxToken, inPath)
                    localOut
                } catch (e: Exception) {
                    showStatus("Errore processo Dropbox: ${e.message}", true)
                    null
                }
            }

            if (resultFile != null && resultFile.exists() && resultFile.length() > 44L) {
                remoteProgress = 1f
                remoteProgressText = "Conversione completata con successo!"
                val duration = getDurationMs(resultFile)
                val newClip = RecordingClip(
                    name = resultFile.name,
                    path = resultFile.absolutePath,
                    durationMs = duration
                )
                recordings = (recordings + newClip).distinctBy { it.path }
                lastRvcOutputClip = newClip
                refreshRecordings()

                // Aggiunta automatica alla Timeline del Mixer
                val targetTrackIdx = (0..31).firstOrNull { i -> mixerTracks.none { it.trackIndex == i } } ?: 0
                val okImport = bridge.importTrack(newClip.path, newClip.name)
                if (okImport) {
                    val srcTrack = if (trackIndexToMute != null) {
                        mixerTracks.firstOrNull { it.trackIndex == trackIndexToMute }
                    } else {
                        mixerTracks.firstOrNull { it.clipPath == clipToConvert.path }
                    }
                    val finalOffset = targetOffsetMs ?: srcTrack?.offsetMs ?: 0L
                    bridge.setTrackOffset(targetTrackIdx, (finalOffset * 48000L) / 1000L)

                    if (autoMuteOriginal && srcTrack != null) {
                        mixerTracks = mixerTracks.map {
                            if (it.trackIndex == srcTrack.trackIndex) it.copy(muted = true) else it
                        }
                        bridge.setTrackMute(srcTrack.trackIndex, true)
                    }

                    mixerTracks = mixerTracks + com.rvc.droid.ui.project.MixerTrackState(
                        trackIndex = targetTrackIdx,
                        clipPath = newClip.path,
                        clipName = newClip.name,
                        durationMs = duration,
                        offsetMs = finalOffset
                    )
                }

                showStatus("Conversione RVC completata! Nuova traccia aggiunta al mixer.")
                onFinished?.invoke(resultFile)
            } else {
                remoteProgress = -1f
                remoteProgressText = "Conversione fallita."
            }
        }
    }

    val oauthToken by com.rvc.droid.MainActivity.oauthTokenEvent
    LaunchedEffect(oauthToken) {
        val t = oauthToken
        if (!t.isNullOrBlank()) {
            dropboxToken = t
            persistDropbox("dropbox_token", t)
            testDropboxConnection()
            syncDropboxConfig()
            showStatus("Dropbox connesso con successo via OAuth!")
            com.rvc.droid.MainActivity.oauthTokenEvent.value = null
        }
    }

    LaunchedEffect(hasPermission) {
        try { WavEditor.init(ctx.cacheDir) } catch (_: Exception) {}
        try {
            if (!hasPermission) {
                showStatus("Microphone permission is required for live capture", true)
                return@LaunchedEffect
            }
            bridge.initialize()
            com.rvc.droid.RVCCoreApplication.logToFile("Init: engine initialized")
            ProjectManager.initialize(ctx)

            recentProjects = ProjectManager.listProjects().map { f ->
                val state = ProjectManager.loadProject(f)
                ProjectInfo(
                    name = state?.name ?: f.nameWithoutExtension,
                    path = f.absolutePath,
                    date = state?.createdAt ?: "",
                    size = "${f.length() / 1024}KB"
                )
            }

            refreshRecordings()
            if (dropboxToken.isNotBlank()) {
                syncDropboxConfig()
            }
            val monOk = bridge.startMonitoring()
            com.rvc.droid.RVCCoreApplication.logToFile("Init: startMonitoring=$monOk isMonitoring=${bridge.isMonitoring()}")
            // Niente autoload dei modelli all'avvio: la conversione e' remota.
            // Crea solo la prima traccia armabile se il progetto e' vuoto.
            if (mixerTracks.isEmpty()) {
                val firstIndex = bridge.addTrack("Voce 1")
                if (firstIndex >= 0) {
                    mixerTracks = listOf(
                        MixerTrackState(
                            trackIndex = firstIndex,
                            clipPath = "",
                            clipName = "Voce 1",
                            durationMs = 0L,
                        )
                    )
                    armedTrackIndex = firstIndex
                }
            }
            val monAfter = if (!bridge.isMonitoring()) {
                com.rvc.droid.RVCCoreApplication.logToFile("Init: monitoring stopped, restarting")
                bridge.startMonitoring()
            } else true
            if (!monOk || !monAfter) showStatus("Monitoring failed to start", true)
        } catch (e: Exception) {
            android.util.Log.e("MainScreen", "Init error: ${e.message}")
        }
    }

    fun copyToInternal(uri: Uri, prefix: String = "asset", requiredExt: String = ""): String? {
        return try {
            val inputStream = ctx.contentResolver.openInputStream(uri) ?: return null
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "${prefix}_${System.currentTimeMillis()}"
            var safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            // Se il file SAF non ha estensione (succede con picker Android
            // perché lastPathSegment spesso ritorna un id numerico), forziamo
            // quella richiesta. Senza questo, file ONNX verrebbero salvati come
            // "1788..._msf_1000381856" senza .onnx e rifiutati dal magic bytes
            // check. Esempio: ContentResolver ritorna "1000381857" per il
            // picker di Nicola_Spot_Locali.onnx.
            if (requiredExt.isNotBlank() && !safeName.lowercase().endsWith(requiredExt.lowercase())) {
                safeName = safeName + requiredExt
            }
            val outFile = File(ctx.filesDir, "${System.currentTimeMillis()}_$safeName")
            outFile.outputStream().use { output -> inputStream.copyTo(output) }
            inputStream.close()
            com.rvc.droid.RVCCoreApplication.logToFile("copyToInternal: ${outFile.absolutePath} (${outFile.length() / 1024} KB)")
            outFile.absolutePath
        } catch (e: Exception) {
            com.rvc.droid.RVCCoreApplication.logToFile("copyToInternal failed: ${e.message}")
            null
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val localPath = copyToInternal(it)
            if (localPath != null) {
                videoPath = localPath
                showStatus("Video loaded")
            }
        }
    }

    val synthPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val localPath = copyToInternal(it, "synth", requiredExt = ".onnx")
            if (localPath != null) {
                scope.launch {
                    try {
                        val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { bridge.loadSynthModel(localPath) }
                        if (ok) {
                            synthModel = bridge.getSynthModelName()
                            loadedSynth = true
                            lastError = null
                            showStatus("Modello RVC ONNX caricato")
                            tryAutoLoadContentVec()
                            tryAutoLoadRvcArtifacts()
                        } else {
                            lastError = "Failed to load synth model"
                            showStatus(lastError!!, true)
                        }
                    } catch (e: Exception) {
                        lastError = e.message ?: "Failed to load synth model"
                        showStatus(lastError!!, true)
                    }
                }
            }
        }
    }

    val vecPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val localPath = copyToInternal(it, "vec", requiredExt = ".onnx")
            if (localPath != null) {
                scope.launch {
                    try {
                        val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { bridge.loadContentVec(localPath) }
                        if (ok) {
                            vecModel = bridge.getVecModelName()
                            loadedVec = true
                            lastError = null
                            showStatus("ContentVec loaded")
                        } else {
                            lastError = "Failed to load ContentVec"
                            showStatus(lastError!!, true)
                        }
                    } catch (e: Exception) {
                        lastError = e.message ?: "Failed to load ContentVec"
                            showStatus(lastError!!, true)
                    }
                }
            }
        }
    }

    val indexPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val localPath = copyToInternal(it)
            if (localPath != null) {
                scope.launch {
                    try {
                        val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { bridge.loadIndex(localPath) }
                        if (ok) {
                            loadedIndex = true
                            lastError = null
                            showStatus("Index loaded")
                        } else {
                            lastError = "Failed to load index"
                            showStatus(lastError!!, true)
                        }
                    } catch (e: Exception) {
                        lastError = e.message ?: "Failed to load index"
                        showStatus(lastError!!, true)
                    }
                }
            }
        }
    }

    val f0Picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val localPath = copyToInternal(it, "rmvpe", requiredExt = ".onnx")
            if (localPath != null) {
                scope.launch {
                    try {
                        val melFile = File(ctx.filesDir, "rmvpe_melfb.bin")
                        if (!melFile.exists()) {
                            try {
                                ctx.assets.open("rmvpe_melfb.bin").use { input ->
                                    melFile.outputStream().use { output -> input.copyTo(output) }
                                }
                            } catch (_: Exception) {}
                        }
                        val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { bridge.loadF0Model(localPath) }
                        if (ok) {
                            loadedF0 = true
                            lastError = null
                            showStatus("F0 (rmvpe) caricato")
                        } else {
                            lastError = "Failed to load F0 model (serve rmvpe_melfb.bin accanto al .onnx)"
                            showStatus(lastError!!, true)
                        }
                    } catch (e: Exception) {
                        lastError = e.message ?: "Failed to load F0 model"
                        showStatus(lastError!!, true)
                    }
                }
            }
        }
    }

    val modelsDirPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: android.net.Uri? ->
        uri?.let { u ->
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    u, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            // Salva l'URI SAF (sorgente di verità) + prova a ricostruire il path
            // (per compatibilità con l'autoload File-based)
            val docId = try { android.provider.DocumentsContract.getTreeDocumentId(u) } catch (_: Exception) { "" }
            val path = if (docId.startsWith("primary:")) {
                "/storage/emulated/0/" + docId.removePrefix("primary:")
            } else if (docId.isNotEmpty() && docId.contains(":")) {
                "/storage/emulated/0/" + docId.substringAfter(":")
            } else ""
            persistModelsSafUri(u.toString())
            persistModelsDir(path)
            modelsDir = path
            showStatus("Cartella modelli: $path — copio modelli base via SAF")
            loadedSynth = false; loadedVec = false; loadedF0 = false; loadedIndex = false
            scope.launch {
                val results = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.rvc.droid.RVCCoreApplication.copyBaseModelsFromSafTree(u)
                }
                showStatus("SAF copy: ${results.joinToString(" | ")}")
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    tryAutoLoadRvcArtifacts()
                    tryAutoLoadContentVec()
                    tryAutoLoadF0()
                }
                reportAutoloadStatus()
            }
        }
    }

    val backingPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val localPath = copyToInternal(it)
            if (localPath != null) {
                scope.launch {
                    try {
                        val ok = bridge.loadBackingTrack(localPath)
                        backingLoaded = ok
                        lastError = if (ok) null else "Failed to load backing track"
                        if (ok) showStatus("Backing track loaded") else showStatus(lastError!!, true)
                    } catch (e: Exception) {
                        lastError = e.message ?: "Failed to load backing track"
                        showStatus(lastError!!, true)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        // Attesa fissa: l'autoload iniziale (subito dopo bridge.initialize)
        // avviene gia' in Init(). Qui riprendiamo dopo 3s per:
        // 1) Se l'utente ha gia' scelto una cartella in una sessione
        //    precedente, l'URI SAF e' in rvc_prefs/models_saf_uri. Lo
        //    riusiamo per copiare i modelli base via ContentResolver
        //    (funziona anche con EACCES su /Download/Modelli/Android 11+).
        // 2) Se i modelli base sono gia' presenti in
        //    getExternalFilesDir/models/, autoload li trova al primo colpo.
        kotlinx.coroutines.delay(3_000)
        if (!bridge.isInitialized) return@LaunchedEffect
        val anyMissing = !loadedSynth || !loadedVec || !loadedF0
        if (!anyMissing) return@LaunchedEffect
        // Prova prima a riusare l'URI SAF persistito.
        val safUri = savedModelsSafUri()
        if (safUri.isNotBlank()) {
            val u = try { android.net.Uri.parse(safUri) } catch (_: Exception) { null }
            if (u != null) {
                val stillGranted = ctx.contentResolver.persistedUriPermissions.any { it.uri == u && it.isReadPermission }
                if (stillGranted) {
                    val results = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.rvc.droid.RVCCoreApplication.copyBaseModelsFromSafTree(u)
                    }
                    com.rvc.droid.RVCCoreApplication.logToFile("autoload delayed SAF copy: ${results.joinToString(" | ")}")
                }
            }
        }
        // Poi autoload classico (cerca in /Download/Modelli + dirs)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (!loadedSynth) tryAutoLoadRvcArtifacts()
            if (!loadedVec) tryAutoLoadContentVec()
            if (!loadedF0) tryAutoLoadF0()
        }
        reportAutoloadStatus()
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            if (bridge.isInitialized) {
                try {
                    val l = bridge.getLufs()
                    momentary = l.momentary; shortTerm = l.shortTerm; integrated = l.integrated; truePeak = l.truePeak
                    compGR = try { bridge.getCompressorGR() } catch (_: Exception) { 0f }
                    limiterGR = try { bridge.getLimiterGR() } catch (_: Exception) { 0f }
                    gateGR = try { bridge.getNoiseGateGR() } catch (_: Exception) { 0f }
                    val amp = try { bridge.getInputLevel() } catch (_: Exception) { null }
                    if (amp != null) {
                        inputLevel = InputLevel(
                            left = amp.left, right = amp.right,
                            peakLeft = amp.peakLeft, peakRight = amp.peakRight, clipping = amp.clipping
                        )
                    }
                    if (isRunning || bridge.isMonitoring()) {
                        try {
                            waveformPeaks = bridge.getWaveformPeaks()
                            val total = bridge.getWaveformTotalPeaks()
                            if (total > 0) {
                                waveformProgress = (bridge.getWaveformWritePos() % 4096).toFloat() / 4096f
                            }
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun sendRvcParams() {
        bridge.setRvcParams(
            formantShift,
            when (f0Method) {
                "harvest" -> 1
                "dio" -> 2
                else -> 0
            },
            indexRate, protection, noiseScale
        )
    }

    val exportFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val sourceFile = pendingExportFile
                    if (sourceFile != null && sourceFile.exists()) {
                        ctx.contentResolver.openOutputStream(selectedUri)?.use { outStream ->
                            sourceFile.inputStream().use { it.copyTo(outStream) }
                        }
                        showStatus("Exported successfully to selected location")
                    }
                } catch (e: Exception) {
                    showStatus("Failed to save to selected location", true)
                } finally {
                    pendingExportFile = null
                }
            }
        }
    }

    // ------------------------------------------------------------- DAW actions
    fun createStudioTrack(name: String = "Voce"): Int {
        if (!bridge.isInitialized) bridge.initialize()
        val index = bridge.addTrack(name)
        if (index < 0) {
            showStatus("Impossibile creare la traccia (limite 32?)", true)
            return -1
        }
        mixerTracks = mixerTracks + MixerTrackState(
            trackIndex = index,
            clipPath = "",
            clipName = name,
            durationMs = 0L,
        )
        bridge.setTrackVolume(index, 0.85f)
        armedTrackIndex = index
        return index
    }

    fun addStudioTrack() {
        createStudioTrack("Voce ${mixerTracks.size + 1}")
    }

    fun removeStudioTrack(index: Int) {
        if (mixerTracks.none { it.trackIndex == index }) return
        bridge.removeTrack(index)
        mixerTracks = mixerTracks
            .filter { it.trackIndex != index }
            .map { if (it.trackIndex > index) it.copy(trackIndex = it.trackIndex - 1) else it }
        if (armedTrackIndex == index) {
            armedTrackIndex = mixerTracks.firstOrNull()?.trackIndex
        } else if (armedTrackIndex != null && armedTrackIndex!! > index) {
            armedTrackIndex = armedTrackIndex!! - 1
        }
        if (selectedTrackIdx > index) selectedTrackIdx -= 1
        selectedTimelineClipId = selectedTimelineClipId?.takeIf { id -> mixerTracks.any { it.clipId == id } }
    }

    fun importRecordingIntoTrack(file: File, track: MixerTrackState): Boolean {
        val name = track.clipName.ifBlank { "Voce ${track.trackIndex + 1}" }
        val ok = bridge.importTrackAt(track.trackIndex, file.absolutePath, name)
        if (!ok) return false
        val duration = getDurationMs(file)
        mixerTracks = mixerTracks.map {
            if (it.trackIndex == track.trackIndex) {
                it.copy(clipPath = file.absolutePath, clipName = name, durationMs = duration, offsetMs = 0L)
            } else it
        }
        bridge.setTrackOffset(track.trackIndex, 0L)
        return true
    }

    val onToggleRecording: () -> Unit = {
        scope.launch {
            try {
                if (!hasPermission) {
                    showStatus("Permesso microfono negato: abilitalo nelle impostazioni Android", true)
                    return@launch
                }
                if (!bridge.isInitialized) bridge.initialize()
                if (isRunning) {
                    bridge.stopRecording()
                    delay(120)
                    if (recordingStartedAudio) {
                        bridge.stopAudio()
                        if (!bridge.startMonitoring()) showStatus("Failed to switch back to monitoring", true)
                        recordingStartedAudio = false
                    }
                    videoIsPlaying = false
                    val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
                    val newest = dir.listFiles { f -> f.extension == "wav" }?.maxByOrNull { it.lastModified() }
                    val target = mixerTracks.firstOrNull { it.trackIndex == armedTrackIndex }
                    if (newest != null && target != null) {
                        if (importRecordingIntoTrack(newest, target)) {
                            selectedTimelineClipId = target.clipId
                            selectedTrackIdx = target.trackIndex
                        } else {
                            showStatus("WAV salvato ma non caricato nella traccia armata", true)
                        }
                    }
                    recordings = dir.listFiles { f -> f.extension == "wav" }?.map {
                        RecordingClip(name = it.name, path = it.absolutePath, durationMs = getDurationMs(it))
                    } ?: emptyList()
                    showStatus("Recording saved (${recordings.size} total)")
                } else {
                    val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
                    val recPath = File(dir, "rec_${System.currentTimeMillis()}.wav").absolutePath

                    // Assicura una traccia armata: la registrazione finisce li'.
                    if (armedTrackIndex == null || mixerTracks.none { it.trackIndex == armedTrackIndex }) {
                        if (mixerTracks.isEmpty()) createStudioTrack("Voce 1")
                        else armedTrackIndex = mixerTracks.first().trackIndex
                    }

                    // Sync backing track
                    if (backingLoaded) {
                        bridge.setBackingTrackPosition((backingPosition * 48000 * 60).toLong())
                    }

                    // Se il monitor e' gia' attivo, registra dallo stesso flusso audio:
                    // fermarlo e riaprirlo causava file muti su alcuni telefoni.
                    val monitoringWasRunning = bridge.isMonitoring()
                    recordingStartedAudio = false
                    if (!monitoringWasRunning) {
                        bridge.stopAudio()
                        delay(150)
                        // Voce PULITA: la conversione RVC avviene sul PC.
                        val started = bridge.startAudio(false)
                        if (!started) {
                            showStatus("Failed to start audio engine", true)
                            if (!bridge.startMonitoring()) showStatus("Also failed to restart monitoring", true)
                            return@launch
                        }
                        recordingStartedAudio = true
                        delay(250) // lascia aprire lo stream del microfono
                    }

                    val recStarted = bridge.startRecording(recPath, "16", false, false)
                    if (!recStarted) {
                        showStatus("Registrazione non avviata: controlla il microfono", true)
                        if (recordingStartedAudio) {
                            bridge.stopAudio()
                            bridge.startMonitoring()
                            recordingStartedAudio = false
                        }
                        return@launch
                    }
                    if (videoPath != null) {
                        videoPositionMs = (backingPosition * 60 * 1000).toLong()
                        videoIsPlaying = true
                    }
                    showStatus("Recording started")
                }
                isRunning = !isRunning
            } catch (e: Exception) {
                showStatus("Audio error: ${e.message}", true)
            }
        }
    }

    // ------------------------------------------------------- progetti Dropbox
    fun refreshRemoteBundles() {
        if (dropboxToken.isBlank() || bundleListing) return
        bundleListing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                DropboxClient.listFolder(dropboxToken, "/TestRVC/Projects")
            }
            result.onSuccess { entries ->
                remoteBundles = entries.filter { it.isFile && it.name.lowercase().endsWith(".zip") }
            }.onFailure {
                showStatus("Elenco progetti Dropbox non disponibile: ${it.message}", true)
            }
            bundleListing = false
            bundleListingLoaded = true
        }
    }

    fun uploadProjectBundle() {
        if (dropboxToken.isBlank()) {
            showStatus("Collega prima Dropbox (sezione RVC remoto)", true)
            cloudSection = 0
            return
        }
        if (bundleBusy) return
        bundleBusy = true
        bundleProgress = 0f
        bundleStatus = "Preparazione bundle..."
        scope.launch {
            val projectName = currentProjectName.ifBlank { ProjectManager.defaultProjectName() }
            val zip = File(ctx.cacheDir, ProjectBundle.suggestedZipName(projectName))
            val exportResult = withContext(Dispatchers.IO) {
                ProjectBundle.export(ctx, currentProjectState(projectName), zip)
            }
            val exported = exportResult.getOrElse {
                bundleBusy = false
                bundleStatus = "Errore: ${it.message}"
                showStatus("Bundle non creato: ${it.message}", true)
                return@launch
            }
            bundleStatus = "Pubblicazione su Dropbox (%.1f MB)...".format(exported.sizeBytes / 1024.0 / 1024.0)
            withContext(Dispatchers.IO) {
                DropboxClient.createFolder(dropboxToken, "/TestRVC/Projects")
            }
            val remotePath = "/TestRVC/Projects/${zip.name}"
            val uploadResult = withContext(Dispatchers.IO) {
                DropboxClient.uploadFile(dropboxToken, remotePath, zip) { progress ->
                    scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { bundleProgress = progress }
                }
            }
            uploadResult.onSuccess {
                bundleStatus = "Bundle caricato: ${zip.name}"
                showStatus("Progetto caricato su Dropbox: ${zip.name}")
                refreshRemoteBundles()
            }.onFailure {
                bundleStatus = "Errore upload: ${it.message}"
                showStatus("Upload progetto fallito: ${it.message}", true)
            }
            zip.delete()
            bundleBusy = false
        }
    }

    fun downloadProjectBundle(entry: RemoteEntry) {
        if (dropboxToken.isBlank() || bundleBusy) return
        bundleBusy = true
        bundleProgress = 0f
        bundleStatus = "Download di ${entry.name}..."
        scope.launch {
            val zip = File(ctx.cacheDir, "remote_${entry.name}")
            val download = withContext(Dispatchers.IO) {
                DropboxClient.downloadFile(dropboxToken, entry.path, zip)
            }
            download.onFailure {
                bundleBusy = false
                bundleStatus = "Download fallito: ${it.message}"
                showStatus("Download progetto fallito: ${it.message}", true)
                return@launch
            }
            bundleStatus = "Import del progetto..."
            val imported = withContext(Dispatchers.IO) { ProjectBundle.import(ctx, zip) }
            imported.onSuccess { result ->
                loadProjectByPath(result.projectFile.absolutePath)
                bundleStatus = "Progetto importato: ${result.projectName}"
                showStatus("Progetto importato: ${result.projectName} (${result.clips} clip)")
                cloudSection = 0
            }.onFailure {
                bundleStatus = "Import fallito: ${it.message}"
                showStatus("Import progetto fallito: ${it.message}", true)
            }
            zip.delete()
            bundleBusy = false
        }
    }

    fun deleteRemoteBundle(entry: RemoteEntry) {
        if (dropboxToken.isBlank() || bundleBusy) return
        scope.launch {
            val result = withContext(Dispatchers.IO) { DropboxClient.deleteFile(dropboxToken, entry.path) }
            result.onSuccess {
                remoteBundles = remoteBundles.filterNot { it.path == entry.path }
                showStatus("Bundle remoto eliminato: ${entry.name}")
            }.onFailure { showStatus("Eliminazione fallita: ${it.message}", true) }
        }
    }

    LaunchedEffect(currentTab, cloudSection, dropboxToken) {
        if (currentTab == DAWTab.CLOUD && cloudSection == 1 && !bundleListingLoaded && dropboxToken.isNotBlank()) {
            refreshRemoteBundles()
        }
    }

    // ------------------------------------------------- controllo remoto del PC
    fun refreshPcStatus() {
        if (dropboxToken.isBlank() || pcStatusLoading) return
        pcStatusLoading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                DropboxClient.downloadText(dropboxToken, "/TestRVC/status.json")
            }
            result.onSuccess { text ->
                pcStatus = PcStatusParser.parse(text)
                pcStatusError = if (pcStatus == null) "status.json presente ma non valido" else ""
            }.onFailure {
                pcStatusError = "PC non raggiungibile: ${it.message}"
            }
            pcStatusLoading = false
        }
    }

    fun remoteF0Method(): String = if (f0Method == "dio") "rmvpe" else f0Method

    fun sendRemoteConfigToPc() {
        if (dropboxToken.isBlank() || remoteConfigBusy) return
        remoteConfigBusy = true
        remoteConfigStatus = "Invio configurazione al PC…"
        scope.launch {
            val payload = org.json.JSONObject().apply {
                put("input_dir", dropboxInputDir)
                put("output_dir", dropboxOutputDir)
                selectedRemoteModel?.modelPath?.takeIf { it.isNotBlank() }?.let { put("model_path", it) }
                selectedRemoteModel?.indexPath?.takeIf { it.isNotBlank() }?.let { put("index_path", it) }
                put("pitch", pitchShift)
                put("method", remoteF0Method())
                put("protect", protection.toDouble())
                put("index_rate", indexRate.toDouble())
                put("model_version", "v2")
                put("output_format", "wav")
            }
            val result = withContext(Dispatchers.IO) {
                DropboxClient.uploadText(dropboxToken, dropboxConfigFile, payload.toString(2))
            }
            result.onSuccess {
                remoteConfigStatus = "Inviata. Attendo che il PC la applichi…"
                delay(6000)
                refreshPcStatus()
                remoteConfigStatus = "Config inviata: il PC la applica con hot-reload."
            }.onFailure {
                remoteConfigStatus = "Invio fallito: ${it.message}"
            }
            remoteConfigBusy = false
        }
    }

    fun readRemoteConfigFromPc() {
        if (dropboxToken.isBlank() || remoteConfigBusy) return
        remoteConfigBusy = true
        remoteConfigStatus = "Lettura config dal PC…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                DropboxClient.downloadText(dropboxToken, dropboxConfigFile)
            }
            result.onSuccess { text ->
                try {
                    val root = org.json.JSONObject(text)
                    pitchShift = root.optInt("pitch", pitchShift)
                    val method = root.optString("method", f0Method)
                    f0Method = when (method) {
                        "harvest" -> "harvest"
                        "crepe", "pm", "rmvpe" -> "rmvpe"
                        else -> f0Method
                    }
                    protection = root.optDouble("protect", protection.toDouble()).toFloat()
                    indexRate = root.optDouble("index_rate", indexRate.toDouble()).toFloat()
                    val modelPath = root.optString("model_path", "")
                    if (modelPath.isNotBlank()) {
                        remoteModels.firstOrNull { it.modelPath == modelPath }?.let { selectedRemoteModel = it }
                    }
                    remoteConfigStatus = "Config letta dal PC: pitch ${pitchShift}, metodo ${remoteF0Method()}, index ${"%.0f".format(indexRate * 100)}%"
                    sendRvcParams()
                } catch (e: Exception) {
                    remoteConfigStatus = "Config non valida: ${e.message}"
                }
            }.onFailure {
                remoteConfigStatus = "Lettura fallita: ${it.message}"
            }
            remoteConfigBusy = false
        }
    }

    LaunchedEffect(currentTab, cloudSection, dropboxToken) {
        if (currentTab == DAWTab.CLOUD && cloudSection == 2 && dropboxToken.isNotBlank()) {
            while (true) {
                refreshPcStatus()
                delay(10_000)
            }
        }
    }

    if (showStudioPluginRack) {
        Dialog(
            onDismissRequest = { showStudioPluginRack = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = RVCColors.bg) {
                PluginRackView(
                    onClose = { showStudioPluginRack = false },
                    gateState = gateState,
                    gateGR = gateGR,
                    voiceBands = voiceBands,
                    musicBands = musicBands,
                    compThreshold = compThreshold,
                    compRatio = compRatio,
                    compAttack = compAttack,
                    compRelease = compRelease,
                    compMakeup = compMakeup,
                    compGR = compGR,
                    limiterThreshold = limiterThreshold,
                    limiterRelease = limiterRelease,
                    limiterGR = limiterGR,
                    reverbMix = reverbMix,
                    reverbRoomSize = reverbRoomSize,
                    reverbDamping = reverbDamping,
                    voiceVolume = voiceVol,
                    musicVolume = musicVol,
                    masterVolume = masterVol,
                    monitoringGain = monitoringGain,
                    momentary = momentary,
                    shortTerm = shortTerm,
                    integrated = integrated,
                    truePeak = truePeak,
                    onGateToggle = {
                        val s = gateState.copy(enabled = !gateState.enabled)
                        gateState = s
                        bridge.setNoiseGate(s.enabled, s.threshold, s.attackMs, s.holdMs, s.releaseMs)
                    },
                    onGateThreshold = {
                        val s = gateState.copy(threshold = it)
                        gateState = s
                        bridge.setNoiseGate(s.enabled, s.threshold, s.attackMs, s.holdMs, s.releaseMs)
                    },
                    onVoiceBandUpdate = { i, band ->
                        voiceBands = voiceBands.toMutableList().also { it[i] = band }
                        bridge.setVoiceEQ(voiceBands.map { NativeAudioBridge.EQBand(it.freq, it.gainDB, it.q) })
                    },
                    onMusicBandUpdate = { i, band ->
                        musicBands = musicBands.toMutableList().also { it[i] = band }
                        bridge.setMusicEQ(musicBands.map { NativeAudioBridge.EQBand(it.freq, it.gainDB, it.q) })
                    },
                    onCompThreshold = { compThreshold = it; bridge.setSideChain(it, compRatio, compAttack, compRelease, compMakeup) },
                    onCompRatio = { compRatio = it; bridge.setSideChain(compThreshold, it, compAttack, compRelease, compMakeup) },
                    onCompAttack = { compAttack = it; bridge.setSideChain(compThreshold, compRatio, it, compRelease, compMakeup) },
                    onCompRelease = { compRelease = it; bridge.setSideChain(compThreshold, compRatio, compAttack, it, compMakeup) },
                    onCompMakeup = { compMakeup = it; bridge.setSideChain(compThreshold, compRatio, compAttack, compRelease, it) },
                    onLimiterThreshold = { limiterThreshold = it; bridge.setLimiter(it, limiterRelease) },
                    onLimiterRelease = { limiterRelease = it; bridge.setLimiter(limiterThreshold, it) },
                    onReverbMix = { reverbMix = it; bridge.setReverbMix(it) },
                    onReverbRoomSize = { reverbRoomSize = it; bridge.setReverbRoomSize(it) },
                    onReverbDamping = { reverbDamping = it; bridge.setReverbDamping(it) },
                    onVoiceVolume = { voiceVol = it; bridge.setVolumes(it, musicVol, masterVol) },
                    onMusicVolume = { musicVol = it; bridge.setVolumes(voiceVol, it, masterVol) },
                    onMasterVolume = { masterVol = it; bridge.setVolumes(voiceVol, musicVol, it) },
                    onMonitoringGain = { monitoringGain = it; bridge.setMonitoringGain(it) },
                    onResetMastering = { if (bridge.isInitialized) bridge.masteringReset() },
                )
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            DAWTopBar(
                projectName = currentProjectName,
                isRecording = isRunning,
                statusLabel = if (lastSavedAt != null) "SALVATO" else "PROGETTO",
                statusColor = if (lastSavedAt != null) RVCColors.success else RVCColors.textMute,
                onSave = { saveProject() },
                onSettings = { refreshDevices(); showSettingsDialog = true },
            )
        },
        bottomBar = {
            DAWBottomNavigation(
                selectedTab = currentTab,
                onTabSelected = { tab ->
                    currentTab = tab
                    if (tab == DAWTab.CLOUD && cloudSection == 1 && !bundleListingLoaded) {
                        refreshRemoteBundles()
                    }
                },
            )
        }
    ) { paddingValues ->
        Box(Modifier.padding(paddingValues)) {
            Column(Modifier.fillMaxSize()) {
                if (statusMessage != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = if (statusIsError) RVCColors.accentOrange.copy(alpha = 0.2f) else RVCColors.accentGreen.copy(alpha = 0.15f))
                    ) {
                        Text(
                            statusMessage!!,
                            color = if (statusIsError) RVCColors.accentOrange else RVCColors.accentGreen,
                            modifier = Modifier.padding(10.dp),
                            fontSize = 12.sp
                        )
                    }
                }

                Box(Modifier.weight(1f)) {
                    when (currentTab) {
                        DAWTab.STUDIO -> StudioWorkspace(
                            isRunning = isRunning,
                            inputLevel = inputLevel,
                            recordings = recordings,
                            tracks = mixerTracks,
                            armedTrackIndex = armedTrackIndex,
                            selectedClipId = selectedTimelineClipId,
                            timelinePositionMs = timelinePositionMs,
                            timelineDurationMs = timelineDurationMs,
                            timelinePlaying = timelinePlaying,
                            waveformPeaks = waveformPeaks,
                            waveformProgress = waveformProgress,
                            videoPath = videoPath,
                            videoPlaying = videoIsPlaying,
                            showVideo = showStudioVideo,
                            onToggleVideo = { showStudioVideo = !showStudioVideo },
                            onLoadVideo = { videoPicker.launch(arrayOf("video/*")) },
                            onPlayPauseVideo = { videoIsPlaying = !videoIsPlaying },
                            onStopVideo = { videoIsPlaying = false; videoPositionMs = 0L },
                            onVideoPositionChanged = { videoPositionMs = it },
                            onStartStop = onToggleRecording,
                            onPlayTimeline = {
                                bridge.resetMixerPosition()
                                timelinePositionMs = 0L
                                timelinePlaying = true
                                bridge.startAudio(false)
                            },
                            onStopTimeline = {
                                timelinePlaying = false
                                bridge.stopAudio()
                                bridge.startMonitoring()
                            },
                            onSeekTimeline = { ms ->
                                timelinePositionMs = ms.coerceAtLeast(0L)
                                bridge.setMixerPositionMs(timelinePositionMs)
                            },
                            onAddTrack = { addStudioTrack() },
                            onRemoveTrack = { removeStudioTrack(it) },
                            onArmTrack = { index -> armedTrackIndex = index },
                            onSelectTrack = { selectedTrackIdx = it },
                            onSelectClip = { clipId ->
                                selectedTimelineClipId = clipId
                                mixerTracks.firstOrNull { it.clipId == clipId }?.let { selectedTrackIdx = it.trackIndex }
                            },
                            onTrackVolume = { idx, vol ->
                                mixerTracks = mixerTracks.map { if (it.trackIndex == idx) it.copy(volume = vol) else it }
                                bridge.setTrackVolume(idx, vol)
                            },
                            onTrackPan = { idx, pan ->
                                mixerTracks = mixerTracks.map { if (it.trackIndex == idx) it.copy(pan = pan) else it }
                                bridge.setTrackPan(idx, pan)
                            },
                            onTrackMute = { idx, muted ->
                                mixerTracks = mixerTracks.map { if (it.trackIndex == idx) it.copy(muted = muted) else it }
                                bridge.setTrackMute(idx, muted)
                            },
                            onTrackSolo = { idx, solo ->
                                mixerTracks = mixerTracks.map { if (it.trackIndex == idx) it.copy(solo = solo) else it }
                                bridge.setTrackSolo(idx, solo)
                            },
                            onTrackOffsetChange = { idx, clipId, offset ->
                                mixerTracks = mixerTracks.map {
                                    if (it.clipId == clipId) it.copy(offsetMs = offset.coerceAtLeast(0L)) else it
                                }
                                bridge.setTrackOffset(idx, offset.coerceAtLeast(0L) * 48)
                            },
                            voiceVolume = voiceVol,
                            musicVolume = musicVol,
                            masterVolume = masterVol,
                            momentary = momentary,
                            shortTerm = shortTerm,
                            integrated = integrated,
                            truePeak = truePeak,
                            onVoiceVolume = { voiceVol = it; bridge.setVolumes(it, musicVol, masterVol) },
                            onMusicVolume = { musicVol = it; bridge.setVolumes(voiceVol, it, masterVol) },
                            onMasterVolume = { masterVol = it; bridge.setVolumes(voiceVol, musicVol, it) },
                            onOpenPluginRack = { showStudioPluginRack = true },
                            backingLoaded = backingLoaded,
                            backingGain = backingGain,
                            backingPosition = backingPosition,
                            onLoadBacking = { backingPicker.launch(arrayOf("*/*")) },
                            onBackingGain = { backingGain = it; bridge.setBackingTrackGain(it) },
                            onBackingSeek = {
                                backingPosition = it
                                if (videoPath != null) videoPositionMs = (it * 60 * 1000).toLong()
                            },
                            gobboText = teleprompterText,
                            onGobboTextChange = { teleprompterText = it },
                            gobboFontSize = teleprompterFontSize,
                            onGobboFontSizeChange = { teleprompterFontSize = it },
                            gobboSpeed = teleprompterScrollSpeed,
                            onGobboSpeedChange = { teleprompterScrollSpeed = it },
                            showGobbo = showTeleprompter,
                            onToggleGobbo = { showTeleprompter = !showTeleprompter },
                            onExport = { showExportDialog = true },
                            onExportClip = { clip ->
                                exportSourceClip = clip
                                showExportDialog = true
                            },
                            onDeleteClip = { clip ->
                                File(clip.path).delete()
                                recordings = recordings.filter { it.id != clip.id }
                            },
                        )

                        DAWTab.EDITOR -> EditorView(
                            selectedClip = selectedClip,
                            clips = recordings,
                            onSelectClip = { selectedClip = it },
                            onSilence = { start, end ->
                                selectedClip?.let {
                                    if (WavEditor.silence(it.path, start, end)) {
                                        showStatus("Silenced selection")
                                    } else {
                                        showStatus("Silence failed: file not WAV/readable", true)
                                    }
                                }
                            },
                            onTrim = { start, end ->
                                selectedClip?.let {
                                    if (WavEditor.trimInPlace(it.path, start, end)) {
                                        val newDur = end - start
                                        recordings = recordings.map { r ->
                                            if (r.id == it.id) r.copy(durationMs = newDur) else r
                                        }
                                        selectedClip = recordings.find { r -> r.id == it.id }
                                        showStatus("Trimmed clip")
                                    } else {
                                        showStatus("Trim failed: file not readable or empty selection", true)
                                    }
                                }
                            },
                            onCut = { start, end ->
                                selectedClip?.let {
                                    val data = WavEditor.cutSelection(it.path, start, end)
                                    if (data != null) {
                                        editorClipboard = data
                                        showStatus("Cut to clipboard")
                                    }
                                }
                            },
                            onCopy = { start, end ->
                                selectedClip?.let {
                                    val data = WavEditor.copySelection(it.path, start, end)
                                    if (data != null) {
                                        editorClipboard = data
                                        showStatus("Copied to clipboard")
                                    }
                                }
                            },
                            onPaste = { insertMs ->
                                selectedClip?.let {
                                    val clip = editorClipboard
                                    if (clip != null) {
                                        if (WavEditor.pasteAt(it.path, insertMs, clip)) {
                                            showStatus("Pasted")
                                        }
                                    } else {
                                        showStatus("Clipboard empty", true)
                                    }
                                }
                            },
                            onUndo = {
                                selectedClip?.let {
                                    if (WavEditor.undo(it.path)) {
                                        val info = WavEditor.getAudioInfo(it.path)
                                        if (info != null) {
                                            recordings = recordings.map { r ->
                                                if (r.id == it.id) r.copy(durationMs = info.durationMs) else r
                                            }
                                            selectedClip = recordings.find { r -> r.id == it.id }
                                        }
                                        showStatus("Undone")
                                    }
                                }
                            },
                            onRedo = {
                                selectedClip?.let {
                                    if (WavEditor.redo(it.path)) {
                                        val info = WavEditor.getAudioInfo(it.path)
                                        if (info != null) {
                                            recordings = recordings.map { r ->
                                                if (r.id == it.id) r.copy(durationMs = info.durationMs) else r
                                            }
                                            selectedClip = recordings.find { r -> r.id == it.id }
                                        }
                                        showStatus("Redone")
                                    }
                                }
                            },
                            onSaveAs = {
                                selectedClip?.let {
                                    saveAsName = it.name.replace(".wav", "_copy")
                                    showSaveAsDialog = true
                                }
                            },
                            onExportCustom = { options ->
                                selectedClip?.let { clip ->
                                    val outDir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
                                    val exportedFile = WavEditor.exportCustomFormat(clip.path, outDir, options)
                                    if (exportedFile != null && exportedFile.exists()) {
                                        refreshRecordings()
                                        if (options.format.equals("MP3", true)) {
                                            val cmdFile = File(exportedFile.parentFile, exportedFile.nameWithoutExtension + ".mp3cmd.txt")
                                            val msg = "WAV pronto: ${exportedFile.name}. Comando MP3 salvato in ${cmdFile.name}. Esegui il .bat sul PC per generare il .mp3."
                                            showStatus(msg, false)
                                        } else {
                                            val formatDesc = "WAV ${options.sampleRate / 1000}k/${options.bitDepth}b"
                                            val peakDesc = if (options.normalize) " [Peak %.1f dBFS]".format(options.peakDb) else ""
                                            showStatus("Esportato: ${exportedFile.name} ($formatDesc$peakDesc)")
                                        }
                                        pendingExportFile = exportedFile
                                        exportFilePicker.launch(exportedFile.name)
                                    } else {
                                        val message = if (options.format.equals("MP3", true)) {
                                            "Esportazione MP3 fallita"
                                        } else "Errore durante l'esportazione audio"
                                        showStatus(message, true)
                                    }
                                }
                            },
                            onPlaybackStart = { bridge.stopAudio() },
                            onPlaybackStop = { if (!isRunning) bridge.startMonitoring() },
                            nativePlaybackPositionMs = if (timelinePlaying) bridge.getMixerPositionMs() else null,
                            nativePlaybackDurationMs = if (timelinePlaying) bridge.getMixerDurationMs() else null,
                        )

                        DAWTab.CLOUD -> Column(Modifier.fillMaxSize()) {
                            RvcSegmentedTabs(
                                tabs = listOf("RVC remoto", "Progetti", "Controllo PC"),
                                selectedIndex = cloudSection,
                                onSelect = { cloudSection = it },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                            when (cloudSection) {
                                0 -> Box(Modifier.weight(1f)) {
                            RvcView(
                                selectedClip = rvcInputClip,
                                clips = recordings,
                                onSelectClip = { rvcInputClip = it },
                                onLoadSynth = { synthPicker.launch(arrayOf("*/*")) },
                                onLoadVec = { vecPicker.launch(arrayOf("*/*")) },
                                onLoadF0 = { f0Picker.launch(arrayOf("*/*")) },
                                onLoadIndex = { indexPicker.launch(arrayOf("*/*")) },
                                loadedSynth = loadedSynth,
                                loadedVec = loadedVec,
                                loadedF0 = loadedF0,
                                loadedIndex = loadedIndex,
                                modelsDir = modelsDir,
                                onPickModelsDir = { modelsDirPicker.launch(null) },
                                onRefreshModels = {
                                    loadedSynth = false; loadedVec = false; loadedF0 = false; loadedIndex = false
                                    scope.launch {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            tryAutoLoadRvcArtifacts()
                                            tryAutoLoadContentVec()
                                            tryAutoLoadF0()
                                        }
                                        reportAutoloadStatus()
                                    }
                                },
                                onForgetModels = {
                                    prefs.edit()
                                        .remove("synth_path")
                                        .remove("vec_path")
                                        .remove("f0_path")
                                        .remove("index_path")
                                        .remove("models_dir")
                                        .apply()
                                    modelsDir = ""
                                    loadedSynth = false; loadedVec = false; loadedF0 = false; loadedIndex = false
                                    showStatus("Modelli dimenticati. Riapri una cartella per ricaricarli.")
                                },
                                onPrepareBaseModels = {
                                    scope.launch {
                                        val results = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            com.rvc.droid.RVCCoreApplication.ensureBaseModelsAvailable()
                                        }
                                        val msg = if (results.isEmpty()) "Nessun modello base copiato"
                                                 else results.joinToString(" | ")
                                        showStatus("Bootstrap: $msg")
                                        // Dopo la copia, rilancia l'autoload
                                        loadedSynth = false; loadedVec = false; loadedF0 = false; loadedIndex = false
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            tryAutoLoadRvcArtifacts()
                                            tryAutoLoadContentVec()
                                            tryAutoLoadF0()
                                        }
                                        reportAutoloadStatus()
                                    }
                                },
                                pitchShift = pitchShift,
                                onPitchShift = { pitchShift = it; sendRvcParams() },
                                formantShift = formantShift,
                                onFormantShift = { formantShift = it; sendRvcParams() },
                                f0Method = f0Method,
                                onF0Method = { f0Method = it; sendRvcParams() },
                                indexRate = indexRate,
                                onIndexRate = { indexRate = it; sendRvcParams() },
                                protection = protection,
                                onProtection = { protection = it; sendRvcParams() },
                                noiseScale = noiseScale,
                                onNoiseScale = { noiseScale = it; sendRvcParams() },
                                onApplyRvc = {
                                    val input = rvcInputClip
                                    if (input != null && !isApplyingRvc) {
                                        isApplyingRvc = true
                                        scope.launch {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                tryAutoLoadRvcArtifacts()
                                            }
                                            // Re-sync: dopo un run i modelli HuBERT/F0
                                            // vengono scaricati lato C++. Se Kotlin
                                            // crede siano caricati ma il bridge no,
                                            // ricaricali dai path salvati nelle prefs.
                                            if (!bridge.areModelsLoaded() && bridge.isInitialized) {
                                                com.rvc.droid.RVCCoreApplication.logToFile("processOffline: bridge reports models missing, reloading from prefs")
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                    val vecPath = savedModel("vec_path")
                                                    if (vecPath.isNotBlank() && java.io.File(vecPath).isFile) {
                                                        bridge.loadContentVec(vecPath)
                                                    }
                                                    val f0Path = savedModel("f0_path")
                                                    if (f0Path.isNotBlank() && java.io.File(f0Path).isFile) {
                                                        bridge.loadF0Model(f0Path)
                                                    }
                                                }
                                            }
                                            if (!loadedSynth || !loadedVec || !loadedF0 || !bridge.areModelsLoaded()) {
                                                showStatus("Modelli incompleti: synth.onnx, hubert_base.onnx e rmvpe.onnx sono necessari", true)
                                                isApplyingRvc = false
                                                return@launch
                                            }
                                            val outDir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
                                            val outFile = File(outDir, "RVC_${System.currentTimeMillis()}.wav")
                                            offlineProgress = 0f
                                            com.rvc.droid.RVCCoreApplication.logToFile("RVC start: " + com.rvc.droid.RVCCoreApplication.getMemoryInfo())
                                            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                bridge.processOffline(input.path, outFile.absolutePath, pitchShift) { progress ->
                                                    scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
                                                        offlineProgress = progress
                                                    }
                                                }
                                            }
                                            com.rvc.droid.RVCCoreApplication.logToFile("RVC done (ok=$ok): " + com.rvc.droid.RVCCoreApplication.getMemoryInfo())
                                            if (ok && outFile.isFile && outFile.length() > 44L) {
                                                offlineProgress = 1f
                                                val newClip = RecordingClip(name = outFile.name, path = outFile.absolutePath, durationMs = getDurationMs(outFile))
                                                recordings = (recordings + newClip).distinctBy { it.path }
                                                lastRvcOutputClip = newClip
                                                refreshRecordings()
                                                showStatus("Conversione RVC completata!")
                                            } else {
                                                offlineProgress = -1f
                                                outFile.delete()
                                                showStatus("Conversione RVC fallita: nessun WAV valido prodotto", true)
                                            }
                                            isApplyingRvc = false
                                        }
                                    }
                                },
                                convertedClip = lastRvcOutputClip,
                                onExportClip = { clip ->
                                    exportSourceClip = clip
                                    showExportDialog = true
                                },
                                rvcProgress = offlineProgress,
                                isRemoteMode = isRemoteRvcMode,
                                onRemoteModeChange = {
                                    isRemoteRvcMode = it
                                    persistDropboxBool("remote_mode_enabled", it)
                                },
                                dropboxToken = dropboxToken,
                                onDropboxTokenChange = {
                                    dropboxToken = it
                                    persistDropbox("dropbox_token", it)
                                },
                                dropboxAppKey = dropboxAppKey,
                                onDropboxAppKeyChange = {
                                    dropboxAppKey = it
                                    persistDropbox("dropbox_app_key", it)
                                },
                                dropboxInputDir = dropboxInputDir,
                                onDropboxInputDirChange = {
                                    dropboxInputDir = it
                                    persistDropbox("dropbox_input_dir", it)
                                },
                                dropboxOutputDir = dropboxOutputDir,
                                onDropboxOutputDirChange = {
                                    dropboxOutputDir = it
                                    persistDropbox("dropbox_output_dir", it)
                                },
                                dropboxConfigFile = dropboxConfigFile,
                                onDropboxConfigFileChange = {
                                    dropboxConfigFile = it
                                    persistDropbox("dropbox_config_file", it)
                                },
                                remoteModels = remoteModels,
                                selectedRemoteModel = selectedRemoteModel,
                                onSelectRemoteModel = { selectedRemoteModel = it },
                                onSyncDropboxConfig = { syncDropboxConfig() },
                                onTestDropboxConnection = { testDropboxConnection() },
                                dropboxConnectionStatus = dropboxConnectionStatus,
                                autoMuteOriginal = autoMuteOriginal,
                                onAutoMuteOriginalChange = {
                                    autoMuteOriginal = it
                                    persistDropboxBool("auto_mute_original", it)
                                },
                                onApplyRemoteRvc = {
                                    val clip = rvcInputClip ?: selectedClip ?: recordings.firstOrNull()
                                    if (clip != null) {
                                        applyRemoteRvc(clip)
                                    } else {
                                        showStatus("Seleziona una clip vocale da convertire", true)
                                    }
                                },
                                remoteProgressText = remoteProgressText,
                                remoteProgress = remoteProgress,
                                onCleanGarbage = { cleanGarbage() },
                                freedStorageStatus = freedStorageStatus
                            )
                            if (isApplyingRvc) {
                                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(color = RVCColors.accentPurple)
                                        Spacer(Modifier.height(8.dp))
                                        Text("Applying RVC Model...", color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            }
                                }
                                1 -> ProjectSyncView(
                                    dropboxToken = dropboxToken,
                                    projectName = currentProjectName,
                                    clipCount = recordings.size,
                                    hasVideo = videoPath != null,
                                    remoteEntries = remoteBundles,
                                    isListing = bundleListing,
                                    isBusy = bundleBusy,
                                    progress = bundleProgress,
                                    statusText = bundleStatus,
                                    onRefresh = { refreshRemoteBundles() },
                                    onUpload = { uploadProjectBundle() },
                                    onDownload = { downloadProjectBundle(it) },
                                    onDeleteRemote = { deleteRemoteBundle(it) },
                                    onOpenDropboxSettings = { cloudSection = 0 },
                                )
                                else -> RemoteControlView(
                                    dropboxToken = dropboxToken,
                                    status = pcStatus,
                                    statusLoading = pcStatusLoading,
                                    statusError = pcStatusError,
                                    configBusy = remoteConfigBusy,
                                    configStatus = remoteConfigStatus,
                                    pitchShift = pitchShift,
                                    onPitchShift = { pitchShift = it; sendRvcParams() },
                                    formantShift = formantShift,
                                    onFormantShift = { formantShift = it; sendRvcParams() },
                                    f0Method = f0Method,
                                    onF0Method = { f0Method = it; sendRvcParams() },
                                    indexRate = indexRate,
                                    onIndexRate = { indexRate = it; sendRvcParams() },
                                    protection = protection,
                                    onProtection = { protection = it; sendRvcParams() },
                                    noiseScale = noiseScale,
                                    onNoiseScale = { noiseScale = it; sendRvcParams() },
                                    remoteModels = remoteModels,
                                    selectedRemoteModel = selectedRemoteModel,
                                    onSelectRemoteModel = { selectedRemoteModel = it },
                                    onRefreshStatus = { refreshPcStatus() },
                                    onSendConfig = { sendRemoteConfigToPc() },
                                    onReadConfig = { readRemoteConfigFromPc() },
                                    onOpenDropboxSettings = { cloudSection = 0 },
                                )
                            }
                        }

                        DAWTab.VIDEO -> VideoWorkspace(
                            videoPath = videoPath,
                            isPlaying = videoIsPlaying,
                            positionMs = videoPositionMs,
                            timelinePositionMs = timelinePositionMs,
                            onLoadVideo = { videoPicker.launch(arrayOf("video/*")) },
                            onPlayPause = { videoIsPlaying = !videoIsPlaying },
                            onStop = { videoIsPlaying = false; videoPositionMs = 0L },
                            onSeekTo = { videoPositionMs = it },
                            onPositionChanged = { videoPositionMs = it },
                            onSeekTimelineToVideo = {
                                timelinePositionMs = videoPositionMs.coerceIn(0L, timelineDurationMs)
                                if (bridge.isInitialized) bridge.setMixerPositionMs(timelinePositionMs)
                            },
                            onGoToStudio = { currentTab = DAWTab.STUDIO },
                        )

                        DAWTab.PROMPTER -> PrompterScreen(
                            text = teleprompterText,
                            onTextChange = { teleprompterText = it },
                            fontSize = teleprompterFontSize,
                            onFontSizeChange = { teleprompterFontSize = it },
                            scrollSpeed = teleprompterScrollSpeed,
                            onScrollSpeedChange = { teleprompterScrollSpeed = it },
                            isRecording = isRunning,
                            onToggleRecording = onToggleRecording,
                        )

                        DAWTab.MIXER -> MixerView(
                            bridge = bridge,
                            inputLevel = inputLevel,
                            gateState = gateState, gateGR = gateGR,
                            voiceBands = voiceBands, musicBands = musicBands,
                            compThreshold = compThreshold, compRatio = compRatio,
                            compAttack = compAttack, compRelease = compRelease,
                            compMakeup = compMakeup, compGR = compGR,
                            limiterThreshold = limiterThreshold, limiterRelease = limiterRelease,
                            limiterGR = limiterGR,
                            voiceVolume = voiceVol, musicVolume = musicVol, masterVolume = masterVol,
                            monitoringGain = monitoringGain,
                            reverbMix = reverbMix, reverbRoomSize = reverbRoomSize, reverbDamping = reverbDamping,
                            momentary = momentary, shortTerm = shortTerm,
                            integrated = integrated, truePeak = truePeak,
                            onVoiceBandUpdate = { i, b ->
                                voiceBands = voiceBands.toMutableList().also { it[i] = b }
                                bridge.setVoiceEQ(voiceBands.map { NativeAudioBridge.EQBand(it.freq, it.gainDB, it.q) })
                            },
                            onMusicBandUpdate = { i, b ->
                                musicBands = musicBands.toMutableList().also { it[i] = b }
                                bridge.setMusicEQ(musicBands.map { NativeAudioBridge.EQBand(it.freq, it.gainDB, it.q) })
                            },
                            onCompThreshold = { compThreshold = it; bridge.setSideChain(it, compRatio, compAttack, compRelease, compMakeup) },
                            onCompRatio = { compRatio = it; bridge.setSideChain(compThreshold, it, compAttack, compRelease, compMakeup) },
                            onCompAttack = { compAttack = it; bridge.setSideChain(compThreshold, compRatio, it, compRelease, compMakeup) },
                            onCompRelease = { compRelease = it; bridge.setSideChain(compThreshold, compRatio, compAttack, it, compMakeup) },
                            onCompMakeup = { compMakeup = it; bridge.setSideChain(compThreshold, compRatio, compAttack, compRelease, it) },
                            onLimiterThreshold = { limiterThreshold = it; bridge.setLimiter(it, limiterRelease) },
                            onLimiterRelease = { limiterRelease = it; bridge.setLimiter(limiterThreshold, it) },
                            onGateToggle = {
                                val s = gateState.copy(enabled = !gateState.enabled)
                                gateState = s
                                bridge.setNoiseGate(s.enabled, s.threshold, s.attackMs, s.holdMs, s.releaseMs)
                            },
                            onGateThreshold = {
                                val s = gateState.copy(threshold = it)
                                gateState = s
                                bridge.setNoiseGate(s.enabled, s.threshold, s.attackMs, s.holdMs, s.releaseMs)
                            },
                            onVoiceVolume = { voiceVol = it; bridge.setVolumes(it, musicVol, masterVol) },
                            onMusicVolume = { musicVol = it; bridge.setVolumes(voiceVol, it, masterVol) },
                            onMasterVolume = { masterVol = it; bridge.setVolumes(voiceVol, musicVol, it) },
                            onMonitoringGain = { monitoringGain = it; bridge.setMonitoringGain(it) },
                            onReverbMix = { reverbMix = it; bridge.setReverbMix(it) },
                            onReverbRoomSize = { reverbRoomSize = it; bridge.setReverbRoomSize(it) },
                            onReverbDamping = { reverbDamping = it; bridge.setReverbDamping(it) },
                            onLoadTrack = { idx ->
                                showClipSelectorForTrack = idx
                            },
                            onRemoveTrack = { trackIdx ->
                                scope.launch {
                                    withContext(Dispatchers.IO) { bridge.removeTrack(trackIdx) }
                                    mixerTracks = mixerTracks.filter { it.trackIndex != trackIdx }
                                    showStatus("Traccia ${trackIdx + 1} rimossa")
                                }
                            },
                            onMoveTrack = move@{ trackIdx, direction ->
                                // Sposta la traccia nella lista: su (-1) o giù (+1).
                                val sorted = mixerTracks.sortedBy { it.offsetMs }
                                val currentIdx = sorted.indexOfFirst { it.trackIndex == trackIdx }
                                if (currentIdx < 0) return@move
                                val target = currentIdx + direction
                                if (target < 0 || target >= sorted.size) return@move
                                val a = sorted[currentIdx]
                                val b = sorted[target]
                                val aOffset = a.offsetMs
                                val newTracks = mixerTracks.map {
                                    when (it.trackIndex) {
                                        a.trackIndex -> it.copy(offsetMs = b.offsetMs)
                                        b.trackIndex -> it.copy(offsetMs = aOffset)
                                        else -> it
                                    }
                                }
                                mixerTracks = newTracks
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        bridge.setTrackOffset(a.trackIndex, b.offsetMs * 48)
                                        bridge.setTrackOffset(b.trackIndex, aOffset * 48)
                                    }
                                }
                            },
                            mixerTracks = mixerTracks,
                            timelinePositionMs = timelinePositionMs,
                            selectedClipId = selectedTimelineClipId,
                            selectedTrackIndex = selectedTrackIdx,
                            onClipSelected = { clipId ->
                                selectedTimelineClipId = clipId
                                mixerTracks.firstOrNull { it.clipId == clipId }?.let {
                                    selectedTrackIdx = it.trackIndex
                                }
                            },
                            onTrackVolume = { idx, vol ->
                                mixerTracks = mixerTracks.map { if (it.trackIndex == idx) it.copy(volume = vol) else it }
                                scope.launch { withContext(Dispatchers.IO) { bridge.setTrackVolume(idx, vol) } }
                            },
                            onTrackPan = { idx, pan ->
                                mixerTracks = mixerTracks.map { if (it.trackIndex == idx) it.copy(pan = pan) else it }
                                scope.launch { withContext(Dispatchers.IO) { bridge.setTrackPan(idx, pan) } }
                            },
                            onTrackMute = { idx, muted ->
                                mixerTracks = mixerTracks.map { if (it.trackIndex == idx) it.copy(muted = muted) else it }
                                scope.launch { withContext(Dispatchers.IO) { bridge.setTrackMute(idx, muted) } }
                            },
                            onTrackSolo = { idx, solo ->
                                mixerTracks = mixerTracks.map { if (it.trackIndex == idx) it.copy(solo = solo) else it }
                                scope.launch { withContext(Dispatchers.IO) { bridge.setTrackSolo(idx, solo) } }
                            },
                            onTrackDuck = { idx, amt ->
                                mixerTracks = mixerTracks.map { if (it.trackIndex == idx) it.copy(duckAmount = amt) else it }
                                scope.launch { withContext(Dispatchers.IO) { bridge.setTrackDuck(idx, amt) } }
                            },
                            onTrackOffsetChange = { idx, clipId, offset ->
                                mixerTracks = mixerTracks.toMutableList().also { list ->
                                    val t = list.find { it.trackIndex == idx && it.clipId == clipId }
                                    if (t != null) {
                                        val clipIdx = list.indexOf(t)
                                        list[clipIdx] = t.copy(offsetMs = offset)
                                        bridge.setTrackOffset(clipIdx, (offset * 48000 / 1000))
                                    }
                                }
                            },
                            onSeekTimeline = { seekMs ->
                                timelinePositionMs = seekMs.coerceIn(0L, timelineDurationMs)
                                if (bridge.isInitialized) {
                                    bridge.setMixerPositionMs(timelinePositionMs)
                                }
                            },
                            onRemoveClip = { clipId ->
                                val clipIdx = mixerTracks.indexOfFirst { it.clipId == clipId }
                                if (clipIdx >= 0) {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { bridge.removeTrack(clipIdx) }
                                        mixerTracks = mixerTracks.filterNot { it.clipId == clipId }
                                        showStatus("Clip rimossa dalla timeline")
                                    }
                                }
                            },
                            onRenameClip = { clipId, newName ->
                                mixerTracks = mixerTracks.map {
                                    if (it.clipId == clipId) it.copy(clipName = newName) else it
                                }
                                showStatus("Clip rinominata in: $newName")
                            },
                            onConvertSelectionRemoteRvc = { startMs, endMs ->
                                scope.launch {
                                    val outDir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
                                    val activeClip = mixerTracks.firstOrNull { it.clipId == selectedTimelineClipId }
                                    val sourcePath = activeClip?.clipPath ?: recordings.firstOrNull()?.path
                                    if (sourcePath == null || !File(sourcePath).exists()) {
                                        showStatus("Nessuna traccia audio valida per la selezione", true)
                                        return@launch
                                    }
                                    val selFile = File(outDir, "TMP_SEL_${System.currentTimeMillis()}.wav")
                                    val opts = WavEditor.ExportOptions(
                                        outputName = selFile.nameWithoutExtension,
                                        startMs = startMs,
                                        endMs = endMs,
                                        sampleRate = 48000,
                                        bitDepth = 16,
                                        format = "WAV"
                                    )
                                    val extracted = kotlinx.coroutines.withContext(Dispatchers.IO) {
                                        WavEditor.exportCustomFormat(sourcePath, outDir, opts)
                                    }
                                    if (extracted != null && extracted.exists() && extracted.length() > 44L) {
                                        val clipToConv = RecordingClip(
                                            name = extracted.name,
                                            path = extracted.absolutePath,
                                            durationMs = (endMs - startMs).coerceAtLeast(0L)
                                        )
                                        applyRemoteRvc(
                                            clipToConvert = clipToConv,
                                            trackIndexToMute = activeClip?.trackIndex,
                                            targetOffsetMs = startMs
                                        )
                                    } else {
                                        showStatus("Errore nell'estrazione della porzione audio", true)
                                    }
                                }
                            },
                            onOpenRegionDialog = {
                                exportSourceClip = selectedClip ?: recordings.firstOrNull()
                                showRegionExportDialog = true
                            },
                            onExportRegion = { startMs, endMs ->
                                scope.launch {
                                    val mixFile = File(ctx.cacheDir, "mixer_region_${startMs}_${endMs}.wav")
                                    val rendered = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        bridge.renderMixer(mixFile.absolutePath)
                                    }
                                    if (rendered && mixFile.isFile && mixFile.length() > 44L) {
                                        exportSourceClip = RecordingClip(
                                            name = "Mixdown_Regione",
                                            path = mixFile.absolutePath,
                                            durationMs = getDurationMs(mixFile)
                                        )
                                        showExportDialog = true
                                    } else {
                                        showStatus("Esportazione regione non riuscita", true)
                                    }
                                }
                            },
                            onPlayTimeline = {
                                bridge.resetMixerPosition()
                                timelinePositionMs = 0L
                                timelinePlaying = true
                                bridge.startAudio(false)
                            },
                            onStopTimeline = {
                                timelinePlaying = false
                                bridge.stopAudio()
                            },
                            onResetMastering = { if (bridge.isInitialized) bridge.masteringReset() },
                            onExport = {
                                scope.launch {
                                    val mixFile = File(ctx.cacheDir, "mixer_export.wav")
                                    val rendered = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        bridge.renderMixer(mixFile.absolutePath)
                                    }
                                    if (rendered && mixFile.isFile && mixFile.length() > 44L) {
                                        exportSourceClip = RecordingClip(
                                            name = "Mixdown",
                                            path = mixFile.absolutePath,
                                            durationMs = getDurationMs(mixFile)
                                        )
                                        showExportDialog = true
                                    } else {
                                        showStatus("Mixdown non disponibile: verifica le clip assegnate", true)
                                    }
                                }
                            },
                            onExportStereo = {
                                scope.launch {
                                    val mixFile = File(ctx.cacheDir, "mixer_export_stereo.wav")
                                    val rendered = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        bridge.renderMixerStereo(mixFile.absolutePath, 48000)
                                    }
                                    if (rendered && mixFile.isFile && mixFile.length() > 44L) {
                                        exportSourceClip = RecordingClip(
                                            name = "Mixdown Stereo",
                                            path = mixFile.absolutePath,
                                            durationMs = getDurationMs(mixFile)
                                        )
                                        showExportDialog = true
                                    } else {
                                        showStatus("Mixdown stereo non disponibile: verifica le clip assegnate", true)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        if (showExportDialog) {
            val clipToExport = exportSourceClip ?: selectedClip ?: rvcInputClip ?: recordings.firstOrNull()
            if (clipToExport != null) {
                ExportFormatDialog(
                    initialName = clipToExport.name.removeSuffix(".wav"),
                    totalDurationMs = clipToExport.durationMs,
                    selectionStartMs = 0L,
                    selectionEndMs = clipToExport.durationMs,
                    hasSelection = false,
                    onDismiss = { showExportDialog = false },
                    onConfirmExport = { options ->
                        showExportDialog = false
                        val outDir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
                        val exportedFile = WavEditor.exportCustomFormat(clipToExport.path, outDir, options)
                        if (exportedFile != null && exportedFile.exists()) {
                            refreshRecordings()
                            val formatDesc = if (options.format == "MP3") "MP3 ${options.mp3BitrateKbps}k" else "WAV ${options.sampleRate / 1000}k/${options.bitDepth}b"
                            val peakDesc = if (options.normalize) " [Peak %.1f dBFS]".format(options.peakDb) else ""
                            showStatus("Esportato: ${exportedFile.name} ($formatDesc$peakDesc)")
                            exportFilePicker.launch(exportedFile.name)
                        } else {
                            showStatus("Errore durante l'esportazione audio", true)
                        }
                        exportSourceClip = null
                    }
                )
            } else {
                showExportDialog = false
                showStatus("Nessuna registrazione disponibile da esportare", true)
            }
        }
        if (showRegionExportDialog) {
            val clipForRegions = exportSourceClip ?: selectedClip ?: rvcInputClip ?: recordings.firstOrNull()
            if (clipForRegions != null) {
                RegionExportDialog(
                    clipName = clipForRegions.name,
                    totalDurationMs = clipForRegions.durationMs,
                    onDismiss = { showRegionExportDialog = false },
                    onConfirm = { regions, baseOptions ->
                        scope.launch {
                            val outDir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val results = WavEditor.exportRegions(clipForRegions.path, regions, baseOptions, outDir)
                                val okCount = results.count { it != null }
                                showStatus(
                                    if (okCount == regions.size) "Esportate $okCount regioni"
                                    else "Esportate $okCount/${regions.size} regioni (alcune fallite)"
                                )
                            }
                            showRegionExportDialog = false
                        }
                    },
                    onConfirmRemoteRvc = { regions, baseOptions ->
                        scope.launch {
                            val outDir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
                            showRegionExportDialog = false
                            showStatus("Avvio batch RVC Dropbox per ${regions.size} regioni...")
                            val exportedFiles = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                WavEditor.exportRegions(clipForRegions.path, regions, baseOptions, outDir)
                            }
                            exportedFiles.filterNotNull().forEachIndexed { index, file ->
                                val regionSpec = regions.getOrNull(index)
                                val clip = RecordingClip(
                                    name = file.name,
                                    path = file.absolutePath,
                                    durationMs = (regionSpec?.endMs?.minus(regionSpec.startMs)) ?: getDurationMs(file)
                                )
                                applyRemoteRvc(
                                    clipToConvert = clip,
                                    targetOffsetMs = regionSpec?.startMs ?: 0L
                                )
                            }
                        }
                    }
                )
            } else {
                showRegionExportDialog = false
                showStatus("Nessuna registrazione disponibile", true)
            }
        }
        if (showRenameClipDialog) {
            AlertDialog(
                onDismissRequest = { showRenameClipDialog = false },
                title = { Text("Rinomina clip") },
                text = {
                    TextField(
                        value = renameClipName,
                        onValueChange = { renameClipName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val name = renameClipName.trim()
                        if (name.isNotEmpty()) {
                            mixerTracks = mixerTracks.map { clip ->
                                if (clip.clipId == selectedTimelineClipId) clip.copy(clipName = name) else clip
                            }
                        }
                        showRenameClipDialog = false
                    }) { Text("Salva") }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameClipDialog = false }) { Text("Annulla") }
                }
            )
        }
        if (showSaveAsDialog) {
            AlertDialog(
                onDismissRequest = { showSaveAsDialog = false },
                title = { Text("Save Clip As") },
                text = {
                    TextField(
                        value = saveAsName,
                        onValueChange = { saveAsName = it },
                        label = { Text("New name") }
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        selectedClip?.let { clip ->
                            val outDir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
                            val outFile = File(outDir, if (saveAsName.endsWith(".wav")) saveAsName else "$saveAsName.wav")
                            if (WavEditor.saveAs(clip.path, outFile.absolutePath)) {
                                val newClip = RecordingClip(name = outFile.name, path = outFile.absolutePath, durationMs = clip.durationMs)
                                recordings = recordings + newClip
                                showStatus("Saved as ${outFile.name}")
                            } else {
                                showStatus("Save failed", true)
                            }
                        }
                        showSaveAsDialog = false
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveAsDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showClipSelectorForTrack >= -1 && recordings.isNotEmpty()) {
            // Il dialog mostra la lista delle clip; -1 = "assegna alla prima
            // traccia libera" (gestito nel click handler).
            AlertDialog(
                onDismissRequest = { showClipSelectorForTrack = -2 },
                title = { Text(if (showClipSelectorForTrack >= 0) "Assegna a Traccia ${showClipSelectorForTrack + 1}" else "Aggiungi Traccia al Mixer") },
                text = {
                    Box(Modifier.height(300.dp)) {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(recordings) { clip ->
                                TextButton(
                                    onClick = {
                                        val requestedIdx = showClipSelectorForTrack
                                        // -1: usa la prima traccia libera; altrimenti usa quella richiesta
                                        val targetTrackIdx = if (requestedIdx < 0) {
                                            (0..31).firstOrNull { i -> mixerTracks.none { it.trackIndex == i } } ?: 0
                                        } else requestedIdx
                                        scope.launch {
                                            val ok = bridge.importTrack(clip.path, clip.name)
                                            if (ok) {
                                                mixerTracks = mixerTracks + com.rvc.droid.ui.project.MixerTrackState(
                                                    trackIndex = targetTrackIdx,
                                                    clipPath = clip.path,
                                                    clipName = clip.name,
                                                    durationMs = clip.durationMs
                                                )
                                                showStatus("Traccia ${targetTrackIdx + 1}: ${clip.name}")
                                            } else {
                                                showStatus("Errore nell'assegnazione", true)
                                            }
                                            showClipSelectorForTrack = -2
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(clip.name, color = RVCColors.textPrimary, fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showClipSelectorForTrack = -2 }) { Text("Annulla") }
                }
            )
        }

        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("IMPOSTAZIONI DISPOSITIVI AUDIO", fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
                text = {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        Text("Dispositivo di Ingresso (Microfono)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = RVCColors.accentCyan)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                            selectedInputDevice = -1
                            bridge.setInputDevice(0)
                            showStatus("Ingresso: Predefinito di Sistema")
                        }.padding(vertical = 4.dp)) {
                            RadioButton(selected = selectedInputDevice == -1, onClick = {
                                selectedInputDevice = -1
                                bridge.setInputDevice(0)
                                showStatus("Ingresso: Predefinito di Sistema")
                            })
                            Spacer(Modifier.width(4.dp))
                            Text("Predefinito di Sistema (Auto)", fontSize = 12.sp, fontWeight = if (selectedInputDevice == -1) FontWeight.Bold else FontWeight.Normal)
                        }
                        inputDevices.forEach { dev ->
                            val name = getDeviceDisplayName(dev)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                                selectedInputDevice = dev.id
                                bridge.setInputDevice(dev.id)
                                showStatus("Ingresso impostato: $name")
                            }.padding(vertical = 4.dp)) {
                                RadioButton(selected = selectedInputDevice == dev.id, onClick = {
                                    selectedInputDevice = dev.id
                                    bridge.setInputDevice(dev.id)
                                    showStatus("Ingresso impostato: $name")
                                })
                                Spacer(Modifier.width(4.dp))
                                Text(name, fontSize = 12.sp, fontWeight = if (selectedInputDevice == dev.id) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Dispositivo di Uscita (Ascolto/Cuffie)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = RVCColors.accentGreen)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                            selectedOutputDevice = -1
                            bridge.setOutputDevice(0)
                            showStatus("Uscita: Predefinita di Sistema")
                        }.padding(vertical = 4.dp)) {
                            RadioButton(selected = selectedOutputDevice == -1, onClick = {
                                selectedOutputDevice = -1
                                bridge.setOutputDevice(0)
                                showStatus("Uscita: Predefinita di Sistema")
                            })
                            Spacer(Modifier.width(4.dp))
                            Text("Predefinito di Sistema (Auto)", fontSize = 12.sp, fontWeight = if (selectedOutputDevice == -1) FontWeight.Bold else FontWeight.Normal)
                        }
                        outputDevices.forEach { dev ->
                            val name = getDeviceDisplayName(dev)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                                selectedOutputDevice = dev.id
                                bridge.setOutputDevice(dev.id)
                                showStatus("Uscita impostata: $name")
                            }.padding(vertical = 4.dp)) {
                                RadioButton(selected = selectedOutputDevice == dev.id, onClick = {
                                    selectedOutputDevice = dev.id
                                    bridge.setOutputDevice(dev.id)
                                    showStatus("Uscita impostata: $name")
                                })
                                Spacer(Modifier.width(4.dp))
                                Text(name, fontSize = 12.sp, fontWeight = if (selectedOutputDevice == dev.id) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                },
                confirmButton = { Button(onClick = { showSettingsDialog = false }) { Text("Chiudi") } }
            )
        }
    }
}
