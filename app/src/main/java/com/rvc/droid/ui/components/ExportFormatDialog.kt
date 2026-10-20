package com.rvc.droid.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvc.droid.audio.WavEditor
import com.rvc.droid.ui.theme.RVCColors

@Composable
fun ExportFormatDialog(
    initialName: String,
    totalDurationMs: Long,
    selectionStartMs: Long,
    selectionEndMs: Long,
    hasSelection: Boolean,
    onDismiss: () -> Unit,
    onConfirmExport: (WavEditor.ExportOptions) -> Unit
) {
    var outputName by remember { mutableStateOf(initialName) }
    var selectedFormatIndex by remember { mutableIntStateOf(0) } // 0: WAV 48k 24b, 1: WAV 44.1k 16b, 2: MP3 44.1k
    var exportSelectionOnly by remember { mutableStateOf(hasSelection) }
    var normalizeEnabled by remember { mutableStateOf(true) }
    var targetPeakDb by remember { mutableFloatStateOf(-1.0f) }
    var mp3Channels by remember { mutableIntStateOf(2) } // 1: Mono, 2: Stereo
    var mp3Bitrate by remember { mutableIntStateOf(320) } // 128, 192, 256, 320

    val formats = listOf(
        "WAV 48kHz 24-bit" to "Studio / Broadcast Master",
        "WAV 44.1kHz 16-bit" to "Standard CD / Web",
        "MP3 44.1kHz" to "Salva WAV e usa tools/export_mp3.py sul PC"
    )

    val peakPresets = listOf(-0.1f, -0.5f, -1.0f, -2.0f, -3.0f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, contentDescription = null, tint = RVCColors.accentCyan, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("ESPORTA TRACCIA AUDIO", fontSize = 15.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Nome File
                OutlinedTextField(
                    value = outputName,
                    onValueChange = { outputName = it },
                    label = { Text("Nome File di Output", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RVCColors.accentCyan,
                        unfocusedBorderColor = RVCColors.knobBorder
                    )
                )

                // Ambito di Esportazione (Selezione vs Intero)
                if (hasSelection) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RVCColors.bgSurface),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Text("PORZIONE DA ESPORTARE", fontSize = 10.sp, color = RVCColors.textDim, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = exportSelectionOnly,
                                    onClick = { exportSelectionOnly = true },
                                    label = { Text("Selezione (%.1fs)".format((selectionEndMs - selectionStartMs) / 1000f), fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = RVCColors.accentCyan.copy(alpha = 0.3f),
                                        selectedLabelColor = RVCColors.accentCyan
                                    )
                                )
                                FilterChip(
                                    selected = !exportSelectionOnly,
                                    onClick = { exportSelectionOnly = false },
                                    label = { Text("Intero (%.1fs)".format(totalDurationMs / 1000f), fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = RVCColors.accentCyan.copy(alpha = 0.3f),
                                        selectedLabelColor = RVCColors.accentCyan
                                    )
                                )
                            }
                        }
                    }
                }

                // Formato Audio
                Card(
                    colors = CardDefaults.cardColors(containerColor = RVCColors.bgSurface),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text("FORMATO DI USCITA", fontSize = 10.sp, color = RVCColors.textDim, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        formats.forEachIndexed { index, (label, desc) ->
                            val isSelected = selectedFormatIndex == index
                            val isAvailable = index < 2
                            Surface(
                                onClick = { if (isAvailable) selectedFormatIndex = index },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected && isAvailable) RVCColors.accentCyan.copy(alpha = 0.2f) else Color.Transparent,
                                border = BorderStroke(1.dp, if (isSelected && isAvailable) RVCColors.accentCyan else RVCColors.knobBorder),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            ) {
                                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = isSelected && isAvailable, onClick = { if (isAvailable) selectedFormatIndex = index }, enabled = isAvailable)
                                    Spacer(Modifier.width(4.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSelected && isAvailable) Color.White else RVCColors.textDim)
                                        Text(desc, fontSize = 9.sp, color = if (isAvailable) RVCColors.textDim else RVCColors.accentOrange)
                                    }
                                }
                            }
                        }

                        // Opzioni specifiche MP3
                        if (selectedFormatIndex == 2) {
                            Spacer(Modifier.height(8.dp))
                            Text("CANALI MP3", fontSize = 9.sp, color = RVCColors.textDim, fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = mp3Channels == 1,
                                    onClick = { mp3Channels = 1 },
                                    label = { Text("Mono", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = mp3Channels == 2,
                                    onClick = { mp3Channels = 2 },
                                    label = { Text("Stereo", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("BITRATE MP3", fontSize = 9.sp, color = RVCColors.textDim, fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(128, 192, 256, 320).forEach { kbps ->
                                    FilterChip(
                                        selected = mp3Bitrate == kbps,
                                        onClick = { mp3Bitrate = kbps },
                                        label = { Text("${kbps}k", fontSize = 9.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Normalizzazione con Selezione Picco
                Card(
                    colors = CardDefaults.cardColors(containerColor = RVCColors.bgSurface),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("NORMALIZZAZIONE PICCO", fontSize = 10.sp, color = RVCColors.textDim, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Switch(checked = normalizeEnabled, onCheckedChange = { normalizeEnabled = it })
                        }
                        if (normalizeEnabled) {
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Picco Target:", fontSize = 11.sp, color = RVCColors.textPrimary)
                                Spacer(Modifier.weight(1f))
                                Text("%.1f dBFS".format(targetPeakDb), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RVCColors.accentGreen, fontFamily = FontFamily.Monospace)
                            }
                            Slider(
                                value = targetPeakDb,
                                onValueChange = { targetPeakDb = (it * 10).toInt() / 10f },
                                valueRange = -6.0f..0.0f,
                                steps = 11,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = RVCColors.accentGreen,
                                    activeTrackColor = RVCColors.accentGreen
                                )
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                peakPresets.forEach { db ->
                                    Text(
                                        "%.1f".format(db),
                                        fontSize = 9.sp,
                                        color = if (targetPeakDb == db) RVCColors.accentGreen else RVCColors.textDim,
                                        fontWeight = if (targetPeakDb == db) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.clickable { targetPeakDb = db }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sMs = if (exportSelectionOnly) selectionStartMs else 0L
                    val eMs = if (exportSelectionOnly) selectionEndMs else -1L
                    val (sr, bits, fmt) = when (selectedFormatIndex) {
                        0 -> Triple(48000, 24, "WAV")
                        1 -> Triple(44100, 16, "WAV")
                        else -> Triple(44100, 16, "MP3")
                    }
                    val options = WavEditor.ExportOptions(
                        outputName = outputName.ifBlank { "export_${System.currentTimeMillis()}" },
                        startMs = sMs,
                        endMs = eMs,
                        sampleRate = sr,
                        bitDepth = bits,
                        format = fmt,
                        mp3Channels = mp3Channels,
                        mp3BitrateKbps = mp3Bitrate,
                        normalize = normalizeEnabled,
                        peakDb = targetPeakDb
                    )
                    onConfirmExport(options)
                },
                colors = ButtonDefaults.buttonColors(containerColor = RVCColors.accentGreen),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("ESPORTA ORA", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla", color = RVCColors.textDim)
            }
        }
    )
}
