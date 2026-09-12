package com.star4droid.mc.animation.ai

import android.content.Context
import com.example.BuildConfig
import com.star4droid.mc.animation.animation.ActionBlock
import com.star4droid.mc.animation.animation.ActionBlockType
import com.star4droid.mc.animation.animation.TimelineAsset
import com.star4droid.mc.animation.character.CharacterFactory
import com.star4droid.mc.animation.engine.camera.EditorCamera
import com.star4droid.mc.animation.engine.math.Vec3
import com.star4droid.mc.animation.engine.scene.Material
import com.star4droid.mc.animation.engine.scene.SceneGraph
import com.star4droid.mc.animation.engine.scene.SceneNode
import com.star4droid.mc.animation.engine.scene.SceneNodeType
import com.star4droid.mc.animation.engine.scene.Transform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class AiExecutionResult(
    val success: Boolean,
    val message: String,
    val createdNodeIds: List<String> = emptyList(),
    val createdBlockIds: List<String> = emptyList()
)

class SideAiAssistant(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Executes an AI prompt to interact with the scene.
     * Supports:
     * - Grouping objects
     * - Creating blocks / characters
     * - Animating (walk, run, jump, wave, slide)
     * - Camera control
     * - World building (structures with dimensions like [dimension x:6 y:4 z:8] or natural text)
     * - Listing scene items and positions
     */
    suspend fun processPrompt(
        prompt: String,
        sceneGraph: SceneGraph,
        camera: EditorCamera,
        timeline: TimelineAsset?,
        selectedNodeId: String?,
        createInNewGroup: Boolean,
        apiKeyOverride: String? = null
    ): AiExecutionResult = withContext(Dispatchers.IO) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) {
            return@withContext AiExecutionResult(false, "Prompt cannot be empty.")
        }

        // 1. Check if it's a listing query
        if (isListingQuery(trimmed)) {
            return@withContext listSceneObjects(sceneGraph)
        }

        // 2. Try rule-based / procedural execution first for instant, rock-solid response
        val proceduralResult = tryProceduralExecution(
            prompt = trimmed,
            sceneGraph = sceneGraph,
            camera = camera,
            timeline = timeline,
            selectedNodeId = selectedNodeId,
            createInNewGroup = createInNewGroup
        )
        if (proceduralResult != null) {
            return@withContext proceduralResult
        }

        // 3. If online API key available, query Gemini
        val effectiveApiKey = if (!apiKeyOverride.isNullOrBlank()) apiKeyOverride
        else try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }

        if (effectiveApiKey.isNotBlank() && effectiveApiKey != "MY_GEMINI_API_KEY") {
            try {
                return@withContext executeWithGemini(
                    prompt = trimmed,
                    apiKey = effectiveApiKey,
                    sceneGraph = sceneGraph,
                    camera = camera,
                    timeline = timeline,
                    selectedNodeId = selectedNodeId,
                    createInNewGroup = createInNewGroup
                )
            } catch (e: Exception) {
                // Fallback to intelligent fallback heuristic
                return@withContext executeFallback(trimmed, sceneGraph, camera, timeline, selectedNodeId, createInNewGroup)
            }
        } else {
            // Offline intelligent fallback
            return@withContext executeFallback(trimmed, sceneGraph, camera, timeline, selectedNodeId, createInNewGroup)
        }
    }

    private fun isListingQuery(prompt: String): Boolean {
        val lower = prompt.lowercase()
        return lower.contains("list") && (lower.contains("scene") || lower.contains("object") || lower.contains("item") || lower.contains("node"))
    }

    private fun listSceneObjects(sceneGraph: SceneGraph): AiExecutionResult {
        val nodes = sceneGraph.nodes.values.toList()
        if (nodes.isEmpty()) {
            return AiExecutionResult(true, "The scene is currently empty.")
        }

        val sb = StringBuilder("Scene Items (${nodes.size} total):\n")
        for (node in nodes) {
            val pos = node.baseTransform.position
            val parentName = node.parentId?.let { sceneGraph.getNode(it)?.name } ?: "Root"
            sb.append("• ${node.name} [${node.type}] at (${String.format("%.1f", pos.x)}, ${String.format("%.1f", pos.y)}, ${String.format("%.1f", pos.z)}), Parent: $parentName\n")
        }
        return AiExecutionResult(true, sb.toString().trimEnd())
    }

    private fun tryProceduralExecution(
        prompt: String,
        sceneGraph: SceneGraph,
        camera: EditorCamera,
        timeline: TimelineAsset?,
        selectedNodeId: String?,
        createInNewGroup: Boolean
    ): AiExecutionResult? {
        val lower = prompt.lowercase()

        // 1. Grouping
        if (lower.startsWith("group") || lower.contains("group selected") || lower.contains("create group")) {
            val groupName = extractGroupName(prompt) ?: "New Group"
            val groupNode = SceneNode(
                id = UUID.randomUUID().toString(),
                name = groupName,
                type = SceneNodeType.GROUP,
                baseTransform = Transform(position = camera.target)
            )
            sceneGraph.addNode(groupNode, null)

            // If an object was selected, add it to this group
            if (selectedNodeId != null) {
                sceneGraph.reparentNode(selectedNodeId, groupNode.id)
            }
            return AiExecutionResult(true, "Created group '$groupName' and organized objects.", listOf(groupNode.id))
        }

        // 2. Camera Controls
        if (lower.contains("camera")) {
            if (lower.contains("front") || lower.contains("reset")) {
                camera.target = Vec3(0f, 1f, 0f)
                camera.yaw = 0f
                camera.pitch = 10f
                camera.distance = 6f
                return AiExecutionResult(true, "Camera reset to front view.")
            } else if (lower.contains("top") || lower.contains("overhead")) {
                camera.pitch = 85f
                camera.distance = 12f
                return AiExecutionResult(true, "Camera switched to top-down view.")
            } else if (lower.contains("side") || lower.contains("right")) {
                camera.yaw = 90f
                camera.pitch = 15f
                return AiExecutionResult(true, "Camera switched to side view.")
            } else if (lower.contains("focus") && selectedNodeId != null) {
                val node = sceneGraph.getNode(selectedNodeId)
                if (node != null) {
                    camera.target = node.baseTransform.position
                    return AiExecutionResult(true, "Camera focused on '${node.name}'.")
                }
            }
        }

        // 3. Delete / Remove
        if (lower.startsWith("delete") || lower.startsWith("remove") || lower.contains("delete selected") || lower.contains("remove selected")) {
            if (selectedNodeId != null) {
                val node = sceneGraph.getNode(selectedNodeId)
                val name = node?.name ?: "Selected"
                sceneGraph.removeNode(selectedNodeId)
                return AiExecutionResult(true, "Deleted '$name' from scene.")
            } else if (lower.contains("all") || lower.contains("scene")) {
                val toRemove = sceneGraph.nodes.values.filter { it.type != SceneNodeType.CAMERA }.map { it.id }
                for (id in toRemove) {
                    sceneGraph.removeNode(id)
                }
                return AiExecutionResult(true, "Cleared ${toRemove.size} objects from scene.")
            }
        }

        // 4. Character Spawning (Handled BEFORE standalone animation so "Spawn Steve and make him walk" works!)
        if (lower.contains("spawn") || lower.contains("create character") || lower.contains("add character") || lower.contains("add steve") || lower.contains("add alex") || lower.contains("add zombie")) {
            val isAlex = lower.contains("alex")
            val skinId = when {
                lower.contains("alex") -> "alex"
                lower.contains("zombie") -> "zombie"
                lower.contains("knight") -> "knight"
                lower.contains("miner") -> "miner"
                else -> "steve"
            }

            val countPattern = Pattern.compile("(\\d+)\\s*(?:characters?|steves?|alex(?:es)?|zombies?|miners?|knights?)")
            val countMatcher = countPattern.matcher(lower)
            val spawnCount = if (countMatcher.find()) {
                countMatcher.group(1)?.toIntOrNull()?.coerceIn(1, 10) ?: 1
            } else 1

            val spawnedIds = mutableListOf<String>()
            val createdBlockIds = mutableListOf<String>()
            val charName = skinId.replaceFirstChar { it.uppercase() }

            for (i in 1..spawnCount) {
                val spawnPos = sceneGraph.findNonOverlappingPosition(
                    requiredSpan = Vec3(1.5f, 2.0f, 1.5f),
                    preferredOrigin = camera.target
                )
                val totalCount = sceneGraph.nodes.count { it.value.characterPartType == com.star4droid.mc.animation.engine.scene.CharacterPartType.ROOT } + 1
                val rootId = CharacterFactory.addCharacterToScene(
                    sceneGraph = sceneGraph,
                    name = "$charName $totalCount",
                    isAlex = isAlex,
                    skinId = skinId,
                    position = spawnPos
                )
                spawnedIds.add(rootId)

                // If prompt ALSO requested animation (e.g. "spawn steve and make him walk")
                if (timeline != null && (lower.contains("walk") || lower.contains("run") || lower.contains("jump") || lower.contains("wave") || lower.contains("slide"))) {
                    val animType = when {
                        lower.contains("run") -> ActionBlockType.RUN
                        lower.contains("jump") -> ActionBlockType.JUMP
                        lower.contains("wave") -> ActionBlockType.WAVE
                        lower.contains("slide") -> ActionBlockType.SLIDE_TO_POS
                        else -> ActionBlockType.WALK
                    }
                    val nextStartTime = timeline.actionBlocks.maxOfOrNull { it.startTime + it.duration } ?: 0f
                    val block = ActionBlock(
                        name = "$charName ${animType.displayName}",
                        type = animType,
                        targetNodeId = rootId,
                        startTime = nextStartTime,
                        duration = animType.defaultDuration,
                        trackRow = timeline.actionBlocks.size % 4,
                        startPosition = spawnPos,
                        targetPosition = spawnPos + Vec3(0f, 0f, 3f),
                        enablePositionMove = true
                    )
                    timeline.addActionBlock(block)
                    createdBlockIds.add(block.id)
                }
            }

            val animMsg = if (createdBlockIds.isNotEmpty()) " with animation in timeline." else "."
            return AiExecutionResult(
                true,
                if (spawnCount == 1) "Spawned character '$charName'$animMsg"
                else "Spawned $spawnCount characters at non-overlapping positions$animMsg",
                spawnedIds,
                createdBlockIds
            )
        }

        // 5. Standalone Animation Commands
        if (timeline != null && (lower.contains("walk") || lower.contains("run") || lower.contains("jump") || lower.contains("wave") || lower.contains("slide") || lower.contains("move") || lower.contains("scale"))) {
            val targetId = selectedNodeId ?: sceneGraph.nodes.values.firstOrNull { it.type == SceneNodeType.CHARACTER_ROOT }?.id
                ?: sceneGraph.nodes.values.firstOrNull { it.type != SceneNodeType.CAMERA }?.id

            if (targetId == null) {
                return AiExecutionResult(false, "No character or object found to animate. Please spawn or select an object first.")
            }
            val targetNode = sceneGraph.getNode(targetId) ?: return null

            val type = when {
                lower.contains("run") -> ActionBlockType.RUN
                lower.contains("jump") -> ActionBlockType.JUMP
                lower.contains("wave") -> ActionBlockType.WAVE
                lower.contains("slide") -> ActionBlockType.SLIDE_TO_POS
                lower.contains("scale") -> ActionBlockType.SCALE
                lower.contains("move") -> ActionBlockType.MOVE_TO_POS
                else -> ActionBlockType.WALK
            }

            val nextStartTime = timeline.actionBlocks.maxOfOrNull { it.startTime + it.duration } ?: 0f
            val block = ActionBlock(
                name = "${targetNode.name} ${type.displayName}",
                type = type,
                targetNodeId = targetId,
                startTime = nextStartTime,
                duration = type.defaultDuration,
                trackRow = timeline.actionBlocks.size % 4,
                startPosition = targetNode.baseTransform.position,
                targetPosition = targetNode.baseTransform.position + Vec3(0f, 0f, 3f),
                scaleVector = if (type == ActionBlockType.SCALE) Vec3(1.5f, 1.5f, 1.5f) else Vec3.ONE,
                enablePositionMove = true
            )
            timeline.addActionBlock(block)
            return AiExecutionResult(true, "Added animation block '${type.displayName}' for '${targetNode.name}'.", emptyList(), listOf(block.id))
        }

        // 6. Single Block Placement
        if ((lower.startsWith("add block") || lower.startsWith("place block") || lower.startsWith("create block")) && !lower.contains("house") && !lower.contains("tower")) {
            val tex = when {
                lower.contains("stone") -> "stone"
                lower.contains("cobble") -> "cobblestone"
                lower.contains("brick") -> "bricks"
                lower.contains("dirt") -> "dirt"
                lower.contains("grass") -> "grass_top"
                lower.contains("gold") -> "gold_block"
                lower.contains("diamond") -> "diamond_block"
                lower.contains("obsidian") -> "obsidian"
                lower.contains("glass") -> "glass"
                else -> "wood"
            }
            val blockNode = SceneNode(
                id = UUID.randomUUID().toString(),
                name = "${tex.replaceFirstChar { it.uppercase() }} Block",
                type = SceneNodeType.BLOCK,
                baseTransform = Transform(position = camera.target),
                material = Material(textureAssetId = tex),
                boxDimensions = Vec3.ONE
            )
            sceneGraph.addNode(blockNode, null)
            return AiExecutionResult(true, "Placed ${blockNode.name} at camera center.", listOf(blockNode.id))
        }

        // 5. Structure Generation (House, Castle, Tower, Stairs, Wall, Platform)
        val isStructure = lower.contains("house") || lower.contains("castle") || lower.contains("tower") ||
                lower.contains("stairs") || lower.contains("wall") || lower.contains("room") ||
                lower.contains("pyramid") || lower.contains("cube") || lower.contains("dimension")

        if (isStructure) {
            val dims = parseDimensions(prompt)
            return generateStructure(
                prompt = prompt,
                dims = dims,
                sceneGraph = sceneGraph,
                camera = camera,
                createInNewGroup = createInNewGroup
            )
        }

        return null
    }

    private fun parseDimensions(prompt: String): Vec3 {
        var dx = 4f
        var dy = 3f
        var dz = 4f

        // Pattern [dimension x:10 y:5 z:10] or dimension z:10
        val p = Pattern.compile("(?i)(?:dimension|dim)?\\s*(?:x\\s*[:=]\\s*(\\d+))?\\s*(?:y\\s*[:=]\\s*(\\d+))?\\s*(?:z\\s*[:=]\\s*(\\d+))?")
        val m = p.matcher(prompt)
        while (m.find()) {
            m.group(1)?.toFloatOrNull()?.let { dx = it.coerceIn(1f, 30f) }
            m.group(2)?.toFloatOrNull()?.let { dy = it.coerceIn(1f, 30f) }
            m.group(3)?.toFloatOrNull()?.let { dz = it.coerceIn(1f, 30f) }
        }

        // Also check "5x4x6" or "5x5"
        val p2 = Pattern.compile("(\\d+)\\s*[xX*]\\s*(\\d+)(?:\\s*[xX*]\\s*(\\d+))?")
        val m2 = p2.matcher(prompt)
        if (m2.find()) {
            m2.group(1)?.toFloatOrNull()?.let { dx = it.coerceIn(1f, 30f) }
            m2.group(2)?.toFloatOrNull()?.let {
                if (m2.group(3) != null) {
                    dy = it.coerceIn(1f, 30f)
                    dz = m2.group(3)!!.toFloat().coerceIn(1f, 30f)
                } else {
                    dz = it.coerceIn(1f, 30f)
                }
            }
        }

        return Vec3(dx, dy, dz)
    }

    private fun generateStructure(
        prompt: String,
        dims: Vec3,
        sceneGraph: SceneGraph,
        camera: EditorCamera,
        createInNewGroup: Boolean
    ): AiExecutionResult {
        val lower = prompt.lowercase()
        val createdIds = mutableListOf<String>()

        val width = dims.x.toInt().coerceIn(1, 16)
        val height = dims.y.toInt().coerceIn(1, 16)
        val depth = dims.z.toInt().coerceIn(1, 16)

        val baseOrigin = sceneGraph.findNonOverlappingPosition(
            requiredSpan = Vec3(width.toFloat() + 1f, height.toFloat(), depth.toFloat() + 1f),
            preferredOrigin = camera.target
        )

        val wallTexture = when {
            lower.contains("brick") -> "bricks"
            lower.contains("stone") -> "stone"
            lower.contains("cobble") -> "cobblestone"
            lower.contains("gold") -> "gold_block"
            lower.contains("diamond") -> "diamond_block"
            lower.contains("obsidian") -> "obsidian"
            else -> "wood"
        }
        val roofTexture = if (wallTexture == "wood") "cobblestone" else "wood"

        var parentGroupId: String? = null
        if (createInNewGroup) {
            val groupName = when {
                lower.contains("house") -> "House Structure"
                lower.contains("castle") -> "Castle Structure"
                lower.contains("tower") -> "Tower Structure"
                lower.contains("stairs") -> "Staircase"
                else -> "Generated Structure"
            }
            val groupNode = SceneNode(
                id = UUID.randomUUID().toString(),
                name = "$groupName (${width}x${height}x${depth})",
                type = SceneNodeType.GROUP,
                baseTransform = Transform(position = baseOrigin)
            )
            sceneGraph.addNode(groupNode, null)
            parentGroupId = groupNode.id
            createdIds.add(groupNode.id)
        }

        fun placeBlock(gx: Int, gy: Int, gz: Int, texture: String, name: String) {
            val blockNode = SceneNode(
                id = UUID.randomUUID().toString(),
                name = name,
                type = SceneNodeType.BLOCK,
                baseTransform = Transform(
                    position = baseOrigin + Vec3(gx.toFloat(), gy.toFloat(), gz.toFloat())
                ),
                material = Material(textureAssetId = texture),
                boxDimensions = Vec3.ONE
            )
            sceneGraph.addNode(blockNode, parentGroupId)
            createdIds.add(blockNode.id)
        }

        when {
            lower.contains("tower") -> {
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        for (z in 0 until depth) {
                            if (x == 0 || x == width - 1 || z == 0 || z == depth - 1) {
                                placeBlock(x, y, z, wallTexture, "Tower Wall")
                            }
                        }
                    }
                }
            }

            lower.contains("stairs") -> {
                for (step in 0 until width.coerceAtLeast(height)) {
                    for (z in 0 until depth) {
                        for (y in 0..step) {
                            placeBlock(step, y, z, wallTexture, "Stair Step")
                        }
                    }
                }
            }

            lower.contains("pyramid") -> {
                val levels = width / 2
                for (level in 0..levels) {
                    val y = level
                    for (x in level until (width - level)) {
                        for (z in level until (depth - level)) {
                            placeBlock(x, y, z, wallTexture, "Pyramid Block")
                        }
                    }
                }
            }

            // House or Default Room structure
            else -> {
                // Walls and floor
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        for (z in 0 until depth) {
                            val isWall = x == 0 || x == width - 1 || z == 0 || z == depth - 1
                            val isDoorway = (x == width / 2 && z == 0 && (y == 0 || y == 1))
                            val isWindow = (y == 1 && ((x == 1 && z == 0) || (x == width - 2 && z == 0)))

                            if (y == 0) {
                                // Floor
                                placeBlock(x, y, z, "wood", "Floor Block")
                            } else if (isWall && !isDoorway) {
                                val tex = if (isWindow) "glass" else wallTexture
                                placeBlock(x, y, z, tex, "Wall Block")
                            }
                        }
                    }
                }
                // Roof
                for (x in 0 until width) {
                    for (z in 0 until depth) {
                        placeBlock(x, height, z, roofTexture, "Roof Block")
                    }
                }
            }
        }

        sceneGraph.updateWorldMatrices()
        return AiExecutionResult(
            true,
            "Generated structure (${width}x${height}x${depth}) with ${createdIds.size} blocks.",
            createdIds
        )
    }

    private suspend fun executeWithGemini(
        prompt: String,
        apiKey: String,
        sceneGraph: SceneGraph,
        camera: EditorCamera,
        timeline: TimelineAsset?,
        selectedNodeId: String?,
        createInNewGroup: Boolean
    ): AiExecutionResult {
        val systemPrompt = """
You are an expert Minecraft 3D Scene AI assistant. Convert the user prompt into a structured JSON instruction.
Possible actions:
1. "CREATE_STRUCTURE": {"type":"house"|"tower"|"stairs"|"wall"|"cube", "width":int, "height":int, "depth":int, "material":"wood"|"stone"|"bricks"|"glass"|"gold_block"|"diamond_block"}
2. "SPAWN_CHARACTER": {"skin":"steve"|"alex"|"zombie"|"knight"|"miner"}
3. "ANIMATE": {"action":"walk"|"run"|"jump"|"wave"|"slide", "duration":float}
4. "CAMERA": {"preset":"front"|"top"|"side"|"focus"}
5. "GROUP": {"name":string}

Respond ONLY with valid JSON.
"""

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", "$systemPrompt\nUser request: $prompt")
                }))
            }))
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: Exception) {
            return executeFallback(prompt, sceneGraph, camera, timeline, selectedNodeId, createInNewGroup)
        }

        val body = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            return executeFallback(prompt, sceneGraph, camera, timeline, selectedNodeId, createInNewGroup)
        }

        val json = JSONObject(body)
        val text = json.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text", "") ?: ""

        val cleanJson = text.substringAfter("```json").substringBefore("```").trim()
        val command = try {
            JSONObject(if (cleanJson.startsWith("{")) cleanJson else text)
        } catch (e: Exception) {
            return executeFallback(prompt, sceneGraph, camera, timeline, selectedNodeId, createInNewGroup)
        }

        val action = command.optString("action", "")
        return when (action) {
            "CREATE_STRUCTURE" -> {
                val w = command.optInt("width", 4).toFloat()
                val h = command.optInt("height", 3).toFloat()
                val d = command.optInt("depth", 4).toFloat()
                generateStructure(prompt, Vec3(w, h, d), sceneGraph, camera, createInNewGroup)
            }
            "SPAWN_CHARACTER" -> {
                val skin = command.optString("skin", "steve")
                val spawnPos = sceneGraph.findNonOverlappingPosition(
                    requiredSpan = Vec3(1.5f, 2.0f, 1.5f),
                    preferredOrigin = camera.target
                )
                val rootId = CharacterFactory.addCharacterToScene(
                    sceneGraph = sceneGraph,
                    name = skin.replaceFirstChar { it.uppercase() },
                    isAlex = skin == "alex",
                    skinId = skin,
                    position = spawnPos
                )
                AiExecutionResult(true, "Spawned character '$skin'.", listOf(rootId))
            }
            "ANIMATE" -> {
                val targetId = selectedNodeId ?: sceneGraph.nodes.values.firstOrNull { it.type == SceneNodeType.CHARACTER_ROOT }?.id
                if (targetId != null && timeline != null) {
                    val anim = command.optString("type", command.optString("anim", "walk")).lowercase()
                    val animType = when {
                        anim.contains("run") -> ActionBlockType.RUN
                        anim.contains("jump") -> ActionBlockType.JUMP
                        anim.contains("wave") -> ActionBlockType.WAVE
                        anim.contains("slide") -> ActionBlockType.SLIDE_TO_POS
                        anim.contains("scale") -> ActionBlockType.SCALE
                        else -> ActionBlockType.WALK
                    }
                    val targetNode = sceneGraph.getNode(targetId)
                    val nextStart = timeline.actionBlocks.maxOfOrNull { it.startTime + it.duration } ?: 0f
                    val block = ActionBlock(
                        name = "${targetNode?.name ?: "Character"} ${animType.displayName}",
                        type = animType,
                        targetNodeId = targetId,
                        startTime = nextStart,
                        duration = animType.defaultDuration,
                        trackRow = timeline.actionBlocks.size % 4,
                        startPosition = targetNode?.baseTransform?.position ?: Vec3.ZERO,
                        targetPosition = (targetNode?.baseTransform?.position ?: Vec3.ZERO) + Vec3(0f, 0f, 3f),
                        enablePositionMove = true
                    )
                    timeline.addActionBlock(block)
                    AiExecutionResult(true, "Added animation '${animType.displayName}' for '${targetNode?.name}'.", emptyList(), listOf(block.id))
                } else {
                    executeFallback(prompt, sceneGraph, camera, timeline, selectedNodeId, createInNewGroup)
                }
            }
            "CAMERA" -> {
                val preset = command.optString("preset", "front").lowercase()
                when (preset) {
                    "top" -> { camera.pitch = 85f; camera.distance = 12f }
                    "side" -> { camera.yaw = 90f; camera.pitch = 15f; camera.distance = 6f }
                    else -> { camera.target = Vec3(0f, 1f, 0f); camera.yaw = 0f; camera.pitch = 10f; camera.distance = 6f }
                }
                AiExecutionResult(true, "Switched camera to $preset view.")
            }
            "GROUP" -> {
                val name = command.optString("name", "New Group")
                val groupNode = SceneNode(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    type = SceneNodeType.GROUP,
                    baseTransform = Transform(position = camera.target)
                )
                sceneGraph.addNode(groupNode, null)
                if (selectedNodeId != null) sceneGraph.reparentNode(selectedNodeId, groupNode.id)
                AiExecutionResult(true, "Grouped objects under '$name'.", listOf(groupNode.id))
            }
            else -> executeFallback(prompt, sceneGraph, camera, timeline, selectedNodeId, createInNewGroup)
        }
    }

    private fun executeFallback(
        prompt: String,
        sceneGraph: SceneGraph,
        camera: EditorCamera,
        timeline: TimelineAsset?,
        selectedNodeId: String?,
        createInNewGroup: Boolean
    ): AiExecutionResult {
        // Try procedural one more time
        val procedural = tryProceduralExecution(prompt, sceneGraph, camera, timeline, selectedNodeId, createInNewGroup)
        if (procedural != null) return procedural

        val lower = prompt.lowercase()
        if (lower.contains("house") || lower.contains("tower") || lower.contains("wall") || lower.contains("stairs") || lower.contains("castle") || lower.contains("cube")) {
            val dims = parseDimensions(prompt)
            return generateStructure(prompt, dims, sceneGraph, camera, createInNewGroup)
        }

        return AiExecutionResult(
            false,
            "Could not interpret '$prompt'. Try commands like:\n• 'Spawn Steve and make him walk'\n• 'Build a wooden house [dimension x:6 y:4 z:8]'\n• 'Build a stone tower 8 blocks high'\n• 'Make selected jump'\n• 'Set camera to top view'"
        )
    }

    private fun extractGroupName(prompt: String): String? {
        val pattern = Pattern.compile("(?i)(?:as|named|name|to)\\s+[\"']?([a-zA-Z0-9_ ]+)[\"']?")
        val matcher = pattern.matcher(prompt)
        return if (matcher.find()) matcher.group(1)?.trim() else null
    }
}
