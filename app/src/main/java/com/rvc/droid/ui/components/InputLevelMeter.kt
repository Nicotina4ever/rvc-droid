package com.rvc.droid.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvc.droid.ui.theme.RVCColors

data class InputLevel(val left: Float = -80f, val right: Float = -80f,
                      val peakLeft: Float = -80f, val peakRight: Float = -80f,
                      val clipping: Boolean = false)

@Composable
fun InputLevelMeter(level: InputLevel, modifier: Modifier = Modifier, label: String = "LIVE INPUT") {
    // If right channel is silent (mono microphone stream), mirror left to right for responsive dual metering
    val rVal = if (level.right <= -79f && level.left > -79f) level.left else level.right
    val rPeak = if (level.peakRight <= -79f && level.peakLeft > -79f) level.peakLeft else level.peakRight

    val al by animateFloatAsState(level.left, label="l")
    val ar by animateFloatAsState(rVal, label="r")
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = RVCColors.bgSurface)) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = RVCColors.textDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.weight(1f))
                if (level.clipping) Text("CLIP!", color = RVCColors.accentRed, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            LevelBar("L", al, level.peakLeft, RVCColors.accentCyan)
            Spacer(Modifier.height(2.dp))
            LevelBar("R", ar, rPeak, RVCColors.accentCyan)
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(0f,-6f,-12f,-24f,-48f).forEach { Text("%.0f".format(it), color = RVCColors.textDim, fontSize = 7.sp, fontFamily = FontFamily.Monospace) }
            }
        }
    }
}

@Composable
private fun LevelBar(label: String, dbValue: Float, peakDb: Float, color: Color) {
    val range = -60f..6f; val frac = ((dbValue - range.start)/(range.endInclusive - range.start)).coerceIn(0f,1f)
    val pf = ((peakDb - range.start)/(range.endInclusive - range.start)).coerceIn(0f,1f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = RVCColors.textSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(16.dp))
        BoxWithConstraints(Modifier.weight(1f).height(20.dp).clip(RoundedCornerShape(3.dp)).background(RVCColors.bgDeep)) {
            Canvas(Modifier.fillMaxSize()) {
                val bw = size.width; val bh = size.height
                val ge = bw*0.70f; val ye = bw*0.88f
                drawRect(RVCColors.meterLow.copy(alpha=0.25f), Offset.Zero, Size(ge,bh))
                drawRect(RVCColors.meterMid.copy(alpha=0.25f), Offset(ge,0f), Size(ye-ge,bh))
                drawRect(RVCColors.meterHigh.copy(alpha=0.25f), Offset(ye,0f), Size(bw-ye,bh))
                val fw = bw*frac.coerceIn(0f,1f)
                val fc = when { dbValue > -3f -> RVCColors.accentRed; dbValue > -12f -> RVCColors.accentYellow; else -> color }
                drawRect(fc.copy(alpha=0.85f), Offset.Zero, Size(fw,bh))
                if(peakDb > -60f) drawCircle(fc, 4f, Offset((bw*pf).coerceAtMost(bw-2f), bh/2f))
                drawRoundRect(Color.White.copy(alpha=0.1f), Offset.Zero, Size(bw,bh), CornerRadius(3f,3f), style=Stroke(1f))
            }
        }
        Spacer(Modifier.width(4.dp))
        Text("%.1f".format(dbValue), color = if(dbValue>-3f) RVCColors.accentRed else RVCColors.textSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(42.dp))
    }
}
