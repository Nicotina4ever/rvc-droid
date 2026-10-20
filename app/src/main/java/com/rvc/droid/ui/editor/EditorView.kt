package com.rvc.droid.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.rvc.droid.audio.WavEditor
import com.rvc.droid.audio.WavPlayer
import com.rvc.droid.ui.components.AudioWaveform
import com.rvc.droid.ui.project.RecordingClip
import com.rvc.droid.ui.theme.RVCColors

import com.rvc.droid.ui.components.ExportFormatDialog

@Composable
fun EditorView(
    modifier: Modifier = Modifier,
    selectedClip: RecordingClip?,
    clips: List<RecordingClip> = emptyList(),
    onSelectClip: (RecordingClip) -> Unit = {},
    onSilence: (Long, Long) -> Unit = { _, _ -> },
    onTrim: (Long, Long) -> Unit = { _, _ -> },
    onCut: (Long, Long) -> Unit = { _, _ -> },
    onCopy: (Long, Long) -> Unit = { _, _ -> },
    onPaste: (Long) -> Unit = { _ -> },
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onSaveAs: () -> Unit = {},
    onExportCustom: (WavEditor.ExportOptions) -> Unit = {},
    onPlaybackStart: () -> Unit = {},
    onPlaybackStop: () -> Unit = {},
    nativePlaybackPositionMs: Long? = null,
    nativePlaybackDurationMs: Long? = null,
) {
    var selectionStart by remember { mutableFloatStateOf(0.1f) }
    var selectionEnd by remember { mutableFloatStateOf(0.9f) }
    var waveformKey by remember { mutableIntStateOf(0) }
    var playheadPos by remember { mutableFloatStateOf(0f) }
    var waveformZoom by remember { mutableFloatStateOf(1f) }
    var activeHandle by remember { mutableIntStateOf(-1) }
    var isPlaying by remember { mutableStateOf(false) }
    var audioInfo by remember { mutableStateOf<WavEditor.AudioInfo?>(null) }
    var showClipSelector by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    val amplitudes = remember(selectedClip, waveformKey) {
        if (selectedClip == null) emptyList<Int>()
        else WavEditor.getPeaks(selectedClip.path, 300)
    }

    LaunchedEffect(selectedClip?.path) {
        audioInfo = selectedClip?.path?.let { WavEditor.getAudioInfo(it) }
        selectionStart = 0.1f
        selectionEnd = 0.9f
        playheadPos = 0f
        waveformZoom = 1f
    }

    DisposableEffect(Unit) {
        WavPlayer.onProgressChanged = handler@{ currentMs, totalMs ->
            // Ignore WavPlayer progress when the editor is following the native mixdown.
            if (nativePlaybackPositionMs != null) return@handler
            if (totalMs > 0) playheadPos = currentMs.toFloat() / totalMs
        }
        WavPlayer.onPlaybackComplete = {
            isPlaying = false
            playheadPos = 0f
            onPlaybackStop()
        }
        onDispose {
            if (WavPlayer.isPlaying()) {
                WavPlayer.stop()
                onPlaybackStop()
            }
            WavPlayer.onProgressChanged = null
            WavPlayer.onPlaybackComplete = null
        }
    }

    // Map native mixer position onto the waveform playhead when the caller supplies it.
    LaunchedEffect(nativePlaybackPositionMs, nativePlaybackDurationMs) {
        val pos = nativePlaybackPositionMs ?: return@LaunchedEffect
        val dur = nativePlaybackDurationMs
        if (dur != null && dur > 0) {
            playheadPos = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
        }
    }

    Column(modifier.fillMaxSize().background(RVCColors.bgDeep).verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("WAVEFORM EDITOR", color = RVCColors.accentCyan, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))

            if (selectedClip != null) {
                IconButton(onClick = onSaveAs) {
                    Icon(Icons.Default.Done, contentDescription = "Save As", tint = RVCColors.accentGreen)
                }
                IconButton(onClick = { onUndo(); waveformKey++ }, enabled = WavEditor.canUndo(selectedClip.path)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Undo", tint = if (WavEditor.canUndo(selectedClip.path)) RVCColors.accentCyan else RVCColors.textDim)
                }
                IconButton(onClick = { onRedo(); waveformKey++ }, enabled = WavEditor.canRedo(selectedClip.path)) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = if (WavEditor.canRedo(selectedClip.path)) RVCColors.accentCyan else RVCColors.textDim, modifier = Modifier.size(24.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // === IN-PLACE CLIP SELECTOR CARD ===
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = RVCColors.bgSurface)
        ) {
            Column(Modifier.padding(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("TRACCIA AUDIO IN EDITING", color = RVCColors.textDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Text(
                            selectedClip?.name ?: "Nessuna clip selezionata",
                            color = if (selectedClip != null) RVCColors.accentCyan else RVCColors.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (clips.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showClipSelector = !showClipSelector },
                            modifier = Modifier.height(30.dp),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(if (showClipSelector) "CHIUDI" else "CAMBIA", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                if (showClipSelector || selectedClip == null) {
                    Spacer(Modifier.height(8.dp))
                    if (clips.isEmpty()) {
                        Text("Nessuna traccia registrata. Registra una clip in Studio per iniziare.", color = RVCColors.textDim, fontSize = 11.sp)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            clips.forEach { clip ->
                                val isCur = clip.id == selectedClip?.id
                                Surface(
                                    onClick = {
                                        onSelectClip(clip)
                                        showClipSelector = false
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isCur) RVCColors.accentCyan.copy(alpha = 0.2f) else RVCColors.bgDeep,
                                    border = BorderStroke(1.dp, if (isCur) RVCColors.accentCyan else RVCColors.knobBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(clip.name, color = if (isCur) Color.White else RVCColors.textPrimary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                        Text("%.1fs".format(clip.durationMs / 1000f), color = RVCColors.textDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                        if (isCur) {
                                            Spacer(Modifier.width(6.dp))
                                            Icon(Icons.Default.Check, contentDescription = null, tint = RVCColors.accentCyan, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (selectedClip != null) {
            val dur = selectedClip.durationMs / 1000f

            // Audio info
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = RVCColors.bgSurface)) {
                Row(Modifier.padding(10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(selectedClip.name, color = RVCColors.accentGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    audioInfo?.let { info ->
                        Text("${info.sampleRate}Hz ${info.bitsPerSample}bit ${info.channels}ch %.1fs".format(info.durationMs / 1000f),
                            color = RVCColors.textDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Waveform card
            Card(Modifier.fillMaxWidth().height(200.dp), colors = CardDefaults.cardColors(containerColor = RVCColors.bgSurface)) {
                Column(Modifier.padding(12.dp)) {
                    Box(Modifier.weight(1f).fillMaxWidth().graphicsLayer {
                        scaleX = waveformZoom
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }) {
                        AudioWaveform(
                            modifier = Modifier.fillMaxSize(),
                            amplitudes = amplitudes,
                            progress = if (isPlaying) playheadPos else 0f,
                            onProgressChange = { position ->
                                playheadPos = position.coerceIn(0f, 1f)
                                if (!isPlaying && audioInfo != null) {
                                    WavPlayer.seekTo((playheadPos * audioInfo!!.durationMs).toLong())
                                }
                            },
                            waveformBrush = SolidColor(RVCColors.accentCyan),
                            progressBrush = SolidColor(RVCColors.accentOrange.copy(alpha = 0.6f))
                        )

                        // Selection + playhead overlay
                        Canvas(Modifier.fillMaxSize().pointerInput(selectedClip.id, selectionStart, selectionEnd) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    val position = (start.x / size.width).coerceIn(0f, 1f)
                                    val startDistance = kotlin.math.abs(position - selectionStart)
                                    val endDistance = kotlin.math.abs(position - selectionEnd)
                                    activeHandle = if (minOf(startDistance, endDistance) < 0.06f) {
                                        if (startDistance <= endDistance) 0 else 1
                                    } else -1
                                    if (activeHandle < 0) playheadPos = position
                                },
                                onDrag = { change, _ ->
                                    val position = (change.position.x / size.width).coerceIn(0f, 1f)
                                    if (activeHandle == 0) {
                                        selectionStart = position.coerceIn(0f, selectionEnd - 0.01f)
                                    } else if (activeHandle == 1) {
                                        selectionEnd = position.coerceIn(selectionStart + 0.01f, 1f)
                                    } else {
                                        playheadPos = position
                                    }
                                    change.consume()
                                },
                                onDragEnd = { activeHandle = -1 },
                                onDragCancel = { activeHandle = -1 }
                            )
                        }) {
                            val startX = selectionStart * size.width
                            val endX = selectionEnd * size.width

                            // Darken non-selected areas
                            drawRect(color = Color.Black.copy(alpha = 0.5f), topLeft = Offset(0f, 0f), size = Size(startX, size.height))
                            drawRect(color = Color.Black.copy(alpha = 0.5f), topLeft = Offset(endX, 0f), size = Size(size.width - endX, size.height))

                            // Highlight selection
                            drawRect(color = RVCColors.accentCyan.copy(alpha = 0.1f), topLeft = Offset(startX, 0f), size = Size(endX - startX, size.height))

                            // Selection borders
                            drawLine(color = RVCColors.accentCyan, start = Offset(startX, 0f), end = Offset(startX, size.height), strokeWidth = 2.dp.toPx())
                            drawLine(color = RVCColors.accentCyan, start = Offset(endX, 0f), end = Offset(endX, size.height), strokeWidth = 2.dp.toPx())

                            // Playhead
                            if (isPlaying || playheadPos > 0f) {
                                val phX = playheadPos * size.width
                                drawLine(color = RVCColors.accentOrange, start = Offset(phX, 0f), end = Offset(phX, size.height), strokeWidth = 2.dp.toPx())
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("START", color = RVCColors.textDim, fontSize = 9.sp, modifier = Modifier.width(36.dp))
                        Slider(value = selectionStart, onValueChange = { selectionStart = it.coerceAtMost(selectionEnd - 0.01f) }, modifier = Modifier.weight(1f).height(24.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("END", color = RVCColors.textDim, fontSize = 9.sp, modifier = Modifier.width(36.dp))
                        Slider(value = selectionEnd, onValueChange = { selectionEnd = it.coerceAtLeast(selectionStart + 0.01f) }, modifier = Modifier.weight(1f).height(24.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ZOOM", color = RVCColors.textDim, fontSize = 9.sp, modifier = Modifier.width(36.dp))
                        Slider(
                            value = waveformZoom,
                            onValueChange = { waveformZoom = it },
                            valueRange = 1f..4f,
                            modifier = Modifier.weight(1f).height(24.dp)
                        )
                        Text("%.1fx".format(waveformZoom), color = RVCColors.textDim, fontSize = 9.sp, modifier = Modifier.width(32.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Selection info
            val selStartMs = (selectionStart * dur * 1000).toLong()
            val selEndMs = (selectionEnd * dur * 1000).toLong()
            val selDur = (selectionEnd - selectionStart) * dur
            Text("SELECTION: %.2fs - %.2fs (%.2fs)".format(selectionStart * dur, selectionEnd * dur, selDur),
                color = RVCColors.textPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)

            Spacer(Modifier.height(12.dp))

            // Playback controls
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (isPlaying) {
                            WavPlayer.stop()
                            isPlaying = false
                            playheadPos = 0f
                            onPlaybackStop()
                        } else {
                            selectedClip?.path?.let { path ->
                                onPlaybackStart()
                                WavPlayer.play(path, selStartMs, selEndMs)
                                isPlaying = true
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlaying) RVCColors.accentOrange else RVCColors.accentCyan
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        if (isPlaying) "STOP" else "PLAY",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Edit operations — CUT / COPY / PASTE / SILENCE / TRIM
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onCut(selStartMs, selEndMs); waveformKey++ },
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RVCColors.bgSurface),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, RVCColors.accentOrange.copy(alpha = 0.5f))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("CUT", color = RVCColors.accentOrange, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text("Remove + copy", color = RVCColors.textDim, fontSize = 8.sp)
                    }
                }

                Button(
                    onClick = { onCopy(selStartMs, selEndMs) },
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RVCColors.bgSurface),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, RVCColors.accentGreen.copy(alpha = 0.5f))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("COPY", color = RVCColors.accentGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text("To clipboard", color = RVCColors.textDim, fontSize = 8.sp)
                    }
                }

                Button(
                    onClick = { onPaste(selStartMs); waveformKey++ },
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RVCColors.bgSurface),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, RVCColors.accentCyan.copy(alpha = 0.5f))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("PASTE", color = RVCColors.accentCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text("Insert at cursor", color = RVCColors.textDim, fontSize = 8.sp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSilence(selStartMs, selEndMs); waveformKey++ },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RVCColors.bgSurface),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, RVCColors.textDim.copy(alpha = 0.5f))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SILENCE", color = RVCColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text("Mute selection", color = RVCColors.textDim, fontSize = 8.sp)
                    }
                }

                Button(
                    onClick = { onTrim(selStartMs, selEndMs); waveformKey++ },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RVCColors.bgSurface),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, RVCColors.textDim.copy(alpha = 0.5f))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TRIM", color = RVCColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text("Crop to selection", color = RVCColors.textDim, fontSize = 8.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Dedicated Multi-Format Export Button
            Button(
                onClick = { showExportDialog = true },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RVCColors.accentGreen),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("ESPORTA AUDIO (WAV 24b/16b • MP3 • NORMALIZZATO)", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }

        if (showExportDialog && selectedClip != null) {
            val hasSelection = selectionEnd > selectionStart && (selectionEnd - selectionStart) < 0.999f
            val selStartMs = (selectionStart * selectedClip.durationMs).toLong()
            val selEndMs = (selectionEnd * selectedClip.durationMs).toLong()
            ExportFormatDialog(
                initialName = selectedClip.name.removeSuffix(".wav"),
                totalDurationMs = selectedClip.durationMs,
                selectionStartMs = selStartMs,
                selectionEndMs = selEndMs,
                hasSelection = hasSelection,
                onDismiss = { showExportDialog = false },
                onConfirmExport = { options ->
                    showExportDialog = false
                    onExportCustom(options)
                }
            )
        }
    }
}
