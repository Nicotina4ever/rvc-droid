package com.rvc.droid.ui.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvc.droid.ui.components.InputLevel
import com.rvc.droid.ui.components.InputLevelMeter
import com.rvc.droid.ui.components.TeleprompterOverlay
import com.rvc.droid.ui.components.TimelineView
import com.rvc.droid.ui.components.TimelineViewport
import com.rvc.droid.ui.components.TrackClip
import com.rvc.droid.ui.components.formatTimecode
import com.rvc.droid.ui.project.MixerTrackState
import com.rvc.droid.ui.project.RecordingClip
import com.rvc.droid.ui.theme.RVCColors
import com.rvc.droid.ui.theme.RvcSpacing
import com.rvc.droid.ui.theme.RvcTypography
import com.rvc.droid.ui.video.VideoPreviewView
import kotlin.math.roundToInt

/**
 * Studio unico: trasporto, tracce creabili/armabili, timeline, mixer,
 * video di riferimento e gobbo nella stessa schermata.
 */
@Composable
fun StudioWorkspace(
    modifier: Modifier = Modifier,
    isRunning: Boolean,
    inputLevel: InputLevel,
    recordings: List<RecordingClip>,
    tracks: List<MixerTrackState>,
    armedTrackIndex: Int?,
    selectedClipId: String?,
    timelinePositionMs: Long,
    timelineDurationMs: Long,
    timelinePlaying: Boolean,
    waveformPeaks: FloatArray,
    waveformProgress: Float,
    videoPath: String?,
    videoPlaying: Boolean,
    showVideo: Boolean,
    onToggleVideo: () -> Unit,
    onLoadVideo: () -> Unit,
    onPlayPauseVideo: () -> Unit,
    onStopVideo: () -> Unit,
    onVideoPositionChanged: (Long) -> Unit,
    onStartStop: () -> Unit,
    onPlayTimeline: () -> Unit,
    onStopTimeline: () -> Unit,
    onSeekTimeline: (Long) -> Unit,
    onAddTrack: () -> Unit,
    onRemoveTrack: (Int) -> Unit,
    onArmTrack: (Int) -> Unit,
    onSelectTrack: (Int) -> Unit,
    onSelectClip: (String) -> Unit,
    onTrackVolume: (Int, Float) -> Unit,
    onTrackPan: (Int, Float) -> Unit,
    onTrackMute: (Int, Boolean) -> Unit,
    onTrackSolo: (Int, Boolean) -> Unit,
    onTrackOffsetChange: (Int, String, Long) -> Unit,
    voiceVolume: Float,
    musicVolume: Float,
    masterVolume: Float,
    momentary: Float,
    shortTerm: Float,
    integrated: Float,
    truePeak: Float,
    onVoiceVolume: (Float) -> Unit,
    onMusicVolume: (Float) -> Unit,
    onMasterVolume: (Float) -> Unit,
    onOpenPluginRack: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenAdvancedMixer: () -> Unit,
    backingLoaded: Boolean,
    backingGain: Float,
    backingPosition: Float,
    onLoadBacking: () -> Unit,
    onBackingGain: (Float) -> Unit,
    onBackingSeek: (Float) -> Unit,
    gobboText: String,
    onGobboTextChange: (String) -> Unit,
    gobboFontSize: Int,
    onGobboFontSizeChange: (Int) -> Unit,
    gobboSpeed: Float,
    onGobboSpeedChange: (Float) -> Unit,
    showGobbo: Boolean,
    onToggleGobbo: () -> Unit,
    onExport: () -> Unit,
    onExportClip: (RecordingClip) -> Unit,
    onDeleteClip: (RecordingClip) -> Unit,
) {
    var viewport by remember { mutableStateOf(TimelineViewport()) }
    val armedTrack = tracks.firstOrNull { it.trackIndex == armedTrackIndex }
    val clips = tracks
        .filter { it.clipPath.isNotBlank() && it.durationMs > 0 }
        .map { track ->
            TrackClip(
                id = track.clipId,
                trackIndex = track.trackIndex,
                name = track.clipName.ifBlank { "Traccia ${track.trackIndex + 1}" },
                durationMs = track.durationMs,
                offsetMs = track.offsetMs,
                color = trackColor(track.trackIndex),
            )
        }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(RVCColors.bg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "STUDIO",
                    color = RVCColors.accentStrong,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (armedTrack != null) "ARM · ${armedTrack.clipName.ifBlank { "Traccia ${armedTrack.trackIndex + 1}" }}"
                    else "NESSUNA TRACCIA ARMATA",
                    color = if (armedTrack != null) RVCColors.danger else RVCColors.warning,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = onToggleVideo,
                    shape = RoundedCornerShape(7.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Icon(Icons.Filled.Movie, contentDescription = null, tint = RVCColors.textDim, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (showVideo) "VIDEO ON" else "VIDEO", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        item {
            TransportCard(
                isRunning = isRunning,
                inputLevel = inputLevel,
                timelinePositionMs = timelinePositionMs,
                timelinePlaying = timelinePlaying,
                armedLabel = armedTrack?.let { "TRACCIA ${it.trackIndex + 1}" } ?: "NESSUNA",
                onStartStop = onStartStop,
                onPlayTimeline = onPlayTimeline,
                onStopTimeline = onStopTimeline,
            )
        }

        item {
            TrackSection(
                tracks = tracks,
                armedTrackIndex = armedTrackIndex,
                onAddTrack = onAddTrack,
                onRemoveTrack = onRemoveTrack,
                onArmTrack = onArmTrack,
                onSelectTrack = onSelectTrack,
                onTrackVolume = onTrackVolume,
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = RVCColors.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("TIMELINE", style = RvcTypography.labelSmall, color = RVCColors.textMute)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onOpenEditor, enabled = selectedClipId != null) {
                            Text("EDIT WAV", fontSize = 9.sp, color = if (selectedClipId != null) RVCColors.accentStrong else RVCColors.textMute)
                        }
                        Text(formatTimecode(timelinePositionMs), color = RVCColors.accentStrong, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    if (clips.isEmpty()) {
                        Box(
                            Modifier.fillMaxWidth().height(150.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Nessuna clip", color = RVCColors.textDim, fontSize = 12.sp)
                                Spacer(Modifier.height(6.dp))
                                Text("Arma una traccia e premi REC", color = RVCColors.textMute, fontSize = 10.sp)
                            }
                        }
                    } else {
                        TimelineView(
                            tracks = clips,
                            onOffsetChange = { clipId, offset ->
                                val track = tracks.firstOrNull { it.clipId == clipId }
                                if (track != null) onTrackOffsetChange(track.trackIndex, clipId, offset)
                            },
                            playbackPositionMs = timelinePositionMs,
                            totalDurationMs = timelineDurationMs,
                            selectedClipId = selectedClipId,
                            onClipSelected = onSelectClip,
                            viewport = viewport,
                            onViewportChange = { viewport = it },
                            onSeekPosition = onSeekTimeline,
                        )
                    }
                }
            }
        }

        item { MixerSection(
            tracks = tracks,
            voiceVolume = voiceVolume,
            musicVolume = musicVolume,
            masterVolume = masterVolume,
            momentary = momentary,
            shortTerm = shortTerm,
            integrated = integrated,
            truePeak = truePeak,
            onVoiceVolume = onVoiceVolume,
            onMusicVolume = onMusicVolume,
            onMasterVolume = onMasterVolume,
            onOpenPluginRack = onOpenPluginRack,
            onOpenAdvancedMixer = onOpenAdvancedMixer,
            onTrackVolume = onTrackVolume,
            onTrackPan = onTrackPan,
            onTrackMute = onTrackMute,
            onTrackSolo = onTrackSolo,
        ) }

        if (showVideo) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = RVCColors.surface), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        VideoPreviewView(
                            modifier = Modifier.fillMaxWidth().height(240.dp),
                            videoPath = videoPath,
                            isPlaying = videoPlaying,
                            onLoadVideo = onLoadVideo,
                            onPlayPause = onPlayPauseVideo,
                            onStop = onStopVideo,
                            onPositionChanged = onVideoPositionChanged,
                        )
                    }
                }
            }
        }

        item {
            GobboCard(
                text = gobboText,
                onTextChange = onGobboTextChange,
                fontSize = gobboFontSize,
                onFontSizeChange = onGobboFontSizeChange,
                speed = gobboSpeed,
                onSpeedChange = onGobboSpeedChange,
                isVisible = showGobbo,
                onToggleVisibility = onToggleGobbo,
                isRecording = isRunning,
            )
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = RVCColors.surface), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("BACKING TRACK", color = RVCColors.textMute, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = onLoadBacking,
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RVCColors.surface2),
                        ) {
                            Text(if (backingLoaded) "CAMBIA" else "CARICA", fontSize = 9.sp)
                        }
                    }
                    if (backingLoaded) {
                        Slider(
                            value = backingGain,
                            onValueChange = onBackingGain,
                            modifier = Modifier.fillMaxWidth().height(22.dp),
                            colors = SliderDefaults.colors(thumbColor = RVCColors.accentStrong),
                        )
                        Text("Posizione ${"%.0f".format(backingPosition * 60)}s", color = RVCColors.textDim, fontSize = 9.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onExport,
                        modifier = Modifier.fillMaxWidth().height(34.dp),
                        shape = RoundedCornerShape(7.dp),
                        border = BorderStroke(1.dp, RVCColors.success),
                    ) {
                        Text("ESPORTA MIXDOWN", color = RVCColors.success, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (recordings.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = RVCColors.surface), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("TAKE RECORDING", color = RVCColors.textMute, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(recordings, key = { it.id }) { take ->
                                Row(
                                    Modifier
                                        .background(RVCColors.surface2, RoundedCornerShape(7.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.width(150.dp)) {
                                        Text(take.name, color = RVCColors.text, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("%.1fs".format(take.durationMs / 1000f), color = RVCColors.textDim, fontSize = 9.sp)
                                    }
                                    TextButton(onClick = { onExportClip(take) }) { Text("EXPORT", fontSize = 9.sp) }
                                    IconButton(onClick = { onDeleteClip(take) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Elimina", tint = RVCColors.danger, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransportCard(
    isRunning: Boolean,
    inputLevel: InputLevel,
    timelinePositionMs: Long,
    timelinePlaying: Boolean,
    armedLabel: String,
    onStartStop: () -> Unit,
    onPlayTimeline: () -> Unit,
    onStopTimeline: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = RVCColors.surface), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onStartStop,
                    modifier = Modifier.size(if (isRunning) 56.dp else 64.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = RVCColors.danger),
                ) {
                    Icon(
                        if (isRunning) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                        contentDescription = if (isRunning) "Stop" else "Registra",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isRunning) "REGISTRAZIONE…" else "PRONTO",
                        color = if (isRunning) RVCColors.danger else RVCColors.success,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("ARM: $armedLabel", color = RVCColors.textMute, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(4.dp))
                    Text(formatTimecode(timelinePositionMs), color = RVCColors.text, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row {
                        IconButton(onClick = if (timelinePlaying) onStopTimeline else onPlayTimeline, modifier = Modifier.size(36.dp)) {
                            Icon(
                                if (timelinePlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = "Play timeline",
                                tint = RVCColors.accentStrong,
                            )
                        }
                    }
                    Text(if (timelinePlaying) "PLAY" else "STOP", color = RVCColors.textMute, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }
            }
            Spacer(Modifier.height(8.dp))
            InputLevelMeter(level = inputLevel)
        }
    }
}

@Composable
private fun TrackSection(
    tracks: List<MixerTrackState>,
    armedTrackIndex: Int?,
    onAddTrack: () -> Unit,
    onRemoveTrack: (Int) -> Unit,
    onArmTrack: (Int) -> Unit,
    onSelectTrack: (Int) -> Unit,
    onTrackVolume: (Int, Float) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = RVCColors.surface), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TRACCE", style = RvcTypography.labelSmall, color = RVCColors.textMute)
                Spacer(Modifier.weight(1f))
                Text("${tracks.size}/32", color = RVCColors.textDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = onAddTrack,
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RVCColors.accent),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("TRACCIA", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(6.dp))
            if (tracks.isEmpty()) {
                Text("Crea una traccia e arma il registratore.", color = RVCColors.textDim, fontSize = 11.sp)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(tracks, key = { it.trackIndex }) { track ->
                        TrackHeaderCard(
                            track = track,
                            armed = armedTrackIndex == track.trackIndex,
                            onArm = { onArmTrack(track.trackIndex) },
                            onSelect = { onSelectTrack(track.trackIndex) },
                            onRemove = { onRemoveTrack(track.trackIndex) },
                            onVolume = { onTrackVolume(track.trackIndex, it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackHeaderCard(
    track: MixerTrackState,
    armed: Boolean,
    onArm: () -> Unit,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    onVolume: (Float) -> Unit,
) {
    Column(
        Modifier
            .width(154.dp)
            .background(if (armed) RVCColors.dangerSoft else RVCColors.surface2, RoundedCornerShape(8.dp))
            .padding(8.dp)
            .clickable(onClick = onSelect),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(trackColor(track.trackIndex), CircleShape))
            Spacer(Modifier.width(5.dp))
            Text(
                track.clipName.ifBlank { "Traccia ${track.trackIndex + 1}" },
                color = RVCColors.text,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(22.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = RVCColors.textMute, modifier = Modifier.size(13.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onArm,
            modifier = Modifier.fillMaxWidth().height(27.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (armed) RVCColors.danger else RVCColors.surface3,
            ),
            shape = RoundedCornerShape(5.dp),
        ) {
            Text(if (armed) "ARMATA" else "ARMA", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Slider(
            value = track.volume,
            onValueChange = onVolume,
            modifier = Modifier.fillMaxWidth().height(18.dp),
            colors = SliderDefaults.colors(
                thumbColor = RVCColors.accentStrong,
                activeTrackColor = RVCColors.accent,
            ),
        )
    }
}

@Composable
private fun MixerSection(
    tracks: List<MixerTrackState>,
    voiceVolume: Float,
    musicVolume: Float,
    masterVolume: Float,
    momentary: Float,
    shortTerm: Float,
    integrated: Float,
    truePeak: Float,
    onVoiceVolume: (Float) -> Unit,
    onMusicVolume: (Float) -> Unit,
    onMasterVolume: (Float) -> Unit,
    onOpenPluginRack: () -> Unit,
    onOpenAdvancedMixer: () -> Unit,
    onTrackVolume: (Int, Float) -> Unit,
    onTrackPan: (Int, Float) -> Unit,
    onTrackMute: (Int, Boolean) -> Unit,
    onTrackSolo: (Int, Boolean) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = RVCColors.surface), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("MIXER", style = RvcTypography.labelSmall, color = RVCColors.textMute)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onOpenAdvancedMixer) {
                    Text("MIXER AVANZATO", fontSize = 9.sp, color = RVCColors.textMute)
                }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = onOpenPluginRack,
                    modifier = Modifier.height(27.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RVCColors.surface3),
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = null, tint = RVCColors.accent2, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("PLUGIN", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(tracks, key = { it.trackIndex }) { track ->
                    Column(
                        Modifier.width(132.dp).background(RVCColors.surface2, RoundedCornerShape(7.dp)).padding(7.dp),
                    ) {
                        Text("T${track.trackIndex + 1}", color = trackColor(track.trackIndex), fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Slider(value = track.volume, onValueChange = { onTrackVolume(track.trackIndex, it) }, modifier = Modifier.height(18.dp))
                        Slider(value = track.pan, onValueChange = { onTrackPan(track.trackIndex, it) }, valueRange = -1f..1f, modifier = Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { onTrackMute(track.trackIndex, !track.muted) }, modifier = Modifier.height(24.dp)) {
                                Text(if (track.muted) "M ON" else "M", color = if (track.muted) RVCColors.danger else RVCColors.textDim, fontSize = 9.sp)
                            }
                            TextButton(onClick = { onTrackSolo(track.trackIndex, !track.solo) }, modifier = Modifier.height(24.dp)) {
                                Text(if (track.solo) "S ON" else "S", color = if (track.solo) RVCColors.warning else RVCColors.textDim, fontSize = 9.sp)
                            }
                        }
                    }
                }
                item {
                    Column(
                        Modifier.width(150.dp).background(RVCColors.surface3, RoundedCornerShape(7.dp)).padding(7.dp),
                    ) {
                        Text("MASTER", color = RVCColors.accentStrong, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Slider(value = masterVolume, onValueChange = onMasterVolume, modifier = Modifier.height(18.dp))
                        Text("V ${"%.1f".format(voiceVolume)}  M ${"%.1f".format(musicVolume)}", color = RVCColors.textDim, fontSize = 9.sp)
                        Text("LUFS ${"%.1f".format(shortTerm)} / I ${"%.1f".format(integrated)}", color = RVCColors.textDim, fontSize = 8.sp)
                        Text("TP ${"%.1f".format(truePeak)}  M ${"%.1f".format(momentary)}", color = RVCColors.textDim, fontSize = 8.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun GobboCard(
    text: String,
    onTextChange: (String) -> Unit,
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit,
    isRecording: Boolean,
) {
    if (!isVisible) {
        OutlinedButton(
            onClick = onToggleVisibility,
            modifier = Modifier.fillMaxWidth().height(34.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("GOBBO · APRI TELEPROMPTER", color = RVCColors.accentStrong, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    } else {
        TeleprompterOverlay(
            text = text,
            onTextChange = onTextChange,
            fontSize = fontSize,
            onFontSizeChange = onFontSizeChange,
            scrollSpeed = speed,
            onScrollSpeedChange = onSpeedChange,
            isVisible = true,
            onToggleVisibility = onToggleVisibility,
            isRecording = isRecording,
        )
    }
}

private fun trackColor(index: Int): Color {
    val palette = listOf(
        RVCColors.accent,
        RVCColors.accent2,
        RVCColors.success,
        RVCColors.warning,
        RVCColors.info,
        RVCColors.danger,
    )
    return palette[index % palette.size]
}
