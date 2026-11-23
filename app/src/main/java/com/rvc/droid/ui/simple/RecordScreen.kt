package com.rvc.droid.ui.simple

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rvc.droid.ui.components.InputLevel
import com.rvc.droid.ui.components.InputLevelMeter
import com.rvc.droid.ui.components.RvcButton
import com.rvc.droid.ui.components.RvcCard
import com.rvc.droid.ui.components.RvcEmptyState
import com.rvc.droid.ui.components.RvcPill
import com.rvc.droid.ui.components.RvcScreenHeader
import com.rvc.droid.ui.components.RvcSectionLabel
import com.rvc.droid.ui.components.formatTimecode
import com.rvc.droid.ui.project.MixerTrackState
import com.rvc.droid.ui.theme.RVCColors
import com.rvc.droid.ui.theme.RvcRadius
import com.rvc.droid.ui.theme.RvcSpacing
import com.rvc.droid.ui.theme.RvcTypography
import kotlinx.coroutines.delay

/**
 * Schermata principale "da tasca": un solo gesto per registrare.
 * In orizzontale (>= 700 dp) le schede si dispongono su due colonne come una DAW.
 */
@Composable
fun RecordScreen(
    modifier: Modifier = Modifier,
    isRunning: Boolean,
    inputLevel: InputLevel,
    tracks: List<MixerTrackState>,
    armedTrackIndex: Int?,
    takeCount: Int,
    lastTakeName: String?,
    videoPath: String?,
    videoPlaying: Boolean,
    onStartStop: () -> Unit,
    onArmTrack: (Int) -> Unit,
    onAddTrack: () -> Unit,
    onLoadVideo: () -> Unit,
    onToggleVideo: () -> Unit,
    onOpenGobbo: () -> Unit,
    onOpenTracks: () -> Unit,
) {
    var elapsed by remember { mutableIntStateOf(0) }
    var showVideoPath by remember { mutableStateOf(false) }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            elapsed = 0
            while (true) {
                delay(1000)
                elapsed += 1
            }
        }
    }

    val armedTrack = tracks.firstOrNull { it.trackIndex == armedTrackIndex }

    val header: @Composable () -> Unit = {
        RvcScreenHeader(
            kicker = if (isRunning) "Registrazione" else "Pronto",
            title = "Registra",
            subtitle = if (armedTrack != null) {
                "Destinazione: ${armedTrack.clipName.ifBlank { "Traccia ${armedTrack.trackIndex + 1}" }}"
            } else {
                "Nessuna traccia armata"
            },
            actions = {
                RvcPill(
                    if (isRunning) "REC" else "STUDIO",
                    if (isRunning) RVCColors.danger else RVCColors.success,
                )
            },
        )
    }

    val recordCard: @Composable () -> Unit = {
        RvcCard(
            modifier = Modifier.fillMaxWidth(),
            title = if (isRunning) formatTimecode(elapsed * 1000L) else "Tocca per registrare",
            subtitle = if (isRunning) "Registrazione in corso" else "Il take viene salvato nella traccia armata",
            accent = if (isRunning) RVCColors.danger else RVCColors.accent,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(116.dp)
                        .clip(CircleShape)
                        .background(if (isRunning) RVCColors.danger else RVCColors.dangerSoft)
                        .clickable { onStartStop() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isRunning) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                        contentDescription = if (isRunning) "Ferma" else "Registra",
                        tint = RVCColors.danger,
                        modifier = Modifier.size(46.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            InputLevelMeter(level = inputLevel, label = "INGRESSO")
            Spacer(Modifier.height(8.dp))
            Text(
                if (takeCount > 0) "$takeCount take · ultimo: ${lastTakeName ?: "—"}" else "Nessun take ancora",
                style = RvcTypography.bodySmall,
                color = RVCColors.textMute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    val tracksCard: @Composable () -> Unit = {
        RvcCard(
            title = "Tracce",
            subtitle = "Scegli dove registrare",
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RvcButton("+ TRACCIA", onAddTrack, icon = Icons.Filled.Add, primary = true)
                    Spacer(Modifier.width(6.dp))
                    RvcButton("APRI", onOpenTracks, icon = Icons.Filled.GraphicEq)
                }
            },
        ) {
            if (tracks.isEmpty()) {
                RvcEmptyState(
                    icon = Icons.Filled.Add,
                    title = "Nessuna traccia",
                    text = "Crea una traccia per iniziare.",
                    actionLabel = "+ TRACCIA",
                    onAction = onAddTrack,
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(tracks, key = { it.trackIndex }) { track ->
                        val armed = track.trackIndex == armedTrackIndex
                        Column(
                            Modifier
                                .width(150.dp)
                                .clip(RoundedCornerShape(RvcRadius.sm))
                                .background(if (armed) RVCColors.dangerSoft else RVCColors.surface2)
                                .clickable { onArmTrack(track.trackIndex) }
                                .padding(10.dp),
                        ) {
                            Text(
                                track.clipName.ifBlank { "Traccia ${track.trackIndex + 1}" },
                                style = RvcTypography.bodySmall,
                                color = RVCColors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (armed) "IN REGISTRAZIONE" else "TOCCA PER ARMARE",
                                style = RvcTypography.labelSmall,
                                color = if (armed) RVCColors.danger else RVCColors.textMute,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }

    val videoCard: @Composable () -> Unit = {
        RvcCard(
            title = "Video di riferimento",
            subtitle = if (videoPath != null) "Caricato" else "Nessun video",
            accent = RVCColors.info,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RvcButton(if (videoPath != null) "PLAY/PAUSE" else "CARICA", {
                        if (videoPath != null) onToggleVideo() else onLoadVideo()
                    }, icon = Icons.Filled.PlayArrow)
                    Spacer(Modifier.width(6.dp))
                    RvcButton("VIDEO", { showVideoPath = !showVideoPath }, icon = Icons.Filled.Movie)
                }
            },
        ) {
            Text(
                if (videoPath != null) {
                    if (videoPlaying) "Riproduzione attiva: doppia pure guardando il video."
                    else "Video pronto. Premi PLAY/PAUSE per farlo partire."
                } else {
                    "Carica il video da doppiare: resta visibile nel tab Video."
                },
                style = RvcTypography.bodySmall,
                color = RVCColors.textDim,
            )
            if (showVideoPath && videoPath != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    videoPath,
                    style = RvcTypography.labelSmall,
                    color = RVCColors.textMute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    val gobboCard: @Composable () -> Unit = {
        RvcCard(title = "Gobbo", subtitle = "Teleprompter per gli spot", accent = RVCColors.accent2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RvcButton(
                    "APRI GOBBO",
                    onOpenGobbo,
                    icon = Icons.Filled.Article,
                    primary = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(6.dp))
            RvcSectionLabel("Lo script resta salvato sul telefono")
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(RVCColors.bg),
    ) {
        val wide = maxWidth >= 700.dp
        if (wide) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(horizontal = RvcSpacing.lg, vertical = RvcSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(RvcSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(RvcSpacing.md),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { header() }
                item { recordCard() }
                item { tracksCard() }
                item { videoCard() }
                item { gobboCard() }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                modifier = Modifier.fillMaxSize().padding(horizontal = RvcSpacing.lg, vertical = RvcSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(RvcSpacing.md),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                item { header() }
                item { recordCard() }
                item { tracksCard() }
                item { videoCard() }
                item { gobboCard() }
            }
        }
    }
}
