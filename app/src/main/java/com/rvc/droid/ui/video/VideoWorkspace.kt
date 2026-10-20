package com.rvc.droid.ui.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rvc.droid.ui.components.RvcButton
import com.rvc.droid.ui.components.RvcCard
import com.rvc.droid.ui.components.RvcEmptyState
import com.rvc.droid.ui.components.RvcMetricRow
import com.rvc.droid.ui.components.RvcScreenHeader
import com.rvc.droid.ui.components.formatTimecode
import com.rvc.droid.ui.theme.RVCColors
import com.rvc.droid.ui.theme.RvcSpacing
import java.io.File

/**
 * Area video: lettore di riferimento sincronizzabile con la timeline.
 * Non fa editing video distruttivo; serve per doppiaggio/spot con riferimento visivo.
 */
@Composable
fun VideoWorkspace(
    modifier: Modifier = Modifier,
    videoPath: String?,
    isPlaying: Boolean,
    positionMs: Long,
    timelinePositionMs: Long,
    onLoadVideo: () -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPositionChanged: (Long) -> Unit,
    onSeekTimelineToVideo: () -> Unit,
    onGoToStudio: () -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        RvcScreenHeader(
            kicker = "Riferimento visivo",
            title = "Video",
            subtitle = "Carica il video dello spot e allinealo alla timeline: la registrazione parte insieme al video.",
        )

        if (videoPath == null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = RvcSpacing.lg),
            ) {
                RvcCard(title = "Nessun video caricato") {
                    RvcEmptyState(
                        icon = Icons.Filled.Movie,
                        title = "Aggiungi il video di riferimento",
                        text = "MP4, MKV o WebM dalla memoria del telefono. Il video resta locale al progetto e viene sincronizzato con la timeline quando premi REC.",
                        actionLabel = "Carica video",
                        onAction = onLoadVideo,
                    )
                }
                Spacer(Modifier.height(RvcSpacing.md))
                RvcCard(title = "Come funziona") {
                    Text(
                        "1. Carica il video dello spot.\n" +
                            "2. Vai nello Studio e premi REC: il video parte insieme alla registrazione.\n" +
                            "3. In Timeline monta le riprese e in Mixer rifinisci l'audio.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = RVCColors.textDim,
                    )
                }
            }
            return
        }

        VideoPreviewView(
            videoPath = videoPath,
            currentPositionMs = positionMs,
            isPlaying = isPlaying,
            onLoadVideo = onLoadVideo,
            onPlayPause = onPlayPause,
            onStop = onStop,
            onSeekTo = onSeekTo,
            onPositionChanged = onPositionChanged,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        RvcCard(
            title = "Sincronizzazione timeline",
            subtitle = File(videoPath).name,
            modifier = Modifier.padding(horizontal = RvcSpacing.lg, vertical = RvcSpacing.sm),
        ) {
            RvcMetricRow("Posizione video", formatTimecode(positionMs), RVCColors.accentStrong)
            RvcMetricRow("Posizione timeline", formatTimecode(timelinePositionMs), RVCColors.textDim)
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RvcSpacing.sm),
            ) {
                RvcButton(
                    text = "Allinea timeline",
                    onClick = onSeekTimelineToVideo,
                    icon = Icons.Filled.Sync,
                    modifier = Modifier.weight(1f),
                )
                RvcButton(
                    text = "Doppia in Studio",
                    onClick = onGoToStudio,
                    icon = Icons.Filled.Mic,
                    primary = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
