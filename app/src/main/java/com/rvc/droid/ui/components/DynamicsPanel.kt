package com.rvc.droid.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvc.droid.ui.theme.RVCColors

@Composable
fun CompCurve(
    threshold: Float,
    ratio: Float,
    gainReduction: Float = 0f,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.width(140.dp).height(100.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(RVCColors.bgDeep)
            .border(1.dp, RVCColors.knobBorder, RoundedCornerShape(6.dp))
    ) {
        val w = maxWidth; val h = maxHeight
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pad = 12f; val cw = size.width - pad * 2; val ch = size.height - pad * 2
            val threshFrac = (threshold + 60f) / 70f
            val threshY = ch * (1f - threshFrac) + pad
            val threshX = ch * threshFrac + pad

            // Grid lines
            for (i in 0..5) {
                val y = pad + ch * i / 5f; val x = pad + cw * i / 5f
                drawLine(Color.White.copy(alpha = 0.06f), Offset(pad, y), Offset(size.width - pad, y))
                drawLine(Color.White.copy(alpha = 0.06f), Offset(x, pad), Offset(x, size.height - pad))
            }

            // Transfer curve (before threshold = 1:1, after = ratio:1)
            val path = Path()
            val kneeX = pad + cw * threshFrac
            path.moveTo(pad, size.height - pad) // -60dB input -> -60dB output
            path.lineTo(kneeX, size.height - pad - ch * threshFrac) // threshold
            val outReduction = if (gainReduction > 0) gainReduction else 0f
            val afterRatio = 1f / ratio
            val endOutFrac = (threshFrac - (1f - threshFrac) * (1f - afterRatio) - outReduction / 70f).coerceIn(0f, 1f)
            path.lineTo(size.width - pad, size.height - pad - ch * endOutFrac)
            drawPath(path, RVCColors.accentCyan, style = Stroke(2f))

            // Input/output labels
            drawContext.canvas.apply {
                drawLine(Color.White.copy(alpha = 0.15f), Offset(pad - 4, threshY), Offset(pad + cw + 4, threshY), strokeWidth = 1f)
            }
        }
        Column(Modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.Bottom) {
            if (gainReduction > 0.5f) {
                Text("-%.1f dB".format(gainReduction), color = RVCColors.accentRed, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun KnobControl(
    label: String,
    value: Float,
    display: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    color: Color = RVCColors.accentCyan
) {
    Column(modifier = modifier.width(56.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text(label, color = RVCColors.textDim, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(2.dp))
        BoxWithConstraints(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(RVCColors.knobBg)
                .border(1.5f.dp, color, RoundedCornerShape(20.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val delta = -dragAmount.y / size.height.toFloat() // vertical drag
                        val range = valueRange.endInclusive - valueRange.start
                        val newValue = (value + delta * range).coerceIn(valueRange.start, valueRange.endInclusive)
                        onValueChange(newValue)
                    }
                }
        ) {
            val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f; val cy = size.height / 2f; val r = size.width / 2f - 6f
                val angle = -225f + fraction * 270f
                val rad = Math.toRadians(angle.toDouble())
                val ex = cx + r * kotlin.math.cos(rad).toFloat()
                val ey = cy + r * kotlin.math.sin(rad).toFloat()
                drawCircle(Color.White.copy(alpha = 0.1f), r + 2f, Offset(cx, cy))
                drawCircle(RVCColors.bgDeep, r, Offset(cx, cy))
                drawArc(color.copy(alpha = 0.3f), -225f, 270f, false, Offset(cx - r, cy - r), Size(r * 2, r * 2), style = Stroke(2f))
                drawArc(color, -225f, fraction * 270f, false, Offset(cx - r, cy - r), Size(r * 2, r * 2), style = Stroke(2.5f))
                drawCircle(color, 4f, Offset(ex, ey))
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(display, color = RVCColors.textPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun SideChainPanel(
    threshold: Float, ratio: Float, attack: Float, release: Float, makeup: Float,
    gainReduction: Float,
    onThreshold: (Float) -> Unit,
    onRatio: (Float) -> Unit,
    onAttack: (Float) -> Unit,
    onRelease: (Float) -> Unit,
    onMakeup: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(RVCColors.bgCard).padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        CompCurve(threshold, ratio, gainReduction)
        Spacer(Modifier.width(8.dp))
        KnobControl("Thresh", threshold, "%.0f".format(threshold) + "dB", onThreshold, valueRange = -60f..0f, color = RVCColors.accentOrange)
        KnobControl("Ratio", ratio, "%.1f:1".format(ratio), onRatio, valueRange = 1f..20f, color = RVCColors.accentOrange)
        KnobControl("Attack", attack, "%.0f".format(attack) + "ms", onAttack, valueRange = 0.1f..100f, color = RVCColors.accentOrange)
        KnobControl("Release", release, "%.0f".format(release) + "ms", onRelease, valueRange = 1f..500f, color = RVCColors.accentOrange)
        KnobControl("Makeup", makeup, "%.1f".format(makeup) + "dB", onMakeup, valueRange = 0f..24f, color = RVCColors.accentOrange)
    }
}

@Composable
fun LimiterPanel(
    threshold: Float, release: Float, gainReduction: Float,
    onThreshold: (Float) -> Unit,
    onRelease: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(RVCColors.bgCard).padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        BoxWithConstraints(Modifier.width(100.dp).height(40.dp).clip(RoundedCornerShape(4.dp)).background(RVCColors.bgDeep)) {
            val fraction = (gainReduction / 20f).coerceIn(0f, 1f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(RVCColors.meterBg, Offset.Zero, size)
                drawRect(RVCColors.accentRed.copy(alpha = 0.8f), Offset(0f, size.height * (1f - fraction)), Size(size.width, size.height * fraction))
            }
        }
        KnobControl("Thresh", threshold, "%.0f".format(threshold) + "dB", onThreshold, valueRange = -30f..0f, color = RVCColors.accentRed)
        KnobControl("Release", release, "%.0f".format(release) + "ms", onRelease, valueRange = 1f..500f, color = RVCColors.accentRed)
    }
}


