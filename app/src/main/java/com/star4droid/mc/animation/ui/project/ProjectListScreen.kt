package com.star4droid.mc.animation.ui.project

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.star4droid.mc.animation.project.ProjectMetadata
import com.star4droid.mc.animation.project.ProjectRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    onOpenProject: (String) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { ProjectRepository(context) }
    var projects by remember { mutableStateOf<List<ProjectMetadata>>(emptyList()) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var projectToRename by remember { mutableStateOf<ProjectMetadata?>(null) }
    var projectToDelete by remember { mutableStateOf<ProjectMetadata?>(null) }

    fun refreshProjects() {
        var list = repository.listProjects()
        if (list.isEmpty()) {
            // First time launch: auto-create initial sample project!
            repository.createProject("Steve's Adventure", "steve")
            list = repository.listProjects()
        }
        projects = list
    }

    LaunchedEffect(Unit) {
        refreshProjects()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF22C55E), Color(0xFF15803D))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "MC",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Minecraft Animator",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color(0xFFE2E8F0)
                            )
                            Text(
                                "3D Keyframe & Rigging Studio",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = Color(0xFF22C55E),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.testTag("create_project_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Create New Project")
            }
        },
        containerColor = Color(0xFF0F172A)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "My Projects (${projects.size})",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = Color(0xFFCBD5E1),
                modifier = Modifier.padding(vertical = 12.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(projects, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        onClick = { onOpenProject(project.id) },
                        onRename = { projectToRename = project },
                        onDuplicate = {
                            repository.duplicateProject(project.id)
                            refreshProjects()
                        },
                        onDelete = { projectToDelete = project }
                    )
                }
            }
        }
    }

    // Create Project Dialog
    if (showCreateDialog) {
        var newName by remember { mutableStateOf("") }
        var selectedTemplate by remember { mutableStateOf("steve") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Project", color = Color(0xFFE2E8F0)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Project Name") },
                        placeholder = { Text("e.g. Sword Fight Animation") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_project_name_input")
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Starting Template:", fontSize = 13.sp, color = Color(0xFF94A3B8))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { selectedTemplate = "steve" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedTemplate == "steve") Color(0xFF22C55E) else Color(0xFF1E293B)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Steve Scene", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { selectedTemplate = "blank" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedTemplate == "blank") Color(0xFF22C55E) else Color(0xFF1E293B)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Blank Scene", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val nameToUse = if (newName.isNotBlank()) newName.trim() else "Animation ${projects.size + 1}"
                        val newId = repository.createProject(nameToUse, selectedTemplate)
                        showCreateDialog = false
                        refreshProjects()
                        onOpenProject(newId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                    modifier = Modifier.testTag("confirm_create_project_button")
                ) {
                    Text("Create & Open")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // Rename Dialog
    projectToRename?.let { proj ->
        var renameText by remember { mutableStateOf(proj.name) }
        AlertDialog(
            onDismissRequest = { projectToRename = null },
            title = { Text("Rename Project", color = Color(0xFFE2E8F0)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            repository.renameProject(proj.id, renameText.trim())
                            refreshProjects()
                        }
                        projectToRename = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToRename = null }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // Delete Confirmation
    projectToDelete?.let { proj ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Delete Project", color = Color(0xFFE2E8F0)) },
            text = {
                Text("Are you sure you want to delete \"${proj.name}\"? This action cannot be undone.", color = Color(0xFF94A3B8))
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.deleteProject(proj.id)
                        projectToDelete = null
                        refreshProjects()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}

@Composable
fun ProjectCard(
    project: ProjectMetadata,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val formattedDate = remember(project.updatedAt) {
        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        sdf.format(Date(project.updatedAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("project_card_${project.id}"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Stylized 3D Project Thumbnail Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF334155), Color(0xFF0F172A))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Voxel block representation
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF22C55E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⛏️", fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${project.nodeCount} Objects",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        project.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFFF1F5F9),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        formattedDate,
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "Menu",
                            tint = Color(0xFF94A3B8)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(Color(0xFF1E293B))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open", color = Color(0xFFE2E8F0)) },
                            leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color(0xFF22C55E)) },
                            onClick = { menuExpanded = false; onClick() }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename", color = Color(0xFFE2E8F0)) },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null, tint = Color(0xFF60A5FA)) },
                            onClick = { menuExpanded = false; onRename() }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate", color = Color(0xFFE2E8F0)) },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, tint = Color(0xFFF59E0B)) },
                            onClick = { menuExpanded = false; onDuplicate() }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = Color(0xFFEF4444)) },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = Color(0xFFEF4444)) },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}
