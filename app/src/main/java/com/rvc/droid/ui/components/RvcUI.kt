package com.rvc.droid.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvc.droid.ui.theme.RVCColors
import com.rvc.droid.ui.theme.RvcRadius
import com.rvc.droid.ui.theme.RvcSpacing
import com.rvc.droid.ui.theme.RvcTypography

/* ------------------------------------------------------------------ helpers */

fun formatTimecode(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSeconds = safe / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val tenths = (safe % 1000) / 100
    return "%02d:%02d.%d".format(minutes, seconds, tenths)
}

fun formatDurationShort(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val seconds = safe / 1000
    return if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"
}

/* ------------------------------------------------------------------- header */

@Composable
fun RvcScreenHeader(
    title: String,
    subtitle: String? = null,
    kicker: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RvcSpacing.lg, vertical = RvcSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (kicker != null) {
                Text(
                    kicker.uppercase(),
                    style = RvcTypography.labelSmall,
                    color = RVCColors.accentStrong,
                )
            }
            Text(
                title,
                style = RvcTypography.headlineSmall,
                color = RVCColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = RvcTypography.bodySmall,
                    color = RVCColors.textMute,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (actions != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(RvcSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

/* --------------------------------------------------------------------- card */

@Composable
fun RvcCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    accent: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    contentPadding: Int = 14,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RvcRadius.md),
        colors = CardDefaults.cardColors(containerColor = RVCColors.surface),
        border = BorderStroke(1.dp, RVCColors.border),
    ) {
        Column(Modifier.padding(contentPadding.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (accent != null) {
                        Box(
                            Modifier
                                .size(width = 3.dp, height = 14.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(accent)
                        )
                        Spacer(Modifier.width(7.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            title.uppercase(),
                            style = RvcTypography.labelMedium,
                            color = accent ?: RVCColors.textDim,
                            fontWeight = FontWeight.Bold,
                        )
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = RvcTypography.bodySmall,
                                color = RVCColors.textMute,
                            )
                        }
                    }
                    trailing?.invoke()
                }
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

@Composable
fun RvcDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(RVCColors.border)
    )
}

/* ---------------------------------------------------------------- pulsanti */

@Composable
fun RvcButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
    accent: Color = RVCColors.accent,
) {
    val shape = RoundedCornerShape(RvcRadius.sm)
    if (primary) {
        Button(
            onClick = onClick,
            modifier = modifier.height(38.dp),
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = accent,
                contentColor = RVCColors.accentContrast,
                disabledContainerColor = RVCColors.surface3,
                disabledContentColor = RVCColors.textMute,
            ),
        ) {
            ButtonIcon(icon)
            Text(text, style = RvcTypography.labelLarge, maxLines = 1)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(38.dp),
            enabled = enabled,
            shape = shape,
            border = BorderStroke(1.dp, if (enabled) RVCColors.borderStrong else RVCColors.border),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (enabled) RVCColors.textDim else RVCColors.textMute,
            ),
        ) {
            ButtonIcon(icon)
            Text(text, style = RvcTypography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
private fun ButtonIcon(icon: ImageVector?) {
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
    }
}

/* -------------------------------------------------------------------- pill */

@Composable
fun RvcPill(
    text: String,
    color: Color = RVCColors.accent,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(RvcRadius.pill))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(RvcRadius.pill))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            style = RvcTypography.labelSmall,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
fun RvcStatusPill(active: Boolean, activeText: String, inactiveText: String) {
    RvcPill(
        text = if (active) activeText else inactiveText,
        color = if (active) RVCColors.success else RVCColors.textMute,
    )
}

/* ------------------------------------------------------------------ metriche */

@Composable
fun RvcMetricRow(
    label: String,
    value: String,
    valueColor: Color = RVCColors.text,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = RvcTypography.bodySmall,
            color = RVCColors.textMute,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = RvcTypography.labelMedium,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            trailing()
        }
    }
}

/* ------------------------------------------------------------------- slider */

@Composable
fun RvcSliderRow(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    valueText: String? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = RvcTypography.bodySmall,
                color = RVCColors.textDim,
                modifier = Modifier.weight(1f),
            )
            Text(
                valueText ?: formatPercent(value, valueRange),
                style = RvcTypography.labelMedium,
                color = RVCColors.accentStrong,
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.height(26.dp),
            colors = SliderDefaults.colors(
                thumbColor = RVCColors.accentStrong,
                activeTrackColor = RVCColors.accent,
                inactiveTrackColor = RVCColors.sliderTrack,
            ),
        )
    }
}

private fun formatPercent(value: Float, range: ClosedFloatingPointRange<Float>): String {
    return if (range.start >= 0f && range.endInclusive <= 1f) {
        "${(value * 100).toInt()}%"
    } else {
        String.format("%.2f", value)
    }
}

@Composable
fun RvcSwitchRow(
    label: String,
    hint: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = RvcTypography.bodyMedium, color = RVCColors.text)
            if (hint != null) {
                Text(hint, style = RvcTypography.bodySmall, color = RVCColors.textMute)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = RVCColors.accentContrast,
                checkedTrackColor = RVCColors.accent,
                uncheckedThumbColor = RVCColors.textMute,
                uncheckedTrackColor = RVCColors.surface3,
            ),
        )
    }
}

/* ------------------------------------------------------------------ tabs */

@Composable
fun RvcSegmentedTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RvcRadius.sm))
            .background(RVCColors.surface2)
            .border(1.dp, RVCColors.border, RoundedCornerShape(RvcRadius.sm))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        tabs.forEachIndexed { index, title ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(RvcRadius.xs))
                    .background(if (selected) RVCColors.accent else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    title,
                    style = RvcTypography.labelLarge,
                    color = if (selected) RVCColors.accentContrast else RVCColors.textDim,
                    maxLines = 1,
                )
            }
        }
    }
}

/* --------------------------------------------------------------- empty state */

@Composable
fun RvcEmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(RvcSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = RVCColors.textMute, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, style = RvcTypography.titleMedium, color = RVCColors.text)
        Spacer(Modifier.height(4.dp))
        Text(
            text,
            style = RvcTypography.bodySmall,
            color = RVCColors.textMute,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            RvcButton(actionLabel, onAction, primary = true)
        }
    }
}

/* -------------------------------------------------------------- riga azione */

@Composable
fun RvcActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    accent: Color = RVCColors.textDim,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RvcRadius.sm))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 2.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = RvcTypography.bodyMedium, color = RVCColors.text)
            if (subtitle != null) {
                Text(subtitle, style = RvcTypography.bodySmall, color = RVCColors.textMute, maxLines = 1)
            }
        }
        if (trailing != null) trailing() else if (onClick != null) {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = RVCColors.textMute, modifier = Modifier.size(18.dp))
        }
    }
}

/** Etichetta di sezione usata nei pannelli (uppercase, tracking largo). */
@Composable
fun RvcSectionLabel(text: String, color: Color = RVCColors.textMute) {
    Text(
        text.uppercase(),
        style = RvcTypography.labelSmall,
        color = color,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.2.sp,
    )
}
