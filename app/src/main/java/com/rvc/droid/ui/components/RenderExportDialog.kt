package com.rvc.droid.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import com.rvc.droid.ui.theme.RVCColors

enum class ExportFormat(val label: String, val ext: String, val bitsPerSample: Int) {
    WAV16("WAV 16bit", ".wav", 16),
    WAV24("WAV 24bit", ".wav", 24),
    WAV32F("WAV 32bit Float", ".wav", 32)
}

data class ExportState(
    val isExporting: Boolean = false,
    val progress: Float = 0f,
    val recordingSec: Float = 0f,
    val format: ExportFormat = ExportFormat.WAV24,
    val outputName: String = "",
    val includeRVC: Boolean = true,
    val includeMastering: Boolean = true,
    val completed: Boolean = false,
    val outputPath: String = "",
    val error: String = "",
    val isMixdown: Boolean = false
)

@Composable
fun RenderExportDialog(
    visible: Boolean,
    state: ExportState,
    onDismiss: () -> Unit,
    onFormatChange: (ExportFormat) -> Unit,
    onNameChange: (String) -> Unit,
    onToggleRVC: () -> Unit,
    onToggleMastering: () -> Unit,
    onStartRender: () -> Unit,
    onStartOfflineRender: () -> Unit,
    onOpenFile: (String) -> Unit,
    onSaveToFolder: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!visible) return
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = RVCColors.bgSurface)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("ESPORTA AUDIO", color = RVCColors.accentOrange, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Spacer(Modifier.height(12.dp))

                // File name
                Text("Nome File", color = RVCColors.textDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = state.outputName,
                    onValueChange = onNameChange,
                    enabled = !state.isExporting,
                    placeholder = { Text("mix_esportato", fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = RVCColors.textPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RVCColors.accentOrange, unfocusedBorderColor = RVCColors.knobBorder,
                        cursorColor = RVCColors.accentOrange
                    )
                )

                Spacer(Modifier.height(8.dp))

                // Format selection
                Text("Formato WAV", color = RVCColors.textDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExportFormat.entries.forEach { fmt ->
                        FilterChip(
                            selected = state.format == fmt,
                            onClick = { onFormatChange(fmt) },
                            label = { Text(fmt.label, fontSize = 9.sp, fontFamily = FontFamily.Monospace) },
                            enabled = !state.isExporting,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RVCColors.accentOrange.copy(alpha = 0.2f),
                                selectedLabelColor = RVCColors.accentOrange
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = RVCColors.knobBorder, selectedBorderColor = RVCColors.accentOrange,
                                enabled = !state.isExporting, selected = state.format == fmt
                            ),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Options
                Text("Opzioni Elaborazione", color = RVCColors.textDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.includeRVC, onCheckedChange = { onToggleRVC() },
                        enabled = !state.isExporting,
                        colors = CheckboxDefaults.colors(checkedColor = RVCColors.accentCyan, uncheckedColor = RVCColors.textDim)
                    )
                    Text("Includi RVC", color = RVCColors.textSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.width(16.dp))
                    Checkbox(
                        checked = state.includeMastering, onCheckedChange = { onToggleMastering() },
                        enabled = !state.isExporting,
                        colors = CheckboxDefaults.colors(checkedColor = RVCColors.accentOrange, uncheckedColor = RVCColors.textDim)
                    )
                    Text("Catena Mastering", color = RVCColors.textSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }

                Spacer(Modifier.height(8.dp))

                if (!state.isExporting && !state.completed) {
                    Text("• RENDER OFFLINE: Renderizza velocemente tutto il mix multitraccia.", color = RVCColors.textDim, fontSize = 9.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("• REC LIVE: Registra l'audio dal vivo in tempo reale.", color = RVCColors.textDim, fontSize = 9.sp)
                    Spacer(Modifier.height(10.dp))
                }

                // Elapsed time (real recording duration)
                if (state.isExporting) {
                    val minutes = (state.recordingSec / 60).toInt()
                    val secs = (state.recordingSec % 60).toInt()
                    Text("Registrazione in corso: %02d:%02d".format(minutes, secs),
                        color = RVCColors.accentOrange, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (state.recordingSec % 60f) / 60f },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = RVCColors.accentOrange,
                        trackColor = RVCColors.sliderTrack
                    )
                    Spacer(Modifier.height(4.dp))
                }
                if (state.completed) {
                    val minutes = (state.recordingSec / 60).toInt()
                    val secs = (state.recordingSec % 60).toInt()
                    val fileSize = state.outputPath.let { path ->
                        if (path.isNotEmpty()) {
                            val file = java.io.File(path)
                            if (file.exists()) "%.1f KB".format(file.length() / 1024.0) else ""
                        } else ""
                    }
                    Text("✓ Esportato: %02d:%02d ($fileSize)".format(minutes, secs),
                        color = RVCColors.accentGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(state.outputPath, color = RVCColors.textDim, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(8.dp))
                }

                // Error
                if (state.error.isNotEmpty()) {
                    Text(state.error, color = RVCColors.accentRed, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(8.dp))
                }

                // Buttons
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!state.isExporting && !state.completed) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onStartOfflineRender, modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RVCColors.accentGreen)
                            ) { Text("RENDER OFFLINE", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = RVCColors.bgDeep, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }

                            Button(onClick = onStartRender, modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RVCColors.accentOrange)
                            ) { Text("REC LIVE MIX", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = RVCColors.bgDeep, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
                        }
                    }

                    if (state.isExporting) {
                        Button(onClick = onStartRender, modifier = Modifier.fillMaxWidth().height(40.dp), shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RVCColors.accentRed)
                        ) { Text("STOP REGISTRAZIONE", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
                    }

                    if (state.completed) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onSaveToFolder, modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RVCColors.accentCyan)
                            ) { Text("SALVA IN...", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = RVCColors.bgDeep, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }

                            Button(onClick = { onOpenFile(state.outputPath) }, modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RVCColors.accentGreen)
                            ) { Text("APRI FILE", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = RVCColors.bgDeep, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
                        }
                    }

                    OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(36.dp), shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, RVCColors.knobBorder), colors = ButtonDefaults.outlinedButtonColors(contentColor = RVCColors.textSecondary)
                    ) { Text(if(state.isExporting) "ANNULLA" else "CHIUDI", fontSize = 10.sp, fontFamily = FontFamily.Monospace) }
                }
            }
        }
    }
}
