package com.rvc.droid.ui.rvc

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvc.droid.audio.WavPlayer
import com.rvc.droid.cloud.RemoteRvcModel
import com.rvc.droid.ui.project.RecordingClip
import com.rvc.droid.ui.theme.RVCColors
import com.rvc.droid.RVCCoreApplication

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun RvcView(
    modifier: Modifier = Modifier,
    selectedClip: RecordingClip?,
    clips: List<RecordingClip>,
    onSelectClip: (RecordingClip) -> Unit,
    onLoadSynth: () -> Unit,
    onLoadVec: () -> Unit,
    onLoadF0: () -> Unit,
    onLoadIndex: () -> Unit,
    loadedSynth: Boolean,
    loadedVec: Boolean,
    loadedF0: Boolean,
    loadedIndex: Boolean,
    modelsDir: String = "",
    onPickModelsDir: () -> Unit = {},
    onRefreshModels: () -> Unit = {},
    onForgetModels: () -> Unit = {},
    onPrepareBaseModels: () -> Unit = {},
    pitchShift: Int,
    onPitchShift: (Int) -> Unit,
    formantShift: Int = 0,
    onFormantShift: (Int) -> Unit = {},
    f0Method: String = "rmvpe",
    onF0Method: (String) -> Unit = {},
    indexRate: Float = 0.5f,
    onIndexRate: (Float) -> Unit = {},
    protection: Float = 0.5f,
    onProtection: (Float) -> Unit = {},
    noiseScale: Float = 0.666f,
    onNoiseScale: (Float) -> Unit = {},
    convertedClip: RecordingClip? = null,
    onExportClip: (RecordingClip) -> Unit = {},
    onApplyRvc: () -> Unit,
    rvcProgress: Float,
    isIndexOptional: Boolean = true,
    // Dropbox Remote parameters
    isRemoteMode: Boolean = true,
    onRemoteModeChange: (Boolean) -> Unit = {},
    dropboxToken: String = "",
    onDropboxTokenChange: (String) -> Unit = {},
    dropboxAppKey: String = "",
    onDropboxAppKeyChange: (String) -> Unit = {},
    dropboxInputDir: String = "/TestRVC/AudioIn",
    onDropboxInputDirChange: (String) -> Unit = {},
    dropboxOutputDir: String = "/TestRVC/AudioOut",
    onDropboxOutputDirChange: (String) -> Unit = {},
    dropboxConfigFile: String = "/TestRVC/config.json",
    onDropboxConfigFileChange: (String) -> Unit = {},
    remoteModels: List<RemoteRvcModel> = emptyList(),
    selectedRemoteModel: RemoteRvcModel? = null,
    onSelectRemoteModel: (RemoteRvcModel) -> Unit = {},
    onSyncDropboxConfig: () -> Unit = {},
    onTestDropboxConnection: () -> Unit = {},
    dropboxConnectionStatus: String = "",
    autoMuteOriginal: Boolean = true,
    onAutoMuteOriginalChange: (Boolean) -> Unit = {},
    onApplyRemoteRvc: () -> Unit = {},
    remoteProgressText: String = "",
    remoteProgress: Float = -1f,
    onCleanGarbage: () -> Unit = {},
    freedStorageStatus: String = ""
) {
    val uriHandler = LocalUriHandler.current
    var previewingClipPath by remember { mutableStateOf<String?>(null) }
    var showLogDialog by remember { mutableStateOf(false) }
    var showDiagDialog by remember { mutableStateOf(false) }
    var showDropboxSettings by remember { mutableStateOf(false) }
    var showModelDropdown by remember { mutableStateOf(false) }
    var logText by remember { mutableStateOf("") }
    var diagText by remember { mutableStateOf("") }
    val localOnnxEnabled = RvcFeatures.LOCAL_ONNX_ENABLED
    val remoteMode = !localOnnxEnabled || isRemoteMode

    fun readLogTail(): String {
        return try {
            val real = RVCCoreApplication.getLogFile()
            if (real != null && real.exists()) {
                return real.readLines().takeLast(80).joinToString("\n") +
                       "\n\n[file: ${real.absolutePath} (${real.length() / 1024} KB)]"
            }
            val candidates = listOf(
                java.io.File(android.os.Environment.getExternalStorageDirectory(), "Modelli/native.log"),
                java.io.File(android.os.Environment.getExternalStorageDirectory(), "Android/data/com.rvc.droid/files/native.log"),
                java.io.File("/storage/emulated/0/Modelli/native.log"),
                java.io.File("/storage/emulated/0/Download/Modelli/native.log")
            )
            for (f in candidates) {
                if (f.exists() && f.canRead()) {
                    return f.readLines().takeLast(80).joinToString("\n") +
                           "\n\n[file: ${f.absolutePath} (${f.length() / 1024} KB)]"
                }
            }
            "(nessun log ancora)"
        } catch (e: Exception) { "Errore lettura log: ${e.message}" }
    }

    fun readAutoloadDiag(): String {
        val sb = StringBuilder()
        val paths = listOf(
            java.io.File("/storage/emulated/0/Android/data/com.rvc.droid/files/models"),
            java.io.File("/storage/emulated/0/Android/data/com.rvc.droid/files"),
            java.io.File("/sdcard/Download/Modelli"),
            java.io.File("/storage/emulated/0/Download/Modelli"),
            java.io.File("/sdcard/Download"),
            java.io.File("/storage/emulated/0/Download"),
            java.io.File("/sdcard/Modelli"),
            java.io.File("/storage/emulated/0/Modelli")
        )
        sb.appendLine("=== AUTOLOAD SCAN ===")
        for (dir in paths) {
            if (!dir.exists()) continue
            val files = try { dir.listFiles() ?: emptyArray() } catch (e: Exception) { continue }
            val interesting = files.filter { f0 ->
                val n = f0.name.lowercase()
                n.endsWith(".onnx") || n.endsWith(".fbin") || n.endsWith(".index") || n.endsWith(".bin")
            }
            if (interesting.isNotEmpty()) {
                sb.appendLine("V ${dir.absolutePath} (${interesting.size} modelli):")
                for (m in interesting.take(15)) {
                    sb.appendLine("    ${m.name} (${m.length() / 1024} KB)")
                }
            }
        }
        return sb.toString()
    }

    var abActiveTrack by remember { mutableStateOf("B") }
    var abIsPlaying by remember { mutableStateOf(false) }
    var abProgressMs by remember { mutableLongStateOf(0L) }
    var abTotalMs by remember { mutableLongStateOf(1L) }

    DisposableEffect(Unit) {
        val prevProgress = WavPlayer.onProgressChanged
        val prevComplete = WavPlayer.onPlaybackComplete
        WavPlayer.onProgressChanged = { cur, tot ->
            abProgressMs = cur
            abTotalMs = tot.coerceAtLeast(1L)
        }
        WavPlayer.onPlaybackComplete = {
            abIsPlaying = false
            abProgressMs = 0L
        }
        onDispose {
            WavPlayer.stop()
            WavPlayer.onProgressChanged = prevProgress
            WavPlayer.onPlaybackComplete = prevComplete
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(RVCColors.bgDeep)
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("RVC VOICE CONVERSION", color = RVCColors.accentPurple, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onCleanGarbage,
                colors = ButtonDefaults.buttonColors(containerColor = RVCColors.bgSurface),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(30.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = RVCColors.accentOrange, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Pulisci Cache", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = RVCColors.accentOrange)
            }
        }

        if (freedStorageStatus.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(freedStorageStatus, color = RVCColors.accentGreen, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }

        Spacer(Modifier.height(10.dp))

        // Modalità Selector: Remoto (Dropbox) vs Locale (Onnx)
        if (localOnnxEnabled) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = RVCColors.bgSurface)) {
            Row(
                Modifier.fillMaxWidth().padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = { onRemoteModeChange(true) },
                    modifier = Modifier.weight(1f).height(38.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRemoteMode) RVCColors.accentPurple else RVCColors.bgDeep
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("REMOTO (DROPBOX/PC)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onRemoteModeChange(false) },
                    modifier = Modifier.weight(1f).height(38.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isRemoteMode) RVCColors.accentCyan else RVCColors.bgDeep
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.SettingsVoice, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("LOCALE (ONNX)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        } else {
            Text(
                "RVC REMOTO VIA PC · i modelli .pth restano sul PC, nessuna conversione ONNX",
                color = RVCColors.accentStrong,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        if (remoteMode) {
            // === CARD DROPBOX REMOTO ===
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = RVCColors.bgSurface)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = RVCColors.accentPurple, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("CONFIGURAZIONE DROPBOX / PC PORTATILE", color = RVCColors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { showDropboxSettings = !showDropboxSettings }, modifier = Modifier.height(28.dp)) {
                            Text(if (showDropboxSettings) "Chiudi" else "Cartelle", fontSize = 9.sp, color = RVCColors.accentCyan)
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Pulsante OAuth 2.0 nativo per login a 1-click
                    Button(
                        onClick = {
                            val key = dropboxAppKey.trim()
                            if (key.isNotEmpty()) {
                                val url = "https://www.dropbox.com/oauth2/authorize?client_id=$key&response_type=token&redirect_uri=rvcdroid://dropbox-auth"
                                try {
                                    uriHandler.openUri(url)
                                } catch (_: Exception) {}
                            } else {
                                showDropboxSettings = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RVCColors.accentPurple),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    ) {
                        Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (dropboxToken.isNotBlank()) "RICONNETTI DROPBOX (OAuth 1-Click)" else "ACCEDI CON DROPBOX (OAuth 1-Click)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    OutlinedTextField(
                        value = dropboxToken,
                        onValueChange = onDropboxTokenChange,
                        label = { Text("Dropbox Access Token", fontSize = 9.sp) },
                        placeholder = { Text("Incolla qui il tuo Access Token", fontSize = 9.sp) },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    try {
                                        uriHandler.openUri("https://www.dropbox.com/developers/apps")
                                    } catch (_: Exception) {}
                                }
                            ) {
                                Icon(
                                    Icons.Default.OpenInNew,
                                    contentDescription = "Apri Dropbox Developer App Console",
                                    tint = RVCColors.accentCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Serve un token?",
                            fontSize = 8.sp,
                            color = RVCColors.textDim
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                try {
                                    uriHandler.openUri("https://www.dropbox.com/developers/apps")
                                } catch (_: Exception) {}
                            }
                        ) {
                            Text(
                                "Genera in Dropbox App Console",
                                fontSize = 8.sp,
                                color = RVCColors.accentCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(2.dp))
                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = null,
                                tint = RVCColors.accentCyan,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = onTestDropboxConnection,
                            colors = ButtonDefaults.buttonColors(containerColor = RVCColors.bgDeep),
                            modifier = Modifier.weight(1f).height(32.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text("Test Connessione", fontSize = 9.sp, color = RVCColors.accentCyan)
                        }

                        Button(
                            onClick = onSyncDropboxConfig,
                            colors = ButtonDefaults.buttonColors(containerColor = RVCColors.bgDeep),
                            modifier = Modifier.weight(1f).height(32.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, tint = RVCColors.accentGreen, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Sync Config", fontSize = 9.sp, color = RVCColors.accentGreen)
                        }
                    }

                    if (dropboxConnectionStatus.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            dropboxConnectionStatus,
                            color = if (dropboxConnectionStatus.contains("Errore", ignoreCase = true)) RVCColors.accentRed else RVCColors.accentGreen,
                            fontSize = 9.sp, fontFamily = FontFamily.Monospace
                        )
                    }

                    if (showDropboxSettings) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = dropboxAppKey,
                            onValueChange = onDropboxAppKeyChange,
                            label = { Text("Dropbox App Key (per OAuth)", fontSize = 8.sp) },
                            placeholder = { Text("Es. k7x89ab12cd34ef (da App Console)", fontSize = 8.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Aggiungi 'rvcdroid://dropbox-auth' nei Redirect URIs della tua app Dropbox",
                            fontSize = 7.5.sp,
                            color = RVCColors.textDim,
                            modifier = Modifier.padding(start = 4.dp, top = 1.dp, bottom = 4.dp)
                        )
                        OutlinedTextField(
                            value = dropboxInputDir,
                            onValueChange = onDropboxInputDirChange,
                            label = { Text("Cartella AudioIn (Dropbox)", fontSize = 8.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = dropboxOutputDir,
                            onValueChange = onDropboxOutputDirChange,
                            label = { Text("Cartella AudioOut (Dropbox)", fontSize = 8.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = dropboxConfigFile,
                            onValueChange = onDropboxConfigFileChange,
                            label = { Text("Percorso config.json (Dropbox)", fontSize = 8.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = RVCColors.knobBorder)

                    // MODELLO REMOTO DROP-DOWN
                    Text("MODELLO RVC SUL PORTATILE", color = RVCColors.accentOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(4.dp))

                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { showModelDropdown = true },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = RVCColors.bgDeep)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = RVCColors.accentPurple, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    selectedRemoteModel?.name ?: "(Nessun modello selezionato - tocca per scegliere)",
                                    color = if (selectedRemoteModel != null) Color.White else RVCColors.textDim,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = RVCColors.accentCyan)
                            }
                        }

                        DropdownMenu(
                            expanded = showModelDropdown,
                            onDismissRequest = { showModelDropdown = false },
                            modifier = Modifier.background(RVCColors.bgSurface)
                        ) {
                            if (remoteModels.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Nessun modello in lista. Tocca 'Sync Config'", fontSize = 11.sp) },
                                    onClick = { showModelDropdown = false }
                                )
                            } else {
                                remoteModels.forEach { model ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(model.name, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = RVCColors.textPrimary)
                                                if (model.modelPath.isNotBlank()) {
                                                    Text(model.modelPath.substringAfterLast("/").substringAfterLast("\\"), fontSize = 9.sp, color = RVCColors.textDim)
                                                }
                                            }
                                        },
                                        onClick = {
                                            onSelectRemoteModel(model)
                                            showModelDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (selectedRemoteModel != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "PTH: ${selectedRemoteModel.modelPath}\nIndex: ${if (selectedRemoteModel.indexPath.isNotBlank()) selectedRemoteModel.indexPath else "Nessuno"}",
                            fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = RVCColors.textDim
                        )
                    }
                }
            }
        } else if (localOnnxEnabled) {
            // === CARD LOCALE ONNX ===
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = RVCColors.bgSurface)) {
                Column(Modifier.padding(12.dp)) {
                    Text("COMPONENTI MODELLO LOCALE (ONNX)", color = RVCColors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ModelButton("1. Voce", loadedSynth, onLoadSynth, Modifier.weight(1f))
                        ModelButton("2. HuBERT", loadedVec, onLoadVec, Modifier.weight(1f))
                        ModelButton("3. F0", loadedF0, onLoadF0, Modifier.weight(1f))
                        ModelButton("4. Index", loadedIndex, onLoadIndex, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onPickModelsDir, modifier = Modifier.height(28.dp)) { Text("Scegli Cartella", fontSize = 9.sp) }
                        TextButton(onClick = onRefreshModels, modifier = Modifier.height(28.dp)) { Text("Ricarica", fontSize = 9.sp) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { logText = readLogTail(); showLogDialog = true }, modifier = Modifier.height(28.dp)) {
                            Text("Log", fontSize = 9.sp, color = RVCColors.accentOrange)
                        }
                        TextButton(onClick = { diagText = readAutoloadDiag(); showDiagDialog = true }, modifier = Modifier.height(28.dp)) {
                            Text("Scan", fontSize = 9.sp, color = RVCColors.accentCyan)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Clip Selection Card
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = RVCColors.bgSurface)) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TRACCIA VOCALE DA CONVERTIRE", color = RVCColors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("${clips.size} clip", color = RVCColors.textDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(8.dp))

                if (clips.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("Nessuna registrazione disponibile. Registra una clip in Studio.", color = RVCColors.textDim, fontSize = 11.sp)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        clips.forEach { clip ->
                            val isSelected = clip.id == selectedClip?.id
                            Surface(
                                onClick = { onSelectClip(clip) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) RVCColors.accentPurple.copy(alpha = 0.25f) else RVCColors.bgDeep,
                                border = BorderStroke(1.dp, if (isSelected) RVCColors.accentPurple else RVCColors.knobBorder)
                            ) {
                                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            if (previewingClipPath == clip.path) {
                                                WavPlayer.stop()
                                                previewingClipPath = null
                                            } else {
                                                WavPlayer.play(clip.path)
                                                previewingClipPath = clip.path
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            if (previewingClipPath == clip.path) Icons.Default.Close else Icons.Default.PlayArrow,
                                            contentDescription = "Preview",
                                            tint = if (previewingClipPath == clip.path) RVCColors.accentOrange else RVCColors.accentCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(clip.name, color = if (isSelected) Color.White else RVCColors.textPrimary, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                        Text("%.1fs".format(clip.durationMs / 1000f), color = RVCColors.textDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                    }
                                    if (isSelected) {
                                        Surface(shape = RoundedCornerShape(4.dp), color = RVCColors.accentPurple) {
                                            Text("SELEZIONATO", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Settings & Action Card
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = RVCColors.bgSurface)) {
            Column(Modifier.padding(12.dp)) {
                // Pitch Shift
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(28.dp)) {
                    Text("Pitch", color = RVCColors.textSecondary, fontSize = 10.sp, modifier = Modifier.width(48.dp))
                    Slider(
                        value = (pitchShift + 12) / 24f,
                        onValueChange = { onPitchShift((it * 24 - 12).toInt()) },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = RVCColors.accentPurple, activeTrackColor = RVCColors.accentPurple)
                    )
                    Text("%+d".format(pitchShift), color = RVCColors.accentPurple, fontSize = 10.sp, modifier = Modifier.width(32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }

                // Index Rate
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(28.dp)) {
                    Text("Index", color = RVCColors.textSecondary, fontSize = 10.sp, modifier = Modifier.width(48.dp))
                    Slider(
                        value = indexRate,
                        onValueChange = onIndexRate,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = RVCColors.accentPurple, activeTrackColor = RVCColors.accentPurple)
                    )
                    Text("%.0f%%".format(indexRate * 100), color = RVCColors.accentPurple, fontSize = 10.sp, modifier = Modifier.width(32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }

                // Protect
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(28.dp)) {
                    Text("Protect", color = RVCColors.textSecondary, fontSize = 10.sp, modifier = Modifier.width(48.dp))
                    Slider(
                        value = protection,
                        onValueChange = onProtection,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = RVCColors.accentPurple, activeTrackColor = RVCColors.accentPurple)
                    )
                    Text("%.0f%%".format(protection * 100), color = RVCColors.accentPurple, fontSize = 10.sp, modifier = Modifier.width(32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }

                // F0 Method
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(32.dp)) {
                    Text("F0", color = RVCColors.textSecondary, fontSize = 10.sp, modifier = Modifier.width(48.dp))
                    listOf("rmvpe", "harvest", "crepe", "pm").forEach { method ->
                        FilterChip(
                            selected = f0Method == method,
                            onClick = { onF0Method(method) },
                            label = { Text(method.uppercase(), fontSize = 8.sp) },
                            modifier = Modifier.padding(end = 4.dp).height(24.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = RVCColors.accentPurple.copy(alpha = 0.3f), selectedLabelColor = RVCColors.accentPurple)
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Opzione: Aggiungi a nuova traccia e muta la sorgente
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = autoMuteOriginal,
                        onCheckedChange = onAutoMuteOriginalChange,
                        colors = CheckboxDefaults.colors(checkedColor = RVCColors.accentPurple)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Crea nuova traccia e muta la traccia sorgente", fontSize = 10.sp, color = RVCColors.textPrimary)
                }

                Spacer(Modifier.height(8.dp))

                if (remoteMode) {
                    if (remoteProgressText.isNotBlank()) {
                        Text(remoteProgressText, color = RVCColors.accentCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(4.dp))
                    }
                    if (remoteProgress in 0f..0.99f) {
                        LinearProgressIndicator(progress = { remoteProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = RVCColors.accentPurple)
                        Spacer(Modifier.height(8.dp))
                    }

                    Button(
                        onClick = onApplyRemoteRvc,
                        enabled = selectedClip != null && dropboxToken.isNotBlank() && (remoteProgress < 0f || remoteProgress >= 1f),
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RVCColors.accentPurple),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (remoteProgress in 0f..0.99f) "ELABORAZIONE IN CORSO SU PC..." else "CONVERTI CON RVC (DROPBOX)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                } else if (localOnnxEnabled) {
                    if (rvcProgress in 0f..0.99f) {
                        LinearProgressIndicator(progress = { rvcProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = RVCColors.accentCyan)
                        Spacer(Modifier.height(8.dp))
                    }

                    Button(
                        onClick = onApplyRvc,
                        enabled = selectedClip != null && loadedSynth && loadedVec && loadedF0 && (rvcProgress < 0f || rvcProgress >= 1f),
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RVCColors.accentCyan),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (rvcProgress in 0f..0.99f) "CONVERSIONE IN CORSO..." else "CONVERTI CON RVC LOCALE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                // Confronto A/B rapido
                if (convertedClip != null && selectedClip != null) {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = RVCColors.bgDeep),
                        border = BorderStroke(1.dp, RVCColors.accentPurple.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CompareArrows, contentDescription = null, tint = RVCColors.accentPurple, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("CONFRONTO RAPIDO A / B", color = RVCColors.accentPurple, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { abActiveTrack = "A"; if (abIsPlaying) WavPlayer.play(selectedClip.path, abProgressMs) },
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = if (abActiveTrack == "A") RVCColors.accentCyan else RVCColors.bgSurface)
                                ) {
                                    Text("A: ORIGINALE", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { abActiveTrack = "B"; if (abIsPlaying) WavPlayer.play(convertedClip.path, abProgressMs) },
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = if (abActiveTrack == "B") RVCColors.accentPurple else RVCColors.bgSurface)
                                ) {
                                    Text("B: CONVERTITO", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = {
                                        if (abIsPlaying) {
                                            WavPlayer.stop()
                                            abIsPlaying = false
                                        } else {
                                            val p = if (abActiveTrack == "A") selectedClip.path else convertedClip.path
                                            abIsPlaying = true
                                            WavPlayer.play(p, abProgressMs)
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(34.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = if (abIsPlaying) RVCColors.accentOrange else RVCColors.accentGreen)
                                ) {
                                    Icon(if (abIsPlaying) Icons.Default.Close else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (abIsPlaying) "STOP" else "ASCOLTA [${abActiveTrack}]", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { onExportClip(convertedClip) },
                                    modifier = Modifier.weight(1f).height(34.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = RVCColors.accentPurple)
                                ) {
                                    Text("ESPORTA", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLogDialog) {
        AlertDialog(
            onDismissRequest = { showLogDialog = false },
            title = { Text("Log nativo", fontSize = 13.sp) },
            text = { Text(logText, fontSize = 8.sp, fontFamily = FontFamily.Monospace) },
            confirmButton = { TextButton(onClick = { showLogDialog = false }) { Text("Chiudi") } }
        )
    }

    if (showDiagDialog) {
        AlertDialog(
            onDismissRequest = { showDiagDialog = false },
            title = { Text("Scansione modelli", fontSize = 13.sp) },
            text = { Text(diagText, fontSize = 8.sp, fontFamily = FontFamily.Monospace) },
            confirmButton = { TextButton(onClick = { showDiagDialog = false }) { Text("Chiudi") } }
        )
    }
}

@Composable
fun ModelButton(label: String, loaded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (loaded) RVCColors.accentGreen else RVCColors.textSecondary),
        border = BorderStroke(1.dp, if (loaded) RVCColors.accentGreen else RVCColors.textDim),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (loaded) Icons.Default.CheckCircle else Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(label, fontSize = 9.sp)
        }
    }
}
