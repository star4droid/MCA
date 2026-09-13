package com.star4droid.mc.animation.ui.ai_studio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.star4droid.mc.animation.ai.AiChatHistoryRepository
import com.star4droid.mc.animation.ai.ChatSession
import com.star4droid.mc.animation.ai.GeminiApiService
import com.star4droid.mc.animation.animation.AnimationEvaluator
import com.star4droid.mc.animation.animation.Interpolation
import com.star4droid.mc.animation.animation.TimelineAsset
import com.star4droid.mc.animation.character.CharacterFactory
import com.star4droid.mc.animation.engine.math.Vec3
import com.star4droid.mc.animation.engine.scene.CameraData
import com.star4droid.mc.animation.engine.scene.CharacterPartType
import com.star4droid.mc.animation.engine.scene.LightData
import com.star4droid.mc.animation.engine.scene.Material
import com.star4droid.mc.animation.engine.scene.SceneGraph
import com.star4droid.mc.animation.engine.scene.SceneNode
import com.star4droid.mc.animation.engine.scene.SceneNodeType
import com.star4droid.mc.animation.engine.scene.TimeOfDay
import com.star4droid.mc.animation.engine.scene.Transform
import com.star4droid.mc.animation.ui.blocks.CustomBlocksRepository
import com.star4droid.mc.animation.ui.editor.EditorViewModel
import com.star4droid.mc.animation.ui.editor.Viewport3D
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

enum class StudioObjectType {
    CHARACTER,
    BLOCK,
    OBJECT
}

@Composable
fun AiAnimationStudioScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var objectType by remember { mutableStateOf(StudioObjectType.CHARACTER) }
    var isChatOpen by remember { mutableStateOf(true) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isHistoryOpen by remember { mutableStateOf(false) }
    var isGroundVisible by remember { mutableStateOf(true) }

    // Multi-Session Persistent Chat History
    var sessions by remember { mutableStateOf(AiChatHistoryRepository.loadSessions(context)) }
    var activeSessionId by remember { mutableStateOf(sessions.lastOrNull()?.id ?: "") }

    val activeSession = sessions.find { it.id == activeSessionId } ?: sessions.firstOrNull() ?: ChatSession().also {
        sessions = listOf(it)
        activeSessionId = it.id
    }

    val messages = activeSession.messages
    var isLoading by remember { mutableStateOf(false) }
    var lastAnimationJson by remember { mutableStateOf<String?>(messages.lastOrNull { !it.animationJson.isNullOrBlank() }?.animationJson) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Studio Preview Scene Graph & Animation Engine
    val studioSceneGraph = remember { SceneGraph() }
    var isStudioPlaying by remember { mutableStateOf(false) }
    var studioTime by remember { mutableStateOf(0f) }
    var activePreviewTimeline by remember { mutableStateOf<TimelineAsset?>(null) }

    fun updateCurrentMessages(newMsgs: List<AiChatMessage>) {
        val updatedTitle = if (activeSession.title == "New Chat" || activeSession.title == "Welcome Session") {
            newMsgs.firstOrNull { it.sender == "USER" }?.text?.take(24) ?: activeSession.title
        } else activeSession.title

        val updatedSession = activeSession.copy(title = updatedTitle, messages = newMsgs)
        val updatedList = sessions.map { if (it.id == activeSessionId) updatedSession else it }
        sessions = updatedList
        AiChatHistoryRepository.saveSessions(context, updatedList)
    }

    fun startNewChat() {
        val newSession = ChatSession(title = "New Chat", messages = AiChatHistoryRepository.defaultWelcomeMessages())
        val updatedList = sessions + newSession
        sessions = updatedList
        activeSessionId = newSession.id
        lastAnimationJson = null
        AiChatHistoryRepository.saveSessions(context, updatedList)
    }

    fun clearStudioPreview() {
        // Remove all nodes that are not Camera, Light, or Ground
        val idsToRemove = studioSceneGraph.nodes.values
            .filter { it.type != SceneNodeType.CAMERA && it.type != SceneNodeType.LIGHT && it.type != SceneNodeType.GROUND }
            .map { it.id }
        for (id in idsToRemove) {
            studioSceneGraph.removeNode(id)
        }
        activePreviewTimeline = null
        isStudioPlaying = false
        studioTime = 0f
        studioSceneGraph.updateWorldMatrices()
    }

    fun showObjResult(name: String, objContent: String, colorHex: String?, textureId: String?) {
        // Requirement: "clear preview in it, when click show the result, clear the preview and show it"
        clearStudioPreview()

        try {
            val tempFile = java.io.File(context.cacheDir, "preview_model_${System.currentTimeMillis()}.obj")
            tempFile.writeText(objContent)
            val node = com.star4droid.mc.animation.utils.ObjImporter.parseObjFile(tempFile)
            node.name = name
            if (!colorHex.isNullOrBlank()) {
                val parsedColor = try {
                    android.graphics.Color.parseColor(colorHex)
                } catch (e: Exception) {
                    0xFF38BDF8.toInt()
                }
                node.material = node.material.copy(color = parsedColor)
            }
            if (!textureId.isNullOrBlank()) {
                node.material = node.material.copy(textureAssetId = textureId)
            }
            val heightOffset = (node.boxDimensions.y * 0.5f).coerceAtLeast(0.4f)
            node.baseTransform = Transform(position = Vec3(0f, heightOffset, 0f))
            node.animatedTransform = Transform(position = Vec3(0f, heightOffset, 0f))
            studioSceneGraph.addNode(node)
            studioSceneGraph.updateWorldMatrices()
            viewModel.camera.target = Vec3(0f, heightOffset, 0f)
            val maxSpan = maxOf(node.boxDimensions.x, node.boxDimensions.y, node.boxDimensions.z)
            viewModel.camera.distance = (maxSpan * 2.2f).coerceIn(2.5f, 6.0f)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setupStudioScene(type: StudioObjectType) {
        studioSceneGraph.nodes.clear()
        studioSceneGraph.rootNodeIds.clear()

        // 1. Camera Node
        val cameraNode = SceneNode(
            id = UUID.randomUUID().toString(),
            name = "Studio Camera",
            type = SceneNodeType.CAMERA,
            baseTransform = Transform(position = Vec3(0f, 1.8f, 4.5f), rotation = Vec3(-10f, 0f, 0f)),
            animatedTransform = Transform(position = Vec3(0f, 1.8f, 4.5f), rotation = Vec3(-10f, 0f, 0f)),
            cameraData = CameraData(fov = 60f, near = 0.1f, far = 100f, enabled = true)
        )
        studioSceneGraph.addNode(cameraNode)

        // 2. Light Node
        val lightNode = SceneNode(
            id = UUID.randomUUID().toString(),
            name = "Studio Light",
            type = SceneNodeType.LIGHT,
            baseTransform = Transform(position = Vec3(3f, 6f, 3f)),
            animatedTransform = Transform(position = Vec3(3f, 6f, 3f)),
            lightData = LightData(color = 0xFFFFFFFF.toInt(), intensity = 1.3f, timeOfDay = TimeOfDay.NOON)
        )
        studioSceneGraph.addNode(lightNode)

        // 3. Environment Object
        if (type == StudioObjectType.CHARACTER) {
            CharacterFactory.addCharacterToScene(
                sceneGraph = studioSceneGraph,
                name = "Steve",
                isAlex = false,
                skinId = "steve",
                position = Vec3(0f, 0f, 0f)
            )
        } else if (type == StudioObjectType.BLOCK) {
            val blockNode = SceneNode(
                id = UUID.randomUUID().toString(),
                name = "Block",
                type = SceneNodeType.BLOCK,
                baseTransform = Transform(position = Vec3(0f, 0.5f, 0f)),
                animatedTransform = Transform(position = Vec3(0f, 0.5f, 0f)),
                material = Material(textureAssetId = "grass"),
                boxDimensions = Vec3(1f, 1f, 1f)
            )
            studioSceneGraph.addNode(blockNode)
        }
        // If OBJECT, start empty with ground, waiting for AI generation or loaded object

        // Ground Grid Platform
        val ground = SceneNode(
            id = UUID.randomUUID().toString(),
            name = "Ground",
            type = SceneNodeType.GROUND,
            baseTransform = Transform(position = Vec3(0f, -0.5f, 0f), scale = Vec3(8f, 1f, 8f)),
            animatedTransform = Transform(position = Vec3(0f, -0.5f, 0f), scale = Vec3(8f, 1f, 8f)),
            material = Material(textureAssetId = "grass"),
            boxDimensions = Vec3(1f, 1f, 1f),
            visible = isGroundVisible
        )
        studioSceneGraph.addNode(ground)

        studioSceneGraph.updateWorldMatrices()
    }

    fun parseJsonToStudioTimeline(jsonContent: String): TimelineAsset {
        val timeline = TimelineAsset(name = "Studio Animation", duration = 3.0f)
        try {
            val json = JSONObject(jsonContent)
            if (json.has("duration")) {
                timeline.duration = json.optDouble("duration", 3.0).toFloat().coerceAtLeast(1.0f)
            }
            if (json.has("tracks")) {
                val tracksArr = json.getJSONArray("tracks")
                for (i in 0 until tracksArr.length()) {
                    val trackObj = tracksArr.getJSONObject(i)
                    val targetNodeName = trackObj.optString("targetNode", "")
                    val targetId = resolveStudioTargetId(studioSceneGraph, targetNodeName)

                    if (trackObj.has("keyframes")) {
                        val kfArr = trackObj.getJSONArray("keyframes")
                        for (j in 0 until kfArr.length()) {
                            val kfObj = kfArr.getJSONObject(j)
                            val t = kfObj.optDouble("time", 0.0).toFloat()

                            if (kfObj.has("position")) {
                                val p = kfObj.getJSONArray("position")
                                timeline.getOrCreateTrack(targetId, "transform.position.x").addOrUpdateKeyframe(t, p.getDouble(0).toFloat(), Interpolation.SMOOTH)
                                timeline.getOrCreateTrack(targetId, "transform.position.y").addOrUpdateKeyframe(t, p.getDouble(1).toFloat(), Interpolation.SMOOTH)
                                timeline.getOrCreateTrack(targetId, "transform.position.z").addOrUpdateKeyframe(t, p.getDouble(2).toFloat(), Interpolation.SMOOTH)
                            }
                            if (kfObj.has("rotation")) {
                                val r = kfObj.getJSONArray("rotation")
                                timeline.getOrCreateTrack(targetId, "transform.rotation.x").addOrUpdateKeyframe(t, r.getDouble(0).toFloat(), Interpolation.SMOOTH)
                                timeline.getOrCreateTrack(targetId, "transform.rotation.y").addOrUpdateKeyframe(t, r.getDouble(1).toFloat(), Interpolation.SMOOTH)
                                timeline.getOrCreateTrack(targetId, "transform.rotation.z").addOrUpdateKeyframe(t, r.getDouble(2).toFloat(), Interpolation.SMOOTH)
                            }
                            if (kfObj.has("scale")) {
                                val s = kfObj.getJSONArray("scale")
                                timeline.getOrCreateTrack(targetId, "transform.scale.x").addOrUpdateKeyframe(t, s.getDouble(0).toFloat(), Interpolation.SMOOTH)
                                timeline.getOrCreateTrack(targetId, "transform.scale.y").addOrUpdateKeyframe(t, s.getDouble(1).toFloat(), Interpolation.SMOOTH)
                                timeline.getOrCreateTrack(targetId, "transform.scale.z").addOrUpdateKeyframe(t, s.getDouble(2).toFloat(), Interpolation.SMOOTH)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return timeline
    }

    fun playPreviewAnimation(jsonContent: String) {
        val timeline = parseJsonToStudioTimeline(jsonContent)
        activePreviewTimeline = timeline
        studioTime = 0f
        isStudioPlaying = true
    }

    LaunchedEffect(objectType) {
        setupStudioScene(objectType)
    }

    // Studio Preview Animation Playback Loop
    LaunchedEffect(isStudioPlaying, activePreviewTimeline) {
        while (isStudioPlaying && activePreviewTimeline != null) {
            delay(16)
            studioTime += 0.016f
            val maxDur = activePreviewTimeline?.duration ?: 3.0f
            if (studioTime > maxDur) {
                studioTime = 0f
            }
            activePreviewTimeline?.let { tl ->
                AnimationEvaluator.evaluateTimeline(tl, studioSceneGraph, studioTime)
            }
        }
    }

    fun sendPromptToAi(prompt: String) {
        val userMsg = AiChatMessage(sender = "USER", text = prompt)
        val updated = messages + userMsg
        updateCurrentMessages(updated)
        isLoading = true

        scope.launch {
            try {
                if (objectType == StudioObjectType.OBJECT) {
                    val result = GeminiApiService.generate3DObject(
                        context = context,
                        prompt = prompt,
                        chatHistory = messages
                    )
                    val aiMsg = AiChatMessage(
                        sender = "AI",
                        text = "Generated 3D object '${result.name}'. ${result.description}",
                        objContent = result.objContent,
                        objName = result.name,
                        objColorHex = result.colorHex,
                        objTextureId = result.suggestedTexture
                    )
                    val newList = messages + userMsg + aiMsg
                    updateCurrentMessages(newList)

                    // Auto preview: clear preview first and show result!
                    showObjResult(result.name, result.objContent, result.colorHex, result.suggestedTexture)
                } else {
                    val jsonResult = GeminiApiService.generateAnimation(
                        context = context,
                        prompt = prompt,
                        targetType = objectType.name,
                        previousAnimationJson = lastAnimationJson,
                        chatHistory = messages
                    )
                    lastAnimationJson = jsonResult

                    val aiMsg = AiChatMessage(
                        sender = "AI",
                        text = "Created animation based on '$prompt'. Tap below to play!",
                        animationJson = jsonResult,
                        animationName = "AI: $prompt"
                    )
                    val newList = messages + userMsg + aiMsg
                    updateCurrentMessages(newList)

                    // Auto Play on preview
                    playPreviewAnimation(jsonResult)
                }
            } catch (e: Exception) {
                val errText = if (e.message?.contains("timeout", ignoreCase = true) == true) {
                    "Request timed out. Please check your network connection and try again."
                } else {
                    "Failed to generate ${if (objectType == StudioObjectType.OBJECT) "object" else "animation"}: ${e.message}"
                }
                val errList = messages + userMsg + AiChatMessage(
                    sender = "AI",
                    text = errText
                )
                updateCurrentMessages(errList)
            } finally {
                isLoading = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B101B))) {
        // 1. Dedicated Isolated Studio 3D Viewport
        Viewport3D(viewModel = viewModel, sceneGraph = studioSceneGraph, modifier = Modifier.fillMaxSize())

        // Replay & Clear Preview Buttons Overlay (Top Left of Viewport)
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xCC0F172A),
            modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 90.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        lastAnimationJson?.let { playPreviewAnimation(it) } ?: run {
                            studioTime = 0f
                            isStudioPlaying = true
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Replay, contentDescription = "Replay Preview", tint = Color(0xFF38BDF8))
                }
                Text(if (isStudioPlaying) "Playing..." else "Replay", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)

                Spacer(modifier = Modifier.width(6.dp))
                Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color(0xFF334155)))
                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = { clearStudioPreview() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.LayersClear, contentDescription = "Clear Preview", tint = Color(0xFFEF4444))
                }
                Text("Clear", fontSize = 11.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold)
            }
        }

        // 2. Top Header Bar with Transparent Sub-row for Ground Hide Toggle
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xE60F172A),
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AI Studio", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    }

                    // Mode Switcher: Icons only without text, show text for selected only!
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E293B))
                            .padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Character
                        Surface(
                            onClick = { objectType = StudioObjectType.CHARACTER },
                            shape = RoundedCornerShape(6.dp),
                            color = if (objectType == StudioObjectType.CHARACTER) Color(0xFF8B5CF6) else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Person, contentDescription = "Character", tint = Color.White, modifier = Modifier.size(16.dp))
                                if (objectType == StudioObjectType.CHARACTER) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Character", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(2.dp))

                        // Block
                        Surface(
                            onClick = { objectType = StudioObjectType.BLOCK },
                            shape = RoundedCornerShape(6.dp),
                            color = if (objectType == StudioObjectType.BLOCK) Color(0xFF10B981) else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Extension, contentDescription = "Block", tint = Color.White, modifier = Modifier.size(16.dp))
                                if (objectType == StudioObjectType.BLOCK) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Block", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(2.dp))

                        // Object (OBJ Creation Mode)
                        Surface(
                            onClick = { objectType = StudioObjectType.OBJECT },
                            shape = RoundedCornerShape(6.dp),
                            color = if (objectType == StudioObjectType.OBJECT) Color(0xFFF59E0B) else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Category, contentDescription = "Object", tint = Color.White, modifier = Modifier.size(16.dp))
                                if (objectType == StudioObjectType.OBJECT) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Object", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Transparent Sub-row UNDER Top Banner for Ground Hide Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x660F172A))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = {
                        isGroundVisible = !isGroundVisible
                        studioSceneGraph.nodes.values.firstOrNull { it.type == SceneNodeType.GROUND }?.visible = isGroundVisible
                    },
                    shape = RoundedCornerShape(6.dp),
                    color = if (isGroundVisible) Color(0xFF3B82F6) else Color(0xFF334155)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (isGroundVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = "Toggle Ground", tint = Color.White, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isGroundVisible) "Hide Ground" else "Show Ground", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 3. Bottom Floating FAB Action Bar: Play/Pause FAB next to Chat Toggle FAB
        Row(
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play / Pause Circular FAB
            FloatingActionButton(
                onClick = {
                    if (!isStudioPlaying && activePreviewTimeline == null && lastAnimationJson != null) {
                        playPreviewAnimation(lastAnimationJson!!)
                    } else {
                        isStudioPlaying = !isStudioPlaying
                    }
                },
                containerColor = if (isStudioPlaying) Color(0xFFEF4444) else Color(0xFF22C55E),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    if (isStudioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isStudioPlaying) "Pause Animation" else "Play Animation",
                    modifier = Modifier.size(26.dp)
                )
            }

            // Toggle Chat Circular FAB
            FloatingActionButton(
                onClick = { isChatOpen = !isChatOpen },
                containerColor = Color(0xFF8B5CF6),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    if (isChatOpen) Icons.Default.Close else Icons.Default.Chat,
                    contentDescription = "Toggle Chat",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 4. AI Chat Overlay
        if (isChatOpen) {
            AiAnimationChatOverlay(
                messages = messages,
                isLoading = isLoading,
                onSendMessage = { prompt -> sendPromptToAi(prompt) },
                onPlayAnimation = { json -> playPreviewAnimation(json) },
                onSaveCustomBlock = { name, json ->
                    CustomBlocksRepository.savePreset(
                        context = context,
                        name = name,
                        description = "Saved AI animation preset",
                        category = objectType.name,
                        jsonContent = json
                    )
                    statusMessage = "Saved block preset successfully!"
                },
                onShowObjResult = { name, objContent, colorHex, tex ->
                    showObjResult(name, objContent, colorHex, tex)
                },
                onSaveObj = { name, objContent, colorHex, tex ->
                    com.star4droid.mc.animation.objects.SavedObjectsRepository.saveObject(
                        context = context,
                        name = name,
                        objContent = objContent,
                        colorHex = colorHex,
                        textureAssetId = tex
                    )
                    android.widget.Toast.makeText(context, "Saved '$name' to Saved Objects!", android.widget.Toast.LENGTH_SHORT).show()
                },
                onAddToScene = { name, objContent, colorHex, tex ->
                    try {
                        val saved = com.star4droid.mc.animation.objects.SavedObjectsRepository.saveObject(
                            context = context,
                            name = name,
                            objContent = objContent,
                            colorHex = colorHex,
                            textureAssetId = tex
                        )
                        viewModel.importObjFile(java.io.File(saved.objFilePath))
                        android.widget.Toast.makeText(context, "Added '$name' to your scene!", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Failed to add to scene: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                isObjectMode = (objectType == StudioObjectType.OBJECT),
                onNewChat = { startNewChat() },
                onClearChat = {
                    updateCurrentMessages(emptyList())
                    lastAnimationJson = null
                },
                onOpenHistory = { isHistoryOpen = true },
                onOpenSettings = { isSettingsOpen = true },
                onCloseChat = { isChatOpen = false },
                onRetryMessage = { prompt -> sendPromptToAi(prompt) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
            )
        }

        // 5. Settings Dialog
        if (isSettingsOpen) {
            GeminiSettingsDialog(onDismiss = { isSettingsOpen = false })
        }

        // 6. Multi-Session History Dialog
        if (isHistoryOpen) {
            AlertDialog(
                onDismissRequest = { isHistoryOpen = false },
                title = { Text("Chat History & Sessions", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(sessions) { session ->
                            val isSelected = session.id == activeSessionId
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color(0xFF2563EB).copy(alpha = 0.5f) else Color(0xFF0F172A)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    activeSessionId = session.id
                                    lastAnimationJson = session.messages.lastOrNull { !it.animationJson.isNullOrBlank() }?.animationJson
                                    isHistoryOpen = false
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(session.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                        Text("${session.messages.size} messages", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                    }

                                    if (sessions.size > 1) {
                                        IconButton(
                                            onClick = {
                                                val updated = AiChatHistoryRepository.deleteSession(context, session.id)
                                                sessions = updated
                                                if (activeSessionId == session.id) {
                                                    activeSessionId = updated.firstOrNull()?.id ?: ""
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Session", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { isHistoryOpen = false }) {
                        Text("Close", color = Color(0xFF8B5CF6))
                    }
                },
                containerColor = Color(0xFF1E293B)
            )
        }
    }
}

private fun resolveStudioTargetId(sceneGraph: SceneGraph, name: String): String {
    if (name.isBlank()) return sceneGraph.nodes.keys.firstOrNull() ?: ""
    if (sceneGraph.nodes.containsKey(name)) return name

    val norm = name.replace("_", "").replace(" ", "").lowercase()
    val aliasType = when (norm) {
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

    if (aliasType != null) {
        val matched = sceneGraph.nodes.values.firstOrNull { it.characterPartType == aliasType }
        if (matched != null) return matched.id
    }

    val byName = sceneGraph.nodes.values.firstOrNull {
        it.name.replace("_", "").replace(" ", "").lowercase().contains(norm)
    }
    if (byName != null) return byName.id

    return sceneGraph.nodes.keys.firstOrNull() ?: ""
}
