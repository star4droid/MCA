package com.star4droid.mc.animation.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

data class EditorUiState(
    val projectId: String = "",
    val projectName: String = "Untitled",
    val selectedNodeId: String? = null,
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

    private var projectMetadata: ProjectMetadata? = null

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null

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

            _uiState.value = _uiState.value.copy(
                projectId = projectId,
                projectName = loaded.metadata.name,
                activeTimelineId = activeTlId,
                selectedNodeId = sceneGraph.rootNodeIds.firstOrNull(),
                version = System.currentTimeMillis()
            )
            updateRendererSelectedNode()
        }
    }

    fun selectNode(nodeId: String?) {
        _uiState.value = _uiState.value.copy(
            selectedNodeId = nodeId,
            version = System.currentTimeMillis()
        )
        updateRendererSelectedNode()
    }

    private fun updateRendererSelectedNode() {
        renderer?.selectedNodeId = _uiState.value.selectedNodeId
    }

    fun setEditorMode(mode: EditorMode) {
        gizmoController.currentMode = mode
        if (mode == EditorMode.CAMERA) {
            camera.isUsingSceneCamera = true
            _uiState.value = _uiState.value.copy(
                editorMode = mode,
                isSceneCameraActive = true,
                version = System.currentTimeMillis()
            )
        } else {
            camera.isUsingSceneCamera = false
            _uiState.value = _uiState.value.copy(
                editorMode = mode,
                isSceneCameraActive = false,
                version = System.currentTimeMillis()
            )
        }
    }

    fun toggleCameraMode() {
        val next = !camera.isUsingSceneCamera
        camera.isUsingSceneCamera = next
        _uiState.value = _uiState.value.copy(
            isSceneCameraActive = next,
            editorMode = if (next) EditorMode.CAMERA else EditorMode.MOVE,
            version = System.currentTimeMillis()
        )
    }

    fun setTimeOfDay(tod: TimeOfDay) {
        renderer?.currentTimeOfDay = tod
        _uiState.value = _uiState.value.copy(
            timeOfDay = tod,
            version = System.currentTimeMillis()
        )
    }

    // --- Playback Engine ---
    fun togglePlayPause() {
        if (_uiState.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        _uiState.value = _uiState.value.copy(isPlaying = true)
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            val activeTimeline = getActiveTimeline()
            val maxDuration = activeTimeline?.duration ?: 10.0f
            var lastTime = System.nanoTime()

            while (isActive && _uiState.value.isPlaying) {
                delay(16) // ~60fps
                val now = System.nanoTime()
                val dt = (now - lastTime) / 1_000_000_000f
                lastTime = now

                var newTime = _uiState.value.currentTime + dt
                if (newTime > maxDuration) {
                    if (_uiState.value.isLooping) {
                        newTime = 0f
                    } else {
                        newTime = maxDuration
                        _uiState.value = _uiState.value.copy(isPlaying = false, currentTime = newTime)
                        evaluateAnimation(newTime)
                        break
                    }
                }
                _uiState.value = _uiState.value.copy(currentTime = newTime)
                evaluateAnimation(newTime)
            }
        }
    }

    fun pause() {
        playbackJob?.cancel()
        _uiState.value = _uiState.value.copy(isPlaying = false)
    }

    fun seekTo(time: Float) {
        val activeTl = getActiveTimeline()
        val duration = activeTl?.duration ?: 10f
        val clamped = time.coerceIn(0f, duration)
        _uiState.value = _uiState.value.copy(currentTime = clamped, version = System.currentTimeMillis())
        evaluateAnimation(clamped)
    }

    fun resetToStart() {
        pause()
        seekTo(0f)
        sceneGraph.resetToBase()
    }

    fun evaluateAnimation(time: Float) {
        AnimationEvaluator.evaluate(sceneGraph, timelines, timelineInstances, time)
    }

    fun toggleLoop() {
        _uiState.value = _uiState.value.copy(isLooping = !_uiState.value.isLooping)
    }

    // --- Keyframe System ---
    fun addKeyframe(nodeId: String, propertyPath: String, value: Float) {
        val activeTl = getActiveTimeline() ?: return
        val track = activeTl.getOrCreateTrack(nodeId, propertyPath)
        val kf = track.addOrUpdateKeyframe(_uiState.value.currentTime, value, Interpolation.SMOOTH)
        SoundPlayer.playSound(SoundPlayer.SoundType.POP)
        _uiState.value = _uiState.value.copy(
            canUndo = historyManager.canUndo,
            canRedo = historyManager.canRedo,
            version = System.currentTimeMillis()
        )
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

    fun deleteKeyframe(trackId: String, keyframeId: String) {
        val activeTl = getActiveTimeline() ?: return
        val track = activeTl.tracks.firstOrNull { it.id == trackId } ?: return
        track.removeKeyframe(keyframeId)
        _uiState.value = _uiState.value.copy(version = System.currentTimeMillis())
    }

    fun updateKeyframeInterpolation(trackId: String, keyframeId: String, interp: Interpolation) {
        val activeTl = getActiveTimeline() ?: return
        val track = activeTl.tracks.firstOrNull { it.id == trackId } ?: return
        track.keyframes.firstOrNull { it.id == keyframeId }?.interpolation = interp
        _uiState.value = _uiState.value.copy(version = System.currentTimeMillis())
        evaluateAnimation(_uiState.value.currentTime)
    }

    // --- Presets ---
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
            _uiState.value = _uiState.value.copy(version = System.currentTimeMillis())
        }
    }

    // --- Scene Objects Management ---
    fun addBlock(textureId: String = "grass") {
        val id = UUID.randomUUID().toString()
        val camEye = camera.getEyePosition()
        val camDir = (camera.target - camEye).normalized()
        val spawnPos = camera.target + Vec3(0f, 0.5f, 0f)

        val node = SceneNode(
            id = id,
            name = "Block ${sceneGraph.nodes.size + 1}",
            type = SceneNodeType.BLOCK,
            baseTransform = Transform(position = spawnPos),
            animatedTransform = Transform(position = spawnPos),
            material = Material(textureAssetId = textureId),
            boxDimensions = Vec3.ONE
        )
        historyManager.executeCommand(AddNodeCommand(sceneGraph, node, null))
        selectNode(node.id)
        SoundPlayer.playSound(SoundPlayer.SoundType.STEP)
        updateHistoryState()
    }

    fun addCharacter(isAlex: Boolean = false) {
        val name = if (isAlex) "Alex" else "Steve"
        val skinId = if (isAlex) "alex" else "steve"
        val rootId = CharacterFactory.addCharacterToScene(
            sceneGraph = sceneGraph,
            name = "$name ${sceneGraph.nodes.count { it.value.characterPartType == com.star4droid.mc.animation.engine.scene.CharacterPartType.ROOT } + 1}",
            isAlex = isAlex,
            skinId = skinId,
            position = camera.target
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
        val oldT = node.baseTransform.copyTransform()
        node.baseTransform = newTransform.copyTransform()
        node.animatedTransform = newTransform.copyTransform()
        sceneGraph.updateWorldMatrices()
        _uiState.value = _uiState.value.copy(version = System.currentTimeMillis())
    }

    fun updateNodeMaterial(nodeId: String, textureAssetId: String, opacity: Float = 1.0f) {
        val node = sceneGraph.getNode(nodeId) ?: return
        node.material = node.material.copy(
            textureAssetId = textureAssetId,
            opacity = opacity
        )
        _uiState.value = _uiState.value.copy(version = System.currentTimeMillis())
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

    // --- Panels Toggles ---
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
