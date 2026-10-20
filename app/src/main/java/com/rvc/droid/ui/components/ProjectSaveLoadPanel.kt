package com.rvc.droid.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvc.droid.ui.theme.RVCColors
import java.io.File

data class ProjectInfo(
    val name: String,
    val path: String,
    val date: String,
    val size: String
)

@Composable
fun ProjectSaveLoadPanel(
    currentProjectName: String,
    recentProjects: List<ProjectInfo>,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onLoad: (String) -> Unit,
    onDelete: (String) -> Unit,
    onNewProject: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPanel by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        OutlinedButton(
            onClick = { showPanel = !showPanel },
            modifier = Modifier.fillMaxWidth().height(32.dp),
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, if(showPanel) RVCColors.accentGreen else RVCColors.knobBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = if(showPanel) RVCColors.accentGreen else RVCColors.textSecondary)
        ) {
            Text("PROJECT: $currentProjectName", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }

        if (showPanel) {
            Spacer(Modifier.height(6.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = RVCColors.bgSurface)
            ) {
                Column(Modifier.padding(10.dp)) {
                    // Action buttons row
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = onNewProject, modifier = Modifier.weight(1f).height(30.dp), shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, RVCColors.accentCyan), colors = ButtonDefaults.outlinedButtonColors(contentColor = RVCColors.accentCyan),
                            contentPadding = PaddingValues(horizontal = 6.dp)) { Text("NEW", fontSize = 9.sp, fontFamily = FontFamily.Monospace) }
                        Button(onClick = onSave, modifier = Modifier.weight(1f).height(30.dp), shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RVCColors.accentGreen), contentPadding = PaddingValues(horizontal = 6.dp)
                        ) { Text("SAVE", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = RVCColors.bgDeep) }
                        OutlinedButton(onClick = onSaveAs, modifier = Modifier.weight(1f).height(30.dp), shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, RVCColors.accentGreen), colors = ButtonDefaults.outlinedButtonColors(contentColor = RVCColors.accentGreen),
                            contentPadding = PaddingValues(horizontal = 6.dp)) { Text("SAVE AS", fontSize = 9.sp, fontFamily = FontFamily.Monospace) }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Recent projects list
                    if (recentProjects.isEmpty()) {
                        Text("Nessun progetto recente", color = RVCColors.textDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    } else {
                        Text("PROGETTI RECENTI", color = RVCColors.textDim, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(4.dp))
                        recentProjects.forEach { proj ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(RVCColors.bgCard).clickable { onLoad(proj.path) }.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(proj.name, color = RVCColors.textPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                    Text(proj.date, color = RVCColors.textDim, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                }
                                Text(proj.size, color = RVCColors.textDim, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                Spacer(Modifier.width(8.dp))
                                IconButton(onClick = { onDelete(proj.path) }, modifier = Modifier.size(20.dp)) {
                                    Text("X", color = RVCColors.accentRed.copy(alpha=0.6f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                        }
                    }
                }
            }
        }
    }
}
