package com.rvc.droid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvc.droid.ui.theme.RVCColors
import kotlinx.coroutines.delay

import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalClipboardManager

@Composable
fun TeleprompterOverlay(
    text: String,
    onTextChange: (String) -> Unit,
    fontSize: Int = 24,
    onFontSizeChange: (Int) -> Unit,
    scrollSpeed: Float = 0.3f,
    onScrollSpeedChange: (Float) -> Unit,
    isVisible: Boolean = true,
    onToggleVisibility: () -> Unit = {},
    isRecording: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showSettings by remember { mutableStateOf(false) }
    var autoScroll by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(isRecording, autoScroll, scrollSpeed) {
        if (isRecording && autoScroll && text.isNotEmpty()) {
            while (true) {
                delay(50)
                val step = (scrollSpeed * 3f).toInt().coerceAtLeast(1)
                val newOffset = scrollState.value + step
                if (newOffset <= scrollState.maxValue) {
                    scrollState.scrollTo(newOffset)
                } else {
                    break
                }
            }
        }
    }

    if (!isVisible) return

    Column(
        modifier
            .fillMaxWidth()
            .background(RVCColors.bgSurface, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(RVCColors.bgSurface, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("TESTO / GOBBO", color = RVCColors.accentCyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (!isRecording) {
                OutlinedButton(
                    onClick = {
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrEmpty()) onTextChange(clip)
                    },
                    modifier = Modifier.height(26.dp),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Text("INCOLLA", fontSize = 8.sp, color = RVCColors.accentGreen, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(6.dp))
                Switch(checked = autoScroll, onCheckedChange = { autoScroll = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = RVCColors.accentCyan, checkedThumbColor = RVCColors.accentCyan),
                    modifier = Modifier.scale(0.8f))
                Spacer(Modifier.width(2.dp))
                Text("Scroll", color = RVCColors.textDim, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(6.dp))
            }
            IconButton(onClick = { showSettings = !showSettings }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = if (showSettings) RVCColors.accentCyan else RVCColors.textDim, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onToggleVisibility, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = RVCColors.textDim, modifier = Modifier.size(16.dp))
            }
        }

        if (showSettings) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = RVCColors.bgDeep)) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(28.dp)) {
                        Text("Font", color = RVCColors.textSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(40.dp))
                        Slider(
                            value = (fontSize - 14).toFloat() / 26f,
                            onValueChange = { onFontSizeChange(14 + (it * 26).toInt()) },
                            modifier = Modifier.weight(1f).height(20.dp),
                            colors = SliderDefaults.colors(thumbColor = RVCColors.accentCyan, activeTrackColor = RVCColors.accentCyan)
                        )
                        Text("${fontSize}sp", color = RVCColors.accentCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(42.dp), textAlign = TextAlign.End)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(28.dp)) {
                        Text("Speed", color = RVCColors.textSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(40.dp))
                        Slider(
                            value = scrollSpeed, onValueChange = onScrollSpeedChange,
                            modifier = Modifier.weight(1f).height(20.dp),
                            colors = SliderDefaults.colors(thumbColor = RVCColors.accentGreen, activeTrackColor = RVCColors.accentGreen)
                        )
                        Text("%.0f%%".format(scrollSpeed * 100), color = RVCColors.accentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(42.dp), textAlign = TextAlign.End)
                    }
                }
            }
        }

        if (isRecording && text.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(180.dp).background(Color.Black.copy(alpha = 0.88f)).verticalScroll(scrollState).padding(16.dp)
            ) {
                Text(
                    text,
                    color = Color.White,
                    fontSize = fontSize.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    lineHeight = (fontSize * 1.5f).sp
                )
            }
        } else if (!isRecording) {
            Box(
                modifier = Modifier.fillMaxWidth().height(120.dp).background(RVCColors.bgSurface, RoundedCornerShape(8.dp)).padding(8.dp)
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    textStyle = TextStyle(
                        color = RVCColors.textPrimary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    decorationBox = { innerTextField ->
                        if (text.isEmpty()) {
                            Text("Incolla qui il testo del gobbo...", color = RVCColors.textDim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                        innerTextField()
                    }
                )
            }
        }
    }
}
