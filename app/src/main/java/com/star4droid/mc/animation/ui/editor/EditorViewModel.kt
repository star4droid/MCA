package com.star4droid.mc.animation.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.star4droid.mc.animation.animation.ActionBlock
import com.star4droid.mc.animation.animation.ActionBlockType
import com.star4droid.mc.animation.animation.AnimationEvaluator
import com.star4droid.mc.animation.animation.AnimationTrack
import com.star4droid.mc.animation.animation.Interpolation
import com.star4droid.mc.animation.animation.Keyframe
import com.star4droid.mc.animation.animation.TimelineAsset
import com.star4droid.mc.animation.animation.TimelineInstance
import com.star4droid.mc.animation.animation.presets.PresetGenerator
import com.star4droid.mc.animation.animation.presets.PresetType
import com.star4droid.mc.animation.assets.SoundPlayer
import com.star4droid.mc.animation.character.CharacterFactory
import com.star4droid.mc.animation.engine.camera.EditorCamera
import com.star4droid.mc.animation.engine.gizmo.EditorMode
import com.star4droid.mc.animation.engine.gizmo.GizmoController
import com.star4droid.mc.animation.engine.history.ActionCommand
import com.star4droid.mc.animation.engine.history.AddNodeCommand
import com.star4droid.mc.animation.engine.history.HistoryManager
import com.star4droid.mc.animation.engine.history.RemoveNodeCommand
import com.star4droid.mc.animation.engine.history.TransformCommand
import com.star4droid.mc.animation.engine.math.Vec3
import com.star4droid.mc.animation.engine.rendering.SceneRenderer
import com.star4droid.mc.animation.engine.scene.CameraData
import com.star4droid.mc.animation.engine.scene.LightData
import com.star4droid.mc.animation.engine.scene.Material
import com.star4droid.mc.animation.engine.scene.SceneGraph
import com.star4droid.mc.animation.engine.scene.SceneNode
import com.star4droid.mc.animation.engine.scene.SceneNodeType
import com.star4droid.mc.animation.engine.scene.TimeOfDay
import com.star4droid.mc.animation.engine.scene.Transform
import com.star4droid.mc.animation.project.ProjectMetadata
import com.star4droid.mc.animation.project.ProjectRepository
import com.star4droid.mc.animation.ui.world.WorldBuildingTool
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

data class SceneItem(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Scene 1",
    val nodes: MutableMap<String, SceneNode> = mutableMapOf(),
    val rootIds: MutableList<String> = mutableListOf()
)

data class EditorUiState(
    val projectId: String = "",
    val projectName: String = "Untitled",
    val selectedNodeId: String? = null,
    val isSelectionLocked: Boolean = false,
    val editorMode: EditorMode = EditorMode.MOVE,
    val currentTime: Float = 0f,
    val isPlaying: Boolean = false,
    val isLooping: Boolean = true,
    val activeTimelineId: String = "",
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isHierarchyOpen: Boolean = false,
    val isInspectorOpen: Boolean = true,
    val isTimelineOpen: Boolean = true,
    val isAssetBrowserOpen: Boolean = false,
    val isSceneCameraActive: Boolean = false,
    val isWorldBuildingMode: Boolean = false,
    val worldBuildingTool: WorldBuildingTool = WorldBuildingTool.ADD,
    val selectedBlockTexture: String = "grass",
    val isFileBrowserOpen: Boolean = false,
    val isSideAiOpen: Boolean = false,
    val isSceneManagerOpen: Boolean = false,
    val scenes: List<SceneItem> = listOf(SceneItem(name = "Main Scene")),
    val activeSceneId: String = "",
    val timeOfDay: TimeOfDay = TimeOfDay.NOON,
    val saveMessage: String? = null,
    val version: Long = 0L // Incremented to trigger Compose recomposition
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val projectRepository = ProjectRepository(application)
    val historyManager = HistoryManager()
    val sceneGraph = SceneGraph()
    val camera = EditorCamera()
    val gizmoController = GizmoController(sceneGraph, historyManager)

    var renderer: SceneRenderer? = null

    val timelines = mutableListOf<TimelineAsset>()
    val timelineInstances = mutableListOf<TimelineInstance>()

    private val scenesList = mutableListOf<SceneItem>()

    private var projectMetadata: ProjectMetadata? = null

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null

    init {
        val initialScene = SceneItem(name = "Main Scene")
        scenesList.add(initialScene)
        _uiState.value = _uiState.value.copy(
            scenes = scenesList.toList(),
            activeSceneId = initialScene.id
        )
    }

    fun loadProject(projectId: String) {
        val loaded = projectRepository.loadProject(projectId)
        if (loaded != null) {
            projectMetadata = loaded.metadata
            sceneGraph.nodes.clear()
            sceneGraph.nodes.putAll(loaded.sceneGraph.nodes)
            sceneGraph.rootNodeIds.clear()
            sceneGraph.rootNodeIds.addAll(loaded.sceneGraph.rootNodeIds)
            sceneGraph.updateWorldMatrices()

            timelines.clear()
            timelines.addAll(loaded.timelines)

            timelineInstances.clear()
            timelineInstances.addAll(loaded.timelineInstances)

            val activeTlId = timelines.firstOrNull()?.id ?: ""

            // Sync initial scene
            if (scenesList.isEmpty()) {
                scenesList.add(SceneItem(name = "Main Scene"))
            }
            saveCurrentNodesToScene(scenesList[0])

            _uiState.value = _uiState.value.copy(
                projectId = projectId,
                projectName = loaded.metadata.name,
                activeTimelineId = activeTlId,
                selectedNodeId = sceneGraph.rootNodeIds.firstOrNull(),
                scenes = scenesList.toList(),
                activeSceneId = scenesList[0].id,
                version = System.currentTimeMillis()
            )
            updateRendererSelectedNode()
        }
    }

    fun selectNode(nodeId: String?) {
        // Selection lock enforcement
        if (_uiState.value.isSelectionLocked && _uiState.value.selectedNodeId != null && nodeId != null && nodeId != _uiState.value.selectedNodeId) {
            return
        }
        _uiState.value = _uiState.value.copy(
            selectedNodeId = nodeId,
            version = System.currentTimeMillis()
        )
        updateRendererSelectedNode()
    }

    fun toggleSelectionLock() {
        val locked = !_uiState.value.isSelectionLocked
        _uiState.value = _uiState.value.copy(isSelectionLocked = locked)
    }

    fun renameNode(nodeId: String, newName: String) {
        val node = sceneGraph.getNode(nodeId) ?: return
        node.name = newName
        triggerRecomposition()
    }

    fun reparentNode(childId: String, newParentId: String?) {
        sceneGraph.reparentNode(childId, newParentId)
        triggerRecomposition()
    }

    private fun updateRendererSelectedNode() {
        renderer?.selectedNodeId = _uiState.value.selectedNodeId
    }

    fun setEditorMode(mode: EditorMode) {
        gizmoController.currentMode = mode
        if (mode == EditorMode.CAMERA) {
            camera.isUsingSceneCamera = true
            _uiState.value = _uiState.value.copy(editorMode = mode, isSceneCameraActive = true)
        } else {
            camera.isUsingSceneCamera = false
            _uiState.value = _uiState.value.copy(editorMode = mode, isSceneCameraActive = false)
        }
        triggerRecomposition()
    }

    fun resetCameraToCenter() {
        camera.resetToStartAndCenter()
        _uiState.value = _uiState.value.copy(
            isSceneCameraActive = false,
            editorMode = if (_uiState.value.editorMode == EditorMode.CAMERA) EditorMode.SELECT else _uiState.value.editorMode,
            version = System.currentTimeMillis()
        )
        gizmoController.currentMode = _uiState.value.editorMode
        triggerRecomposition()
    }

    fun setTimeOfDay(timeOfDay: TimeOfDay) {
        _uiState.value = _uiState.value.copy(timeOfDay = timeOfDay)
        renderer?.currentTimeOfDay = timeOfDay
    }

    fun toggleSceneCamera() {
        val next = !_uiState.value.isSceneCameraActive
        _uiState.value = _uiState.value.copy(isSceneCameraActive = next)
        camera.isUsingSceneCamera = next
    }

    // --- Animation Playback ---
    fun togglePlayPause() {
        if (_uiState.value.isPlaying) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    fun startPlayback() {
        playbackJob?.cancel()
        _uiState.value = _uiState.value.copy(isPlaying = true)

        val activeTl = getActiveTimeline() ?: return
        val dur = activeTl.duration.coerceAtLeast(1.0f)

        playbackJob = viewModelScope.launch {
            var lastTime = System.nanoTime()
            while (isActive && _uiState.value.isPlaying) {
                delay(16) // ~60fps
                val now = System.nanoTime()
                val dt = (now - lastTime) / 1_000_000_000.0f
                lastTime = now

                var t = _uiState.value.currentTime + dt
                if (t >= dur) {
                    if (_uiState.value.isLooping) {
                        t %= dur
                    } else {
                        t = dur
                        pausePlayback()
                    }
                }
                seekTo(t)
            }
        }
    }

    fun pausePlayback() {
        playbackJob?.cancel()
        playbackJob = null
        _uiState.value = _uiState.value.copy(isPlaying = false)
    }

    fun seekTo(time: Float) {
        val activeTl = getActiveTimeline() ?: return
        val clamped = time.coerceIn(0f, activeTl.duration.coerceAtLeast(10f))
        _uiState.value = _uiState.value.copy(currentTime = clamped)
        evaluateAnimation(clamped)
    }

    fun resetToStart() {
        pausePlayback()
        seekTo(0f)
    }

    fun toggleLoop() {
        _uiState.value = _uiState.value.copy(isLooping = !_uiState.value.isLooping)
    }

    fun evaluateAnimation(time: Float) {
        val activeTl = getActiveTimeline() ?: return
        AnimationEvaluator.evaluateTimeline(activeTl, sceneGraph, time)
        _uiState.value = _uiState.value.copy(version = System.currentTimeMillis())
    }

    // --- Action Block Management ---
    fun addActionBlock(type: ActionBlockType) {
        val activeTl = getActiveTimeline() ?: return
        val rawTargetId = _uiState.value.selectedNodeId
            ?: sceneGraph.nodes.values.firstOrNull { it.type == SceneNodeType.CHARACTER_ROOT }?.id
            ?: sceneGraph.rootNodeIds.firstOrNull()
            ?: return

        val rawTargetNode = sceneGraph.getNode(rawTargetId) ?: return

        // Resolve to character root if a child/limb part was selected
        var targetNode = rawTargetNode
        var currentCheck = rawTargetNode
        while (currentCheck.parentId != null) {
            val parent = sceneGraph.getNode(currentCheck.parentId!!) ?: break
            currentCheck = parent
            if (currentCheck.type == SceneNodeType.CHARACTER_ROOT) {
                targetNode = currentCheck
                break
            }
        }
        val targetId = targetNode.id

        val nextStartTime = if (activeTl.actionBlocks.isNotEmpty()) {
            activeTl.actionBlocks.maxOf { it.startTime + it.duration }
        } else {
            0f
        }

        // Chain from previous block's end position if available
        val prevBlocks = activeTl.actionBlocks.filter { it.targetNodeId == targetId }
        val startPos = if (prevBlocks.isNotEmpty()) {
            val lastBlock = prevBlocks.maxByOrNull { it.startTime }!!
            lastBlock.targetPosition.copy()
        } else {
            targetNode.baseTransform.position.copy()
        }

        val targetOffset = when (type) {
            ActionBlockType.WALK -> Vec3(0f, 0f, 4f)
            ActionBlockType.RUN -> Vec3(0f, 0f, 6f)
            ActionBlockType.JUMP -> Vec3(0f, 0f, 2f)
            ActionBlockType.SLIDE_TO_POS -> Vec3(3f, 0f, 0f)
            ActionBlockType.MOVE_TO_POS -> Vec3(0f, 0f, 3f)
            else -> Vec3(0f, 0f, 0f)
        }

        val block = ActionBlock(
            name = "${targetNode.name} ${type.displayName}",
            type = type,
            targetNodeId = targetId,
            startTime = nextStartTime,
            duration = type.defaultDuration,
            trackRow = (activeTl.actionBlocks.size) % 3,
            startPosition = startPos,
            targetPosition = startPos + targetOffset
        )
        activeTl.addActionBlock(block)
        SoundPlayer.playSound(SoundPlayer.SoundType.POP)
        evaluateAnimation(_uiState.value.currentTime)
        triggerRecomposition()
    }

    fun removeActionBlock(blockId: String) {
        val activeTl = getActiveTimeline() ?: return
        activeTl.removeActionBlock(blockId)
        evaluateAnimation(_uiState.value.currentTime)
        triggerRecomposition()
    }

    fun updateActionBlock(block: ActionBlock) {
        val activeTl = getActiveTimeline() ?: return
        val idx = activeTl.actionBlocks.indexOfFirst { it.id == block.id }
        if (idx >= 0) {
            activeTl.actionBlocks[idx] = block
        }
        evaluateAnimation(_uiState.value.currentTime)
        triggerRecomposition()
    }

    // --- Keyframing ---
    fun addKeyframe(nodeId: String, propertyPath: String, value: Float) {
        val activeTl = getActiveTimeline() ?: return
        val track = activeTl.getOrCreateTrack(nodeId, propertyPath)
        track.addOrUpdateKeyframe(_uiState.value.currentTime, value, Interpolation.LINEAR)
        _uiState.value = _uiState.value.copy(version = System.currentTimeMillis())
        evaluateAnimation(_uiState.value.currentTime)
    }

    fun keyframeAllTransform(nodeId: String) {
        val node = sceneGraph.getNode(nodeId) ?: return
        val pos = node.animatedTransform.position
        val rot = node.animatedTransform.rotation
        val scale = node.animatedTransform.scale

        addKeyframe(nodeId, "transform.position.x", pos.x)
        addKeyframe(nodeId, "transform.position.y", pos.y)
        addKeyframe(nodeId, "transform.position.z", pos.z)

        addKeyframe(nodeId, "transform.rotation.x", rot.x)
        addKeyframe(nodeId, "transform.rotation.y", rot.y)
        addKeyframe(nodeId, "transform.rotation.z", rot.z)

        addKeyframe(nodeId, "transform.scale.x", scale.x)
        addKeyframe(nodeId, "transform.scale.y", scale.y)
        addKeyframe(nodeId, "transform.scale.z", scale.z)

        SoundPlayer.playSound(SoundPlayer.SoundType.POP)
    }

    fun applyPreset(presetType: PresetType) {
        val selectedId = _uiState.value.selectedNodeId ?: return
        val activeTl = getActiveTimeline() ?: return

        val success = PresetGenerator.applyPreset(
            sceneGraph = sceneGraph,
            selectedNodeId = selectedId,
            timeline = activeTl,
            presetType = presetType,
            startTime = _uiState.value.currentTime
        )
        if (success) {
            SoundPlayer.playSound(SoundPlayer.SoundType.WHOOSH)
            evaluateAnimation(_uiState.value.currentTime)
            triggerRecomposition()
        }
    }

    // --- World Building Mode ---
    fun enterWorldBuildingMode() {
        _uiState.value = _uiState.value.copy(
            isWorldBuildingMode = true,
            isHierarchyOpen = false,
            isInspectorOpen = false,
            isTimelineOpen = false,
            isAssetBrowserOpen = false
        )
    }

    fun exitWorldBuildingMode() {
        _uiState.value = _uiState.value.copy(isWorldBuildingMode = false)
    }

    fun setWorldBuildingTool(tool: WorldBuildingTool) {
        _uiState.value = _uiState.value.copy(worldBuildingTool = tool)
    }

    fun setWorldBuildingTexture(textureId: String) {
        _uiState.value = _uiState.value.copy(selectedBlockTexture = textureId)
    }

    fun addBlockAtCursor() {
        val snapX = Math.round(camera.target.x).toFloat()
        val snapY = Math.round(camera.target.y.coerceAtLeast(0f)).toFloat()
        val snapZ = Math.round(camera.target.z).toFloat()
        addBlockAt(Vec3(snapX, snapY, snapZ), _uiState.value.selectedBlockTexture)
    }

    fun addBlockAt(position: Vec3, textureId: String = _uiState.value.selectedBlockTexture) {
        val id = UUID.randomUUID().toString()
        val node = SceneNode(
            id = id,
            name = "Block ${sceneGraph.nodes.size + 1}",
            type = SceneNodeType.BLOCK,
            baseTransform = Transform(position = position),
            animatedTransform = Transform(position = position),
            material = Material(textureAssetId = textureId),
            boxDimensions = Vec3.ONE
        )
        historyManager.executeCommand(AddNodeCommand(sceneGraph, node, null))
        selectNode(node.id)
        SoundPlayer.playSound(SoundPlayer.SoundType.STEP)
        updateHistoryState()
    }

    fun removeBlock(nodeId: String) {
        val node = sceneGraph.getNode(nodeId) ?: return
        historyManager.executeCommand(RemoveNodeCommand(sceneGraph, node, node.parentId))
        if (_uiState.value.selectedNodeId == nodeId) {
            selectNode(null)
        }
        SoundPlayer.playSound(SoundPlayer.SoundType.POP)
        updateHistoryState()
    }

    // --- Scene Objects Management ---
    fun addBlock(textureId: String = "grass") {
        val spawnPos = sceneGraph.findNonOverlappingPosition(
            requiredSpan = Vec3(1f, 1f, 1f),
            preferredOrigin = camera.target + Vec3(0f, 0.5f, 0f)
        )
        addBlockAt(spawnPos, textureId)
    }

    fun addCharacter(isAlex: Boolean = false, skinId: String = if (isAlex) "alex" else "steve") {
        val name = when (skinId) {
            "alex" -> "Alex"
            "zombie" -> "Zombie"
            "knight" -> "Knight"
            "miner" -> "Miner"
            else -> "Steve"
        }
        val spawnPos = sceneGraph.findNonOverlappingPosition(
            requiredSpan = Vec3(1.5f, 2.0f, 1.5f),
            preferredOrigin = camera.target
        )
        val rootId = CharacterFactory.addCharacterToScene(
            sceneGraph = sceneGraph,
            name = "$name ${sceneGraph.nodes.count { it.value.characterPartType == com.star4droid.mc.animation.engine.scene.CharacterPartType.ROOT } + 1}",
            isAlex = isAlex,
            skinId = skinId,
            position = spawnPos
        )
        selectNode(rootId)
        SoundPlayer.playSound(SoundPlayer.SoundType.STEP)
        updateHistoryState()
    }

    fun addCamera() {
        val id = UUID.randomUUID().toString()
        val node = SceneNode(
            id = id,
            name = "Camera ${sceneGraph.getAllCameras().size + 1}",
            type = SceneNodeType.CAMERA,
            baseTransform = Transform(position = camera.target + Vec3(0f, 2f, 4f)),
            animatedTransform = Transform(position = camera.target + Vec3(0f, 2f, 4f)),
            cameraData = CameraData(fov = 60f)
        )
        historyManager.executeCommand(AddNodeCommand(sceneGraph, node, null))
        selectNode(node.id)
        SoundPlayer.playSound(SoundPlayer.SoundType.POP)
        updateHistoryState()
    }

    fun addLight() {
        val id = UUID.randomUUID().toString()
        val node = SceneNode(
            id = id,
            name = "Sun ${sceneGraph.nodes.values.count { it.type == SceneNodeType.LIGHT } + 1}",
            type = SceneNodeType.LIGHT,
            baseTransform = Transform(position = camera.target + Vec3(3f, 6f, 3f)),
            animatedTransform = Transform(position = camera.target + Vec3(3f, 6f, 3f)),
            lightData = LightData()
        )
        historyManager.executeCommand(AddNodeCommand(sceneGraph, node, null))
        selectNode(node.id)
        SoundPlayer.playSound(SoundPlayer.SoundType.POP)
        updateHistoryState()
    }

    fun duplicateSelectedNode() {
        val id = _uiState.value.selectedNodeId ?: return
        val cloneId = sceneGraph.duplicateNode(id)
        if (cloneId != null) {
            selectNode(cloneId)
            SoundPlayer.playSound(SoundPlayer.SoundType.POP)
            updateHistoryState()
        }
    }

    fun deleteSelectedNode() {
        val id = _uiState.value.selectedNodeId ?: return
        val node = sceneGraph.getNode(id) ?: return
        historyManager.executeCommand(RemoveNodeCommand(sceneGraph, node, node.parentId))
        selectNode(null)
        SoundPlayer.playSound(SoundPlayer.SoundType.POP)
        updateHistoryState()
    }

    fun updateNodeTransform(nodeId: String, newTransform: Transform) {
        val node = sceneGraph.getNode(nodeId) ?: return
        node.baseTransform = newTransform.copyTransform()
        node.animatedTransform = newTransform.copyTransform()
        sceneGraph.updateWorldMatrices()
        triggerRecomposition()
    }

    fun updateNodeMaterial(nodeId: String, textureAssetId: String, opacity: Float = 1.0f) {
        val node = sceneGraph.getNode(nodeId) ?: return
        node.material = node.material.copy(
            textureAssetId = textureAssetId,
            opacity = opacity
        )
        triggerRecomposition()
    }

    // --- Multi-Scene Management ---
    private fun saveCurrentNodesToScene(scene: SceneItem) {
        scene.nodes.clear()
        scene.nodes.putAll(sceneGraph.nodes)
        scene.rootIds.clear()
        scene.rootIds.addAll(sceneGraph.rootNodeIds)
    }

    private fun loadNodesFromScene(scene: SceneItem) {
        sceneGraph.nodes.clear()
        sceneGraph.nodes.putAll(scene.nodes)
        sceneGraph.rootNodeIds.clear()
        sceneGraph.rootNodeIds.addAll(scene.rootIds)
        sceneGraph.updateWorldMatrices()
        selectNode(sceneGraph.rootNodeIds.firstOrNull())
    }

    fun createScene(name: String) {
        val activeScene = scenesList.firstOrNull { it.id == _uiState.value.activeSceneId }
        if (activeScene != null) {
            saveCurrentNodesToScene(activeScene)
        }
        val newScene = SceneItem(name = name)
        scenesList.add(newScene)
        loadNodesFromScene(newScene)
        _uiState.value = _uiState.value.copy(
            scenes = scenesList.toList(),
            activeSceneId = newScene.id
        )
        triggerRecomposition()
    }

    fun switchScene(sceneId: String) {
        val activeScene = scenesList.firstOrNull { it.id == _uiState.value.activeSceneId }
        if (activeScene != null) {
            saveCurrentNodesToScene(activeScene)
        }
        val targetScene = scenesList.firstOrNull { it.id == sceneId } ?: return
        loadNodesFromScene(targetScene)
        _uiState.value = _uiState.value.copy(
            scenes = scenesList.toList(),
            activeSceneId = targetScene.id
        )
        triggerRecomposition()
    }

    fun renameScene(sceneId: String, newName: String) {
        val scene = scenesList.firstOrNull { it.id == sceneId } ?: return
        scene.name = newName
        _uiState.value = _uiState.value.copy(scenes = scenesList.toList())
        triggerRecomposition()
    }

    fun deleteScene(sceneId: String) {
        if (scenesList.size <= 1) return // Keep at least one
        scenesList.removeAll { it.id == sceneId }
        val next = scenesList.first()
        loadNodesFromScene(next)
        _uiState.value = _uiState.value.copy(
            scenes = scenesList.toList(),
            activeSceneId = next.id
        )
        triggerRecomposition()
    }

    fun emptyScene(sceneId: String) {
        val scene = scenesList.firstOrNull { it.id == sceneId } ?: return
        scene.nodes.clear()
        scene.rootIds.clear()
        if (_uiState.value.activeSceneId == sceneId) {
            sceneGraph.nodes.clear()
            sceneGraph.rootNodeIds.clear()
            sceneGraph.updateWorldMatrices()
            selectNode(null)
        }
        triggerRecomposition()
    }

    // --- Undo / Redo ---
    fun undo() {
        if (historyManager.undo()) {
            evaluateAnimation(_uiState.value.currentTime)
            updateHistoryState()
            SoundPlayer.playSound(SoundPlayer.SoundType.POP)
        }
    }

    fun redo() {
        if (historyManager.redo()) {
            evaluateAnimation(_uiState.value.currentTime)
            updateHistoryState()
            SoundPlayer.playSound(SoundPlayer.SoundType.POP)
        }
    }

    private fun updateHistoryState() {
        _uiState.value = _uiState.value.copy(
            canUndo = historyManager.canUndo,
            canRedo = historyManager.canRedo,
            version = System.currentTimeMillis()
        )
    }

    // --- Timelines ---
    fun getActiveTimeline(): TimelineAsset? {
        val id = _uiState.value.activeTimelineId
        return timelines.firstOrNull { it.id == id } ?: timelines.firstOrNull()
    }

    fun createNewTimeline(name: String) {
        val newTl = TimelineAsset(name = name, duration = 10f)
        timelines.add(newTl)
        timelineInstances.add(TimelineInstance(timelineAssetId = newTl.id))
        _uiState.value = _uiState.value.copy(activeTimelineId = newTl.id, version = System.currentTimeMillis())
    }

    fun switchTimeline(timelineId: String) {
        _uiState.value = _uiState.value.copy(activeTimelineId = timelineId, currentTime = 0f, version = System.currentTimeMillis())
        evaluateAnimation(0f)
    }

    // --- Panels & Dialog Toggles ---
    fun toggleHierarchy() {
        _uiState.value = _uiState.value.copy(isHierarchyOpen = !_uiState.value.isHierarchyOpen)
    }

    fun toggleInspector() {
        _uiState.value = _uiState.value.copy(isInspectorOpen = !_uiState.value.isInspectorOpen)
    }

    fun toggleTimeline() {
        _uiState.value = _uiState.value.copy(isTimelineOpen = !_uiState.value.isTimelineOpen)
    }

    fun toggleAssetBrowser() {
        _uiState.value = _uiState.value.copy(isAssetBrowserOpen = !_uiState.value.isAssetBrowserOpen)
    }

    fun toggleFileBrowser() {
        _uiState.value = _uiState.value.copy(isFileBrowserOpen = !_uiState.value.isFileBrowserOpen)
    }

    fun toggleSideAi() {
        _uiState.value = _uiState.value.copy(isSideAiOpen = !_uiState.value.isSideAiOpen)
    }

    fun toggleSceneManager() {
        _uiState.value = _uiState.value.copy(isSceneManagerOpen = !_uiState.value.isSceneManagerOpen)
    }

    fun triggerRecomposition() {
        _uiState.value = _uiState.value.copy(version = System.currentTimeMillis())
    }

    // --- Persistence ---
    fun saveProject() {
        val meta = projectMetadata ?: return
        projectRepository.saveProject(
            id = meta.id,
            metadata = meta,
            sceneGraph = sceneGraph,
            timelines = timelines,
            timelineInstances = timelineInstances
        )
        SoundPlayer.playSound(SoundPlayer.SoundType.DING)
        _uiState.value = _uiState.value.copy(saveMessage = "Project saved successfully!")
        viewModelScope.launch {
            delay(2000)
            _uiState.value = _uiState.value.copy(saveMessage = null)
        }
    }
}
