package com.rvc.droid.ui.studio

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.SolidColor
import com.rvc.droid.ui.components.AudioWaveform
import com.rvc.droid.ui.components.InputLevel
import com.rvc.droid.ui.components.InputLevelMeter
import com.rvc.droid.ui.components.TeleprompterOverlay
import com.rvc.droid.ui.project.RecordingClip
import com.rvc.droid.ui.theme.RVCColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StudioView(
    modifier: Modifier = Modifier,
    isRunning: Boolean = false,
    inputLevel: InputLevel = InputLevel(),
    recordings: List<RecordingClip> = emptyList(),
    backingLoaded: Boolean = false,
    backingGain: Float = 0.5f,
    onStartStop: () -> Unit = {},
    onLoadBacking: () -> Unit = {},
    onBackingGain: (Float) -> Unit = {},
    onBackingSeek: (Float) -> Unit = {},
    backingPosition: Float = 0f,
    onDeleteClip: (RecordingClip) -> Unit = {},
    onSelectClip: (RecordingClip) -> Unit = {},
    onExportClip: (RecordingClip) -> Unit = {},
    onExportRegions: (RecordingClip) -> Unit = {},
    onExport: () -> Unit = {},
    teleprompterText: String = "",
    onTeleprompterTextChange: (String) -> Unit = {},
    teleprompterFontSize: Int = 24,
    onTeleprompterFontSizeChange: (Int) -> Unit = {},
    teleprompterScrollSpeed: Float = 0.3f,
    onTeleprompterScrollSpeedChange: (Float) -> Unit = {},
    showTeleprompter: Boolean = false,
    onToggleTeleprompter: () -> Unit = {},
    waveformPeaks: FloatArray = FloatArray(4096),
    waveformProgress: Float = 0f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pulse"
    )

    var recElapsedSec by remember { mutableIntStateOf(0) }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            recElapsedSec = 0
            while (true) {
                delay(1000)
                recElapsedSec++
            }
        }
    }

    LazyColumn(
        modifier.fillMaxSize().background(RVCColors.bgDeep).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("STUDIO", color = RVCColors.accentCyan, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onToggleTeleprompter, modifier = Modifier.height(28.dp), shape = RoundedCornerShape(6.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(
                        imageVector = if (showTeleprompter) Icons.Default.Close else Icons.Default.EditNote,
                        contentDescription = null,
                        tint = if (showTeleprompter) RVCColors.accentCyan else RVCColors.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (showTeleprompter) "GOBBO ON" else "GOBBO", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.weight(1f))
                Text("${recordings.size} files", color = RVCColors.textSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
            }
        }

        // === RECORDING CONTROLS ===
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = RVCColors.bgSurface)) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isRunning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Canvas(Modifier.size(14.dp)) { drawCircle(RVCColors.accentRed.copy(alpha = pulseAlpha), size.width / 2f) }
                            Spacer(Modifier.width(8.dp))
                            Text("REC ${recElapsedSec / 60}:%02d".format(recElapsedSec % 60),
                                color = RVCColors.accentRed.copy(alpha = pulseAlpha), fontSize = 16.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("READY", color = RVCColors.accentGreen, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { if (isRunning) onStartStop() },
                            enabled = isRunning,
                            modifier = Modifier.size(64.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RVCColors.accentRed)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White, modifier = Modifier.size(28.dp))
                        }

                        Button(
                            onClick = { if (!isRunning) onStartStop() },
                            enabled = !isRunning,
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = RVCColors.accentRed)
                        ) {
                            Icon(Icons.Default.FiberManualRecord, contentDescription = "Record", tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                    }
                }
            }
        }

        // === INPUT LEVEL ===
        item { InputLevelMeter(level = inputLevel) }

        // === LIVE WAVEFORM ===
        if (isRunning) {
            item {
                Card(
                    Modifier.fillMaxWidth().height(100.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = RVCColors.bgSurface)
                ) {
                    AudioWaveform(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        amplitudes = waveformPeaks.map { (it * 100).toInt() },
                        progress = waveformProgress,
                        onProgressChange = {},
                        waveformBrush = SolidColor(RVCColors.accentCyan),
                        progressBrush = SolidColor(RVCColors.accentRed.copy(alpha = 0.5f))
                    )
                }
            }
        }

        // === TELEPROMPTER ===
        item {
            TeleprompterOverlay(
                text = teleprompterText,
                onTextChange = onTeleprompterTextChange,
                fontSize = teleprompterFontSize,
                onFontSizeChange = onTeleprompterFontSizeChange,
                scrollSpeed = teleprompterScrollSpeed,
                onScrollSpeedChange = onTeleprompterScrollSpeedChange,
                isVisible = showTeleprompter,
                onToggleVisibility = onToggleTeleprompter,
                isRecording = isRunning
            )
        }

        // === BACKING TRACK ===
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = RVCColors.bgSurface)) {
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("BACKING TRACK", color = RVCColors.textDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.weight(1f))
                        if (backingLoaded) Text("LOADED", color = RVCColors.accentGreen, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = onLoadBacking, modifier = Modifier.fillMaxWidth().height(30.dp), shape = RoundedCornerShape(6.dp)) {
                        Text(if (backingLoaded) "Change Backing" else "+ Load Backing", fontSize = 10.sp, maxLines = 1)
                    }
                    if (backingLoaded) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(24.dp)) {
                            Text("Gain", color = RVCColors.textSecondary, fontSize = 9.sp, modifier = Modifier.width(32.dp))
                            Slider(value = backingGain, onValueChange = onBackingGain, modifier = Modifier.weight(1f).height(16.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(24.dp)) {
                            Text("Seek", color = RVCColors.textSecondary, fontSize = 9.sp, modifier = Modifier.width(32.dp))
                            Slider(value = backingPosition, onValueChange = onBackingSeek, modifier = Modifier.weight(1f).height(16.dp))
                            Text("%.1fs".format(backingPosition * 60), color = RVCColors.accentCyan, fontSize = 9.sp, modifier = Modifier.width(32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }
                    }
                }
            }
        }

        // === EXPORT BUTTON ===
        item {
            OutlinedButton(
                onClick = onExport,
                modifier = Modifier.fillMaxWidth().height(36.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, RVCColors.accentGreen),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RVCColors.accentGreen)
            ) {
                Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("EXPORT", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }

        // === RECORDINGS LIST ===
        item {
            Text("RECORDINGS", color = RVCColors.textSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }

        if (recordings.isEmpty()) {
            item {
                Text("No recordings yet. Press REC to start.", color = RVCColors.textDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }

        items(recordings) { clip ->
            RecordingItem(
                clip,
                onDelete = { onDeleteClip(clip) },
                onExport = { onExportClip(clip) },
                onExportRegions = { onExportRegions(clip) },
                onClick = { onSelectClip(clip) }
            )
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
fun RecordingItem(
    clip: RecordingClip,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onExportRegions: () -> Unit,
    onClick: () -> Unit
) {
    val dateStr = remember(clip.createdAt) {
        SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date(clip.createdAt))
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = RVCColors.bgSurface)
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = RVCColors.accentCyan, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(clip.name, color = RVCColors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${clip.durationMs / 1000}s | $dateStr", color = RVCColors.textDim, fontSize = 9.sp, maxLines = 1)
            }
            IconButton(onClick = onExport, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Done, contentDescription = "Export", tint = RVCColors.accentGreen, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onExportRegions, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Esporta regioni", tint = RVCColors.accentCyan, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RVCColors.accentRed, modifier = Modifier.size(16.dp))
            }
        }
    }
}
