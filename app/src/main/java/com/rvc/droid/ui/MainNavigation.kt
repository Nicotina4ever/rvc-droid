package com.rvc.droid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvc.droid.ui.components.RvcPill
import com.rvc.droid.ui.theme.RVCColors
import com.rvc.droid.ui.theme.RvcRadius
import com.rvc.droid.ui.theme.RvcSizes
import com.rvc.droid.ui.theme.RvcSpacing
import com.rvc.droid.ui.theme.RvcTypography

/**
 * Le sei aree di lavoro della DAW. L'ordine segue il flusso reale:
 * registra -> monta -> mixa -> video -> gobbo -> cloud/voce.
 */
enum class DAWTab(val title: String, val icon: ImageVector) {
    STUDIO("Studio", Icons.Filled.Mic),
    EDITOR("Timeline", Icons.Filled.GraphicEq),
    MIXER("Mixer", Icons.Filled.Tune),
    VIDEO("Video", Icons.Filled.Movie),
    PROMPTER("Gobbo", Icons.Filled.Article),
    CLOUD("Cloud", Icons.Filled.Cloud),
}

@Composable
fun DAWBottomNavigation(
    selectedTab: DAWTab,
    onTabSelected: (DAWTab) -> Unit,
) {
    Surface(
        color = RVCColors.bgElevated,
        contentColor = RVCColors.text,
        shadowElevation = 12.dp,
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(RVCColors.border)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                DAWTab.entries.forEach { tab ->
                    val selected = selectedTab == tab
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { onTabSelected(tab) }
                            .padding(vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(width = 38.dp, height = 26.dp)
                                .background(
                                    if (selected) RVCColors.accentSoft else Color.Transparent,
                                    RoundedCornerShape(RvcRadius.sm),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                tab.icon,
                                contentDescription = tab.title,
                                tint = if (selected) RVCColors.accentStrong else RVCColors.textMute,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            tab.title,
                            style = RvcTypography.labelSmall,
                            color = if (selected) RVCColors.accentStrong else RVCColors.textMute,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DAWTopBar(
    projectName: String,
    isRecording: Boolean,
    statusLabel: String,
    statusColor: Color,
    onSave: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = RVCColors.bgElevated,
        contentColor = RVCColors.text,
        modifier = modifier,
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(RVCColors.border)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(RvcSizes.topBar)
                    .padding(horizontal = RvcSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .background(
                            Brush.linearGradient(listOf(RVCColors.accent, RVCColors.accent2)),
                            RoundedCornerShape(10.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "RVC",
                        color = RVCColors.accentContrast,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "RVC PORTABLE",
                        style = RvcTypography.labelSmall,
                        color = RVCColors.textMute,
                        letterSpacing = 1.6.sp,
                    )
                    Text(
                        projectName.ifBlank { "Nuovo progetto" },
                        style = RvcTypography.titleMedium,
                        color = RVCColors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isRecording) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(RVCColors.danger, CircleShape)
                        )
                        Spacer(Modifier.width(5.dp))
                        RvcPill("REC", RVCColors.danger)
                    }
                } else {
                    RvcPill(statusLabel, statusColor)
                }
                Spacer(Modifier.width(2.dp))
                IconButton(onClick = onSave, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Filled.Save,
                        contentDescription = "Salva progetto",
                        tint = RVCColors.textDim,
                        modifier = Modifier.size(19.dp),
                    )
                }
                IconButton(onClick = onSettings, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Impostazioni",
                        tint = RVCColors.textDim,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(RVCColors.border)
            )
        }
    }
}
