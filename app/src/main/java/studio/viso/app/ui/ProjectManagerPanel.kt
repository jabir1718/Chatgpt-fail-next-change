package com.creatorstudio.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creatorstudio.app.editor.ProjectSummary

@Composable
fun ProjectManagerPanel(
    projectName: String,
    projects: List<ProjectSummary>,
    onNew: () -> Unit,
    onRename: (String) -> Unit,
    onOpen: (String) -> Unit,
    onRefresh: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var showOpen by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var renameValue by remember(projectName) { mutableStateOf(projectName) }

    Column(
        Modifier.fillMaxWidth().padding(bottom = 12.dp)
            .background(Color(0xFF141A29), RoundedCornerShape(16.dp)).padding(12.dp)
    ) {
        Text("PROJECT", color = Color(0xFF34D8C5), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(projectName, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = onNew, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text("New", fontSize = 10.sp)
            }
            OutlinedButton(onClick = { showOpen = true; onRefresh() }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text("Open", fontSize = 10.sp)
            }
            OutlinedButton(onClick = { renameValue = projectName; showRename = true }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text("Rename", fontSize = 10.sp)
            }
            OutlinedButton(onClick = onDuplicate, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text("Copy", fontSize = 10.sp)
            }
            OutlinedButton(onClick = { showDelete = true }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text("Delete", fontSize = 10.sp, color = Color(0xFFFF7790))
            }
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename project") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    label = { Text("Project name") }
                )
            },
            confirmButton = {
                TextButton(onClick = { onRename(renameValue); showRename = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } }
        )
    }

    if (showOpen) {
        AlertDialog(
            onDismissRequest = { showOpen = false },
            title = { Text("Open project") },
            text = {
                if (projects.isEmpty()) {
                    Text("No saved projects yet.")
                } else {
                    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        projects.forEach { project ->
                            Column(
                                Modifier.fillMaxWidth().clickable {
                                    onOpen(project.id)
                                    showOpen = false
                                }.padding(vertical = 9.dp)
                            ) {
                                Text(project.name, color = Color(0xFF172033), fontWeight = FontWeight.SemiBold)
                                Text("Updated ${project.updatedAtEpochMs}", color = Color.Gray, fontSize = 10.sp)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showOpen = false }) { Text("Close") } }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete this project?") },
            text = { Text("The saved project file will be deleted. Imported media files are not deleted.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDelete = false }) { Text("Delete", color = Color(0xFFCF334E)) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } }
        )
    }
}
