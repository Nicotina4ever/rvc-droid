package com.rvc.droid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvc.droid.audio.RegionSpec
import com.rvc.droid.audio.WavEditor
import com.rvc.droid.ui.theme.RVCColors

/**
 * Dialog avanzato per esportare o convertire un WAV in più regioni/spot,
 * con supporto alla numerazione sequenziale (01, 02, ...), modifica nomi personalizzati,
 * esportazione in file separati e invio diretto a Dropbox per conversione remota RVC.
 */
@Composable
fun RegionExportDialog(
    clipName: String,
    totalDurationMs: Long,
    onDismiss: () -> Unit,
    onConfirm: (regions: List<RegionSpec>, baseOptions: WavEditor.ExportOptions) -> Unit,
    onConfirmRemoteRvc: ((regions: List<RegionSpec>, baseOptions: WavEditor.ExportOptions) -> Unit)? = null
) {
    data class RegionDraft(
        var title: String = "",
        var startMs: Long = 0L,
        var endMs: Long = 0L
    ) {
        fun isValid(): Boolean = title.isNotBlank() && endMs > startMs && endMs <= totalDurationMs
    }

    var regions by remember {
        mutableStateOf(
            listOf(
                RegionDraft(title = "01 SPOT", startMs = 0L, endMs = (totalDurationMs.coerceAtMost(30000L)))
            )
        )
    }
    var prefix by remember { mutableStateOf("") }
    var autoNumberingEnabled by remember { mutableStateOf(true) }
    var selectedFormatIndex by remember { mutableIntStateOf(0) }
    var normalizeEnabled by remember { mutableStateOf(true) }
    var targetPeakDb by remember { mutableFloatStateOf(-1.0f) }
    var mp3Bitrate by remember { mutableIntStateOf(192) }
    var mp3Channels by remember { mutableIntStateOf(2) }

    val formats = listOf(
        "WAV 48kHz 24-bit",
        "WAV 44.1kHz 16-bit",
        "MP3 (tools)"
    )

    fun resequenceTitles() {
        val updated = regions.mapIndexed { index, draft ->
            val numStr = "%02d".format(index + 1)
            // Rimuovi eventuale vecchio numero iniziale tipo "01 ", "01_", "1. "
            val cleanTitle = draft.title.replace(Regex("""^\d+[\s._-]+"""), "").trim()
            val finalName = if (cleanTitle.isNotBlank()) "$numStr $cleanTitle" else "$numStr SPOT"
            draft.copy(title = finalName)
        }
        regions = updated
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "REGIONI MULTIPLE / SPOT SEQUENZIALI",
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = RVCColors.accentCyan
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Traccia: $clipName (${totalDurationMs / 1000.0} s). Definisci gli spot con ordine sequenziale.",
                    color = RVCColors.textSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedTextField(
                        value = prefix,
                        onValueChange = { prefix = it.replace(Regex("[^A-Za-z0-9._ -]"), "_") },
                        label = { Text("Prefisso comune (opzionale)", fontSize = 9.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    Button(
                        onClick = { resequenceTitles() },
                        colors = ButtonDefaults.buttonColors(containerColor = RVCColors.bgSurface),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Default.FormatListNumbered, contentDescription = null, tint = RVCColors.accentCyan, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("01..N", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = RVCColors.accentCyan)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Formato:", color = RVCColors.textDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(65.dp))
                    formats.forEachIndexed { idx, f ->
                        FilterChip(
                            selected = selectedFormatIndex == idx,
                            onClick = { selectedFormatIndex = idx },
                            label = { Text(f.substringBefore(' '), fontSize = 8.sp) },
                            modifier = Modifier.height(28.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Normalizza:", color = RVCColors.textDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(75.dp))
                    Switch(checked = normalizeEnabled, onCheckedChange = { normalizeEnabled = it })
                    if (normalizeEnabled) {
                        Spacer(Modifier.width(6.dp))
                        Text("Peak:", color = RVCColors.textDim, fontSize = 9.sp)
                        listOf(-0.1f, -0.5f, -1.0f, -2.0f).forEach { p ->
                            FilterChip(
                                selected = kotlin.math.abs(targetPeakDb - p) < 0.05f,
                                onClick = { targetPeakDb = p },
                                label = { Text("${p}dB", fontSize = 8.sp) },
                                modifier = Modifier.height(26.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                        }
                    }
                }

                HorizontalDivider(color = RVCColors.knobBorder)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "ELENCO REGIONI SPOT (${regions.size}):",
                        color = RVCColors.accentOrange,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                regions.forEachIndexed { idx, r ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(RVCColors.bgDeep, RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = r.title,
                                onValueChange = { v ->
                                    regions = regions.toMutableList().also {
                                        it[idx] = r.copy(title = v.replace(Regex("[^A-Za-z0-9._ -]"), "_"))
                                    }
                                },
                                label = { Text("Nome Spot (es. 01 SPOT)", fontSize = 8.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f).height(52.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            IconButton(onClick = {
                                if (regions.size > 1) regions = regions.toMutableList().also { it.removeAt(idx) }
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Rimuovi", tint = RVCColors.accentRed)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = if (r.startMs == 0L) "0" else "%.2f".format(r.startMs / 1000.0),
                                onValueChange = { v ->
                                    val ms = (v.replace(',', '.').toDoubleOrNull() ?: 0.0).times(1000).toLong().coerceIn(0L, totalDurationMs)
                                    regions = regions.toMutableList().also { it[idx] = r.copy(startMs = ms) }
                                },
                                label = { Text("Inizio (s)", fontSize = 8.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f).height(52.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            OutlinedTextField(
                                value = "%.2f".format(r.endMs / 1000.0),
                                onValueChange = { v ->
                                    val ms = (v.replace(',', '.').toDoubleOrNull() ?: 0.0).times(1000).toLong().coerceIn(0L, totalDurationMs)
                                    regions = regions.toMutableList().also { it[idx] = r.copy(endMs = ms) }
                                },
                                label = { Text("Fine (s)", fontSize = 8.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f).height(52.dp)
                            )
                        }
                        Row(modifier = Modifier.padding(top = 2.dp)) {
                            TextButton(onClick = {
                                regions = regions.toMutableList().also { it[idx] = r.copy(startMs = (r.startMs - 500L).coerceAtLeast(0L)) }
                            }, modifier = Modifier.height(24.dp)) { Text("-0.5s In", fontSize = 8.sp) }
                            TextButton(onClick = {
                                regions = regions.toMutableList().also { it[idx] = r.copy(startMs = (r.startMs + 500L).coerceAtMost(r.endMs - 100L)) }
                            }, modifier = Modifier.height(24.dp)) { Text("+0.5s In", fontSize = 8.sp) }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                regions = regions.toMutableList().also { it[idx] = r.copy(endMs = (r.endMs - 500L).coerceAtLeast(r.startMs + 100L)) }
                            }, modifier = Modifier.height(24.dp)) { Text("-0.5s Out", fontSize = 8.sp) }
                            TextButton(onClick = {
                                regions = regions.toMutableList().also { it[idx] = r.copy(endMs = (r.endMs + 500L).coerceAtMost(totalDurationMs)) }
                            }, modifier = Modifier.height(24.dp)) { Text("+0.5s Out", fontSize = 8.sp) }
                        }
                    }
                }

                Button(
                    onClick = {
                        val nextIndex = regions.size + 1
                        val numStr = "%02d".format(nextIndex)
                        val lastEnd = regions.lastOrNull()?.endMs ?: 0L
                        val newStart = if (lastEnd < totalDurationMs) lastEnd else 0L
                        val newEnd = (newStart + 30000L).coerceAtMost(totalDurationMs)
                        regions = regions + RegionDraft(
                            title = "$numStr SPOT",
                            startMs = newStart,
                            endMs = newEnd
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RVCColors.bgSurface),
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = RVCColors.accentCyan)
                    Spacer(Modifier.width(6.dp))
                    Text("Aggiungi nuovo Spot/Regione", color = RVCColors.accentCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = {
                        val valid = regions.filter { it.isValid() }
                        if (valid.isEmpty()) return@Button
                        val (sr, bits, fmt) = when (selectedFormatIndex) {
                            0 -> Triple(48000, 24, "WAV")
                            1 -> Triple(44100, 16, "WAV")
                            else -> Triple(44100, 16, "MP3")
                        }
                        val opts = WavEditor.ExportOptions(
                            outputName = prefix.trim(),
                            startMs = 0L, endMs = -1L,
                            sampleRate = sr,
                            bitDepth = bits,
                            format = fmt,
                            mp3Channels = mp3Channels,
                            mp3BitrateKbps = mp3Bitrate,
                            normalize = normalizeEnabled,
                            peakDb = targetPeakDb
                        )
                        val specs = valid.map { RegionSpec(it.title, it.startMs, it.endMs) }
                        onConfirm(specs, opts)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RVCColors.accentGreen),
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Text("ESPORTA ${regions.count { it.isValid() }} REGIONI IN LOCALE", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }

                if (onConfirmRemoteRvc != null) {
                    Button(
                        onClick = {
                            val valid = regions.filter { it.isValid() }
                            if (valid.isEmpty()) return@Button
                            val opts = WavEditor.ExportOptions(
                                outputName = prefix.trim(),
                                startMs = 0L, endMs = -1L,
                                sampleRate = 48000,
                                bitDepth = 16,
                                format = "WAV",
                                normalize = normalizeEnabled,
                                peakDb = targetPeakDb
                            )
                            val specs = valid.map { RegionSpec(it.title, it.startMs, it.endMs) }
                            onConfirmRemoteRvc(specs, opts)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RVCColors.accentPurple),
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("CONVERTI TUTTO CON RVC (DROPBOX)", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla", color = RVCColors.textDim)
            }
        }
    )
}
