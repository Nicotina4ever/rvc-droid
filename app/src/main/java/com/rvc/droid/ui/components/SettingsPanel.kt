package com.rvc.droid.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvc.droid.ui.theme.RVCColors

data class SettingsState(
    val sampleRate: Int = 44100,
    val bitDepth: Int = 16,
    val monitoringEnabled: Boolean = false,
    val monitoringGain: Float = 0.5f,
    val feedbackProtection: Boolean = true,
    val headphonesConnected: Boolean = false,
    val inputDeviceName: String = "Default MIC",
    val outputDeviceName: String = "Default Speaker",
    val synthModelLoaded: String = "",
    val vecModelLoaded: String = "",
    val synthModelSize: String = "",
    val vecModelSize: String = "",
    val bufferSize: Int = 512
)

@Composable
fun SettingsPanel(
    settings: SettingsState,
    onMonitoringToggle: (Boolean) -> Unit = {},
    onMonitoringGain: (Float) -> Unit = {},
    onFeedbackProtection: (Boolean) -> Unit = {},
    onSampleRate: (Int) -> Unit = {},
    onBitDepth: (Int) -> Unit = {},
    onBufferSize: (Int) -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showSettings by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // Toggle button
        OutlinedButton(
            onClick = { showSettings = !showSettings },
            modifier = Modifier.fillMaxWidth().height(32.dp),
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, if(showSettings) RVCColors.accentPurple else RVCColors.knobBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = if(showSettings) RVCColors.accentPurple else RVCColors.textSecondary)
        ) {
            Text("SETTINGS", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }

        if (showSettings) {
            Spacer(Modifier.height(6.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = RVCColors.bgSurface)
            ) {
                Column(Modifier.padding(10.dp)) {
                    // AUDIO DEVICES
                    Text("AUDIO I/O", color = RVCColors.accentPurple, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    SettingsRow("Input") { Text(settings.inputDeviceName, color = RVCColors.textPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace) }
                    SettingsRow("Output") { Text(settings.outputDeviceName, color = RVCColors.textPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace) }
                    SettingsRow("Sample Rate") {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(44100, 48000, 96000).forEach { sr ->
                                FilterChip(selected = settings.sampleRate == sr, onClick = { onSampleRate(sr) },
                                    label = { Text("${sr/1000}kHz", fontSize = 8.sp, fontFamily = FontFamily.Monospace) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = RVCColors.accentPurple.copy(alpha=0.2f), selectedLabelColor = RVCColors.accentPurple),
                                    border = FilterChipDefaults.filterChipBorder(borderColor = RVCColors.knobBorder, selectedBorderColor = RVCColors.accentPurple, enabled = true, selected = settings.sampleRate == sr),
                                    modifier = Modifier.height(24.dp))
                            }
                        }
                    }
                    SettingsRow("Bit Depth") {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(16, 24).forEach { bd ->
                                FilterChip(selected = settings.bitDepth == bd, onClick = { onBitDepth(bd) },
                                    label = { Text("${bd}bit", fontSize = 8.sp, fontFamily = FontFamily.Monospace) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = RVCColors.accentPurple.copy(alpha=0.2f), selectedLabelColor = RVCColors.accentPurple),
                                    border = FilterChipDefaults.filterChipBorder(borderColor = RVCColors.knobBorder, selectedBorderColor = RVCColors.accentPurple, enabled = true, selected = settings.bitDepth == bd),
                                    modifier = Modifier.height(24.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Divider(color = RVCColors.knobBorder.copy(alpha=0.3f), thickness = 0.5.dp)
                    Spacer(Modifier.height(6.dp))

                    // MONITORING
                    Text("MONITORING", color = RVCColors.accentGreen, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Live monitoring", color = RVCColors.textSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.weight(1f))
                        Switch(checked = settings.monitoringEnabled, onCheckedChange = onMonitoringToggle,
                            colors = SwitchDefaults.colors(checkedTrackColor = RVCColors.accentGreen, uncheckedTrackColor = RVCColors.knobBg))
                    }
                    if (settings.monitoringEnabled) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Gain", color = RVCColors.textSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            Spacer(Modifier.width(8.dp))
                            Slider(value = settings.monitoringGain, onValueChange = onMonitoringGain,
                                valueRange = 0f..1f, modifier = Modifier.weight(1f).height(16.dp),
                                colors = SliderDefaults.colors(thumbColor = RVCColors.accentGreen, activeTrackColor = RVCColors.accentGreen, inactiveTrackColor = RVCColors.sliderTrack))
                            Text("%.0f%%".format(settings.monitoringGain*100), color = RVCColors.accentGreen, fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(32.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = settings.feedbackProtection, onCheckedChange = onFeedbackProtection,
                                colors = CheckboxDefaults.colors(checkedColor = RVCColors.accentGreen, uncheckedColor = RVCColors.textDim))
                            Text("Feedback protection", color = RVCColors.textSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                        if (!settings.headphonesConnected && settings.feedbackProtection) {
                            Text("Cuffie non rilevate! Il monitoraggio sara disattivato.", color = RVCColors.accentYellow, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                        }
                        if (settings.headphonesConnected) {
                            Text("Cuffie collegate", color = RVCColors.accentGreen, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Divider(color = RVCColors.knobBorder.copy(alpha=0.3f), thickness = 0.5.dp)
                    Spacer(Modifier.height(6.dp))

                    // MODELS INFO
                    Text("MODELS", color = RVCColors.accentCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    if (settings.synthModelLoaded.isNotEmpty()) {
                        SettingsRow("Synth") { Text(settings.synthModelLoaded, color = RVCColors.textPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace) }
                        if (settings.synthModelSize.isNotEmpty()) SettingsRow("Size") { Text(settings.synthModelSize, color = RVCColors.textDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace) }
                    } else {
                        Text("Nessun modello Synth caricato", color = RVCColors.textDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                    if (settings.vecModelLoaded.isNotEmpty()) {
                        SettingsRow("ContentVec") { Text(settings.vecModelLoaded, color = RVCColors.textPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace) }
                    } else {
                        Text("Nessun modello ContentVec caricato", color = RVCColors.textDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }

                    Spacer(Modifier.height(6.dp))
                    Divider(color = RVCColors.knobBorder.copy(alpha=0.3f), thickness = 0.5.dp)
                    Spacer(Modifier.height(6.dp))

                    // BUFFER
                    Text("BUFFER", color = RVCColors.accentOrange, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    SettingsRow("Size") {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(256, 512, 1024, 2048).forEach { bs ->
                                FilterChip(selected = settings.bufferSize == bs, onClick = { onBufferSize(bs) },
                                    label = { Text("$bs", fontSize = 8.sp, fontFamily = FontFamily.Monospace) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = RVCColors.accentOrange.copy(alpha=0.2f), selectedLabelColor = RVCColors.accentOrange),
                                    border = FilterChipDefaults.filterChipBorder(borderColor = RVCColors.knobBorder, selectedBorderColor = RVCColors.accentOrange, enabled = true, selected = settings.bufferSize == bs),
                                    modifier = Modifier.height(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(label: String, content: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = RVCColors.textDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(72.dp))
        content()
    }
}
