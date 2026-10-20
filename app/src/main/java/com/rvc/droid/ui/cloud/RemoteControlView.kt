package com.rvc.droid.ui.cloud

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rvc.droid.cloud.RemotePcStatus
import com.rvc.droid.cloud.RemoteRvcModel
import com.rvc.droid.ui.components.RvcButton
import com.rvc.droid.ui.components.RvcCard
import com.rvc.droid.ui.components.RvcEmptyState
import com.rvc.droid.ui.components.RvcMetricRow
import com.rvc.droid.ui.components.RvcPill
import com.rvc.droid.ui.components.RvcScreenHeader
import com.rvc.droid.ui.components.RvcSegmentedTabs
import com.rvc.droid.ui.components.RvcSliderRow
import com.rvc.droid.ui.theme.RVCColors
import com.rvc.droid.ui.theme.RvcSpacing
import com.rvc.droid.ui.theme.RvcTypography
import kotlin.math.roundToInt

/**
 * Controllo remoto del PC: stato (heartbeat) e parametri RVC scritti nel
 * config.json condiviso su Dropbox. Funziona senza VPN e senza esporre il PC.
 */
@Composable
fun RemoteControlView(
    modifier: Modifier = Modifier,
    dropboxToken: String,
    status: RemotePcStatus?,
    statusLoading: Boolean,
    statusError: String,
    configBusy: Boolean,
    configStatus: String,
    pitchShift: Int,
    onPitchShift: (Int) -> Unit,
    formantShift: Int,
    onFormantShift: (Int) -> Unit,
    f0Method: String,
    onF0Method: (String) -> Unit,
    indexRate: Float,
    onIndexRate: (Float) -> Unit,
    protection: Float,
    onProtection: (Float) -> Unit,
    noiseScale: Float,
    onNoiseScale: (Float) -> Unit,
    remoteModels: List<RemoteRvcModel>,
    selectedRemoteModel: RemoteRvcModel?,
    onSelectRemoteModel: (RemoteRvcModel) -> Unit,
    onRefreshStatus: () -> Unit,
    onSendConfig: () -> Unit,
    onReadConfig: () -> Unit,
    onOpenDropboxSettings: () -> Unit,
) {
    var showModelMenu by remember { mutableStateOf(false) }
    val online = status?.isOnline() == true
    val tokenReady = dropboxToken.isNotBlank()

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        RvcScreenHeader(
            kicker = "Dropbox · status.json",
            title = "Controllo PC",
            subtitle = "Cambia i parametri RVC da qui: il PC li applica in pochi secondi, ovunque tu sia.",
            actions = {
                IconButton(onClick = onRefreshStatus, enabled = tokenReady && !statusLoading) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Aggiorna stato", tint = RVCColors.textDim)
                }
            },
        )

        Column(
            Modifier.padding(horizontal = RvcSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(RvcSpacing.md),
        ) {
            if (!tokenReady) {
                RvcCard(title = "Dropbox non collegato", accent = RVCColors.warning) {
                    RvcEmptyState(
                        icon = Icons.Filled.Cloud,
                        title = "Serve il token Dropbox",
                        text = "Il controllo remoto usa lo stesso account Dropbox del PC: collegalo nella sezione RVC remoto.",
                        actionLabel = "Apri RVC remoto",
                        onAction = onOpenDropboxSettings,
                    )
                }
                return@Column
            }

            // ------------------------------------------------------- stato PC
            RvcCard(
                title = "Stato PC",
                subtitle = status?.host ?: "",
                accent = if (online) RVCColors.success else RVCColors.textMute,
                trailing = {
                    RvcPill(
                        when {
                            statusLoading -> "AGGIORNAMENTO…"
                            online -> "ONLINE"
                            status != null -> "OFFLINE"
                            else -> "MAI VISTO"
                        },
                        when {
                            online -> RVCColors.success
                            status != null -> RVCColors.warning
                            else -> RVCColors.textMute
                        },
                    )
                },
            ) {
                if (statusError.isNotBlank()) {
                    Text(statusError, style = RvcTypography.bodySmall, color = RVCColors.danger)
                    Spacer(Modifier.height(4.dp))
                }
                if (status == null) {
                    Text(
                        "Nessun heartbeat ricevuto. Apri RVC Portable sul PC con il preset Android attivo: scrivera' status.json ogni pochi secondi.",
                        style = RvcTypography.bodySmall,
                        color = RVCColors.textDim,
                    )
                } else {
                    RvcMetricRow("Ultimo contatto", "${status.ageSeconds()} s fa")
                    RvcMetricRow(
                        "Motore",
                        if (status.engineAvailable) "disponibile · ${status.device.ifBlank { "auto" }}" else "non disponibile",
                        if (status.engineAvailable) RVCColors.success else RVCColors.danger,
                    )
                    RvcMetricRow("Modello PC", status.loadedModel.ifBlank { "—" })
                    RvcMetricRow("Coda", "queued ${status.queueQueued} · running ${status.queueRunning} · done ${status.queueDone}")
                    RvcMetricRow(
                        "Monitor cartella",
                        if (status.watcherRunning) "attivo" else "fermo",
                        if (status.watcherRunning) RVCColors.success else RVCColors.warning,
                    )
                    RvcMetricRow("Config applicata", "${status.lastConfigApplied} volte")
                    if (status.lastConfigError.isNotBlank()) {
                        Text("Errore config: ${status.lastConfigError}", style = RvcTypography.bodySmall, color = RVCColors.danger)
                    }
                }
            }

            // -------------------------------------------------- parametri remoti
            RvcCard(title = "Parametri RVC remoti", accent = RVCColors.accent) {
                RvcSliderRow(
                    label = "Pitch",
                    value = pitchShift.toFloat(),
                    onChange = { onPitchShift(it.roundToInt()) },
                    valueRange = -24f..24f,
                    valueText = "%+d".format(pitchShift),
                )
                RvcSliderRow(
                    label = "Formant",
                    value = formantShift.toFloat(),
                    onChange = { onFormantShift(it.roundToInt()) },
                    valueRange = -12f..12f,
                    valueText = "%+d".format(formantShift),
                )
                RvcSliderRow(
                    label = "Index rate",
                    value = indexRate,
                    onChange = onIndexRate,
                    valueText = "%.0f%%".format(indexRate * 100),
                )
                RvcSliderRow(
                    label = "Protezione consonanti",
                    value = protection,
                    onChange = onProtection,
                    valueRange = 0f..0.5f,
                    valueText = "%.2f".format(protection),
                )
                RvcSliderRow(
                    label = "Noise scale",
                    value = noiseScale,
                    onChange = onNoiseScale,
                    valueText = "%.2f".format(noiseScale),
                )
                Spacer(Modifier.height(4.dp))
                Text("Metodo F0", style = RvcTypography.labelMedium, color = RVCColors.textMute)
                Spacer(Modifier.height(4.dp))
                RvcSegmentedTabs(
                    tabs = listOf("rmvpe", "harvest", "crepe", "pm"),
                    selectedIndex = listOf("rmvpe", "harvest", "crepe", "pm")
                        .indexOf(f0Method).coerceAtLeast(0),
                    onSelect = { index -> onF0Method(listOf("rmvpe", "harvest", "crepe", "pm")[index]) },
                )
                Spacer(Modifier.height(8.dp))
                Text("Modello remoto", style = RvcTypography.labelMedium, color = RVCColors.textMute)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth()) {
                    RvcButton(
                        text = selectedRemoteModel?.name ?: "Scegli modello…",
                        onClick = { showModelMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(expanded = showModelMenu, onDismissRequest = { showModelMenu = false }) {
                        if (remoteModels.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Nessun modello: sincronizza modelli.txt") },
                                onClick = { showModelMenu = false },
                            )
                        }
                        remoteModels.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.name) },
                                onClick = {
                                    onSelectRemoteModel(model)
                                    showModelMenu = false
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                if (configBusy) {
                    Text(configStatus.ifBlank { "Invio al PC…" }, style = RvcTypography.bodySmall, color = RVCColors.accentStrong)
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(RvcSpacing.sm),
                    ) {
                        RvcButton(
                            text = "INVIA AL PC",
                            onClick = onSendConfig,
                            icon = Icons.Filled.CloudUpload,
                            primary = true,
                            modifier = Modifier.weight(1f),
                        )
                        RvcButton(
                            text = "LEGGI CONFIG",
                            onClick = onReadConfig,
                            icon = Icons.Filled.CloudDownload,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (configStatus.isNotBlank() && !configBusy) {
                    Spacer(Modifier.height(6.dp))
                    Text(configStatus, style = RvcTypography.bodySmall, color = RVCColors.textDim)
                }
            }

            RvcCard(title = "Note") {
                Text(
                    "Il telefono scrive config.json nella cartella Dropbox; il PC la applica con hot-reload (2 s) e riscrive status.json. " +
                        "Dopo l'invio puoi usare CONVERTI CON RVC su una clip: il PC usera' i parametri appena inviati.\n\n" +
                        "Nessuna porta aperta sul PC e nessuna VPN: passa tutto dal client Dropbox desktop.",
                    style = RvcTypography.bodySmall,
                    color = RVCColors.textDim,
                )
            }
            Spacer(Modifier.height(RvcSpacing.xl))
        }
    }
}
