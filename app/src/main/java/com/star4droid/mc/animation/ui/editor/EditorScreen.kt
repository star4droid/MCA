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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.star4droid.mc.animation.engine.gizmo.EditorMode
import com.star4droid.mc.animation.engine.scene.TimeOfDay
import com.star4droid.mc.animation.ui.ai.SideAiDialog
import com.star4droid.mc.animation.ui.assets.AssetBrowserSheet
import com.star4droid.mc.animation.ui.files.FileBrowserDialog
import com.star4droid.mc.animation.ui.hierarchy.HierarchyPanel
import com.star4droid.mc.animation.ui.inspector.InspectorPanel
import com.star4droid.mc.animation.ui.scene.SceneManagerDialog
import com.star4droid.mc.animation.ui.timeline.TimelinePanel
import com.star4droid.mc.animation.ui.world.WorldBuildingOverlay

@Composable
fun EditorScreen(
    projectId: String,
    onBack: () -> Unit,
    onToggleOrientation: () -> Unit = {},
    viewModel: EditorViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    var addMenuOpen by remember { mutableStateOf(false) }
    var timeOfDayMenuOpen by remember { mutableStateOf(false) }
    val topBarScrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B101B))) {
        // 1. 3D Viewport
        Viewport3D(viewModel = viewModel)

        // 2. World Building Mode Overlay (If enabled: all other UI hidden!)
        if (uiState.isWorldBuildingMode) {
            WorldBuildingOverlay(
                activeTool = uiState.worldBuildingTool,
                selectedTextureId = uiState.selectedBlockTexture,
                onSelectTool = { viewModel.setWorldBuildingTool(it) },
                onSelectTexture = { viewModel.setWorldBuildingTexture(it) },
                onAddBlockAtCursor = { viewModel.addBlockAtCursor() },
                onOpenImportBrowser = { viewModel.toggleFileBrowser() },
                onExitMode = { viewModel.exitWorldBuildingMode() }
            )
        } else {
            // Normal Editor UI

            // 2A. Clean, Responsive, Scrollable Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xE60F172A))
                    .horizontalScroll(topBarScrollState)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFFE2E8F0))
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Project Name & Scene Selector
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { viewModel.toggleSceneManager() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        uiState.projectName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White,
                        maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Movie, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        val currentScene = uiState.scenes.firstOrNull { it.id == uiState.activeSceneId }
                        Text(
                            currentScene?.name ?: "Main Scene",
                            fontSize = 10.sp,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Orientation Toggle Button
                IconButton(onClick = onToggleOrientation, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ScreenRotation, contentDescription = "Toggle Orientation", tint = Color(0xFFE2E8F0), modifier = Modifier.size(18.dp))
                }

                // Selection Lock Button
                IconButton(
                    onClick = { viewModel.toggleSelectionLock() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (uiState.isSelectionLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Lock Selection",
                        tint = if (uiState.isSelectionLocked) Color(0xFFF59E0B) else Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Transform Modes (Material 3 Icons)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B))
                        .padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModeIconButton(Icons.Default.TouchApp, "Select", uiState.editorMode == EditorMode.SELECT) {
                        viewModel.setEditorMode(EditorMode.SELECT)
                    }
                    ModeIconButton(Icons.Default.OpenWith, "Move", uiState.editorMode == EditorMode.MOVE) {
                        viewModel.setEditorMode(EditorMode.MOVE)
                    }
                    ModeIconButton(Icons.Default.Sync, "Rotate", uiState.editorMode == EditorMode.ROTATE) {
                        viewModel.setEditorMode(EditorMode.ROTATE)
                    }
                    ModeIconButton(Icons.Default.Transform, "Scale", uiState.editorMode == EditorMode.SCALE) {
                        viewModel.setEditorMode(EditorMode.SCALE)
                    }
                    ModeIconButton(Icons.Default.Videocam, "Camera", uiState.editorMode == EditorMode.CAMERA) {
                        viewModel.setEditorMode(EditorMode.CAMERA)
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    IconButton(
                        onClick = { viewModel.resetCameraToCenter() },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Default.CenterFocusStrong,
                            contentDescription = "Reset Camera to Center",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // World Building Mode Button
                Button(
                    onClick = { viewModel.enterWorldBuildingMode() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Build Mode", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Side AI Assistant Button
                Button(
                    onClick = { viewModel.toggleSideAi() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("AI Agent", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(6.dp))

                // File Browser / Import Button
                IconButton(onClick = { viewModel.toggleFileBrowser() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Import Files", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                }

                // Add Object Menu
                Box {
                    IconButton(
                        onClick = { addMenuOpen = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Object", tint = Color(0xFF22C55E), modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(
                        expanded = addMenuOpen,
                        onDismissRequest = { addMenuOpen = false },
                        modifier = Modifier.background(Color(0xFF1E293B))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add Block (Grass)", color = Color.White) },
                            onClick = { addMenuOpen = false; viewModel.addBlock("grass") }
                        )
                        DropdownMenuItem(
                            text = { Text("Add Block (Wood)", color = Color.White) },
                            onClick = { addMenuOpen = false; viewModel.addBlock("wood") }
                        )
                        DropdownMenuItem(
                            text = { Text("Add Block (Diamond)", color = Color.White) },
                            onClick = { addMenuOpen = false; viewModel.addBlock("diamond_block") }
                        )
                        DropdownMenuItem(
                            text = { Text("Add Character (Steve)", color = Color.White) },
                            onClick = { addMenuOpen = false; viewModel.addCharacter(false, "steve") }
                        )
                        DropdownMenuItem(
                            text = { Text("Add Character (Alex)", color = Color.White) },
                            onClick = { addMenuOpen = false; viewModel.addCharacter(true, "alex") }
                        )
                        DropdownMenuItem(
                            text = { Text("Add Character (Zombie)", color = Color.White) },
                            onClick = { addMenuOpen = false; viewModel.addCharacter(false, "zombie") }
                        )
                        DropdownMenuItem(
                            text = { Text("Add Character (Knight)", color = Color.White) },
                            onClick = { addMenuOpen = false; viewModel.addCharacter(false, "knight") }
                        )
                        DropdownMenuItem(
                            text = { Text("Add Character (Miner)", color = Color.White) },
                            onClick = { addMenuOpen = false; viewModel.addCharacter(false, "miner") }
                        )
                        DropdownMenuItem(
                            text = { Text("Add Scene Camera", color = Color.White) },
                            onClick = { addMenuOpen = false; viewModel.addCamera() }
                        )
                        DropdownMenuItem(
                            text = { Text("Add Sun Light", color = Color.White) },
                            onClick = { addMenuOpen = false; viewModel.addLight() }
                        )
                    }
                }

                // Time of Day
                Box {
                    IconButton(
                        onClick = { timeOfDayMenuOpen = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.WbSunny, contentDescription = "Time of Day", tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = timeOfDayMenuOpen,
                        onDismissRequest = { timeOfDayMenuOpen = false },
                        modifier = Modifier.background(Color(0xFF1E293B))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Morning Dawn", color = Color.White) },
                            onClick = { timeOfDayMenuOpen = false; viewModel.setTimeOfDay(TimeOfDay.MORNING) }
                        )
                        DropdownMenuItem(
                            text = { Text("Bright Noon", color = Color.White) },
                            onClick = { timeOfDayMenuOpen = false; viewModel.setTimeOfDay(TimeOfDay.NOON) }
                        )
                        DropdownMenuItem(
                            text = { Text("Golden Sunset", color = Color.White) },
                            onClick = { timeOfDayMenuOpen = false; viewModel.setTimeOfDay(TimeOfDay.EVENING) }
                        )
                        DropdownMenuItem(
                            text = { Text("Midnight Dark", color = Color.White) },
                            onClick = { timeOfDayMenuOpen = false; viewModel.setTimeOfDay(TimeOfDay.NIGHT) }
                        )
                    }
                }

                // Undo / Redo
                IconButton(
                    onClick = { viewModel.undo() },
                    enabled = uiState.canUndo,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Undo,
                        contentDescription = "Undo",
                        tint = if (uiState.canUndo) Color.White else Color(0xFF475569),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { viewModel.redo() },
                    enabled = uiState.canRedo,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Redo,
                        contentDescription = "Redo",
                        tint = if (uiState.canRedo) Color.White else Color(0xFF475569),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Asset Browser
                IconButton(
                    onClick = { viewModel.toggleAssetBrowser() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Palette, contentDescription = "Assets", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                }

                // Save
                Button(
                    onClick = { viewModel.saveProject() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 2B. Floating Camera Joystick & Navigation Controls (Bottom-Left)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = if (uiState.isTimelineOpen) 190.dp else 24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Fly joystick
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x990F172A))
                            .padding(4.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.camera.moveFly(1f, 0f, 0f) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Forward", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Row {
                            IconButton(
                                onClick = { viewModel.camera.moveFly(0f, -1f, 0f) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text("◀", color = Color.White, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { viewModel.camera.moveFly(0f, 1f, 0f) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text("▶", color = Color.White, fontSize = 11.sp)
                            }
                        }
                        IconButton(
                            onClick = { viewModel.camera.moveFly(-1f, 0f, 0f) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Backward", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Elevation
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x990F172A))
                            .padding(4.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.camera.moveFly(0f, 0f, 1f) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Up", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { viewModel.camera.moveFly(0f, 0f, -1f) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Down", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Reset Camera / Center View
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x990F172A))
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconButton(
                            onClick = { viewModel.resetCameraToCenter() },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                Icons.Default.CenterFocusStrong,
                                contentDescription = "Reset Camera to Center",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // 2C. Floating Panel Toggles (Hierarchy, Inspector, Timeline)
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 50.dp, end = 10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC1E293B))
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PanelToggleButton(Icons.Default.AccountTree, "Tree", uiState.isHierarchyOpen) { viewModel.toggleHierarchy() }
                Spacer(modifier = Modifier.width(4.dp))
                PanelToggleButton(Icons.Default.Tune, "Inspect", uiState.isInspectorOpen) { viewModel.toggleInspector() }
                Spacer(modifier = Modifier.width(4.dp))
                PanelToggleButton(Icons.Default.Timeline, "Timeline", uiState.isTimelineOpen) { viewModel.toggleTimeline() }
            }

            // 3. Left Hierarchy Panel
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

            // 4. Right Inspector Panel
            AnimatedVisibility(
                visible = uiState.isInspectorOpen,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 86.dp)
            ) {
                val selectedNode = uiState.selectedNodeId?.let { viewModel.sceneGraph.getNode(it) }
                InspectorPanel(
                    node = selectedNode,
                    allNodes = viewModel.sceneGraph.nodes.values.toList(),
                    isSelectionLocked = uiState.isSelectionLocked,
                    onToggleSelectionLock = { viewModel.toggleSelectionLock() },
                    onRenameNode = { newName ->
                        uiState.selectedNodeId?.let { viewModel.renameNode(it, newName) }
                    },
                    onReparentNode = { newParentId ->
                        uiState.selectedNodeId?.let { viewModel.reparentNode(it, newParentId) }
                    },
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
                    onClose = { viewModel.toggleInspector() },
                    onSelectNode = { viewModel.selectNode(it) }
                )
            }

            // 5. Block-based Timeline Panel (Bottom)
            AnimatedVisibility(
                visible = uiState.isTimelineOpen,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val selectedPos = uiState.selectedNodeId?.let { viewModel.sceneGraph.getNode(it)?.animatedTransform?.position }
                TimelinePanel(
                    timeline = viewModel.getActiveTimeline(),
                    allTimelines = viewModel.timelines,
                    currentTime = uiState.currentTime,
                    isPlaying = uiState.isPlaying,
                    isLooping = uiState.isLooping,
                    selectedNodeId = uiState.selectedNodeId,
                    selectedNodePosition = selectedPos,
                    onPlayPause = { viewModel.togglePlayPause() },
                    onSeek = { viewModel.seekTo(it) },
                    onReset = { viewModel.resetToStart() },
                    onToggleLoop = { viewModel.toggleLoop() },
                    onSelectTimeline = { viewModel.switchTimeline(it) },
                    onCreateTimeline = { viewModel.createNewTimeline(it) },
                    onAddActionBlock = { blockType -> viewModel.addActionBlock(blockType) },
                    onRemoveActionBlock = { blockId -> viewModel.removeActionBlock(blockId) },
                    onUpdateActionBlock = { block -> viewModel.updateActionBlock(block) },
                    onClose = { viewModel.toggleTimeline() }
                )
            }
        }

        // 6. Asset Browser Sheet
        if (uiState.isAssetBrowserOpen) {
            AssetBrowserSheet(
                onDismiss = { viewModel.toggleAssetBrowser() },
                onApplyTexture = { texId ->
                    uiState.selectedNodeId?.let { viewModel.updateNodeMaterial(it, texId) }
                },
                onAddBlockWithTexture = { texId ->
                    viewModel.addBlock(texId)
                },
                onAddCharacterWithSkin = { skinId ->
                    viewModel.addCharacter(skinId == "alex", skinId)
                }
            )
        }

        // 7. File Browser Dialog
        if (uiState.isFileBrowserOpen) {
            FileBrowserDialog(
                onDismiss = { viewModel.toggleFileBrowser() },
                onTextureImported = { texId ->
                    uiState.selectedNodeId?.let { viewModel.updateNodeMaterial(it, texId) }
                }
            )
        }

        // 8. Side AI Dialog
        if (uiState.isSideAiOpen) {
            SideAiDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.toggleSideAi() }
            )
        }

        // 9. Scene Manager Dialog
        if (uiState.isSceneManagerOpen) {
            SceneManagerDialog(
                scenes = uiState.scenes,
                activeSceneId = uiState.activeSceneId,
                onSwitchScene = { viewModel.switchScene(it) },
                onCreateScene = { viewModel.createScene(it) },
                onRenameScene = { id, name -> viewModel.renameScene(id, name) },
                onDeleteScene = { viewModel.deleteScene(it) },
                onEmptyScene = { viewModel.emptyScene(it) },
                onDismiss = { viewModel.toggleSceneManager() }
            )
        }

        // 10. Save notification banner
        uiState.saveMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 54.dp)
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
fun ModeIconButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) Color(0xFF22C55E) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (isSelected) Color.White else Color(0xFF94A3B8),
                modifier = Modifier.size(14.dp)
            )
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
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isActive) Color(0xFF3B82F6) else Color(0xFF334155))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (isActive) Color.White else Color(0xFF94A3B8),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                label,
                fontSize = 10.sp,
                color = if (isActive) Color.White else Color(0xFFCBD5E1),
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
