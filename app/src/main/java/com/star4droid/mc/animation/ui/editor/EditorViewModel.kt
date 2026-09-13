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
import com.star4droid.mc.animation.engine.scene.CharacterPartType
import com.star4droid.mc.animation.engine.scene.LightData
import com.star4droid.mc.animation.engine.scene.Material
import com.star4droid.mc.animation.engine.scene.SceneGraph
import com.star4droid.mc.animation.engine.scene.SceneNode
import com.star4droid.mc.animation.engine.scene.SceneNodeType
import com.star4droid.mc.animation.engine.scene.TimeOfDay
import com.star4droid.mc.animation.engine.scene.Transform
import com.star4droid.mc.animation.project.ProjectMetadata
import com.star4droid.mc.animation.project.ProjectRepository
import com.star4droid.mc.animation.ui.blocks.CustomBlockPreset
import com.star4droid.mc.animation.ui.world.WorldBuildingTool
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.star4droid.mc.animation.export.Mp4ExportConfig
import com.star4droid.mc.animation.export.Mp4Exporter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ActionBlockGlobalSettings(
    val enablePositionMove: Boolean = false,
    val moveVector: Vec3 = Vec3(0f, 0f, 2f),
    val stepSize: Float = 1.0f,
    val speed: Float = 1.0f,
    val duration: Float = 2.0f
)

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
    val worldBuildingParentId: String? = null,
    val selectedBlockTexture: String = "grass",
    val isFileBrowserOpen: Boolean = false,
    val isSideAiOpen: Boolean = false,
    val isSceneManagerOpen: Boolean = false,
    val scenes: List<SceneItem> = listOf(SceneItem(name = "Main Scene")),
    val activeSceneId: String = "",
    val timeOfDay: TimeOfDay = TimeOfDay.NOON,
    val isPositionPickerActive: Boolean = false,
    val pickerTargetBlockId: String? = null,
    val pickerPickedPosition: Vec3 = Vec3.ZERO,
    val prePickerSelectionLocked: Boolean = false,
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
    var glSurfaceView: android.opengl.GLSurfaceView? = null

    val timelines = mutableListOf<TimelineAsset>()
    val timelineInstances = mutableListOf<TimelineInstance>()

    private val scenesList = mutableListOf<SceneItem>()

    private var projectMetadata: ProjectMetadata? = null

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportProgress = MutableStateFlow(0f)
    val exportProgress: StateFlow<Float> = _exportProgress.asStateFlow()

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

    private var cameraAnimJob: Job? = null

    fun resetCameraToCenter() {
        cameraAnimJob?.cancel()
        val startTarget = camera.target.copy()
        val startDist = camera.distance
        val startYaw = camera.yaw
        val startPitch = camera.pitch
        val destTarget = Vec3(0f, 1f, 0f)
        val destDist = 6.0f
        val destYaw = 45.0f
        val destPitch = 25.0f

        camera.isUsingSceneCamera = false
        _uiState.value = _uiState.value.copy(
            isSceneCameraActive = false,
            editorMode = if (_uiState.value.editorMode == EditorMode.CAMERA) EditorMode.SELECT else _uiState.value.editorMode,
            version = System.currentTimeMillis()
        )
        gizmoController.currentMode = _uiState.value.editorMode

        cameraAnimJob = viewModelScope.launch {
            val durationMs = 350L
            val startTime = System.currentTimeMillis()
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
                val t = if (progress < 0.5f) 2f * progress * progress else -1f + (4f - 2f * progress) * progress
                camera.target = Vec3(
                    startTarget.x + (destTarget.x - startTarget.x) * t,
                    startTarget.y + (destTarget.y - startTarget.y) * t,
                    startTarget.z + (destTarget.z - startTarget.z) * t
                )
                camera.distance = startDist + (destDist - startDist) * t
                camera.yaw = startYaw + (destYaw - startYaw) * t
                camera.pitch = startPitch + (destPitch - startPitch) * t
                triggerRecomposition()
                if (progress >= 1f) break
                delay(16)
            }
        }
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

    fun getActiveTimelineDuration(): Float {
        val activeTl = getActiveTimeline() ?: return 10f
        var maxEnd = activeTl.duration.coerceAtLeast(10f)
        for (b in activeTl.actionBlocks) {
            val end = b.startTime + b.duration
            if (end > maxEnd) {
                maxEnd = end
            }
        }
        return maxEnd
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

    private val globalBlockSettings = mutableMapOf<ActionBlockType, ActionBlockGlobalSettings>()

    fun getGlobalSettings(type: ActionBlockType): ActionBlockGlobalSettings {
        return globalBlockSettings.getOrPut(type) {
            ActionBlockGlobalSettings(
                enablePositionMove = false,
                moveVector = when (type) {
                    ActionBlockType.WALK -> Vec3(0f, 0f, 3f)
                    ActionBlockType.RUN -> Vec3(0f, 0f, 5f)
                    ActionBlockType.JUMP -> Vec3(0f, 0f, 2f)
                    else -> Vec3(0f, 0f, 2f)
                },
                stepSize = 1.0f,
                speed = 1.0f,
                duration = type.defaultDuration
            )
        }
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

        // USER REQUEST: Add in pointer position
        val startTime = _uiState.value.currentTime

        val global = getGlobalSettings(type)
        val duration = global.duration
        val endTime = startTime + duration

        // USER REQUEST: When user add block and there's block in the pointer, add it under until there's no one
        var targetRow = 0
        while (true) {
            val collision = activeTl.actionBlocks.any { b ->
                b.trackRow == targetRow && !(endTime <= b.startTime || startTime >= (b.startTime + b.duration))
            }
            if (!collision) break
            targetRow++
        }

        val block = ActionBlock(
            name = "${targetNode.name} ${type.displayName}",
            type = type,
            targetNodeId = targetId,
            startTime = startTime,
            duration = duration,
            trackRow = targetRow,
            speed = global.speed,
            enablePositionMove = global.enablePositionMove,
            moveVector = global.moveVector.copy(),
            scaleVector = targetNode.baseTransform.scale.copy(),
            stepSize = global.stepSize,
            hasCustomSettings = false,
            startPosition = targetNode.baseTransform.position.copy(),
            targetPosition = targetNode.baseTransform.position + global.moveVector
        )
        activeTl.addActionBlock(block)
        SoundPlayer.playSound(SoundPlayer.SoundType.POP)
        evaluateAnimation(_uiState.value.currentTime)
        saveProject()
        triggerRecomposition()
    }

    fun removeActionBlock(blockId: String) {
        val activeTl = getActiveTimeline() ?: return
        val removed = activeTl.removeActionBlock(blockId)
        if (removed) {
            SoundPlayer.playSound(SoundPlayer.SoundType.POP)
            evaluateAnimation(_uiState.value.currentTime)
            saveProject()
            triggerRecomposition()
        }
    }

    fun updateActionBlock(block: ActionBlock) {
        val activeTl = getActiveTimeline() ?: return
        val idx = activeTl.actionBlocks.indexOfFirst { it.id == block.id }
        if (idx >= 0) {
            activeTl.actionBlocks[idx] = block
        }
        evaluateAnimation(_uiState.value.currentTime)
        saveProject()
        triggerRecomposition()
    }

    fun applyActionBlockSettings(block: ActionBlock, applyToAll: Boolean) {
        val activeTl = getActiveTimeline() ?: return
        if (applyToAll) {
            globalBlockSettings[block.type] = ActionBlockGlobalSettings(
                enablePositionMove = block.enablePositionMove,
                moveVector = block.moveVector.copy(),
                stepSize = block.stepSize,
                speed = block.speed,
                duration = block.duration
            )
            for (b in activeTl.actionBlocks) {
                if (b.type == block.type && (!b.hasCustomSettings || b.id == block.id)) {
                    b.enablePositionMove = block.enablePositionMove
                    b.moveVector = block.moveVector.copy()
                    b.stepSize = block.stepSize
                    b.speed = block.speed
                    b.duration = block.duration
                }
            }
        } else {
            block.hasCustomSettings = true
            val idx = activeTl.actionBlocks.indexOfFirst { it.id == block.id }
            if (idx >= 0) {
                activeTl.actionBlocks[idx] = block
            }
        }
        evaluateAnimation(_uiState.value.currentTime)
        triggerRecomposition()
    }

    fun removeCustomSettingsFromBlock(blockId: String) {
        val activeTl = getActiveTimeline() ?: return
        val block = activeTl.actionBlocks.find { it.id == blockId } ?: return
        val global = getGlobalSettings(block.type)
        block.enablePositionMove = global.enablePositionMove
        block.moveVector = global.moveVector.copy()
        block.stepSize = global.stepSize
        block.speed = global.speed
        block.duration = global.duration
        block.hasCustomSettings = false
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

    private fun resolveTargetNodeId(targetNodeName: String, selectedId: String): String {
        if (targetNodeName.isBlank()) return selectedId
        if (sceneGraph.nodes.containsKey(targetNodeName)) return targetNodeName

        val norm = targetNodeName.replace("_", "").replace(" ", "").lowercase()

        val partTypeFromAlias = when (norm) {
            "head" -> CharacterPartType.HEAD
            "body", "torso" -> CharacterPartType.BODY
            "root" -> CharacterPartType.ROOT
            "rightarm", "rightupperarm" -> CharacterPartType.RIGHT_ARM
            "rightforearm", "righthand", "rightelbow" -> CharacterPartType.RIGHT_FOREARM
            "leftarm", "leftupperarm" -> CharacterPartType.LEFT_ARM
            "leftforearm", "lefthand", "leftelbow" -> CharacterPartType.LEFT_FOREARM
            "rightleg", "rightthigh", "rightupperleg" -> CharacterPartType.RIGHT_LEG
            "rightlowerleg", "rightcalf", "rightknee", "rightshin" -> CharacterPartType.RIGHT_LOWER_LEG
            "leftleg", "leftthigh", "leftupperleg" -> CharacterPartType.LEFT_LEG
            "leftlowerleg", "leftcalf", "leftknee", "leftshin" -> CharacterPartType.LEFT_LOWER_LEG
            else -> null
        }

        if (partTypeFromAlias != null) {
            val matched = sceneGraph.nodes.values.firstOrNull { it.characterPartType == partTypeFromAlias }
            if (matched != null) return matched.id
        }

        val byName = sceneGraph.nodes.values.firstOrNull { 
            it.name.replace("_", "").replace(" ", "").lowercase().contains(norm) 
        }
        if (byName != null) return byName.id

        return selectedId
    }

    fun applyCustomBlockPreset(preset: CustomBlockPreset) {
        val selectedId = _uiState.value.selectedNodeId ?: sceneGraph.nodes.keys.firstOrNull() ?: return
        val activeTl = getActiveTimeline() ?: return
        try {
            val json = org.json.JSONObject(preset.jsonContent)
            val startTime = _uiState.value.currentTime

            var maxDuration = 2.0f
            if (json.has("duration")) {
                maxDuration = json.optDouble("duration", 2.0).toFloat().coerceAtLeast(0.5f)
            }

            if (json.has("tracks")) {
                val tracksArray = json.getJSONArray("tracks")
                for (i in 0 until tracksArray.length()) {
                    val trackObj = tracksArray.getJSONObject(i)
                    if (trackObj.has("keyframes")) {
                        val keyframesArray = trackObj.getJSONArray("keyframes")
                        for (j in 0 until keyframesArray.length()) {
                            val kfObj = keyframesArray.getJSONObject(j)
                            val relTime = kfObj.optDouble("time", 0.0).toFloat()
                            if (relTime > maxDuration) {
                                maxDuration = relTime
                            }
                        }
                    }
                }
            }

            // Create ActionBlock storing preset keyframe JSON
            val newBlock = ActionBlock(
                id = java.util.UUID.randomUUID().toString(),
                name = preset.name,
                type = ActionBlockType.ANIMATION_CLIP,
                targetNodeId = selectedId,
                startTime = startTime,
                duration = maxDuration,
                trackRow = 0,
                hasCustomSettings = true,
                customJson = preset.jsonContent
            )
            activeTl.addActionBlock(newBlock)

            SoundPlayer.playSound(SoundPlayer.SoundType.WHOOSH)
            evaluateAnimation(_uiState.value.currentTime)
            saveProject()
            triggerRecomposition()
        } catch (e: Exception) {
            e.printStackTrace()
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

    // --- Position Picker ---
    fun startPositionPicker(blockId: String, initialPos: Vec3) {
        val currentLocked = _uiState.value.isSelectionLocked
        _uiState.value = _uiState.value.copy(
            isPositionPickerActive = true,
            pickerTargetBlockId = blockId,
            pickerPickedPosition = initialPos,
            prePickerSelectionLocked = currentLocked,
            isSelectionLocked = true,
            editorMode = EditorMode.MOVE
        )
    }

    fun updatePickedPosition(pos: Vec3) {
        _uiState.value = _uiState.value.copy(pickerPickedPosition = pos)
    }

    fun confirmPickedPosition(onConfirmed: (Vec3) -> Unit) {
        val pickedPos = _uiState.value.pickerPickedPosition
        val restoreLocked = _uiState.value.prePickerSelectionLocked
        _uiState.value = _uiState.value.copy(
            isPositionPickerActive = false,
            pickerTargetBlockId = null,
            isSelectionLocked = restoreLocked
        )
        onConfirmed(pickedPos)
    }

    fun cancelPositionPicker() {
        val restoreLocked = _uiState.value.prePickerSelectionLocked
        _uiState.value = _uiState.value.copy(
            isPositionPickerActive = false,
            pickerTargetBlockId = null,
            isSelectionLocked = restoreLocked
        )
    }

    fun setWorldBuildingTool(tool: WorldBuildingTool) {
        _uiState.value = _uiState.value.copy(worldBuildingTool = tool)
    }

    fun setWorldBuildingTexture(textureId: String) {
        _uiState.value = _uiState.value.copy(selectedBlockTexture = textureId)
    }

    fun setWorldBuildingParent(parentId: String?) {
        _uiState.value = _uiState.value.copy(worldBuildingParentId = parentId)
    }

    fun addBlockAtCursor() {
        val snapX = Math.round(camera.target.x).toFloat()
        val snapY = Math.round(camera.target.y.coerceAtLeast(0f)).toFloat()
        val snapZ = Math.round(camera.target.z).toFloat()
        addBlockAt(Vec3(snapX, snapY, snapZ), _uiState.value.selectedBlockTexture)
    }

    fun addBlockAt(position: Vec3, textureId: String = _uiState.value.selectedBlockTexture) {
        val id = UUID.randomUUID().toString()
        val rawParentId = _uiState.value.worldBuildingParentId
        val parentNode = rawParentId?.let { sceneGraph.getNode(it) }
        val parentId = if (parentNode?.type == SceneNodeType.BLOCK || parentNode?.type == SceneNodeType.PLANE) null else rawParentId

        val node = SceneNode(
            id = id,
            name = "Block ${sceneGraph.nodes.size + 1}",
            type = SceneNodeType.BLOCK,
            baseTransform = Transform(position = position),
            animatedTransform = Transform(position = position),
            material = Material(textureAssetId = textureId),
            boxDimensions = Vec3.ONE
        )
        historyManager.executeCommand(AddNodeCommand(sceneGraph, node, parentId))
        selectNode(node.id)
        SoundPlayer.playSound(SoundPlayer.SoundType.STEP)
        updateHistoryState()
        saveProject()
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
    fun importObjFile(file: java.io.File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val node = com.star4droid.mc.animation.utils.ObjImporter.parseObjFile(file)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    sceneGraph.addNode(node)
                    selectNode(node.id)
                    SoundPlayer.playSound(SoundPlayer.SoundType.STEP)
                    saveProject()
                    triggerRecomposition()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addBlock(textureId: String = "grass") {
        val spawnPos = sceneGraph.findNonOverlappingPosition(
            requiredSpan = Vec3(1f, 1f, 1f),
            preferredOrigin = camera.target + Vec3(0f, 0.5f, 0f)
        )
        addBlockAt(spawnPos, textureId)
    }

    fun addHalfBlock(textureId: String = "grass") {
        val spawnPos = sceneGraph.findNonOverlappingPosition(
            requiredSpan = Vec3(1f, 0.5f, 1f),
            preferredOrigin = camera.target + Vec3(0f, 0.25f, 0f)
        )
        val id = UUID.randomUUID().toString()
        val node = SceneNode(
            id = id,
            name = "Slab ${sceneGraph.nodes.values.count { it.type == SceneNodeType.HALF_BLOCK } + 1}",
            type = SceneNodeType.HALF_BLOCK,
            baseTransform = Transform(position = spawnPos),
            animatedTransform = Transform(position = spawnPos),
            material = Material(textureAssetId = textureId),
            boxDimensions = Vec3(1f, 0.5f, 1f)
        )
        historyManager.executeCommand(AddNodeCommand(sceneGraph, node, null))
        selectNode(node.id)
        SoundPlayer.playSound(SoundPlayer.SoundType.STEP)
        updateHistoryState()
        saveProject()
    }

    fun addStepBlock(textureId: String = "oak_planks") {
        val spawnPos = sceneGraph.findNonOverlappingPosition(
            requiredSpan = Vec3(1f, 1f, 1f),
            preferredOrigin = camera.target + Vec3(0f, 0.5f, 0f)
        )
        val id = UUID.randomUUID().toString()
        val node = SceneNode(
            id = id,
            name = "Stairs ${sceneGraph.nodes.values.count { it.type == SceneNodeType.STEP_BLOCK } + 1}",
            type = SceneNodeType.STEP_BLOCK,
            baseTransform = Transform(position = spawnPos),
            animatedTransform = Transform(position = spawnPos),
            material = Material(textureAssetId = textureId),
            boxDimensions = Vec3(1f, 1f, 1f)
        )
        historyManager.executeCommand(AddNodeCommand(sceneGraph, node, null))
        selectNode(node.id)
        SoundPlayer.playSound(SoundPlayer.SoundType.STEP)
        updateHistoryState()
        saveProject()
    }

    fun addPlane(textureId: String = "grass") {
        val spawnPos = camera.target + Vec3(0f, 0.05f, 0f)
        val id = UUID.randomUUID().toString()
        val node = SceneNode(
            id = id,
            name = "Plane ${sceneGraph.nodes.values.count { it.type == SceneNodeType.PLANE } + 1}",
            type = SceneNodeType.PLANE,
            baseTransform = Transform(position = spawnPos),
            animatedTransform = Transform(position = spawnPos),
            material = Material(textureAssetId = textureId),
            boxDimensions = Vec3(1.5f, 0.01f, 1.5f)
        )
        historyManager.executeCommand(AddNodeCommand(sceneGraph, node, null))
        selectNode(node.id)
        SoundPlayer.playSound(SoundPlayer.SoundType.STEP)
        updateHistoryState()
        saveProject()
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

    fun addLight(lightType: com.star4droid.mc.animation.engine.scene.LightType = com.star4droid.mc.animation.engine.scene.LightType.POINT) {
        val id = UUID.randomUUID().toString()
        val count = sceneGraph.nodes.values.count { it.type == SceneNodeType.LIGHT } + 1
        val lightName = when (lightType) {
            com.star4droid.mc.animation.engine.scene.LightType.SUN -> "Sun Light $count"
            com.star4droid.mc.animation.engine.scene.LightType.POINT -> "Point Light $count"
            com.star4droid.mc.animation.engine.scene.LightType.SPOT -> "Spot Light $count"
        }
        val node = SceneNode(
            id = id,
            name = lightName,
            type = SceneNodeType.LIGHT,
            baseTransform = Transform(position = camera.target + Vec3(2f, 4f, 2f)),
            animatedTransform = Transform(position = camera.target + Vec3(2f, 4f, 2f)),
            lightData = LightData(lightType = lightType)
        )
        historyManager.executeCommand(AddNodeCommand(sceneGraph, node, null))
        selectNode(node.id)
        SoundPlayer.playSound(SoundPlayer.SoundType.POP)
        updateHistoryState()
    }

    fun updateNodeLightData(nodeId: String, lightData: LightData) {
        val node = sceneGraph.getNode(nodeId) ?: return
        node.lightData = lightData.copy()
        val currentPrefix = when (lightData.lightType) {
            com.star4droid.mc.animation.engine.scene.LightType.SUN -> "Sun Light"
            com.star4droid.mc.animation.engine.scene.LightType.POINT -> "Point Light"
            com.star4droid.mc.animation.engine.scene.LightType.SPOT -> "Spot Light"
        }
        if (node.name.startsWith("Sun Light") || node.name.startsWith("Point Light") || node.name.startsWith("Spot Light")) {
            val suffix = node.name.substringAfterLast(" ", "")
            node.name = "$currentPrefix ${suffix.ifEmpty { "1" }}"
        }
        saveProject()
        triggerRecomposition()
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
        val meta = projectMetadata ?: ProjectMetadata(
            id = UUID.randomUUID().toString(),
            name = _uiState.value.projectName,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        ).also { projectMetadata = it }
        projectRepository.saveProject(
            id = meta.id,
            metadata = meta,
            sceneGraph = sceneGraph,
            timelines = timelines,
            timelineInstances = timelineInstances
        )
        _uiState.value = _uiState.value.copy(saveMessage = "Saved")
        viewModelScope.launch {
            delay(1500)
            if (_uiState.value.saveMessage == "Saved") {
                _uiState.value = _uiState.value.copy(saveMessage = null)
            }
        }
    }

    // --- Animation Import & Export ---
    fun exportCurrentAnimation(name: String) {
        val activeTl = getActiveTimeline() ?: return
        projectRepository.saveAnimationFile(name, activeTl.actionBlocks)
        _uiState.value = _uiState.value.copy(saveMessage = "Exported '$name'!")
        viewModelScope.launch {
            delay(2000)
            _uiState.value = _uiState.value.copy(saveMessage = null)
        }
    }

    fun getSavedAnimationFiles(): List<java.io.File> {
        return projectRepository.getSavedAnimationFiles()
    }

    fun importAnimation(file: java.io.File) {
        val activeTl = getActiveTimeline() ?: return
        val imported = projectRepository.loadAnimationFile(file)
        if (imported.isNotEmpty()) {
            val pointer = _uiState.value.currentTime
            val baseTime = imported.minOfOrNull { it.startTime } ?: 0f
            val targetNode = _uiState.value.selectedNodeId?.let { sceneGraph.getNode(it) }
            for (b in imported) {
                val shiftedStartTime = pointer + (b.startTime - baseTime)
                val newBlock = b.copy(
                    id = UUID.randomUUID().toString(),
                    startTime = shiftedStartTime,
                    targetNodeId = targetNode?.id ?: b.targetNodeId
                )
                activeTl.addActionBlock(newBlock)
            }
            evaluateAnimation(pointer)
            saveProject()
            triggerRecomposition()
            _uiState.value = _uiState.value.copy(saveMessage = "Imported ${file.nameWithoutExtension}!")
            viewModelScope.launch {
                delay(2000)
                _uiState.value = _uiState.value.copy(saveMessage = null)
            }
        }
    }

    fun exportMp4(
        config: Mp4ExportConfig,
        onComplete: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        val activeTimeline = getActiveTimeline() ?: run {
            onError("No active timeline to export")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isExporting.value = true
            _exportProgress.value = 0f

            try {
                val projectDir = projectRepository.getProjectDir(_uiState.value.projectId, _uiState.value.projectName)
                val exportDir = File(projectDir, "exports").apply { mkdirs() }
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val cleanName = _uiState.value.projectName.replace(Regex("[^a-zA-Z0-9_\\-\\s]"), "").trim().ifBlank { "Project" }
                val outputFile = File(exportDir, "${cleanName}_$timeStamp.mp4")

                val exporter = Mp4Exporter()
                exporter.export(
                    context = getApplication(),
                    outputFile = outputFile,
                    config = config,
                    sceneGraph = sceneGraph,
                    timeline = activeTimeline,
                    timelineInstances = timelineInstances.toList(),
                    gizmoController = gizmoController,
                    camera = camera,
                    glSurfaceView = glSurfaceView,
                    callback = object : Mp4Exporter.ExportCallback {
                        override fun onProgress(frame: Int, totalFrames: Int) {
                            _exportProgress.value = frame.toFloat() / totalFrames.coerceAtLeast(1)
                        }

                        override fun onComplete(outputFile: File) {
                            _isExporting.value = false
                            _exportProgress.value = 1f
                            _uiState.value = _uiState.value.copy(
                                saveMessage = "Video exported to ${outputFile.name}"
                            )
                            viewModelScope.launch(Dispatchers.Main) {
                                onComplete(outputFile)
                                delay(2500)
                                if (_uiState.value.saveMessage?.startsWith("Video exported") == true) {
                                    _uiState.value = _uiState.value.copy(saveMessage = null)
                                }
                            }
                        }

                        override fun onError(error: String) {
                            _isExporting.value = false
                            viewModelScope.launch(Dispatchers.Main) {
                                onError(error)
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                _isExporting.value = false
                viewModelScope.launch(Dispatchers.Main) {
                    onError(e.message ?: "Export failed")
                }
            }
        }
    }
}
