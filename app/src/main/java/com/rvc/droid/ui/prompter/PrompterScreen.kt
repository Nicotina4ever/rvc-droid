package com.rvc.droid.ui.prompter

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rvc.droid.ui.components.RvcButton
import com.rvc.droid.ui.components.RvcCard
import com.rvc.droid.ui.components.RvcPill
import com.rvc.droid.ui.components.RvcScreenHeader
import com.rvc.droid.ui.components.RvcSliderRow
import com.rvc.droid.ui.components.RvcSwitchRow
import com.rvc.droid.ui.theme.RVCColors
import com.rvc.droid.ui.theme.RvcRadius
import com.rvc.droid.ui.theme.RvcSpacing
import com.rvc.droid.ui.theme.RvcTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Gobbo / teleprompter professionale con gestione script.
 * Il testo scorre automaticamente durante la registrazione; modalita' specchio
 * per i supporti con vetro a 45 gradi.
 */
@Composable
fun PrompterScreen(
    modifier: Modifier = Modifier,
    text: String,
    onTextChange: (String) -> Unit,
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    scrollSpeed: Float,
    onScrollSpeedChange: (Float) -> Unit,
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var autoScroll by remember { mutableStateOf(PrompterStore.savedAutoScroll(context)) }
    var mirror by remember { mutableStateOf(PrompterStore.savedMirror(context)) }
    var showFullscreen by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showLoadMenu by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(true) }
    var scriptName by remember { mutableStateOf(PrompterStore.lastName(context)) }
    var scripts by remember { mutableStateOf(PrompterStore.list(context)) }
    val scrollState = rememberScrollState()

    // Ripristina l'ultimo script se il progetto ne e' privo.
    LaunchedEffect(Unit) {
        if (text.isBlank()) {
            val saved = PrompterStore.lastText(context)
            if (saved.isNotBlank()) onTextChange(saved)
        }
    }
    LaunchedEffect(autoScroll, scrollSpeed, text, fontSize) {
        if (autoScroll && text.isNotBlank()) {
            while (true) {
                delay(40)
                val step = (scrollSpeed * 3f).toInt().coerceAtLeast(1)
                if (scrollState.value < scrollState.maxValue) {
                    scrollState.scrollTo((scrollState.value + step).coerceAtMost(scrollState.maxValue))
                } else {
                    break
                }
            }
        }
    }
    LaunchedEffect(text) { PrompterStore.saveLast(context, text, scriptName) }
    LaunchedEffect(fontSize, scrollSpeed, mirror) {
        PrompterStore.saveControls(context, fontSize, scrollSpeed, mirror)
    }
    LaunchedEffect(autoScroll) { PrompterStore.saveAutoScroll(context, autoScroll) }

    Column(modifier.fillMaxSize().imePadding()) {
        RvcScreenHeader(
            kicker = "Teleprompter",
            title = "Gobbo",
            subtitle = "Testo grande, scroll automatico e specchio: registra lo spot senza staccare gli occhi.",
            actions = {
                IconButton(onClick = { showFullscreen = true }) {
                    Icon(Icons.Filled.Fullscreen, contentDescription = "Schermo intero", tint = RVCColors.textDim)
                }
                IconButton(onClick = onToggleRecording) {
                    Icon(
                        if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (isRecording) "Ferma" else "Registra",
                        tint = RVCColors.danger,
                    )
                }
            },
        )

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = RvcSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(RvcSpacing.md),
        ) {
            // ------------------------------------------------ anteprima gobbo
            RvcCard(
                title = if (scriptName.isBlank()) "Script corrente" else scriptName,
                accent = RVCColors.accent,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isRecording) RvcPill("REC", RVCColors.danger)
                        Spacer(Modifier.width(6.dp))
                        RvcPill(if (mirror) "SPECCHIO" else "NORMALE", RVCColors.textMute)
                    }
                },
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(RvcRadius.sm))
                        .background(RVCColors.bg)
                        .padding(horizontal = 16.dp),
                ) {
                    if (text.isBlank()) {
                        Text(
                            "Scrivi o carica uno script qui sotto.",
                            style = RvcTypography.bodyMedium,
                            color = RVCColors.textMute,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        Text(
                            text = text,
                            color = RVCColors.text,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * 1.45f).sp,
                            fontFamily = FontFamily.SansSerif,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .graphicsLayer(scaleX = if (mirror) -1f else 1f)
                                .verticalScroll(scrollState)
                                .padding(vertical = 10.dp),
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RvcButton(
                        text = if (autoScroll) "Pausa" else "Avvia",
                        onClick = { autoScroll = !autoScroll },
                        icon = if (autoScroll) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        primary = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { autoScroll = false; coroutineScope.launch { scrollState.scrollTo(0) } }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Torna su", tint = RVCColors.textDim)
                    }
                    IconButton(onClick = { mirror = !mirror }) {
                        Icon(
                            Icons.Filled.Flip,
                            contentDescription = "Specchio",
                            tint = if (mirror) RVCColors.accentStrong else RVCColors.textDim,
                        )
                    }
                    IconButton(onClick = { onFontSizeChange((fontSize - 2).coerceAtLeast(14)) }) {
                        Text("A-", color = RVCColors.textDim, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { onFontSizeChange((fontSize + 2).coerceAtMost(64)) }) {
                        Text("A+", color = RVCColors.textDim, fontWeight = FontWeight.Bold)
                    }
                    RvcPill("$fontSize pt", RVCColors.textMute)
                }

                RvcSliderRow(
                    label = "Velocita' scorrimento",
                    value = scrollSpeed,
                    onChange = onScrollSpeedChange,
                    valueRange = 0.05f..1.2f,
                    valueText = "%.2fx".format(scrollSpeed),
                )
            }

            // --------------------------------------------------- gestione script
            RvcCard(title = "Script", accent = RVCColors.accent2) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(RvcSpacing.sm),
                ) {
                    RvcButton(
                        text = "Salva",
                        onClick = { showSaveDialog = true },
                        icon = Icons.Filled.Save,
                        modifier = Modifier.weight(1f),
                    )
                    RvcButton(
                        text = "Apri",
                        onClick = {
                            scripts = PrompterStore.list(context)
                            showLoadMenu = true
                        },
                        icon = Icons.Filled.FolderOpen,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        DropdownMenu(expanded = showLoadMenu, onDismissRequest = { showLoadMenu = false }) {
                            if (scripts.isEmpty()) {
                                DropdownMenuItem(text = { Text("Nessuno script") }, onClick = { showLoadMenu = false })
                            }
                            scripts.forEach { file ->
                                DropdownMenuItem(
                                    text = { Text(file.nameWithoutExtension) },
                                    onClick = {
                                        scriptName = file.nameWithoutExtension
                                        onTextChange(PrompterStore.load(file))
                                        showLoadMenu = false
                                    },
                                )
                            }
                        }
                    }
                    RvcButton(
                        text = "Nuovo",
                        onClick = {
                            scriptName = PrompterStore.defaultName()
                            onTextChange("")
                            autoScroll = false
                            coroutineScope.launch { scrollState.scrollTo(0) }
                        },
                        icon = Icons.Filled.Add,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.TextFields, contentDescription = null, tint = RVCColors.textMute, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (scriptName.isBlank()) "Script non salvato" else "Script: $scriptName",
                        style = RvcTypography.bodySmall,
                        color = RVCColors.textMute,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showEditor = !showEditor }) {
                        Text(if (showEditor) "Nascondi editor" else "Modifica testo", color = RVCColors.accentStrong)
                    }
                }

                if (showEditor) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        textStyle = TextStyle(
                            color = RVCColors.text,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.SansSerif,
                        ),
                        placeholder = {
                            Text(
                                "Incolla qui il testo dello spot...",
                                color = RVCColors.textMute,
                                style = RvcTypography.bodyMedium,
                            )
                        },
                        shape = RoundedCornerShape(RvcRadius.sm),
                    )
                }
            }

            // --------------------------------------------------------- extra
            RvcCard(title = "Registrazione", accent = RVCColors.success) {
                RvcSwitchRow(
                    label = "Scroll automatico",
                    hint = "Parte da solo quando registri",
                    checked = autoScroll,
                    onCheckedChange = { autoScroll = it },
                )
                RvcSwitchRow(
                    label = "Modalita' specchio",
                    hint = "Per gobbo con vetro semiriflettente",
                    checked = mirror,
                    onCheckedChange = { mirror = it },
                )
                Spacer(Modifier.height(6.dp))
                RvcButton(
                    text = if (isRecording) "STOP REGISTRAZIONE" else "REGISTRA CON GOBBO",
                    onClick = onToggleRecording,
                    primary = true,
                    accent = if (isRecording) RVCColors.danger else RVCColors.accent,
                    icon = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    // ---------------------------------------------------- dialog salva script
    if (showSaveDialog) {
        var draftName by remember { mutableStateOf(scriptName.ifBlank { PrompterStore.defaultName() }) }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Salva script") },
            text = {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    label = { Text("Nome script") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val file = PrompterStore.save(context, draftName, text)
                    scriptName = file.nameWithoutExtension
                    scripts = PrompterStore.list(context)
                    showSaveDialog = false
                }) { Text("Salva") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Annulla") }
            },
        )
    }

    // ----------------------------------------------------- modalita' schermo
    if (showFullscreen) {
        Dialog(
            onDismissRequest = { showFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            val fullScroll = rememberScrollState()
            var fullAuto by remember { mutableStateOf(true) }
            var fullMirror by remember { mutableStateOf(mirror) }
            LaunchedEffect(fullAuto) {
                if (fullAuto) {
                    while (true) {
                        delay(40)
                        val step = (scrollSpeed * 3f).toInt().coerceAtLeast(1)
                        if (fullScroll.value < fullScroll.maxValue) {
                            fullScroll.scrollTo((fullScroll.value + step).coerceAtMost(fullScroll.maxValue))
                        } else break
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { showFullscreen = false })
                    },
            ) {
                Text(
                    text = text.ifBlank { "Nessuno script" },
                    color = Color.White,
                    fontSize = (fontSize + 10).sp,
                    lineHeight = ((fontSize + 10) * 1.5f).sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(scaleX = if (fullMirror) -1f else 1f)
                        .verticalScroll(fullScroll)
                        .padding(horizontal = 20.dp, vertical = 40.dp),
                )
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { fullAuto = !fullAuto }) {
                        Icon(
                            if (fullAuto) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = { fullMirror = !fullMirror }) {
                        Icon(Icons.Filled.Flip, contentDescription = null, tint = Color.White)
                    }
                    IconButton(onClick = { showFullscreen = false }) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
                    }
                }
                Text(
                    "Doppio tap per uscire",
                    color = Color(0x99FFFFFF),
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(10.dp),
                )
            }
        }
    }
}
