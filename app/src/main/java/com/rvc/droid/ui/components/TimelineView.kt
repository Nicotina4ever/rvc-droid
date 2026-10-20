package com.rvc.droid.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvc.droid.ui.theme.RVCColors

data class TrackClip(
    val id: String,
    val trackIndex: Int,
    val name: String,
    val durationMs: Long,
    var offsetMs: Long = 0,
    val color: Color
)

/** Zoom + scroll for the timeline viewport. `zoom=1` shows BASE_VISIBLE_MS. */
data class TimelineViewport(
    val zoom: Float = 1f,
    val scrollMs: Long = 0L
) {
    val visibleDurationMs: Long
        get() = (BASE_VISIBLE_MS / zoom.coerceIn(0.5f, 8f)).toLong()

    fun pxPerMs(widthPx: Float): Float =
        widthPx / visibleDurationMs.coerceAtLeast(1L).toFloat()

    companion object {
        const val BASE_VISIBLE_MS = 60_000L
    }
}

@Composable
fun TimelineView(
    tracks: List<TrackClip>,
    onOffsetChange: (String, Long) -> Unit,
    modifier: Modifier = Modifier,
    playbackPositionMs: Long = 0L,
    totalDurationMs: Long = 60000,
    selectedClipId: String? = null,
    onClipSelected: (String) -> Unit = {},
    viewport: TimelineViewport = TimelineViewport(),
    onViewportChange: (TimelineViewport) -> Unit = {},
    onSeekPosition: (Long) -> Unit = {},
    selectionStartMs: Long = 0L,
    selectionEndMs: Long = 0L
) {
    var internalViewport by remember { mutableStateOf(viewport) }
    val effective = if (viewport == TimelineViewport() && onViewportChange === {}) internalViewport else viewport

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(RVCColors.bgDeep)
            .padding(6.dp)
            .pointerInput(effective) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val totalWidthPx = size.width.toFloat()
                        val pxPerMs = effective.pxPerMs(totalWidthPx)
                        val deltaMs = -(dragAmount.x / pxPerMs).toLong()
                        val next = effective.copy(
                            scrollMs = (effective.scrollMs + deltaMs).coerceAtLeast(0L)
                        )
                        internalViewport = next
                        onViewportChange(next)
                    }
                )
            }
    ) {
        val totalWidthPx = constraints.maxWidth.toFloat()
        val pxPerMs = effective.pxPerMs(totalWidthPx)
        val density = LocalContext.current.resources.displayMetrics.density
        val lanes = tracks.groupBy { it.trackIndex }.toSortedMap().toList()

        Column(Modifier.fillMaxSize()) {
            // 1. Time Ruler Header (clickable & draggable for playhead scrub)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(RVCColors.bgSurface, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .pointerInput(effective, totalWidthPx) {
                        detectTapGestures { offset ->
                            val seekMs = (effective.scrollMs + (offset.x / pxPerMs).toLong()).coerceAtLeast(0L)
                            onSeekPosition(seekMs)
                        }
                    }
                    .pointerInput(effective, totalWidthPx) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val seekMs = (effective.scrollMs + (change.position.x / pxPerMs).toLong()).coerceAtLeast(0L)
                            onSeekPosition(seekMs)
                        }
                    }
            ) {
                val visibleStartSec = (effective.scrollMs / 1000).toInt()
                val visibleEndSec = ((effective.scrollMs + effective.visibleDurationMs) / 1000).toInt() + 2
                val stepSec = when {
                    effective.zoom >= 4f -> 1
                    effective.zoom >= 2f -> 2
                    effective.zoom >= 1f -> 5
                    else -> 10
                }

                val firstTick = (visibleStartSec / stepSec) * stepSec
                for (sec in firstTick..visibleEndSec step stepSec) {
                    val ms = sec * 1000L
                    val xPx = (ms - effective.scrollMs) * pxPerMs
                    if (xPx in 0f..totalWidthPx) {
                        val xDp = (xPx / density).dp
                        Box(
                            Modifier
                                .offset(x = xDp)
                                .width(1.dp)
                                .height(8.dp)
                                .align(Alignment.BottomStart)
                                .background(RVCColors.textDim.copy(alpha = 0.5f))
                        )
                        Text(
                            text = "${sec}s",
                            color = RVCColors.textDim,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.offset(x = xDp + 2.dp, y = 1.dp)
                        )
                    }
                }

                // Ruler playhead tag
                val relMs = (playbackPositionMs - effective.scrollMs).coerceAtLeast(0L)
                val playheadOffsetDp = ((relMs * pxPerMs) / density).dp
                if (relMs >= 0L) {
                    Box(
                        Modifier
                            .offset(x = (playheadOffsetDp - 6.dp).coerceAtLeast(0.dp), y = 1.dp)
                            .size(12.dp)
                            .background(RVCColors.accentOrange, RoundedCornerShape(2.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("▼", color = Color.White, fontSize = 7.sp)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // 2. Track Lanes
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (lanes.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Nessuna traccia. Aggiungi clip al mixer per visualizzarle nella timeline.",
                            color = RVCColors.textDim,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(lanes) { (laneIdx, clips) ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Surface(
                                    color = RVCColors.bgSurface,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.width(32.dp).height(42.dp),
                                    border = BorderStroke(1.dp, RVCColors.accentCyan.copy(alpha = 0.3f))
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("#${laneIdx + 1}", color = RVCColors.accentCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(Modifier.width(4.dp))
                                Box(Modifier.weight(1f)) {
                                    TrackLane(
                                        clips = clips,
                                        viewport = effective,
                                        selectedClipId = selectedClipId,
                                        onClipSelected = onClipSelected,
                                        onOffsetChange = onOffsetChange
                                    )
                                }
                            }
                        }
                    }
                }

                // Selection range overlay (between IN and OUT)
                if (selectionEndMs > selectionStartMs) {
                    val selStartPx = ((selectionStartMs - effective.scrollMs) * pxPerMs).coerceAtLeast(0f)
                    val selEndPx = ((selectionEndMs - effective.scrollMs) * pxPerMs).coerceAtMost(totalWidthPx)
                    if (selEndPx > selStartPx) {
                        val selStartDp = (selStartPx / density).dp
                        val selWidthDp = ((selEndPx - selStartPx) / density).dp
                        Box(
                            Modifier
                                .offset(x = selStartDp)
                                .width(selWidthDp)
                                .fillMaxHeight()
                                .background(RVCColors.accentCyan.copy(alpha = 0.15f))
                                .border(1.dp, RVCColors.accentCyan.copy(alpha = 0.5f))
                        )
                    }
                }

                // Dynamic Playhead vertical line
                val relativeMs = (playbackPositionMs - effective.scrollMs).coerceAtLeast(0L)
                val playheadOffsetDp = ((relativeMs * pxPerMs) / density).dp
                Box(
                    Modifier
                        .fillMaxHeight()
                        .offset(x = playheadOffsetDp)
                        .width(2.dp)
                        .background(RVCColors.accentOrange)
                )
            }
        }
    }
}

@Composable
fun TrackLane(
    clips: List<TrackClip>,
    viewport: TimelineViewport,
    selectedClipId: String? = null,
    onClipSelected: (String) -> Unit = {},
    onOffsetChange: (String, Long) -> Unit
) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(RVCColors.bgSurface, RoundedCornerShape(4.dp))
            .border(1.dp, RVCColors.bgSurface.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
    ) {
        val wPx = constraints.maxWidth.toFloat()
        val density = LocalContext.current.resources.displayMetrics.density
        val pxPerMs = viewport.pxPerMs(wPx)

        clips.forEach { clip ->
            val clipWidth = (clip.durationMs * pxPerMs / density).dp
            val clipOffset = ((clip.offsetMs - viewport.scrollMs) * pxPerMs / density).dp
            val isSelected = clip.id == selectedClipId

            Box(
                Modifier
                    .offset(x = clipOffset)
                    .width(clipWidth.coerceAtLeast(36.dp))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isSelected) RVCColors.accentOrange.copy(alpha = 0.85f)
                        else clip.color.copy(alpha = 0.65f)
                    )
                    .border(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) Color.White else clip.color.copy(alpha = 0.9f),
                        RoundedCornerShape(4.dp)
                    )
                    .clickable { onClipSelected(clip.id) }
                    .pointerInput(clip.id, pxPerMs) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val deltaMs = (dragAmount.x / pxPerMs).toLong()
                            onOffsetChange(clip.id, (clip.offsetMs + deltaMs).coerceAtLeast(0))
                        }
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                Column(Modifier.padding(horizontal = 4.dp)) {
                    Text(
                        clip.name,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "%.1fs (+%.1fs)".format(clip.durationMs / 1000f, clip.offsetMs / 1000f),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
