package com.rvc.droid.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvc.droid.ui.theme.RVCColors

@Composable
fun LufsMeterBar(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float> = -60f..0f,
    modifier: Modifier = Modifier,
    color: Color = RVCColors.accentCyan
) {
    val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    Column(modifier = modifier.width(48.dp)) {
        Text(text = label, color = RVCColors.textDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(2.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().height(160.dp)) {
            val barW = maxWidth
            Canvas(modifier = Modifier.fillMaxSize()) {
                val h = size.height
                // Background
                drawRoundRect(RVCColors.meterBg, Offset.Zero, size, CornerRadius(4f, 4f))
                // Color zones
                val greenEnd = 0.6f; val yellowEnd = 0.85f
                drawRect(RVCColors.meterLow, Offset(0f, h * (1f - greenEnd)), Size(size.width, h * greenEnd))
                drawRect(RVCColors.meterMid, Offset(0f, h * (1f - yellowEnd)), Size(size.width, h * (yellowEnd - greenEnd)))
                drawRect(RVCColors.meterHigh, Offset(0f, 0f), Size(size.width, h * (1f - yellowEnd)))
                // Level fill
                val fillH = h * fraction
                drawRect(color.copy(alpha = 0.9f), Offset(0f, h - fillH), Size(size.width, fillH))
                // Border
                drawRoundRect(Color.White.copy(alpha = 0.15f), Offset.Zero, size, CornerRadius(4f, 4f), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
            }
            Text(
                text = "%.1f".format(value),
                color = if (value > -6f) RVCColors.accentRed else RVCColors.textPrimary,
                fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = 4.dp)
            )
        }
    }
}

@Composable
fun LufsMeterSet(
    momentary: Float, shortTerm: Float, integrated: Float, truePeak: Float,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        LufsMeterBar("Momentary", momentary)
        LufsMeterBar("ShortTerm", shortTerm)
        LufsMeterBar("Integrated", integrated)
        LufsMeterBar("TruePeak", truePeak, color = RVCColors.accentOrange)
    }
}
