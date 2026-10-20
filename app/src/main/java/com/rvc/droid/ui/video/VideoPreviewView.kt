package com.rvc.droid.ui.video

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.rvc.droid.ui.theme.RVCColors
import java.io.File

@Composable
fun VideoPreviewView(
    modifier: Modifier = Modifier,
    videoPath: String? = null,
    currentPositionMs: Long = 0L,
    isPlaying: Boolean = false,
    onLoadVideo: () -> Unit = {},
    onPlayPause: () -> Unit = {},
    onStop: () -> Unit = {},
    onSeekTo: (Long) -> Unit = {},
    onPositionChanged: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var videoDurationMs by remember { mutableStateOf(0L) }
    var videoVolume by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(videoPath) {
        if (videoPath != null) {
            try {
                val player = ExoPlayer.Builder(context).build()
                val mediaItem = MediaItem.fromUri(Uri.fromFile(File(videoPath)))
                player.setMediaItem(mediaItem)
                player.prepare()
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            videoDurationMs = player.duration.coerceAtLeast(0)
                        }
                    }
                })
                exoPlayer = player
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isPlaying) {
        exoPlayer?.let { player ->
            if (isPlaying) player.play() else player.pause()
        }
    }

    LaunchedEffect(videoVolume) {
        exoPlayer?.volume = videoVolume
    }

    LaunchedEffect(exoPlayer, isPlaying) {
        if (exoPlayer != null && isPlaying) {
            while (isPlaying) {
                onPositionChanged(exoPlayer?.currentPosition ?: 0L)
                kotlinx.coroutines.delay(100)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    Column(modifier.fillMaxSize().background(RVCColors.bgDeep).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("VIDEO", color = RVCColors.accentRed, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (videoPath != null) {
                Text("LOADED", color = RVCColors.accentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(Modifier.height(12.dp))

        if (videoPath == null) {
            Box(
                modifier = Modifier.fillMaxWidth().height(180.dp).background(RVCColors.bgSurface, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = RVCColors.textDim, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No video loaded", color = RVCColors.textDim, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onLoadVideo,
                        colors = ButtonDefaults.buttonColors(containerColor = RVCColors.accentRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Load Video", color = Color.White)
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                exoPlayer?.let { player ->
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = player
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Video volume slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.VolumeUp, contentDescription = null, tint = RVCColors.textDim, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = videoVolume,
                    onValueChange = { videoVolume = it },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f).height(20.dp),
                    colors = SliderDefaults.colors(thumbColor = RVCColors.accentRed, activeTrackColor = RVCColors.accentRed)
                )
                Spacer(Modifier.width(8.dp))
                Text("%.0f%%".format(videoVolume * 100), color = RVCColors.accentRed, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
            }

            Spacer(Modifier.height(4.dp))

            // Playback controls
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onStop) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = RVCColors.textDim, modifier = Modifier.size(28.dp))
                }

                IconButton(onClick = onPlayPause) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = RVCColors.accentRed,
                        modifier = Modifier.size(44.dp)
                    )
                }

                IconButton(onClick = onLoadVideo) {
                    Icon(Icons.Default.Search, contentDescription = "Load Video", tint = RVCColors.textDim, modifier = Modifier.size(24.dp))
                }
            }

            // Position display
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatMs(currentPositionMs), color = RVCColors.textPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text(formatMs(videoDurationMs), color = RVCColors.textDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }

            if (videoDurationMs > 0) {
                LinearProgressIndicator(
                    progress = { (currentPositionMs.toFloat() / videoDurationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = RVCColors.accentRed,
                    trackColor = RVCColors.sliderTrack
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
