package com.star4droid.mc.animation.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.star4droid.mc.animation.engine.gizmo.EditorMode
import com.star4droid.mc.animation.engine.scene.TimeOfDay
import com.star4droid.mc.animation.ui.assets.AssetBrowserSheet
import com.star4droid.mc.animation.ui.hierarchy.HierarchyPanel
import com.star4droid.mc.animation.ui.inspector.InspectorPanel
import com.star4droid.mc.animation.ui.timeline.TimelinePanel

@Composable
fun EditorScreen(
    projectId: String,
    onBack: () -> Unit,
    viewModel: EditorViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    var addMenuOpen by remember { mutableStateOf(false) }
    var timeOfDayMenuOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B101B))) {
        // 1. 3D Viewport
        Viewport3D(viewModel = viewModel)

        // 2. Top Bar Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xCC0F172A))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Back & Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFFE2E8F0))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        uiState.projectName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Text(
                        if (uiState.isSceneCameraActive) "🎥 Scene Camera View" else "Orbit Editor Camera",
                        fontSize = 10.sp,
                        color = if (uiState.isSceneCameraActive) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                    )
                }
            }

            // Middle: Mode Switcher
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E293B))
                    .padding(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModeButton("👆", "Select", uiState.editorMode == EditorMode.SELECT) {
                    viewModel.setEditorMode(EditorMode.SELECT)
                }
                ModeButton("↔️", "Move", uiState.editorMode == EditorMode.MOVE) {
                    viewModel.setEditorMode(EditorMode.MOVE)
                }
                ModeButton("🔄", "Rotate", uiState.editorMode == EditorMode.ROTATE) {
                    viewModel.setEditorMode(EditorMode.ROTATE)
                }
                ModeButton("📐", "Scale", uiState.editorMode == EditorMode.SCALE) {
                    viewModel.setEditorMode(EditorMode.SCALE)
                }
                ModeButton("🎥", "Camera", uiState.editorMode == EditorMode.CAMERA) {
                    viewModel.setEditorMode(EditorMode.CAMERA)
                }
            }

            // Right Actions: Add (+), Undo/Redo, Sun/TimeOfDay, Assets, Save
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Time of Day
                Box {
                    IconButton(
                        onClick = { timeOfDayMenuOpen = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text(
                            when (uiState.timeOfDay) {
                                TimeOfDay.MORNING -> "🌅"
                                TimeOfDay.NOON -> "☀️"
                                TimeOfDay.EVENING -> "🌇"
                                TimeOfDay.NIGHT -> "🌙"
                            },
                            fontSize = 16.sp
                        )
                    }
                    DropdownMenu(
                        expanded = timeOfDayMenuOpen,
                        onDismissRequest = { timeOfDayMenuOpen = false },
                        modifier = Modifier.background(Color(0xFF1E293B))
                    ) {
                        DropdownMenuItem(
                            text = { Text("🌅 Morning Dawn", color = Color.White) },
                            onClick = { timeOfDayMenuOpen = false; viewModel.setTimeOfDay(TimeOfDay.MORNING) }
                        )
                        DropdownMenuItem(
                            text = { Text("☀️ Bright Noon", color = Color.White) },
                            onClick = { timeOfDayMenuOpen = false; viewModel.setTimeOfDay(TimeOfDay.NOON) }
                        )
                        DropdownMenuItem(
                            text = { Text("🌇 Golden Sunset", color = Color.White) },
                            onClick = { timeOfDayMenuOpen = false; viewModel.setTimeOfDay(TimeOfDay.EVENING) }
                        )
                        DropdownMenuItem(
                            text = { Text("🌙 Midnight Dark", color = Color.White) },
                            onClick = { timeOfDayMenuOpen = false; viewModel.setTimeOfDay(TimeOfDay.NIGHT) }
                        )
                    }
                }

                // Add Object Menu
                Box {
                    IconButton(
                        onClick = { addMenuOpen = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Object", tint = Color(0xFF22C55E))
                    }
                    DropdownMenu(
                        expanded = addMenuOpen,
                        onDismissRequest = { addMenuOpen = false },
                        modifier = Modifier.background(Color(0xFF1E293B))
                    ) {
                        DropdownMenuItem(
                            text = { Text("🧱 Place Block", color = Color.White) },
                            onClick = { addMenuOpen = false; viewModel.addBlock() }
                        )
                        DropdownMenuItem(
                            text = { Text("🧑 Add Steve Character", color = Color.White) },
                            onClick = { addMenuOpen = false; viewModel.addCharacter(isAlex = false) }
                        )
                        DropdownMenuItem(
                            text = { Text("👩 Add Alex Character", color = Color.White) },
                            onClick = { addMenuOpen = false; viewModel.addCharacter(isAlex = true) }
                        )
                        DropdownMenuItem(
                            text = { Text("🎥 Add Scene Camera", color = Color.White) },
                            onClick = { addMenuOpen = false; viewModel.addCamera() }
                        )
                        DropdownMenuItem(
                            text = { Text("☀️ Add Sun Light", color = Color.White) },
                            onClick = { addMenuOpen = false; viewModel.addLight() }
                        )
                    }
                }

                // Undo / Redo
                IconButton(
                    onClick = { viewModel.undo() },
                    enabled = uiState.canUndo,
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("↩️", fontSize = 14.sp, color = if (uiState.canUndo) Color.White else Color(0xFF475569))
                }
                IconButton(
                    onClick = { viewModel.redo() },
                    enabled = uiState.canRedo,
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("↪️", fontSize = 14.sp, color = if (uiState.canRedo) Color.White else Color(0xFF475569))
                }

                // Asset Browser
                IconButton(
                    onClick = { viewModel.toggleAssetBrowser() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("🎨", fontSize = 15.sp)
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Save button
                Button(
                    onClick = { viewModel.saveProject() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Save", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 3. Floating Viewport Navigation Overlay
        // Camera Fly Joystick & Elevation Controls (Bottom-Left)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = if (uiState.isTimelineOpen) 220.dp else 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Camera fly joystick
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x880F172A))
                        .padding(4.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.camera.moveFly(1f, 0f, 0f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("▲", color = Color.White, fontSize = 12.sp)
                    }
                    Row {
                        IconButton(
                            onClick = { viewModel.camera.moveFly(0f, -1f, 0f) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("◀", color = Color.White, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = { viewModel.camera.moveFly(0f, 1f, 0f) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("▶", color = Color.White, fontSize = 12.sp)
                        }
                    }
                    IconButton(
                        onClick = { viewModel.camera.moveFly(-1f, 0f, 0f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("▼", color = Color.White, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Up / Down elevation buttons
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x880F172A))
                        .padding(4.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.camera.moveFly(0f, 0f, 1f) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Up", tint = Color.White)
                    }
                    IconButton(
                        onClick = { viewModel.camera.moveFly(0f, 0f, -1f) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Down", tint = Color.White)
                    }
                }
            }
        }

        // Floating Panel Toggle Bar (Top-Right under Top Bar)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 54.dp, end = 12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xAA1E293B))
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PanelToggleButton("🗂️", "Hierarchy", uiState.isHierarchyOpen) { viewModel.toggleHierarchy() }
            Spacer(modifier = Modifier.width(4.dp))
            PanelToggleButton("⚙️", "Inspector", uiState.isInspectorOpen) { viewModel.toggleInspector() }
            Spacer(modifier = Modifier.width(4.dp))
            PanelToggleButton("⏱️", "Timeline", uiState.isTimelineOpen) { viewModel.toggleTimeline() }
        }

        // 4. Collapsible Panels
        // Left Hierarchy Panel
        AnimatedVisibility(
            visible = uiState.isHierarchyOpen,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 50.dp)
        ) {
            HierarchyPanel(
                sceneGraph = viewModel.sceneGraph,
                selectedNodeId = uiState.selectedNodeId,
                onSelectNode = { viewModel.selectNode(it) },
                onDeleteNode = { viewModel.deleteSelectedNode() },
                onDuplicateNode = { viewModel.duplicateSelectedNode() },
                onClose = { viewModel.toggleHierarchy() }
            )
        }

        // Right Inspector Panel
        AnimatedVisibility(
            visible = uiState.isInspectorOpen,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 90.dp)
        ) {
            val selectedNode = uiState.selectedNodeId?.let { viewModel.sceneGraph.getNode(it) }
            InspectorPanel(
                node = selectedNode,
                onUpdateTransform = { newT ->
                    uiState.selectedNodeId?.let { viewModel.updateNodeTransform(it, newT) }
                },
                onAddKeyframe = { path, v ->
                    uiState.selectedNodeId?.let { viewModel.addKeyframe(it, path, v) }
                },
                onKeyframeAll = {
                    uiState.selectedNodeId?.let { viewModel.keyframeAllTransform(it) }
                },
                onApplyPreset = { preset -> viewModel.applyPreset(preset) },
                onUpdateMaterial = { texId, op ->
                    uiState.selectedNodeId?.let { viewModel.updateNodeMaterial(it, texId, op) }
                },
                onClose = { viewModel.toggleInspector() }
            )
        }

        // Bottom Timeline Panel
        AnimatedVisibility(
            visible = uiState.isTimelineOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            TimelinePanel(
                timeline = viewModel.getActiveTimeline(),
                allTimelines = viewModel.timelines,
                currentTime = uiState.currentTime,
                isPlaying = uiState.isPlaying,
                isLooping = uiState.isLooping,
                selectedNodeId = uiState.selectedNodeId,
                onPlayPause = { viewModel.togglePlayPause() },
                onSeek = { viewModel.seekTo(it) },
                onReset = { viewModel.resetToStart() },
                onToggleLoop = { viewModel.toggleLoop() },
                onSelectTimeline = { viewModel.switchTimeline(it) },
                onCreateTimeline = { viewModel.createNewTimeline(it) },
                onDeleteKeyframe = { trackId, kfId -> viewModel.deleteKeyframe(trackId, kfId) },
                onUpdateKeyframeInterp = { trackId, kfId, interp -> viewModel.updateKeyframeInterpolation(trackId, kfId, interp) },
                onClose = { viewModel.toggleTimeline() }
            )
        }

        // Asset Browser Sheet
        if (uiState.isAssetBrowserOpen) {
            AssetBrowserSheet(
                onDismiss = { viewModel.toggleAssetBrowser() },
                onApplyTexture = { texId ->
                    uiState.selectedNodeId?.let { viewModel.updateNodeMaterial(it, texId) }
                },
                onAddBlockWithTexture = { texId ->
                    viewModel.addBlock(texId)
                }
            )
        }

        // Save confirmation toast
        uiState.saveMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF22C55E))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(msg, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun ModeButton(
    icon: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) Color(0xFF22C55E) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 13.sp)
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                label,
                fontSize = 11.sp,
                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun PanelToggleButton(
    icon: String,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isActive) Color(0xFF3B82F6) else Color(0xFF334155))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                label,
                fontSize = 11.sp,
                color = if (isActive) Color.White else Color(0xFFCBD5E1),
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
