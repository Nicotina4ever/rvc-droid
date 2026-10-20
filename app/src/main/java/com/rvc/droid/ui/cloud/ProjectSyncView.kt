package com.rvc.droid.ui.cloud

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvc.droid.cloud.RemoteEntry
import com.rvc.droid.ui.components.RvcButton
import com.rvc.droid.ui.components.RvcCard
import com.rvc.droid.ui.components.RvcEmptyState
import com.rvc.droid.ui.components.RvcMetricRow
import com.rvc.droid.ui.components.RvcPill
import com.rvc.droid.ui.components.RvcScreenHeader
import com.rvc.droid.ui.theme.RVCColors
import com.rvc.droid.ui.theme.RvcRadius
import com.rvc.droid.ui.theme.RvcSpacing
import com.rvc.droid.ui.theme.RvcTypography

/**
 * Sync del progetto completo (bundle .rvcdaw.zip) su Dropbox.
 * Il PC vede i bundle nella cartella sincronizzata e puo' riprendere/archiviare
 * il progetto; il telefono puo' riaprire un bundle da qualsiasi dispositivo.
 */
@Composable
fun ProjectSyncView(
    modifier: Modifier = Modifier,
    dropboxToken: String,
    projectName: String,
    clipCount: Int,
    hasVideo: Boolean,
    remoteEntries: List<RemoteEntry>,
    isListing: Boolean,
    isBusy: Boolean,
    progress: Float,
    statusText: String,
    onRefresh: () -> Unit,
    onUpload: () -> Unit,
    onDownload: (RemoteEntry) -> Unit,
    onDeleteRemote: (RemoteEntry) -> Unit,
    onOpenDropboxSettings: () -> Unit,
) {
    val tokenReady = dropboxToken.isNotBlank()
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        RvcScreenHeader(
            kicker = "Dropbox · /TestRVC/Projects",
            title = "Progetti",
            subtitle = "Salva il progetto completo (audio, video, mix, plugin) e riprendilo da PC o da un altro telefono.",
            actions = {
                IconButton(onClick = onRefresh, enabled = tokenReady && !isListing) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Aggiorna elenco", tint = RVCColors.textDim)
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
                        title = "Collega Dropbox",
                        text = "Il token Dropbox si configura nella sezione RVC remoto. Serve lo stesso account del PC per far dialogare telefono e RVC Portable.",
                        actionLabel = "Apri RVC remoto",
                        onAction = onOpenDropboxSettings,
                    )
                }
            } else {
                RvcCard(
                    title = "Carica progetto su Dropbox",
                    subtitle = projectName,
                    accent = RVCColors.accent,
                    trailing = { RvcPill(if (hasVideo) "AUDIO + VIDEO" else "AUDIO", RVCColors.textMute) },
                ) {
                    RvcMetricRow("Clip audio", clipCount.toString())
                    RvcMetricRow("Video", if (hasVideo) "incluso" else "nessuno")
                    RvcMetricRow("Cartella remota", "/TestRVC/Projects")
                    Spacer(Modifier.height(8.dp))
                    if (isBusy) {
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(RvcRadius.pill)),
                            color = RVCColors.accent,
                            trackColor = RVCColors.sliderTrack,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(statusText, style = RvcTypography.bodySmall, color = RVCColors.textDim)
                    } else {
                        RvcButton(
                            text = "CARICA BUNDLE",
                            onClick = onUpload,
                            icon = Icons.Filled.CloudUpload,
                            primary = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            RvcCard(
                title = "Progetti su Dropbox",
                accent = RVCColors.accent2,
                trailing = {
                    if (isListing) RvcPill("CARICAMENTO…", RVCColors.textMute)
                    else RvcPill("${remoteEntries.size}", RVCColors.textMute)
                },
            ) {
                if (remoteEntries.isEmpty()) {
                    RvcEmptyState(
                        icon = Icons.Filled.FolderOpen,
                        title = "Nessun bundle remoto",
                        text = "Carica il primo progetto: comparirà qui e nella cartella Dropbox del PC.",
                    )
                } else {
                    remoteEntries.forEach { entry ->
                        RemoteBundleRow(
                            entry = entry,
                            busy = isBusy,
                            onDownload = { onDownload(entry) },
                            onDelete = { onDeleteRemote(entry) },
                        )
                    }
                }
            }

            RvcCard(title = "Come funziona") {
                Text(
                    "1. Registra e monta sul telefono.\n" +
                        "2. CARICA BUNDLE: crea un .zip con progetto, clip e video e lo mette in /TestRVC/Projects.\n" +
                        "3. Sul PC apri Dropbox/TestRVC/Projects per archiviare o riprendere il progetto.\n" +
                        "4. Su un altro telefono: aggiorna l'elenco, scarica il bundle e riprendi il montaggio.\n\n" +
                        "La conversione voce remota (RVC) usa la cartella AudioIn/AudioOut ed è nella sezione RVC remoto.",
                    style = RvcTypography.bodySmall,
                    color = RVCColors.textDim,
                )
            }
            Spacer(Modifier.height(RvcSpacing.xl))
        }
    }
}

@Composable
private fun RemoteBundleRow(
    entry: RemoteEntry,
    busy: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RvcRadius.sm))
            .background(RVCColors.surface2)
            .clickable(enabled = !busy) { onDownload() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = RVCColors.accentStrong, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                style = RvcTypography.bodyMedium,
                color = RVCColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatBytes(entry.sizeBytes) + " · " + entry.modifiedAt.take(16).replace("T", " "),
                style = RvcTypography.bodySmall,
                color = RVCColors.textMute,
                maxLines = 1,
            )
        }
        IconButton(onClick = onDelete, enabled = !busy) {
            Icon(Icons.Filled.Delete, contentDescription = "Elimina remoto", tint = RVCColors.danger, modifier = Modifier.size(17.dp))
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1) "%.1f MB".format(mb) else "%.0f KB".format(kb)
}
