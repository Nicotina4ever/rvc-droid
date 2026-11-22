package com.rvc.droid.ui.simple

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rvc.droid.ui.components.RvcButton
import com.rvc.droid.ui.components.RvcCard
import com.rvc.droid.ui.components.RvcEmptyState
import com.rvc.droid.ui.components.RvcPill
import com.rvc.droid.ui.components.RvcScreenHeader
import com.rvc.droid.ui.components.RvcSectionLabel
import com.rvc.droid.ui.project.MixerTrackState
import com.rvc.droid.ui.theme.RVCColors
import com.rvc.droid.ui.theme.RvcRadius
import com.rvc.droid.ui.theme.RvcSpacing
import com.rvc.droid.ui.theme.RvcTypography

/** Elenco tracce con i comandi essenziali: niente timeline, niente caos. */
@Composable
fun TracksScreen(
    modifier: Modifier = Modifier,
    tracks: List<MixerTrackState>,
    selectedClipId: String?,
    onSelectTrack: (MixerTrackState) -> Unit,
    onVolume: (Int, Float) -> Unit,
    onPan: (Int, Float) -> Unit,
    onMute: (Int, Boolean) -> Unit,
    onSolo: (Int, Boolean) -> Unit,
    onRemove: (Int) -> Unit,
    onConvert: (MixerTrackState) -> Unit,
    onAddTrack: () -> Unit,
    onMixdown: () -> Unit,
    onOpenMixer: () -> Unit,
    onOpenEditor: (MixerTrackState) -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(RVCColors.bg)
            .padding(horizontal = RvcSpacing.lg, vertical = RvcSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(RvcSpacing.md),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            RvcScreenHeader(
                kicker = "Multitrack",
                title = "Tracce",
                subtitle = "${tracks.size} tracce · tocca una card per selezionarla",
                actions = {
                    RvcButton("+ TRACCIA", onAddTrack, icon = Icons.Filled.Add, primary = true)
                },
            )
        }

        if (tracks.isEmpty()) {
            item {
                RvcCard {
                    RvcEmptyState(
                        icon = Icons.Filled.Add,
                        title = "Nessuna traccia",
                        text = "Crea una traccia e registra dal tab Registra.",
                        actionLabel = "CREA TRACCIA",
                        onAction = onAddTrack,
                    )
                }
            }
        }

        items(tracks, key = { it.trackIndex }) { track ->
            val selected = track.clipId == selectedClipId
            RvcCard(
                modifier = Modifier.fillMaxWidth(),
                title = track.clipName.ifBlank { "Traccia ${track.trackIndex + 1}" },
                subtitle = if (track.durationMs > 0) "%.1fs".format(track.durationMs / 1000f) else "Vuota",
                accent = if (selected) RVCColors.accentStrong else RVCColors.border,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RvcPill("T${track.trackIndex + 1}", trackColor(track.trackIndex))
                        Spacer(Modifier.width(6.dp))
                        IconButton(onClick = { onRemove(track.trackIndex) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Delete, contentDescription = "Elimina", tint = RVCColors.danger)
                        }
                    }
                },
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelectTrack(track) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RvcButton(
                            if (track.muted) "MUTE ON" else "MUTE",
                            { onMute(track.trackIndex, !track.muted) },
                        )
                        Spacer(Modifier.width(6.dp))
                        RvcButton(
                            if (track.solo) "SOLO ON" else "SOLO",
                            { onSolo(track.trackIndex, !track.solo) },
                        )
                        Spacer(Modifier.width(6.dp))
                        RvcPill(
                            if (track.muted) "MUTA" else "ATTIVA",
                            if (track.muted) RVCColors.warning else RVCColors.success,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    RvcSectionLabel("Volume")
                    Slider(
                        value = track.volume,
                        onValueChange = { onVolume(track.trackIndex, it) },
                        colors = SliderDefaults.colors(
                            thumbColor = RVCColors.accentStrong,
                            activeTrackColor = RVCColors.accent,
                        ),
                    )
                    RvcSectionLabel("Pan")
                    Slider(
                        value = track.pan,
                        onValueChange = { onPan(track.trackIndex, it) },
                        valueRange = -1f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = RVCColors.accent2,
                            activeTrackColor = RVCColors.accent2,
                        ),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RvcButton(
                            "CONVERTI",
                            { onConvert(track) },
                            icon = Icons.Filled.PlayArrow,
                            primary = true,
                            enabled = track.clipPath.isNotBlank(),
                        )
                        RvcButton(
                            "EDITOR",
                            { onOpenEditor(track) },
                            icon = Icons.Filled.GraphicEq,
                            enabled = track.clipPath.isNotBlank(),
                        )
                    }
                }
            }
        }

        item {
            RvcCard(title = "Mix", subtitle = "Render e regolazioni avanzate", accent = RVCColors.accent2) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RvcButton("MIXDOWN", onMixdown, icon = Icons.Filled.PlayArrow, primary = true, modifier = Modifier.weight(1f))
                    RvcButton("MIXER", onOpenMixer, icon = Icons.Filled.Tune, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun trackColor(index: Int) = listOf(
    RVCColors.accent,
    RVCColors.accent2,
    RVCColors.success,
    RVCColors.warning,
    RVCColors.info,
    RVCColors.danger,
)[index % 6]
