package com.rvc.droid.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.PathEffect
import com.rvc.droid.ui.theme.RVCColors

data class NoiseGateState(
    val enabled: Boolean = false,
    val threshold: Float = 0.05f,    // 0-1 normalized
    val attackMs: Float = 1f,        // ms
    val holdMs: Float = 50f,         // ms
    val releaseMs: Float = 100f      // ms
)

@Composable
fun NoiseGatePanel(
    gate: NoiseGateState,
    onToggle: () -> Unit = {},
    onThreshold: (Float) -> Unit = {},
    onAttack: (Float) -> Unit = {},
    onHold: (Float) -> Unit = {},
    onRelease: (Float) -> Unit = {},
    gainReduction: Float = 0f,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = RVCColors.bgSurface)
    ) {
        Column(Modifier.padding(8.dp)) {
            // Header con toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "NOISE GATE",
                    color = if (gate.enabled) RVCColors.accentYellow else RVCColors.textDim,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = gate.enabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = RVCColors.accentYellow,
                        uncheckedTrackColor = RVCColors.knobBg
                    ),
                    modifier = Modifier.height(20.dp)
                )
                Spacer(Modifier.weight(1f))

                // Gain reduction indicator
                if (gate.enabled && gainReduction > 0.5f) {
                    Text(
                        "GATE: -%.0fdB".format(gainReduction.coerceAtMost(60f)),
                        color = RVCColors.accentYellow,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            if (gate.enabled) {
                // Threshold slider
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(24.dp)) {
                    Text("Threshold", color = RVCColors.textSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(56.dp))
                    Slider(
                        value = gate.threshold,
                        onValueChange = onThreshold,
                        valueRange = 0f..0.5f,
                        modifier = Modifier.weight(1f).height(16.dp),
                        colors = SliderDefaults.colors(thumbColor = RVCColors.accentYellow, activeTrackColor = RVCColors.accentYellow, inactiveTrackColor = RVCColors.sliderTrack)
                    )
                    Text("%.0f%%".format(gate.threshold * 100), color = RVCColors.accentYellow, fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(36.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    KnobControl("Attack", gate.attackMs, "%.1fms".format(gate.attackMs), onAttack, valueRange = 0.1f..50f, color = RVCColors.accentYellow)
                    KnobControl("Hold", gate.holdMs, "%.0fms".format(gate.holdMs), onHold, valueRange = 1f..500f, color = RVCColors.accentYellow)
                    KnobControl("Release", gate.releaseMs, "%.0fms".format(gate.releaseMs), onRelease, valueRange = 1f..1000f, color = RVCColors.accentYellow)

                    Spacer(Modifier.width(8.dp))

                    // Gate visualization
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(RVCColors.bgDeep)
                            .border(1.dp, RVCColors.knobBorder, RoundedCornerShape(4.dp))
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width; val h = size.height
                            val threshY = h * (1f - gate.threshold.coerceIn(0f, 1f) * 2f).coerceIn(0f, h)
                            drawRect(RVCColors.meterBg, Offset.Zero, size)
                            // Open zone (above threshold)
                            drawRect(RVCColors.accentGreen.copy(alpha = 0.15f), Offset(0f, 0f), Size(w, threshY))
                            // Closed zone (below threshold)
                            drawRect(RVCColors.accentRed.copy(alpha = 0.1f), Offset(0f, threshY), Size(w, h - threshY))
                            // Threshold line
                            drawLine(RVCColors.accentYellow.copy(alpha = 0.6f), Offset(0f, threshY), Offset(w, threshY), strokeWidth = 1f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 3f)))
                            if (gainReduction > 1f) {
                                val grH = (gainReduction / 60f * h).coerceIn(0f, h)
                                drawRect(RVCColors.accentYellow.copy(alpha = 0.4f), Offset(w * 0.7f, h - grH), Size(w * 0.2f, grH))
                            }
                        }
                    }
                }
            } else {
                Text(
                    "Attiva per rimuovere rumore di fondo, fruscii e respiri sotto soglia",
                    color = RVCColors.textDim,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}


