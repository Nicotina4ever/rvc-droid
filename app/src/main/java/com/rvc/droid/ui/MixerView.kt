package com.rvc.droid.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rvc.droid.audio.NativeAudioBridge
import com.rvc.droid.ui.components.*
import com.rvc.droid.ui.plugins.PluginRackView
import com.rvc.droid.ui.project.MixerTrackState
import com.rvc.droid.ui.theme.RVCColors

@Composable
fun MixerView(
    modifier: Modifier = Modifier,
    bridge: NativeAudioBridge = NativeAudioBridge,
    inputLevel: InputLevel = InputLevel(),
    gateState: NoiseGateState = NoiseGateState(), gateGR: Float = 0f,
    voiceBands: List<EQBandState> = List(4) { EQBandState(freq = 1000f * (it + 1), gainDB = 0f, q = 1f) },
    musicBands: List<EQBandState> = List(4) { EQBandState(freq = 500f * (it + 1), gainDB = 0f, q = 1f) },
    compThreshold: Float = -20f, compRatio: Float = 4f, compAttack: Float = 5f, compRelease: Float = 50f, compMakeup: Float = 0f, compGR: Float = 0f,
    limiterThreshold: Float = -0.5f, limiterRelease: Float = 60f, limiterGR: Float = 0f,
    voiceVolume: Float = 0f, musicVolume: Float = 0f, masterVolume: Float = 0f,
    monitoringGain: Float = 0.7f,
    reverbMix: Float = 0f, reverbRoomSize: Float = 0.5f, reverbDamping: Float = 0.5f,
    momentary: Float = -70f, shortTerm: Float = -70f, integrated: Float = -70f, truePeak: Float = -70f,
    onVoiceBandUpdate: (Int, EQBandState) -> Unit = { _, _ -> },
    onMusicBandUpdate: (Int, EQBandState) -> Unit = { _, _ -> },
    onCompThreshold: (Float) -> Unit = {}, onCompRatio: (Float) -> Unit = {},
    onCompAttack: (Float) -> Unit = {}, onCompRelease: (Float) -> Unit = {}, onCompMakeup: (Float) -> Unit = {},
    onLimiterThreshold: (Float) -> Unit = {}, onLimiterRelease: (Float) -> Unit = {},
    onGateToggle: () -> Unit = {}, onGateThreshold: (Float) -> Unit = {},
    onVoiceVolume: (Float) -> Unit = {}, onMusicVolume: (Float) -> Unit = {}, onMasterVolume: (Float) -> Unit = {},
    onMonitoringGain: (Float) -> Unit = {},
    onReverbMix: (Float) -> Unit = {}, onReverbRoomSize: (Float) -> Unit = {}, onReverbDamping: (Float) -> Unit = {},
    onLoadTrack: (Int) -> Unit = {},
    onRemoveTrack: (Int) -> Unit = {},
    onMoveTrack: (Int, Int) -> Unit = { _, _ -> },
    onTrackVolume: (Int, Float) -> Unit = { _, _ -> },
    onTrackPan: (Int, Float) -> Unit = { _, _ -> },
    onTrackMute: (Int, Boolean) -> Unit = { _, _ -> },
    onTrackSolo: (Int, Boolean) -> Unit = { _, _ -> },
    onTrackDuck: (Int, Float) -> Unit = { _, _ -> },
    selectedTrackIndex: Int = 0,
    mixerTracks: List<MixerTrackState> = emptyList(),
    timelinePositionMs: Long = 0L,
    onTrackOffsetChange: (Int, String, Long) -> Unit = { _, _, _ -> },
    selectedClipId: String? = null,
    onClipSelected: (String) -> Unit = {},
    onPlayTimeline: () -> Unit = {},
    onStopTimeline: () -> Unit = {},
    onResetMastering: () -> Unit = {},
    onExport: () -> Unit = {},
    onExportStereo: () -> Unit = {},
    onSeekTimeline: (Long) -> Unit = {},
    onRemoveClip: (String) -> Unit = {},
    onRenameClip: (String, String) -> Unit = { _, _ -> },
    onExportRegion: (Long, Long) -> Unit = { _, _ -> },
    onConvertSelectionRemoteRvc: (Long, Long) -> Unit = { _, _ -> },
    onOpenRegionDialog: () -> Unit = {},
) {
    var selectedEffectTab by remember { mutableIntStateOf(0) }
    var showPluginRack by remember { mutableStateOf(false) }
    var timelineViewport by remember { mutableStateOf(TimelineViewport()) }
    var selectionStartMs by remember { mutableLongStateOf(0L) }
    var selectionEndMs by remember { mutableLongStateOf(0L) }
    var showRenameDialogForClip by remember { mutableStateOf<MixerTrackState?>(null) }
    var renameClipText by remember { mutableStateOf("") }

    val totalMixDurationMs = mixerTracks.maxOfOrNull { it.offsetMs + it.durationMs }?.coerceAtLeast(1L) ?: 60000L

    Column(modifier.fillMaxSize().background(RVCColors.bgDeep).verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("MIXER", color = RVCColors.accentCyan, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Button(onClick = onOpenRegionDialog, colors = ButtonDefaults.buttonColors(containerColor = RVCColors.surface2), shape = RoundedCornerShape(8.dp), modifier = Modifier.height(32.dp)) {
                Text("SPOT 01..N", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RVCColors.accentStrong)
            }
            Spacer(Modifier.width(4.dp))
            Button(onClick = { showPluginRack = true }, colors = ButtonDefaults.buttonColors(containerColor = RVCColors.accentPurple), shape = RoundedCornerShape(8.dp), modifier = Modifier.height(32.dp)) {
                Text("PLUGIN", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(4.dp))
            Button(onClick = onExport, colors = ButtonDefaults.buttonColors(containerColor = RVCColors.accentGreen), shape = RoundedCornerShape(8.dp), modifier = Modifier.height(32.dp)) {
                Text("EXPORT", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(4.dp))
            Button(onClick = onExportStereo, colors = ButtonDefaults.buttonColors(containerColor = RVCColors.accentCyan), shape = RoundedCornerShape(8.dp), modifier = Modifier.height(32.dp)) {
                Text("STEREO", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            if (selectionEndMs > selectionStartMs) {
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = { onExportRegion(selectionStartMs, selectionEndMs) },
                    colors = ButtonDefaults.buttonColors(containerColor = RVCColors.accentPurple),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("REGIONE", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = { onConvertSelectionRemoteRvc(selectionStartMs, selectionEndMs) },
                    colors = ButtonDefaults.buttonColors(containerColor = RVCColors.accentOrange),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("RVC IN/OUT", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        InputLevelMeter(level = inputLevel)
        Spacer(Modifier.height(10.dp))

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = RVCColors.bgSurface)) {
            Column(Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TIMELINE", color = RVCColors.accentOrange, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "%.1fs / %.1fs".format(timelinePositionMs / 1000f, totalMixDurationMs / 1000f),
                        color = RVCColors.textDim,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onPlayTimeline, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = RVCColors.accentGreen)
                    }
                    IconButton(onClick = onStopTimeline, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = "Stop", tint = RVCColors.accentRed)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ZOOM", color = RVCColors.textDim, fontSize = 9.sp, modifier = Modifier.width(40.dp))
                    Slider(
                        value = timelineViewport.zoom,
                        onValueChange = { timelineViewport = timelineViewport.copy(zoom = it) },
                        valueRange = 0.5f..8f,
                        modifier = Modifier.weight(1f).height(20.dp)
                    )
                    Text("%.1fx".format(timelineViewport.zoom), color = RVCColors.textDim, fontSize = 9.sp, modifier = Modifier.width(40.dp))
                }
                Spacer(Modifier.height(6.dp))
                TimelineView(
                    tracks = mixerTracks.map { t ->
                        TrackClip(
                            id = t.clipId,
                            trackIndex = t.trackIndex,
                            name = t.clipName,
                            durationMs = t.durationMs,
                            offsetMs = t.offsetMs,
                            color = when (t.trackIndex % 4) {
                                0 -> RVCColors.accentCyan
                                1 -> RVCColors.accentGreen
                                2 -> RVCColors.accentPurple
                                else -> RVCColors.accentOrange
                            }
                        )
                    },
                    playbackPositionMs = timelinePositionMs,
                    totalDurationMs = totalMixDurationMs,
                    selectedClipId = selectedClipId,
                    onClipSelected = onClipSelected,
                    viewport = timelineViewport,
                    onViewportChange = { timelineViewport = it },
                    onSeekPosition = onSeekTimeline,
                    selectionStartMs = selectionStartMs,
                    selectionEndMs = selectionEndMs,
                    onOffsetChange = { clipId, offset ->
                        val clip = mixerTracks.firstOrNull { it.clipId == clipId }
                        if (clip != null) onTrackOffsetChange(clip.trackIndex, clipId, offset)
                    }
                )

                // Selected Clip Quick Actions panel
                val activeClip = mixerTracks.firstOrNull { it.clipId == selectedClipId }
                if (activeClip != null) {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = RVCColors.bgDeep),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, RVCColors.accentOrange.copy(alpha = 0.5f))
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "CLIP: ${activeClip.clipName}",
                                    color = RVCColors.accentOrange,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        showRenameDialogForClip = activeClip
                                        renameClipText = activeClip.clipName
                                    },
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text("Rinomina", fontSize = 9.sp, color = RVCColors.accentCyan)
                                }
                                TextButton(
                                    onClick = { onRemoveClip(activeClip.clipId) },
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text("Rimuovi", fontSize = 9.sp, color = RVCColors.accentRed)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Offset: %.2fs".format(activeClip.offsetMs / 1000f), color = RVCColors.textDim, fontSize = 9.sp, modifier = Modifier.weight(1f))
                                Button(
                                    onClick = {
                                        val newOff = (activeClip.offsetMs - 100L).coerceAtLeast(0L)
                                        onTrackOffsetChange(activeClip.trackIndex, activeClip.clipId, newOff)
                                    },
                                    modifier = Modifier.height(24.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = RVCColors.bgSurface)
                                ) { Text("-100ms", fontSize = 8.sp) }
                                Button(
                                    onClick = {
                                        val newOff = activeClip.offsetMs + 100L
                                        onTrackOffsetChange(activeClip.trackIndex, activeClip.clipId, newOff)
                                    },
                                    modifier = Modifier.height(24.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = RVCColors.bgSurface)
                                ) { Text("+100ms", fontSize = 8.sp) }
                                Button(
                                    onClick = {
                                        val newOff = (activeClip.offsetMs - 1000L).coerceAtLeast(0L)
                                        onTrackOffsetChange(activeClip.trackIndex, activeClip.clipId, newOff)
                                    },
                                    modifier = Modifier.height(24.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = RVCColors.bgSurface)
                                ) { Text("-1s", fontSize = 8.sp) }
                                Button(
                                    onClick = {
                                        val newOff = activeClip.offsetMs + 1000L
                                        onTrackOffsetChange(activeClip.trackIndex, activeClip.clipId, newOff)
                                    },
                                    modifier = Modifier.height(24.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = RVCColors.bgSurface)
                                ) { Text("+1s", fontSize = 8.sp) }
                            }
                        }
                    }
                }
            }
        }

        if (showRenameDialogForClip != null) {
            val clipToRename = showRenameDialogForClip!!
            AlertDialog(
                onDismissRequest = { showRenameDialogForClip = null },
                title = { Text("Rinomina Clip", fontSize = 13.sp) },
                text = {
                    OutlinedTextField(
                        value = renameClipText,
                        onValueChange = { renameClipText = it },
                        label = { Text("Nome Clip") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (renameClipText.isNotBlank()) {
                            onRenameClip(clipToRename.clipId, renameClipText)
                        }
                        showRenameDialogForClip = null
                    }) { Text("Salva") }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialogForClip = null }) { Text("Annulla") }
                }
            )
        }

        Spacer(Modifier.height(12.dp))

        TabRow(
            selectedTabIndex = selectedEffectTab,
            containerColor = RVCColors.bgSurface,
            contentColor = RVCColors.accentCyan
        ) {
            listOf("Tracce", "Dynamics", "EQ", "Reverb", "Master").forEachIndexed { i, title ->
                Tab(
                    selected = selectedEffectTab == i,
                    onClick = { selectedEffectTab = i },
                    text = { Text(title, fontSize = 10.sp) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        when (selectedEffectTab) {
            0 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = RVCColors.bgSurface)) {
                    Column(Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("TRACCE MIXER", color = RVCColors.accentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                            Button(
                                onClick = { onLoadTrack(-1) },
                                colors = ButtonDefaults.buttonColors(containerColor = RVCColors.accentGreen),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("+ AGGIUNGI", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (mixerTracks.isEmpty()) {
                            Text("Nessuna traccia. Tocca + AGGIUNGI per assegnare una registrazione alla prima traccia.",
                                color = RVCColors.textDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        } else {
                            mixerTracks.forEach { track ->
                                val isSelected = track.trackIndex == selectedTrackIndex
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .padding(vertical = 2.dp)
                                        .fillMaxWidth()
                                        .background(
                                            if (isSelected) RVCColors.accentCyan.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .clickable { onClipSelected(track.clipId) }
                                        .padding(horizontal = 4.dp)
                                ) {
                                    Text("#${track.trackIndex + 1}", color = RVCColors.accentOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                                    Text(track.clipName, color = RVCColors.textPrimary, fontSize = 10.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text("${track.durationMs / 1000}s", color = RVCColors.textDim, fontSize = 9.sp, modifier = Modifier.width(40.dp))
                                    TextButton(onClick = { onMoveTrack(track.trackIndex, -1) }, modifier = Modifier.height(24.dp), contentPadding = PaddingValues(2.dp)) {
                                        Text("↑", fontSize = 11.sp, color = RVCColors.accentCyan)
                                    }
                                    TextButton(onClick = { onMoveTrack(track.trackIndex, +1) }, modifier = Modifier.height(24.dp), contentPadding = PaddingValues(2.dp)) {
                                        Text("↓", fontSize = 11.sp, color = RVCColors.accentCyan)
                                    }
                                    TextButton(onClick = { onRemoveTrack(track.trackIndex) }, modifier = Modifier.height(24.dp), contentPadding = PaddingValues(2.dp)) {
                                        Text("✕", fontSize = 11.sp, color = RVCColors.accentRed, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            // Pannello effetti per la traccia selezionata.
                            val selected = mixerTracks.firstOrNull { it.trackIndex == selectedTrackIndex }
                            if (selected != null) {
                                Spacer(Modifier.height(8.dp))
                                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = RVCColors.bgDeep)) {
                                    Column(Modifier.padding(8.dp)) {
                                        Text(
                                            "EFFETTI TRACCIA #${selected.trackIndex + 1}: ${selected.clipName}",
                                            color = RVCColors.accentPurple, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
                                        )
                                        // Volume (0..1, default 0.85)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("VOL", color = RVCColors.textDim, fontSize = 9.sp, modifier = Modifier.width(36.dp))
                                            Slider(
                                                value = selected.volume,
                                                onValueChange = { onTrackVolume(selected.trackIndex, it) },
                                                valueRange = 0f..1f,
                                                modifier = Modifier.weight(1f).height(20.dp)
                                            )
                                            Text("${(selected.volume * 100).toInt()}%", color = RVCColors.textDim, fontSize = 9.sp, modifier = Modifier.width(36.dp))
                                        }
                                        // Pan (-1..1, default 0)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("PAN", color = RVCColors.textDim, fontSize = 9.sp, modifier = Modifier.width(36.dp))
                                            Slider(
                                                value = selected.pan,
                                                onValueChange = { onTrackPan(selected.trackIndex, it) },
                                                valueRange = -1f..1f,
                                                modifier = Modifier.weight(1f).height(20.dp)
                                            )
                                            Text(panLabel(selected.pan), color = RVCColors.textDim, fontSize = 9.sp, modifier = Modifier.width(48.dp))
                                        }
                                        // Mute / Solo
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Button(
                                                onClick = { onTrackMute(selected.trackIndex, !selected.muted) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (selected.muted) RVCColors.accentRed else RVCColors.bgSurface
                                                ),
                                                modifier = Modifier.weight(1f).height(28.dp),
                                                contentPadding = PaddingValues(2.dp)
                                            ) { Text(if (selected.muted) "MUTE ON" else "MUTE", fontSize = 9.sp) }
                                            Spacer(Modifier.width(4.dp))
                                            Button(
                                                onClick = { onTrackSolo(selected.trackIndex, !selected.solo) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (selected.solo) RVCColors.accentOrange else RVCColors.bgSurface
                                                ),
                                                modifier = Modifier.weight(1f).height(28.dp),
                                                contentPadding = PaddingValues(2.dp)
                                            ) { Text(if (selected.solo) "SOLO ON" else "SOLO", fontSize = 9.sp) }
                                        }
                                        // Ducking verso la traccia "superiore" (trackIndex - 1)
                                        val upperIdx = selected.trackIndex - 1
                                        if (upperIdx >= 0 && mixerTracks.any { it.trackIndex == upperIdx }) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                "DUCK verso traccia #${upperIdx + 1} (auto-livellamento)",
                                                color = RVCColors.accentCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("AMT", color = RVCColors.textDim, fontSize = 9.sp, modifier = Modifier.width(36.dp))
                                                Slider(
                                                    value = selected.duckAmount,
                                                    onValueChange = { onTrackDuck(selected.trackIndex, it) },
                                                    valueRange = 0f..1f,
                                                    modifier = Modifier.weight(1f).height(20.dp)
                                                )
                                                Text("${(selected.duckAmount * 100).toInt()}%", color = RVCColors.textDim, fontSize = 9.sp, modifier = Modifier.width(36.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            1 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {                NoiseGatePanel(
                    gate = gateState,
                    onToggle = onGateToggle,
                    onThreshold = onGateThreshold,
                    onAttack = { s -> bridge.setNoiseGate(gateState.enabled, gateState.threshold, s, gateState.holdMs, gateState.releaseMs) },
                    onHold = { s -> bridge.setNoiseGate(gateState.enabled, gateState.threshold, gateState.attackMs, s, gateState.releaseMs) },
                    onRelease = { s -> bridge.setNoiseGate(gateState.enabled, gateState.threshold, gateState.attackMs, gateState.holdMs, s) },
                    gainReduction = gateGR
                )
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = RVCColors.bgSurface)) {
                    Column(Modifier.padding(8.dp)) {
                        Text("COMPRESSOR", color = RVCColors.accentOrange, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        SideChainPanel(
                            threshold = compThreshold, ratio = compRatio, attack = compAttack,
                            release = compRelease, makeup = compMakeup, gainReduction = compGR,
                            onThreshold = onCompThreshold, onRatio = onCompRatio, onAttack = onCompAttack,
                            onRelease = onCompRelease, onMakeup = onCompMakeup
                        )
                    }
                }
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = RVCColors.bgSurface)) {
                    Column(Modifier.padding(8.dp)) {
                        Text("LIMITER", color = RVCColors.accentRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        LimiterPanel(
                            threshold = limiterThreshold, release = limiterRelease, gainReduction = limiterGR,
                            onThreshold = onLimiterThreshold, onRelease = onLimiterRelease
                        )
                    }
                }
            }
            2 -> Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = RVCColors.bgSurface)) {
                Column(Modifier.padding(8.dp)) {
                    Text("VOICE EQ", color = RVCColors.accentCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    voiceBands.forEachIndexed { i, b ->
                        EQBandSlider(
                            band = b,
                            onGainChange = { g -> onVoiceBandUpdate(i, b.copy(gainDB = g)) },
                            onFreqChange = { f -> onVoiceBandUpdate(i, b.copy(freq = f)) },
                            onQChange = { q -> onVoiceBandUpdate(i, b.copy(q = q)) }
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("MUSIC EQ", color = RVCColors.accentGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    musicBands.forEachIndexed { i, b ->
                        EQBandSlider(
                            band = b,
                            onGainChange = { g -> onMusicBandUpdate(i, b.copy(gainDB = g)) },
                            onFreqChange = { f -> onMusicBandUpdate(i, b.copy(freq = f)) },
                            onQChange = { q -> onMusicBandUpdate(i, b.copy(q = q)) }
                        )
                    }
                }
            }
            3 -> Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = RVCColors.bgSurface)) {
                Column(Modifier.padding(8.dp)) {
                    Text("REVERB", color = RVCColors.accentCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Slider(value = reverbMix, onValueChange = onReverbMix, valueRange = 0f..1f)
                    Text("Mix: %.0f%%".format(reverbMix * 100), color = RVCColors.textDim, fontSize = 9.sp)
                    Slider(value = reverbRoomSize, onValueChange = onReverbRoomSize, valueRange = 0f..1f)
                    Text("Room: %.0f%%".format(reverbRoomSize * 100), color = RVCColors.textDim, fontSize = 9.sp)
                    Slider(value = reverbDamping, onValueChange = onReverbDamping, valueRange = 0f..1f)
                    Text("Damping: %.0f%%".format(reverbDamping * 100), color = RVCColors.textDim, fontSize = 9.sp)
                }
            }
            4 -> Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = RVCColors.bgSurface)) {
                Column(Modifier.padding(8.dp)) {
                    Text("MASTER", color = RVCColors.accentOrange, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("Voice: %.1f dB".format(voiceVolume), color = RVCColors.textDim, fontSize = 9.sp)
                    Text("Music: %.1f dB".format(musicVolume), color = RVCColors.textDim, fontSize = 9.sp)
                    Text("Master: %.1f dB".format(masterVolume), color = RVCColors.textDim, fontSize = 9.sp)
                    Text("Monitoring: %.0f%%".format(monitoringGain * 100), color = RVCColors.textDim, fontSize = 9.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("LUFS", color = RVCColors.accentCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("M: %.1f   S: %.1f".format(momentary, shortTerm), color = RVCColors.textDim, fontSize = 9.sp)
                    Text("I: %.1f   TP: %.1f".format(integrated, truePeak), color = RVCColors.textDim, fontSize = 9.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onResetMastering, modifier = Modifier.height(32.dp)) {
                        Text("RESET MASTERING", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // ------------------------------------------------------- plugin rack full screen
    if (showPluginRack) {
        Dialog(
            onDismissRequest = { showPluginRack = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = RVCColors.bg,
            ) {
                PluginRackView(
                    onClose = { showPluginRack = false },
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
                    voiceVolume = voiceVolume,
                    musicVolume = musicVolume,
                    masterVolume = masterVolume,
                    monitoringGain = monitoringGain,
                    momentary = momentary,
                    shortTerm = shortTerm,
                    integrated = integrated,
                    truePeak = truePeak,
                    onGateToggle = onGateToggle,
                    onGateThreshold = onGateThreshold,
                    onVoiceBandUpdate = onVoiceBandUpdate,
                    onMusicBandUpdate = onMusicBandUpdate,
                    onCompThreshold = onCompThreshold,
                    onCompRatio = onCompRatio,
                    onCompAttack = onCompAttack,
                    onCompRelease = onCompRelease,
                    onCompMakeup = onCompMakeup,
                    onLimiterThreshold = onLimiterThreshold,
                    onLimiterRelease = onLimiterRelease,
                    onReverbMix = onReverbMix,
                    onReverbRoomSize = onReverbRoomSize,
                    onReverbDamping = onReverbDamping,
                    onVoiceVolume = onVoiceVolume,
                    onMusicVolume = onMusicVolume,
                    onMasterVolume = onMasterVolume,
                    onMonitoringGain = onMonitoringGain,
                    onResetMastering = onResetMastering,
                )
            }
        }
    }
}

private fun panLabel(pan: Float): String {
    if (pan > 0.05f) return "R ${(pan * 100).toInt()}%"
    if (pan < -0.05f) return "L ${(-pan * 100).toInt()}%"
    return "C"
}
