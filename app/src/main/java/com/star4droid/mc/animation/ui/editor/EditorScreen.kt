package com.star4droid.mc.animation.ui.editor

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.star4droid.mc.animation.assets.BuiltInAssets
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
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import com.star4droid.mc.animation.ui.ai_studio.AiAnimationStudioScreen
import com.star4droid.mc.animation.ui.blocks.CustomBlocksManagerDialog
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.star4droid.mc.animation.engine.gizmo.EditorMode
import com.star4droid.mc.animation.engine.scene.LightType
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
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val density = LocalDensity.current

    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
        BuiltInAssets.loadSavedCustomTextures(context)
    }

    var addMenuOpen by remember { mutableStateOf(false) }
    var timeOfDayMenuOpen by remember { mutableStateOf(false) }
    var isAiStudioOpen by remember { mutableStateOf(false) }
    var isCustomBlocksManagerOpen by remember { mutableStateOf(false) }
    val topBarScrollState = rememberScrollState()

    // REQUIREMENT 9: Resizable Panel State Sizes with Default Values
    val defaultHierarchyWidth = 260.dp
    val defaultInspectorWidth = 280.dp
    val defaultTimelineHeight = 220.dp
    val defaultTimelineWidth = 320.dp

    var hierarchyWidth by remember { mutableStateOf(defaultHierarchyWidth) }
    var inspectorWidth by remember { mutableStateOf(defaultInspectorWidth) }
    var timelineHeight by remember { mutableStateOf(defaultTimelineHeight) }
    var timelineWidth by remember { mutableStateOf(defaultTimelineWidth) }

    if (isAiStudioOpen) {
        AiAnimationStudioScreen(
            viewModel = viewModel,
            onBack = { isAiStudioOpen = false }
        )
    } else {
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0B101B))) {
            if (!uiState.isWorldBuildingMode) {
            // 1. Top Bar (Top element in vertical Column layout)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(10f)
                    .background(Color(0xE60F172A))
                    .then(if (!isLandscape) Modifier.statusBarsPadding() else Modifier)
                    .horizontalScroll(topBarScrollState)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFFE2E8F0))
                }

                Spacer(modifier = Modifier.width(4.dp))

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

                // Screen Rotate Icon Button (Icon only)
                IconButton(
                    onClick = onToggleOrientation,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.ScreenRotation,
                        contentDescription = "Rotate Screen",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

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

                Spacer(modifier = Modifier.width(4.dp))

                // Transform Modes (Icon-only)
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

                Spacer(modifier = Modifier.width(4.dp))

                // Build Mode Icon Button
                IconButton(
                    onClick = { viewModel.enterWorldBuildingMode() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Build, contentDescription = "Build Mode", tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.width(4.dp))

                // AI Studio Launch Icon Button
                IconButton(
                    onClick = { isAiStudioOpen = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "AI Studio", tint = Color(0xFF8B5CF6), modifier = Modifier.size(19.dp))
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Custom Blocks Manager Icon Button
                IconButton(
                    onClick = { isCustomBlocksManagerOpen = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Extension, contentDescription = "Custom Blocks Manager", tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(onClick = { viewModel.toggleFileBrowser() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Import Files", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                }

                // Add Object Menu (Including Lights)
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
                        modifier = Modifier
                            .background(Color(0xFF1E293B))
                            .heightIn(max = 350.dp)
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
                            text = { Text("Add Plane 🗺️", color = Color.White) },
                            onClick = { addMenuOpen = false; viewModel.addPlane("grass") }
                        )
                        DropdownMenuItem(
                            text = { Text("Add Scene Camera", color = Color.White) },
                            onClick = { addMenuOpen = false; viewModel.addCamera() }
                        )
                        DropdownMenuItem(
                            text = { Text("Add Sun Light ☀️", color = Color.White) },
                            onClick = { addMenuOpen = false; viewModel.addLight(LightType.SUN) }
                        )
                        DropdownMenuItem(
                            text = { Text("Add Point Light 💡", color = Color.White) },
                            onClick = { addMenuOpen = false; viewModel.addLight(LightType.POINT) }
                        )
                        DropdownMenuItem(
                            text = { Text("Add Spot Light 🔦", color = Color.White) },
                            onClick = { addMenuOpen = false; viewModel.addLight(LightType.SPOT) }
                        )
                        DropdownMenuItem(
                            text = { Text("Import 3D Model (.obj) 📦", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold) },
                            onClick = { addMenuOpen = false; viewModel.toggleFileBrowser() }
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

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = { viewModel.toggleAssetBrowser() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Palette, contentDescription = "Assets", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                }

                IconButton(
                    onClick = { viewModel.saveProject() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Save", tint = Color(0xFF22C55E), modifier = Modifier.size(18.dp))
                }
            }

            // 2. Separated Row on the Right Side UNDER top scrollable bar for Tree / Inspect / Timeline toggles!
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(9f)
                    .background(Color(0x800F172A))
                    .padding(end = 8.dp, top = 3.dp, bottom = 3.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
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
            }
        }

        // 3. Main Workspace Container (Starts physically BELOW panel toggles row in Column layout)
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // 2A. 3D Viewport
            Viewport3D(viewModel = viewModel)

            // 2B. World Building Mode Overlay (If enabled: all other UI hidden!)
            if (uiState.isWorldBuildingMode) {
                WorldBuildingOverlay(
                    activeTool = uiState.worldBuildingTool,
                    selectedTextureId = uiState.selectedBlockTexture,
                    selectedParentId = uiState.worldBuildingParentId,
                    parentName = uiState.worldBuildingParentId?.let { viewModel.sceneGraph.getNode(it)?.name } ?: "Root",
                    candidateParents = viewModel.sceneGraph.getAllNodes().filter { it.type != com.star4droid.mc.animation.engine.scene.SceneNodeType.CAMERA },
                    onSelectTool = { viewModel.setWorldBuildingTool(it) },
                    onSelectTexture = { viewModel.setWorldBuildingTexture(it) },
                    onSelectParent = { viewModel.setWorldBuildingParent(it) },
                    onAddBlockAtCursor = { viewModel.addBlockAtCursor() },
                    onOpenImportBrowser = { viewModel.toggleFileBrowser() },
                    onExitMode = { viewModel.exitWorldBuildingMode() }
                )
            } else {
                // Normal Editor Floating Panels Overlay (Under Top Bar in Column)

                // 2C. Inspector Panel (Aligned to Top Right of Workspace container)
                androidx.compose.animation.AnimatedVisibility(
                    visible = uiState.isInspectorOpen,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 6.dp)
                ) {
                    val selectedNode = uiState.selectedNodeId?.let { viewModel.sceneGraph.getNode(it) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PanelResizeHandle(
                            isVertical = false,
                            onDrag = { dx ->
                                with(density) {
                                    val newW = inspectorWidth - dx.toDp()
                                    inspectorWidth = newW.coerceIn(200.dp, 480.dp)
                                }
                            },
                            onDoubleTap = { inspectorWidth = defaultInspectorWidth }
                        )

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
                            onUpdateLightData = { lightData ->
                                uiState.selectedNodeId?.let { viewModel.updateNodeLightData(it, lightData) }
                            },
                            onClose = { viewModel.toggleInspector() },
                            onSelectNode = { viewModel.selectNode(it) },
                            onOpenTextureBrowser = { viewModel.toggleFileBrowser() },
                            modifier = Modifier.width(inspectorWidth)
                        )
                    }
                }

                // 2D. Left Hierarchy Panel with Resizable Drag Handle
                androidx.compose.animation.AnimatedVisibility(
                    visible = uiState.isHierarchyOpen,
                    enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 6.dp, start = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HierarchyPanel(
                            sceneGraph = viewModel.sceneGraph,
                            selectedNodeId = uiState.selectedNodeId,
                            onSelectNode = { viewModel.selectNode(it) },
                            onDeleteNode = { viewModel.deleteSelectedNode() },
                            onDuplicateNode = { viewModel.duplicateSelectedNode() },
                            onClose = { viewModel.toggleHierarchy() },
                            modifier = Modifier.width(hierarchyWidth)
                        )

                        PanelResizeHandle(
                            isVertical = false,
                            onDrag = { dx ->
                                with(density) {
                                    val newW = hierarchyWidth + dx.toDp()
                                    hierarchyWidth = newW.coerceIn(180.dp, 450.dp)
                                }
                            },
                            onDoubleTap = { hierarchyWidth = defaultHierarchyWidth }
                        )
                    }
                }

                // 2E. Block-based Timeline Panel (Orientation Aware: Bottom in Portrait, Side in Landscape)
                androidx.compose.animation.AnimatedVisibility(
                    visible = uiState.isTimelineOpen,
                    enter = if (isLandscape) slideInHorizontally(initialOffsetX = { -it }) + fadeIn() else slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = if (isLandscape) slideOutHorizontally(targetOffsetX = { -it }) + fadeOut() else slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = if (isLandscape) Modifier.align(Alignment.TopStart).padding(top = 6.dp, start = 6.dp) else Modifier.align(Alignment.BottomCenter)
                ) {
                    val selectedPos = uiState.selectedNodeId?.let { viewModel.sceneGraph.getNode(it)?.animatedTransform?.position }

                if (isLandscape) {
                    // Landscape Side Timeline
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TimelinePanel(
                            timeline = viewModel.getActiveTimeline(),
                            allTimelines = viewModel.timelines,
                            currentTime = uiState.currentTime,
                            isPlaying = uiState.isPlaying,
                            isLooping = uiState.isLooping,
                            selectedNodeId = uiState.selectedNodeId,
                            selectedNodePosition = selectedPos,
                            isLandscape = true,
                            onPlayPause = { viewModel.togglePlayPause() },
                            onSeek = { viewModel.seekTo(it) },
                            onReset = { viewModel.resetToStart() },
                            onToggleLoop = { viewModel.toggleLoop() },
                            onSelectTimeline = { viewModel.switchTimeline(it) },
                            onCreateTimeline = { viewModel.createNewTimeline(it) },
                            onAddActionBlock = { blockType -> viewModel.addActionBlock(blockType) },
                            onRemoveActionBlock = { blockId -> viewModel.removeActionBlock(blockId) },
                            onUpdateActionBlock = { block -> viewModel.updateActionBlock(block) },
                            onApplyBlockSettings = { block, applyToAll -> viewModel.applyActionBlockSettings(block, applyToAll) },
                            onRemoveCustomBlockSettings = { blockId -> viewModel.removeCustomSettingsFromBlock(blockId) },
                            onClose = { viewModel.toggleTimeline() },
                            onExportAnimation = { animName -> viewModel.exportCurrentAnimation(animName) },
                            onImportAnimation = { file -> viewModel.importAnimation(file) },
                            getSavedAnimationFiles = { viewModel.getSavedAnimationFiles() },
                            onApplyCustomPreset = { preset -> viewModel.applyCustomBlockPreset(preset) },
                            modifier = Modifier.width(timelineWidth).fillMaxHeight(0.85f)
                        )

                        PanelResizeHandle(
                            isVertical = false,
                            onDrag = { dx ->
                                with(density) {
                                    val newW = timelineWidth + dx.toDp()
                                    timelineWidth = newW.coerceIn(240.dp, 550.dp)
                                }
                            },
                            onDoubleTap = { timelineWidth = defaultTimelineWidth }
                        )
                    }
                } else {
                    // Portrait Bottom Timeline
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PanelResizeHandle(
                            isVertical = true,
                            onDrag = { dy ->
                                with(density) {
                                    val newH = timelineHeight - dy.toDp()
                                    timelineHeight = newH.coerceIn(120.dp, 480.dp)
                                }
                            },
                            onDoubleTap = { timelineHeight = defaultTimelineHeight }
                        )

                        TimelinePanel(
                            timeline = viewModel.getActiveTimeline(),
                            allTimelines = viewModel.timelines,
                            currentTime = uiState.currentTime,
                            isPlaying = uiState.isPlaying,
                            isLooping = uiState.isLooping,
                            selectedNodeId = uiState.selectedNodeId,
                            selectedNodePosition = selectedPos,
                            isLandscape = false,
                            onPlayPause = { viewModel.togglePlayPause() },
                            onSeek = { viewModel.seekTo(it) },
                            onReset = { viewModel.resetToStart() },
                            onToggleLoop = { viewModel.toggleLoop() },
                            onSelectTimeline = { viewModel.switchTimeline(it) },
                            onCreateTimeline = { viewModel.createNewTimeline(it) },
                            onAddActionBlock = { blockType -> viewModel.addActionBlock(blockType) },
                            onRemoveActionBlock = { blockId -> viewModel.removeActionBlock(blockId) },
                            onUpdateActionBlock = { block -> viewModel.updateActionBlock(block) },
                            onApplyBlockSettings = { block, applyToAll -> viewModel.applyActionBlockSettings(block, applyToAll) },
                            onRemoveCustomBlockSettings = { blockId -> viewModel.removeCustomSettingsFromBlock(blockId) },
                            onClose = { viewModel.toggleTimeline() },
                            onExportAnimation = { animName -> viewModel.exportCurrentAnimation(animName) },
                            onImportAnimation = { file -> viewModel.importAnimation(file) },
                            getSavedAnimationFiles = { viewModel.getSavedAnimationFiles() },
                            onApplyCustomPreset = { preset -> viewModel.applyCustomBlockPreset(preset) },
                            modifier = Modifier.height(timelineHeight)
                        )
                    }
                }
            }
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
                },
                onAddPlaneWithTexture = { texId ->
                    viewModel.addPlane(texId)
                },
                onAddCharacterWithSkin = { skinId ->
                    viewModel.addCharacter(skinId == "alex", skinId)
                }
            )
        }

        // File Browser Dialog
        if (uiState.isFileBrowserOpen) {
            FileBrowserDialog(
                onDismiss = { viewModel.toggleFileBrowser() },
                onFileSelected = { file ->
                    if (file.extension.lowercase() in listOf("obj", "json", "bbmodel", "gltf")) {
                        viewModel.importObjFile(file)
                    }
                },
                onTextureImported = { texId ->
                    uiState.selectedNodeId?.let { viewModel.updateNodeMaterial(it, texId) }
                }
            )
        }

        // Side AI Dialog
        if (uiState.isSideAiOpen) {
            SideAiDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.toggleSideAi() }
            )
        }

        // Scene Manager Dialog
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

        // Custom Action Blocks Manager Dialog
        if (isCustomBlocksManagerOpen) {
            CustomBlocksManagerDialog(
                onDismiss = { isCustomBlocksManagerOpen = false },
                onApplyPresetToTimeline = { preset ->
                    viewModel.applyCustomBlockPreset(preset)
                    isCustomBlocksManagerOpen = false
                }
            )
        }

        // Save notification banner
        uiState.saveMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
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
}
}

// REQUIREMENT 9: Panel Resize Handle Component with Double-Tap Reset
@Composable
fun PanelResizeHandle(
    isVertical: Boolean,
    onDrag: (Float) -> Unit,
    onDoubleTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap() }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val delta = if (isVertical) dragAmount.y else dragAmount.x
                    onDrag(delta)
                }
            }
            .clip(CircleShape)
            .background(Color(0xFF3B82F6))
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.DragIndicator,
            contentDescription = "Resize Panel",
            tint = Color.White,
            modifier = Modifier.size(14.dp)
        )
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
            .padding(horizontal = 7.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (isSelected) Color.White else Color(0xFF94A3B8),
            modifier = Modifier.size(16.dp)
        )
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
