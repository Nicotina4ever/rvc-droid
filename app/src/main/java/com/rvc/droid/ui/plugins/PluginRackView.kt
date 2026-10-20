package com.rvc.droid.ui.plugins

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvc.droid.ui.components.EQBandSlider
import com.rvc.droid.ui.components.EQBandState
import com.rvc.droid.ui.components.LufsMeterSet
import com.rvc.droid.ui.components.NoiseGateState
import com.rvc.droid.ui.components.RvcButton
import com.rvc.droid.ui.components.RvcCard
import com.rvc.droid.ui.components.RvcPill
import com.rvc.droid.ui.components.RvcSliderRow
import com.rvc.droid.ui.components.RvcSwitchRow
import com.rvc.droid.ui.theme.RVCColors
import com.rvc.droid.ui.theme.RvcRadius
import com.rvc.droid.ui.theme.RvcSpacing
import com.rvc.droid.ui.theme.RvcTypography
import java.io.File

/**
 * Rack plugin della DAW: catena di post-produzione sul bus master.
 *
 * I moduli sono insert reali del motore nativo (gate, EQ voce/musica,
 * compressore, limiter, riverbero, master). Ogni modulo puo' essere messo in
 * bypass; la catena puo' essere salvata come preset utente e richiamata.
 * La catena e' fissa nell'ordine del segnale (come in un bus master hardware):
 * non e' possibile riordinarla perche' il DSP nativo elabora in quest'ordine.
 */
@Composable
fun PluginRackView(
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
    gateState: NoiseGateState,
    gateGR: Float,
    voiceBands: List<EQBandState>,
    musicBands: List<EQBandState>,
    compThreshold: Float,
    compRatio: Float,
    compAttack: Float,
    compRelease: Float,
    compMakeup: Float,
    compGR: Float,
    limiterThreshold: Float,
    limiterRelease: Float,
    limiterGR: Float,
    reverbMix: Float,
    reverbRoomSize: Float,
    reverbDamping: Float,
    voiceVolume: Float,
    musicVolume: Float,
    masterVolume: Float,
    monitoringGain: Float,
    momentary: Float,
    shortTerm: Float,
    integrated: Float,
    truePeak: Float,
    onGateToggle: () -> Unit,
    onGateThreshold: (Float) -> Unit,
    onVoiceBandUpdate: (Int, EQBandState) -> Unit,
    onMusicBandUpdate: (Int, EQBandState) -> Unit,
    onCompThreshold: (Float) -> Unit,
    onCompRatio: (Float) -> Unit,
    onCompAttack: (Float) -> Unit,
    onCompRelease: (Float) -> Unit,
    onCompMakeup: (Float) -> Unit,
    onLimiterThreshold: (Float) -> Unit,
    onLimiterRelease: (Float) -> Unit,
    onReverbMix: (Float) -> Unit,
    onReverbRoomSize: (Float) -> Unit,
    onReverbDamping: (Float) -> Unit,
    onVoiceVolume: (Float) -> Unit,
    onMusicVolume: (Float) -> Unit,
    onMasterVolume: (Float) -> Unit,
    onMonitoringGain: (Float) -> Unit,
    onResetMastering: () -> Unit,
) {
    val context = LocalContext.current
    var showSavePreset by remember { mutableStateOf(false) }
    var showPresetMenu by remember { mutableStateOf(false) }
    var userPresets by remember { mutableStateOf(FxPresetStore.list(context)) }
    var presetName by remember { mutableStateOf("") }

    // Bypass locali (il DSP non ha un flag bypass: si applicano valori neutri).
    var compBypassed by remember { mutableStateOf(false) }
    var limiterBypassed by remember { mutableStateOf(false) }
    var reverbBypassed by remember { mutableStateOf(false) }
    var compSaved by remember { mutableStateOf(floatArrayOf(compThreshold, compRatio, compAttack, compRelease, compMakeup)) }
    var limiterSaved by remember { mutableStateOf(floatArrayOf(limiterThreshold, limiterRelease)) }
    var reverbSaved by remember { mutableStateOf(reverbMix) }
    LaunchedEffect(compThreshold, compRatio, compAttack, compRelease, compMakeup, compBypassed) {
        if (!compBypassed) {
            compSaved = floatArrayOf(compThreshold, compRatio, compAttack, compRelease, compMakeup)
        }
    }
    LaunchedEffect(limiterThreshold, limiterRelease, limiterBypassed) {
        if (!limiterBypassed) limiterSaved = floatArrayOf(limiterThreshold, limiterRelease)
    }
    LaunchedEffect(reverbMix, reverbBypassed) {
        if (!reverbBypassed) reverbSaved = reverbMix
    }

    val voiceEqEnabled = voiceBands.any { it.enabled }
    val musicEqEnabled = musicBands.any { it.enabled }

    fun setVoiceEqEnabled(enabled: Boolean) {
        voiceBands.forEachIndexed { index, band -> onVoiceBandUpdate(index, band.copy(enabled = enabled)) }
    }
    fun setMusicEqEnabled(enabled: Boolean) {
        musicBands.forEachIndexed { index, band -> onMusicBandUpdate(index, band.copy(enabled = enabled)) }
    }
    fun setCompBypass(bypass: Boolean) {
        compBypassed = bypass
        if (bypass) {
            onCompThreshold(0f); onCompRatio(1f); onCompMakeup(0f)
        } else {
            onCompThreshold(compSaved[0]); onCompRatio(compSaved[1])
            onCompAttack(compSaved[2]); onCompRelease(compSaved[3]); onCompMakeup(compSaved[4])
        }
    }
    fun setLimiterBypass(bypass: Boolean) {
        limiterBypassed = bypass
        if (bypass) {
            onLimiterThreshold(0f); onLimiterRelease(60f)
        } else {
            onLimiterThreshold(limiterSaved[0]); onLimiterRelease(limiterSaved[1])
        }
    }
    fun setReverbBypass(bypass: Boolean) {
        reverbBypassed = bypass
        if (bypass) onReverbMix(0f) else onReverbMix(reverbSaved)
    }

    fun applySnapshot(snapshot: FxPresetSnapshot) {
        compBypassed = false
        limiterBypassed = false
        reverbBypassed = false
        if (snapshot.gate.enabled != gateState.enabled) onGateToggle()
        onGateThreshold(snapshot.gate.threshold)
        snapshot.voiceBands.forEachIndexed { index, band ->
            if (index < voiceBands.size) onVoiceBandUpdate(index, band)
        }
        snapshot.musicBands.forEachIndexed { index, band ->
            if (index < musicBands.size) onMusicBandUpdate(index, band)
        }
        onCompThreshold(snapshot.compThreshold)
        onCompRatio(snapshot.compRatio)
        onCompAttack(snapshot.compAttack)
        onCompRelease(snapshot.compRelease)
        onCompMakeup(snapshot.compMakeup)
        onLimiterThreshold(snapshot.limiterThreshold)
        onLimiterRelease(snapshot.limiterRelease)
        onReverbMix(snapshot.reverbMix)
        onReverbRoomSize(snapshot.reverbRoomSize)
        onReverbDamping(snapshot.reverbDamping)
        onVoiceVolume(snapshot.voiceVolume)
        onMusicVolume(snapshot.musicVolume)
        onMasterVolume(snapshot.masterVolume)
        onMonitoringGain(snapshot.monitoringGain)
    }

    fun currentSnapshot(name: String): FxPresetSnapshot = FxPresetSnapshot(
        name = name,
        gate = gateState,
        voiceBands = voiceBands,
        musicBands = musicBands,
        compThreshold = compThreshold,
        compRatio = compRatio,
        compAttack = compAttack,
        compRelease = compRelease,
        compMakeup = compMakeup,
        limiterThreshold = limiterThreshold,
        limiterRelease = limiterRelease,
        reverbMix = reverbMix,
        reverbRoomSize = reverbRoomSize,
        reverbDamping = reverbDamping,
        voiceVolume = voiceVolume,
        musicVolume = musicVolume,
        masterVolume = masterVolume,
        monitoringGain = monitoringGain,
    )

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = RvcSpacing.lg, vertical = RvcSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(RvcSpacing.md),
    ) {
        // --------------------------------------------------------- intestazione
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PLUGIN RACK", style = RvcTypography.labelSmall, color = RVCColors.accentStrong, letterSpacing = 1.6.sp)
                Text("Post-produzione master", style = RvcTypography.headlineSmall, color = RVCColors.text)
                Text(
                    "Catena insert sul bus master: gate → EQ → compressore → limiter → riverbero.",
                    style = RvcTypography.bodySmall,
                    color = RVCColors.textMute,
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Chiudi rack", tint = RVCColors.textDim)
            }
        }

        // ------------------------------------------------------- preset rapidi
        RvcCard(title = "Preset", accent = RVCColors.accent2) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(RvcSpacing.sm),
            ) {
                FxFactoryPresets.all().forEach { preset ->
                    RvcButton(
                        text = preset.name,
                        onClick = { applySnapshot(preset) },
                        primary = false,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RvcSpacing.sm),
            ) {
                RvcButton(
                    text = "Salva preset",
                    onClick = { showSavePreset = true },
                    icon = Icons.Filled.Save,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.weight(1f)) {
                    RvcButton(
                        text = "Preset utente",
                        onClick = {
                            userPresets = FxPresetStore.list(context)
                            showPresetMenu = true
                        },
                        icon = Icons.Filled.FolderOpen,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(expanded = showPresetMenu, onDismissRequest = { showPresetMenu = false }) {
                        if (userPresets.isEmpty()) {
                            DropdownMenuItem(text = { Text("Nessun preset salvato") }, onClick = { showPresetMenu = false })
                        }
                        userPresets.forEach { file ->
                            DropdownMenuItem(
                                text = { Text(file.nameWithoutExtension) },
                                onClick = {
                                    FxPresetStore.load(file)?.let { applySnapshot(it) }
                                    showPresetMenu = false
                                },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        FxPresetStore.delete(file)
                                        userPresets = FxPresetStore.list(context)
                                    }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Elimina", tint = RVCColors.danger)
                                    }
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            RvcButton(
                text = "Ripristina mastering",
                onClick = onResetMastering,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ------------------------------------------------------- noise gate
        RvcCard(
            title = "01 · Noise Gate",
            subtitle = "Pulizia del rumore di fondo",
            accent = RVCColors.accent,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RvcPill(if (gateState.enabled) "ON" else "BYPASS", if (gateState.enabled) RVCColors.success else RVCColors.textMute)
                    Spacer(Modifier.width(6.dp))
                    RvcPill("GR %.1f dB".format(gateGR), RVCColors.textMute)
                }
            },
        ) {
            RvcSwitchRow(
                label = "Attiva gate",
                hint = "Apre solo quando parli: elimina respiro e rumore tra le frasi",
                checked = gateState.enabled,
                onCheckedChange = { if (it != gateState.enabled) onGateToggle() },
            )
            RvcSliderRow(
                label = "Soglia",
                value = gateState.threshold,
                onChange = onGateThreshold,
                valueRange = 0.005f..0.5f,
                valueText = "%.3f".format(gateState.threshold),
            )
            RvcPill(
                "Attacco %.0f ms · Hold %.0f ms · Release %.0f ms".format(gateState.attackMs, gateState.holdMs, gateState.releaseMs),
                RVCColors.textMute,
            )
        }

        // ---------------------------------------------------------- voice EQ
        RvcCard(
            title = "02 · Voice EQ",
            subtitle = "Equalizzatore voce (4 bande)",
            accent = RVCColors.accent,
            trailing = {
                RvcPill(if (voiceEqEnabled) "ON" else "BYPASS", if (voiceEqEnabled) RVCColors.success else RVCColors.textMute)
            },
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                voiceBands.forEachIndexed { index, band ->
                    EQBandSlider(
                        band = band,
                        onFreqChange = { freq -> onVoiceBandUpdate(index, band.copy(freq = freq)) },
                        onGainChange = { gain -> onVoiceBandUpdate(index, band.copy(gainDB = gain)) },
                        onQChange = { q -> onVoiceBandUpdate(index, band.copy(q = q)) },
                        onToggle = { onVoiceBandUpdate(index, band.copy(enabled = !band.enabled)) },
                        color = RVCColors.accent,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            RvcSwitchRow(
                label = "Modulo attivo",
                hint = "Bypass completo dell'EQ voce",
                checked = voiceEqEnabled,
                onCheckedChange = { setVoiceEqEnabled(it) },
            )
        }

        // ---------------------------------------------------------- music EQ
        RvcCard(
            title = "03 · Music EQ",
            subtitle = "Equalizzatore base musicale",
            accent = RVCColors.accent2,
            trailing = {
                RvcPill(if (musicEqEnabled) "ON" else "BYPASS", if (musicEqEnabled) RVCColors.success else RVCColors.textMute)
            },
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                musicBands.forEachIndexed { index, band ->
                    EQBandSlider(
                        band = band,
                        onFreqChange = { freq -> onMusicBandUpdate(index, band.copy(freq = freq)) },
                        onGainChange = { gain -> onMusicBandUpdate(index, band.copy(gainDB = gain)) },
                        onQChange = { q -> onMusicBandUpdate(index, band.copy(q = q)) },
                        onToggle = { onMusicBandUpdate(index, band.copy(enabled = !band.enabled)) },
                        color = RVCColors.accent2,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            RvcSwitchRow(
                label = "Modulo attivo",
                hint = "Bypass completo dell'EQ musica",
                checked = musicEqEnabled,
                onCheckedChange = { setMusicEqEnabled(it) },
            )
        }

        // ------------------------------------------------------- compressore
        RvcCard(
            title = "04 · Compressore",
            subtitle = "Controllo dinamico con sidechain verso la musica",
            accent = RVCColors.warning,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RvcPill(if (compBypassed) "BYPASS" else "ON", if (compBypassed) RVCColors.textMute else RVCColors.success)
                    Spacer(Modifier.width(6.dp))
                    RvcPill("GR %.1f dB".format(compGR), RVCColors.warning)
                }
            },
        ) {
            RvcSwitchRow(
                label = "Modulo attivo",
                checked = !compBypassed,
                onCheckedChange = { setCompBypass(!it) },
            )
            RvcSliderRow("Soglia", compThreshold, onCompThreshold, valueRange = -60f..0f, valueText = "%.1f dB".format(compThreshold))
            RvcSliderRow("Ratio", compRatio, onCompRatio, valueRange = 1f..20f, valueText = "%.1f:1".format(compRatio))
            RvcSliderRow("Attacco", compAttack, onCompAttack, valueRange = 0.1f..100f, valueText = "%.1f ms".format(compAttack))
            RvcSliderRow("Release", compRelease, onCompRelease, valueRange = 10f..1000f, valueText = "%.0f ms".format(compRelease))
            RvcSliderRow("Makeup", compMakeup, onCompMakeup, valueRange = 0f..24f, valueText = "+%.1f dB".format(compMakeup))
        }

        // ------------------------------------------------------------ limiter
        RvcCard(
            title = "05 · Limiter",
            subtitle = "Brickwall di sicurezza per lo spot",
            accent = RVCColors.danger,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RvcPill(if (limiterBypassed) "BYPASS" else "ON", if (limiterBypassed) RVCColors.textMute else RVCColors.success)
                    Spacer(Modifier.width(6.dp))
                    RvcPill("GR %.1f dB".format(limiterGR), RVCColors.danger)
                }
            },
        ) {
            RvcSwitchRow(
                label = "Modulo attivo",
                checked = !limiterBypassed,
                onCheckedChange = { setLimiterBypass(!it) },
            )
            RvcSliderRow("Threshold", limiterThreshold, onLimiterThreshold, valueRange = -12f..0f, valueText = "%.1f dB".format(limiterThreshold))
            RvcSliderRow("Release", limiterRelease, onLimiterRelease, valueRange = 10f..500f, valueText = "%.0f ms".format(limiterRelease))
        }

        // ----------------------------------------------------------- riverbero
        RvcCard(
            title = "06 · Riverbero",
            subtitle = "Ambienza per voce e musica",
            accent = RVCColors.info,
            trailing = {
                RvcPill(if (reverbBypassed || reverbMix <= 0.001f) "BYPASS" else "ON", if (reverbBypassed || reverbMix <= 0.001f) RVCColors.textMute else RVCColors.success)
            },
        ) {
            RvcSwitchRow(
                label = "Modulo attivo",
                checked = !reverbBypassed,
                onCheckedChange = { setReverbBypass(!it) },
            )
            RvcSliderRow("Mix", reverbMix, onReverbMix, valueRange = 0f..0.6f, valueText = "%.0f%%".format(reverbMix * 100))
            RvcSliderRow("Room size", reverbRoomSize, onReverbRoomSize, valueText = "%.0f%%".format(reverbRoomSize * 100))
            RvcSliderRow("Damping", reverbDamping, onReverbDamping, valueText = "%.0f%%".format(reverbDamping * 100))
        }

        // -------------------------------------------------------------- master
        RvcCard(
            title = "07 · Master",
            subtitle = "Livelli finali e loudness",
            accent = RVCColors.success,
        ) {
            RvcSliderRow("Voce", voiceVolume, onVoiceVolume, valueRange = -24f..12f, valueText = "%+.1f dB".format(voiceVolume))
            RvcSliderRow("Musica", musicVolume, onMusicVolume, valueRange = -24f..12f, valueText = "%+.1f dB".format(musicVolume))
            RvcSliderRow("Master", masterVolume, onMasterVolume, valueRange = -24f..12f, valueText = "%+.1f dB".format(masterVolume))
            RvcSliderRow("Monitoring", monitoringGain, onMonitoringGain, valueText = "%.0f%%".format(monitoringGain * 100))
            Spacer(Modifier.height(8.dp))
            LufsMeterSet(
                momentary = momentary,
                shortTerm = shortTerm,
                integrated = integrated,
                truePeak = truePeak,
            )
        }

        Text(
            "La catena e' elaborata dal motore nativo nell'ordine mostrato. Il bus master riceve tutte le tracce; per volume/pan/mute/solo/ducking delle singole tracce usa la tab Tracce del Mixer.",
            style = RvcTypography.bodySmall,
            color = RVCColors.textMute,
        )
        Spacer(Modifier.height(RvcSpacing.lg))
    }

    if (showSavePreset) {
        AlertDialog(
            onDismissRequest = { showSavePreset = false },
            title = { Text("Salva preset plugin") },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text("Nome preset") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    FxPresetStore.save(context, currentSnapshot(presetName.ifBlank { "Preset" }))
                    userPresets = FxPresetStore.list(context)
                    showSavePreset = false
                    presetName = ""
                }) { Text("Salva") }
            },
            dismissButton = { TextButton(onClick = { showSavePreset = false }) { Text("Annulla") } },
        )
    }
}
