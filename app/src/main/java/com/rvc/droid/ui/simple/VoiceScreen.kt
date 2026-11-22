package com.rvc.droid.ui.simple

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rvc.droid.cloud.RemoteRvcModel
import com.rvc.droid.ui.components.RvcButton
import com.rvc.droid.ui.components.RvcCard
import com.rvc.droid.ui.components.RvcEmptyState
import com.rvc.droid.ui.components.RvcPill
import com.rvc.droid.ui.components.RvcScreenHeader
import com.rvc.droid.ui.components.RvcSectionLabel
import com.rvc.droid.ui.components.RvcSegmentedTabs
import com.rvc.droid.ui.components.RvcSliderRow
import com.rvc.droid.ui.project.RecordingClip
import com.rvc.droid.ui.theme.RVCColors
import com.rvc.droid.ui.theme.RvcRadius
import com.rvc.droid.ui.theme.RvcSpacing
import com.rvc.droid.ui.theme.RvcTypography
import kotlin.math.roundToInt

/** Conversione RVC remota: modelli e parametri in primo piano, un solo pulsante. */
@Composable
fun VoiceScreen(
    modifier: Modifier = Modifier,
    dropboxConnected: Boolean,
    clips: List<RecordingClip>,
    selectedClip: RecordingClip?,
    onSelectClip: (RecordingClip) -> Unit,
    models: List<RemoteRvcModel>,
    selectedModel: RemoteRvcModel?,
    onSelectModel: (RemoteRvcModel) -> Unit,
    pitch: Int,
    onPitch: (Int) -> Unit,
    formant: Int,
    onFormant: (Int) -> Unit,
    indexRate: Float,
    onIndexRate: (Float) -> Unit,
    protection: Float,
    onProtection: (Float) -> Unit,
    noiseScale: Float,
    onNoiseScale: (Float) -> Unit,
    f0Method: String,
    onF0Method: (String) -> Unit,
    progress: Float,
    progressText: String,
    onConvert: () -> Unit,
    onOpenCloud: () -> Unit,
    onOpenAdvancedSettings: () -> Unit,
) {
    var showModelMenu by remember { mutableStateOf(false) }
    val busy = progress in 0f..0.99f
    val methods = listOf("rmvpe", "harvest", "crepe", "pm")
    val presets = listOf("Spot", "Podcast", "Musica")

    fun applyPreset(name: String) {
        when (name) {
            "Spot" -> {
                onPitch(0); onFormant(0); onIndexRate(0.30f); onProtection(0.33f); onNoiseScale(0.666f); onF0Method("rmvpe")
            }
            "Podcast" -> {
                onPitch(0); onFormant(0); onIndexRate(0.50f); onProtection(0.40f); onNoiseScale(0.5f); onF0Method("rmvpe")
            }
            "Musica" -> {
                onPitch(0); onFormant(0); onIndexRate(0.20f); onProtection(0.25f); onNoiseScale(0.8f); onF0Method("rmvpe")
            }
        }
    }

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
                kicker = "PC · Dropbox",
                title = "Voce",
                subtitle = "La conversione avviene sul PC con i modelli .pth originali",
                actions = {
                    RvcPill(
                        if (dropboxConnected) "COLLEGATO" else "DA COLLEGARE",
                        if (dropboxConnected) RVCColors.success else RVCColors.warning,
                    )
                },
            )
        }

        if (!dropboxConnected) {
            item {
                RvcCard(title = "Dropbox", accent = RVCColors.warning) {
                    RvcEmptyState(
                        icon = Icons.Filled.Cloud,
                        title = "Collega Dropbox",
                        text = "Serve una volta sola: stesso account del PC.",
                        actionLabel = "APRI CLOUD",
                        onAction = onOpenCloud,
                    )
                }
            }
        }

        item {
            RvcCard(title = "Modello", subtitle = "Elenco sincronizzato dal PC", accent = RVCColors.accent) {
                Box(Modifier.fillMaxWidth()) {
                    RvcButton(
                        text = selectedModel?.name ?: "Scegli modello…",
                        onClick = { showModelMenu = true },
                        primary = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(expanded = showModelMenu, onDismissRequest = { showModelMenu = false }) {
                        if (models.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Nessun modello: sincronizza dal PC") },
                                onClick = { showModelMenu = false; onOpenCloud() },
                            )
                        }
                        models.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.name) },
                                onClick = { onSelectModel(model); showModelMenu = false },
                            )
                        }
                    }
                }
            }
        }

        item {
            RvcCard(title = "Clip da convertire", subtitle = "Seleziona il take registrato") {
                if (clips.isEmpty()) {
                    RvcEmptyState(
                        icon = Icons.Filled.PlayArrow,
                        title = "Nessuna clip",
                        text = "Registra prima un take dal tab Registra.",
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(clips, key = { it.id }) { clip ->
                            val selected = clip.id == selectedClip?.id
                            Column(
                                Modifier
                                    .width(150.dp)
                                    .clip(RoundedCornerShape(RvcRadius.sm))
                                    .background(if (selected) RVCColors.accentSoft else RVCColors.surface2)
                                    .clickable { onSelectClip(clip) }
                                    .padding(9.dp),
                            ) {
                                Text(
                                    clip.name,
                                    style = RvcTypography.bodySmall,
                                    color = RVCColors.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "%.1fs".format(clip.durationMs / 1000f),
                                    style = RvcTypography.labelSmall,
                                    color = RVCColors.textMute,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            RvcCard(title = "Parametri", subtitle = "Preset rapidi e controlli principali") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEach { name ->
                        RvcButton(name, { applyPreset(name) }, primary = false)
                    }
                }
                Spacer(Modifier.height(8.dp))
                RvcSliderRow(
                    label = "Pitch",
                    value = pitch.toFloat(),
                    onChange = { onPitch(it.roundToInt()) },
                    valueRange = -24f..24f,
                    valueText = "%+d".format(pitch),
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
                Spacer(Modifier.height(6.dp))
                RvcSectionLabel("Metodo F0")
                Spacer(Modifier.height(4.dp))
                RvcSegmentedTabs(
                    tabs = methods,
                    selectedIndex = methods.indexOf(f0Method).coerceAtLeast(0),
                    onSelect = { onF0Method(methods[it]) },
                )
                Spacer(Modifier.height(6.dp))
                RvcButton(
                    "PARAMETRI AVANZATI",
                    onOpenAdvancedSettings,
                    icon = Icons.Filled.Settings,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            RvcCard(title = "Conversione", accent = RVCColors.accent2) {
                RvcSliderRow(
                    label = "Formant",
                    value = formant.toFloat(),
                    onChange = { onFormant(it.roundToInt()) },
                    valueRange = -12f..12f,
                    valueText = "%+d".format(formant),
                )
                RvcSliderRow(
                    label = "Noise scale",
                    value = noiseScale,
                    onChange = onNoiseScale,
                    valueText = "%.2f".format(noiseScale),
                )
                if (busy) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = RVCColors.accent2,
                    )
                }
                if (progressText.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(progressText, style = RvcTypography.bodySmall, color = RVCColors.accentStrong)
                }
                Spacer(Modifier.height(10.dp))
                RvcButton(
                    text = if (busy) "CONVERSIONE IN CORSO…" else "CONVERTI CON RVC",
                    onClick = onConvert,
                    icon = Icons.Filled.PlayArrow,
                    primary = true,
                    enabled = selectedClip != null && dropboxConnected && !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
