package com.rvc.droid.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvc.droid.ui.theme.RVCColors

data class EQBandState(
    val freq: Float = 1000f,
    val gainDB: Float = 0f,
    val q: Float = 1.0f,
    val enabled: Boolean = true
)

@Composable
fun EQBandSlider(
    band: EQBandState,
    onFreqChange: (Float) -> Unit = {},
    onGainChange: (Float) -> Unit = {},
    onQChange: (Float) -> Unit = {},
    onToggle: () -> Unit = {},
    color: Color = RVCColors.accentCyan,
    modifier: Modifier = Modifier
) {
    var dragging by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.width(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (band.enabled) RVCColors.bgCard else RVCColors.bgCard.copy(alpha = 0.5f))
            .border(1.dp, if (dragging) color else RVCColors.knobBorder, RoundedCornerShape(8.dp))
            .padding(6.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        // Freq control
        KnobControl(
            label = "Freq",
            value = band.freq,
            display = "%.0fHz".format(band.freq),
            onValueChange = onFreqChange,
            valueRange = 20f..20000f,
            color = color,
            modifier = Modifier.size(36.dp)
        )
        
        Spacer(Modifier.height(4.dp))
        // Gain slider vertical
        BoxWithConstraints(Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(4.dp)).background(RVCColors.meterBg)) {
            val h = maxHeight
            val fraction = ((band.gainDB + 24f) / 48f).coerceIn(0f, 1f)
            Canvas(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false }
                ) { change, _ ->
                    change.consume()
                    val newFrac = 1f - (change.position.y / h.toPx())
                    onGainChange((newFrac * 48f - 24f).coerceIn(-24f, 24f))
                }
            }) {
                drawRect(RVCColors.bgDeep, Offset.Zero, size)
                val fillH = size.height * fraction
                drawRect(color.copy(alpha = 0.7f), Offset(0f, size.height - fillH), Size(size.width, fillH))
                drawRect(Color.White.copy(alpha = 0.1f), Offset.Zero, size, style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(text = "%.1fdB".format(band.gainDB), color = RVCColors.textPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(4.dp))
        // Q knob
        KnobControl(
            label = "Q",
            value = band.q,
            display = "%.1f".format(band.q),
            onValueChange = onQChange,
            valueRange = 0.1f..10f,
            color = color,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
fun EQ4BandPanel(
    bands: List<EQBandState>,
    onBandUpdate: (Int, EQBandState) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = listOf(RVCColors.accentCyan, RVCColors.accentGreen, RVCColors.accentOrange, RVCColors.accentPurple)
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        bands.forEachIndexed { i, band ->
            EQBandSlider(
                band = band,
                onGainChange = { onBandUpdate(i, band.copy(gainDB = it)) },
                onFreqChange = { onBandUpdate(i, band.copy(freq = it)) },
                onQChange = { onBandUpdate(i, band.copy(q = it)) },
                onToggle = { onBandUpdate(i, band.copy(enabled = !band.enabled)) },
                color = colors[i % colors.size]
            )
        }
    }
}
