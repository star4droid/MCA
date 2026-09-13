package com.star4droid.mc.animation.project

import android.content.Context
import com.star4droid.mc.animation.animation.ActionBlock
import com.star4droid.mc.animation.animation.ActionBlockType
import com.star4droid.mc.animation.animation.AnimationTrack
import com.star4droid.mc.animation.animation.Interpolation
import com.star4droid.mc.animation.animation.Keyframe
import com.star4droid.mc.animation.animation.TimelineAsset
import com.star4droid.mc.animation.animation.TimelineInstance
import com.star4droid.mc.animation.animation.presets.PresetGenerator
import com.star4droid.mc.animation.animation.presets.PresetType
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class ProjectRepository(private val context: Context) {

    private val externalBaseDir: File
        get() {
            val ext = context.getExternalFilesDir(null)
            return ext ?: context.filesDir
        }

    private fun sanitizeFolderName(name: String): String {
        val sanitized = name.replace(Regex("[^a-zA-Z0-9_\\-\\s]"), "").trim()
        return if (sanitized.isNotBlank()) sanitized else "Project"
    }

    fun getProjectDir(id: String, name: String = ""): File {
        val base = externalBaseDir

        if (name.isNotBlank()) {
            val nameDir = File(base, sanitizeFolderName(name))
            if (nameDir.exists() && File(nameDir, "project.json").exists()) {
                return nameDir
            }
        }

        val directDirs = base.listFiles() ?: emptyArray()
        for (d in directDirs) {
            if (d.isDirectory && d.name != "projects") {
                val pf = File(d, "project.json")
                if (pf.exists()) {
                    try {
                        val json = JSONObject(pf.readText())
                        if (json.optString("id") == id) return d
                    } catch (e: Exception) {}
                }
            }
        }

        val legacyDir = File(File(base, "projects"), id)
        if (legacyDir.exists()) return legacyDir
        val legacyInternal = File(File(context.filesDir, "projects"), id)
        if (legacyInternal.exists()) return legacyInternal

        val folderName = if (name.isNotBlank()) sanitizeFolderName(name) else id
        val newDir = File(base, folderName)
        ensureProjectSubdirs(newDir)
        return newDir
    }

    fun ensureProjectSubdirs(projectDir: File) {
        if (!projectDir.exists()) projectDir.mkdirs()
        File(projectDir, "models").apply { if (!exists()) mkdirs() }
        File(projectDir, "sounds").apply { if (!exists()) mkdirs() }
        File(projectDir, "textures").apply { if (!exists()) mkdirs() }
        File(projectDir, "scenes").apply { if (!exists()) mkdirs() }
        File(projectDir, "animations").apply { if (!exists()) mkdirs() }
        File(projectDir, "timelines").apply { if (!exists()) mkdirs() }
    }

    fun listProjects(): List<ProjectMetadata> {
        val list = mutableListOf<ProjectMetadata>()
        val seenIds = mutableSetOf<String>()

        fun scanDir(parent: File) {
            val dirs = parent.listFiles() ?: return
            for (dir in dirs) {
                if (dir.isDirectory) {
                    val metaFile = File(dir, "project.json")
                    if (metaFile.exists()) {
                        try {
                            val json = JSONObject(metaFile.readText())
                            val id = json.getString("id")
                            if (!seenIds.contains(id)) {
                                seenIds.add(id)
                                list.add(
                                    ProjectMetadata(
                                        id = id,
                                        name = json.optString("name", "Untitled"),
                                        version = json.optInt("version", 1),
                                        createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                                        updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
                                        nodeCount = json.optInt("nodeCount", 0),
                                        timelineCount = json.optInt("timelineCount", 1)
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }

        scanDir(externalBaseDir)

        val legacyExt = File(externalBaseDir, "projects")
        if (legacyExt.exists()) scanDir(legacyExt)

        val legacyInt = File(context.filesDir, "projects")
        if (legacyInt.exists()) scanDir(legacyInt)

        return list.sortedByDescending { it.updatedAt }
    }

    fun createProject(name: String, template: String = "steve"): String {
        val id = UUID.randomUUID().toString()
        val projectDir = getProjectDir(id, name)
        ensureProjectSubdirs(projectDir)

        val sceneGraph = SceneGraph()
        val timelines = mutableListOf<TimelineAsset>()
        val instances = mutableListOf<TimelineInstance>()

        // Default Camera
        val cameraNode = SceneNode(
            id = UUID.randomUUID().toString(),
            name = "Camera Main",
            type = SceneNodeType.CAMERA,
            baseTransform = Transform(
                position = Vec3(0f, 2.5f, 5.0f),
                rotation = Vec3(-15f, 0f, 0f)
            ),
            animatedTransform = Transform(
                position = Vec3(0f, 2.5f, 5.0f),
                rotation = Vec3(-15f, 0f, 0f)
            ),
            cameraData = CameraData(fov = 60f, near = 0.1f, far = 500f, enabled = true)
        )
        sceneGraph.addNode(cameraNode)

        // Default Sun / Light
        val lightNode = SceneNode(
            id = UUID.randomUUID().toString(),
            name = "Sun Light",
            type = SceneNodeType.LIGHT,
            baseTransform = Transform(
                position = Vec3(5f, 10f, 5f),
                rotation = Vec3(45f, 30f, 0f)
            ),
            animatedTransform = Transform(
                position = Vec3(5f, 10f, 5f),
                rotation = Vec3(45f, 30f, 0f)
            ),
            lightData = LightData(
                color = 0xFFFFF8E7.toInt(),
                intensity = 1.2f,
                timeOfDay = TimeOfDay.NOON
            )
        )
        sceneGraph.addNode(lightNode)

        // Default Ground Block Platform
        val ground = SceneNode(
            id = UUID.randomUUID().toString(),
            name = "Ground",
            type = SceneNodeType.GROUND,
            baseTransform = Transform(
                position = Vec3(0f, -0.5f, 0f),
                scale = Vec3(10f, 1f, 10f)
            ),
            animatedTransform = Transform(
                position = Vec3(0f, -0.5f, 0f),
                scale = Vec3(10f, 1f, 10f)
            ),
            material = Material(textureAssetId = "grass"),
            boxDimensions = Vec3(1f, 1f, 1f)
        )
        sceneGraph.addNode(ground)

        // Main Timeline
        val mainTimeline = TimelineAsset(
            id = UUID.randomUUID().toString(),
            name = "Main Timeline",
            duration = 10.0f
        )
        timelines.add(mainTimeline)
        instances.add(TimelineInstance(timelineAssetId = mainTimeline.id))

        if (template == "steve") {
            // Add Steve Character in default rest pose
            CharacterFactory.addCharacterToScene(
                sceneGraph = sceneGraph,
                name = "Steve",
                isAlex = false,
                skinId = "steve",
                position = Vec3(0f, 0f, 0f)
            )
        }

        val metadata = ProjectMetadata(
            id = id,
            name = name,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            nodeCount = sceneGraph.nodes.size,
            timelineCount = timelines.size
        )

        saveProject(id, metadata, sceneGraph, timelines, instances)
        return id
    }

    fun loadProject(id: String): LoadedProject? {
        val dir = getProjectDir(id)
        if (!dir.exists()) return null

        val metaFile = File(dir, "project.json")
        val sceneFileInSub = File(File(dir, "scenes"), "scene.json")
        val sceneFileInRoot = File(dir, "scene.json")
        val sceneFile = if (sceneFileInSub.exists()) sceneFileInSub else sceneFileInRoot

        if (!metaFile.exists() || !sceneFile.exists()) return null

        try {
            val metaJson = JSONObject(metaFile.readText())
            val metadata = ProjectMetadata(
                id = metaJson.getString("id"),
                name = metaJson.optString("name", "Untitled"),
                version = metaJson.optInt("version", 1),
                createdAt = metaJson.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = metaJson.optLong("updatedAt", System.currentTimeMillis()),
                nodeCount = metaJson.optInt("nodeCount", 0),
                timelineCount = metaJson.optInt("timelineCount", 1)
            )

            val sceneGraph = SceneGraph()
            val sceneJson = JSONObject(sceneFile.readText())
            val nodesArr = sceneJson.getJSONArray("nodes")
            for (i in 0 until nodesArr.length()) {
                val nodeObj = nodesArr.getJSONObject(i)
                val node = deserializeNode(nodeObj)
                sceneGraph.nodes[node.id] = node
            }

            val rootsArr = sceneJson.optJSONArray("rootNodeIds")
            if (rootsArr != null) {
                for (i in 0 until rootsArr.length()) {
                    sceneGraph.rootNodeIds.add(rootsArr.getString(i))
                }
            } else {
                sceneGraph.rootNodeIds.addAll(
                    sceneGraph.nodes.values.filter { it.parentId == null }.map { it.id }
                )
            }
            sceneGraph.updateWorldMatrices()

            // Load timelines
            val timelines = mutableListOf<TimelineAsset>()
            val animDir = File(dir, "animations")
            val tlDir = File(dir, "timelines")
            for (tDir in listOf(animDir, tlDir)) {
                if (tDir.exists()) {
                    val tlFiles = tDir.listFiles() ?: emptyArray()
                    for (tlFile in tlFiles) {
                        if (tlFile.name.endsWith(".json")) {
                            try {
                                val tlJson = JSONObject(tlFile.readText())
                                val tl = deserializeTimeline(tlJson)
                                if (timelines.none { it.id == tl.id }) {
                                    timelines.add(tl)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }

            if (timelines.isEmpty()) {
                timelines.add(TimelineAsset(name = "Main Timeline"))
            }

            // Load instances
            val instances = mutableListOf<TimelineInstance>()
            val instArr = sceneJson.optJSONArray("timelineInstances")
            if (instArr != null) {
                for (i in 0 until instArr.length()) {
                    val obj = instArr.getJSONObject(i)
                    instances.add(
                        TimelineInstance(
                            timelineAssetId = obj.getString("timelineAssetId"),
                            targetRootObjectId = obj.optString("targetRootObjectId", null),
                            startTime = obj.optDouble("startTime", 0.0).toFloat(),
                            speed = obj.optDouble("speed", 1.0).toFloat(),
                            loop = obj.optBoolean("loop", false),
                            offset = obj.optDouble("offset", 0.0).toFloat()
                        )
                    )
                }
            } else {
                for (tl in timelines) {
                    instances.add(TimelineInstance(timelineAssetId = tl.id))
                }
            }

            return LoadedProject(metadata, sceneGraph, timelines, instances)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun saveProject(
        id: String,
        metadata: ProjectMetadata,
        sceneGraph: SceneGraph,
        timelines: List<TimelineAsset>,
        timelineInstances: List<TimelineInstance>
    ) {
        val dir = getProjectDir(id, metadata.name)
        ensureProjectSubdirs(dir)
        metadata.updatedAt = System.currentTimeMillis()
        metadata.nodeCount = sceneGraph.nodes.size
        metadata.timelineCount = timelines.size

        // 1. project.json
        val metaJson = JSONObject().apply {
            put("id", metadata.id)
            put("name", metadata.name)
            put("version", metadata.version)
            put("createdAt", metadata.createdAt)
            put("updatedAt", metadata.updatedAt)
            put("nodeCount", metadata.nodeCount)
            put("timelineCount", metadata.timelineCount)
        }
        File(dir, "project.json").writeText(metaJson.toString(2))

        // 2. scene.json in scenes/ (and root for legacy compatibility)
        val sceneJson = JSONObject().apply {
            val nodesArr = JSONArray()
            for (node in sceneGraph.nodes.values) {
                nodesArr.put(serializeNode(node))
            }
            put("nodes", nodesArr)

            val rootsArr = JSONArray()
            for (rootId in sceneGraph.rootNodeIds) {
                rootsArr.put(rootId)
            }
            put("rootNodeIds", rootsArr)

            val instArr = JSONArray()
            for (inst in timelineInstances) {
                instArr.put(JSONObject().apply {
                    put("timelineAssetId", inst.timelineAssetId)
                    put("targetRootObjectId", inst.targetRootObjectId)
                    put("startTime", inst.startTime.toDouble())
                    put("speed", inst.speed.toDouble())
                    put("loop", inst.loop)
                    put("offset", inst.offset.toDouble())
                })
            }
            put("timelineInstances", instArr)
        }
        val scenesDir = File(dir, "scenes").apply { if (!exists()) mkdirs() }
        File(scenesDir, "scene.json").writeText(sceneJson.toString(2))
        File(dir, "scene.json").writeText(sceneJson.toString(2))

        // 3. animations/ and timelines/
        val animDir = File(dir, "animations").apply { if (!exists()) mkdirs() }
        val tlDir = File(dir, "timelines").apply { if (!exists()) mkdirs() }
        for (tl in timelines) {
            val tlJson = serializeTimeline(tl)
            val text = tlJson.toString(2)
            File(animDir, "${tl.id}.json").writeText(text)
            File(tlDir, "${tl.id}.json").writeText(text)
        }
    }

    fun duplicateProject(id: String): String? {
        val loaded = loadProject(id) ?: return null
        val newId = UUID.randomUUID().toString()
        val newMeta = loaded.metadata.copy(
            id = newId,
            name = "${loaded.metadata.name} Copy",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        saveProject(newId, newMeta, loaded.sceneGraph, loaded.timelines, loaded.timelineInstances)
        return newId
    }

    fun deleteProject(id: String): Boolean {
        val dir = getProjectDir(id)
        return if (dir.exists()) dir.deleteRecursively() else false
    }

    fun renameProject(id: String, newName: String): Boolean {
        val loaded = loadProject(id) ?: return false
        loaded.metadata.name = newName
        saveProject(id, loaded.metadata, loaded.sceneGraph, loaded.timelines, loaded.timelineInstances)
        return true
    }

    private fun serializeNode(node: SceneNode): JSONObject = JSONObject().apply {
        put("id", node.id)
        put("name", node.name)
        put("type", node.type.name)
        put("parentId", node.parentId)
        put("children", JSONArray(node.children))
        put("baseTransform", serializeTransform(node.baseTransform))
        put("visible", node.visible)
        put("material", JSONObject().apply {
            put("textureAssetId", node.material.textureAssetId)
            put("color", node.material.color)
            put("opacity", node.material.opacity.toDouble())
        })
        node.characterPartType?.let { put("characterPartType", it.name) }
        put("characterSkinId", node.characterSkinId)
        put("boxDimensions", JSONObject().apply {
            put("x", node.boxDimensions.x.toDouble())
            put("y", node.boxDimensions.y.toDouble())
            put("z", node.boxDimensions.z.toDouble())
        })
        node.cameraData?.let {
            put("cameraData", JSONObject().apply {
                put("fov", it.fov.toDouble())
                put("near", it.near.toDouble())
                put("far", it.far.toDouble())
                put("enabled", it.enabled)
            })
        }
        node.lightData?.let {
            put("lightData", JSONObject().apply {
                put("color", it.color)
                put("intensity", it.intensity.toDouble())
                put("timeOfDay", it.timeOfDay.name)
                put("shadows", it.shadows)
            })
        }
    }

    private fun deserializeNode(obj: JSONObject): SceneNode {
        val id = obj.getString("id")
        val name = obj.optString("name", "Node")
        val type = try {
            SceneNodeType.valueOf(obj.optString("type", SceneNodeType.BLOCK.name))
        } catch (e: Exception) { SceneNodeType.BLOCK }

        val parentId = if (obj.has("parentId") && !obj.isNull("parentId")) obj.getString("parentId") else null
        val children = mutableListOf<String>()
        val childrenArr = obj.optJSONArray("children")
        if (childrenArr != null) {
            for (i in 0 until childrenArr.length()) {
                children.add(childrenArr.getString(i))
            }
        }

        val baseTransform = if (obj.has("baseTransform")) {
            deserializeTransform(obj.getJSONObject("baseTransform"))
        } else Transform()

        val visible = obj.optBoolean("visible", true)

        val matObj = obj.optJSONObject("material")
        val material = if (matObj != null) {
            Material(
                textureAssetId = matObj.optString("textureAssetId", "grass"),
                color = matObj.optInt("color", 0xFFFFFFFF.toInt()),
                opacity = matObj.optDouble("opacity", 1.0).toFloat()
            )
        } else Material()

        val charPart = if (obj.has("characterPartType")) {
            try { CharacterPartType.valueOf(obj.getString("characterPartType")) } catch (e: Exception) { null }
        } else null

        val skinId = obj.optString("characterSkinId", "steve")

        val boxObj = obj.optJSONObject("boxDimensions")
        val boxDimensions = if (boxObj != null) {
            Vec3(
                boxObj.optDouble("x", 1.0).toFloat(),
                boxObj.optDouble("y", 1.0).toFloat(),
                boxObj.optDouble("z", 1.0).toFloat()
            )
        } else Vec3.ONE

        val camObj = obj.optJSONObject("cameraData")
        val cameraData = if (camObj != null) {
            CameraData(
                fov = camObj.optDouble("fov", 60.0).toFloat(),
                near = camObj.optDouble("near", 0.1).toFloat(),
                far = camObj.optDouble("far", 1000.0).toFloat(),
                enabled = camObj.optBoolean("enabled", true)
            )
        } else null

        val lightObj = obj.optJSONObject("lightData")
        val lightData = if (lightObj != null) {
            val tod = try {
                TimeOfDay.valueOf(lightObj.optString("timeOfDay", TimeOfDay.NOON.name))
            } catch (e: Exception) { TimeOfDay.NOON }
            LightData(
                color = lightObj.optInt("color", 0xFFFFF2D4.toInt()),
                intensity = lightObj.optDouble("intensity", 1.2).toFloat(),
                timeOfDay = tod,
                shadows = lightObj.optBoolean("shadows", true)
            )
        } else null

        return SceneNode(
            id = id,
            name = name,
            type = type,
            parentId = parentId,
            children = children,
            baseTransform = baseTransform,
            animatedTransform = baseTransform.copyTransform(),
            visible = visible,
            material = material,
            characterPartType = charPart,
            characterSkinId = skinId,
            boxDimensions = boxDimensions,
            cameraData = cameraData,
            lightData = lightData
        )
    }

    private fun serializeTransform(t: Transform): JSONObject = JSONObject().apply {
        put("px", t.position.x.toDouble()); put("py", t.position.y.toDouble()); put("pz", t.position.z.toDouble())
        put("rx", t.rotation.x.toDouble()); put("ry", t.rotation.y.toDouble()); put("rz", t.rotation.z.toDouble())
        put("sx", t.scale.x.toDouble()); put("sy", t.scale.y.toDouble()); put("sz", t.scale.z.toDouble())
        put("pivX", t.pivot.x.toDouble()); put("pivY", t.pivot.y.toDouble()); put("pivZ", t.pivot.z.toDouble())
    }

    private fun deserializeTransform(obj: JSONObject): Transform = Transform(
        position = Vec3(
            obj.optDouble("px", 0.0).toFloat(),
            obj.optDouble("py", 0.0).toFloat(),
            obj.optDouble("pz", 0.0).toFloat()
        ),
        rotation = Vec3(
            obj.optDouble("rx", 0.0).toFloat(),
            obj.optDouble("ry", 0.0).toFloat(),
            obj.optDouble("rz", 0.0).toFloat()
        ),
        scale = Vec3(
            obj.optDouble("sx", 1.0).toFloat(),
            obj.optDouble("sy", 1.0).toFloat(),
            obj.optDouble("sz", 1.0).toFloat()
        ),
        pivot = Vec3(
            obj.optDouble("pivX", 0.0).toFloat(),
            obj.optDouble("pivY", 0.0).toFloat(),
            obj.optDouble("pivZ", 0.0).toFloat()
        )
    )

    private fun serializeVec3(v: Vec3): JSONObject = JSONObject().apply {
        put("x", v.x.toDouble())
        put("y", v.y.toDouble())
        put("z", v.z.toDouble())
    }

    private fun deserializeVec3(obj: JSONObject): Vec3 = Vec3(
        obj.optDouble("x", 0.0).toFloat(),
        obj.optDouble("y", 0.0).toFloat(),
        obj.optDouble("z", 0.0).toFloat()
    )

    private fun serializeTimeline(tl: TimelineAsset): JSONObject = JSONObject().apply {
        put("id", tl.id)
        put("name", tl.name)
        put("duration", tl.duration.toDouble())
        val tracksArr = JSONArray()
        for (track in tl.tracks) {
            tracksArr.put(JSONObject().apply {
                put("id", track.id)
                put("targetObjectId", track.targetObjectId)
                put("propertyPath", track.propertyPath)
                val kfArr = JSONArray()
                for (kf in track.keyframes) {
                    kfArr.put(JSONObject().apply {
                        put("id", kf.id)
                        put("time", kf.time.toDouble())
                        put("value", kf.value.toDouble())
                        put("interpolation", kf.interpolation.name)
                    })
                }
                put("keyframes", kfArr)
            })
        }
        put("tracks", tracksArr)

        val blocksArr = JSONArray()
        for (block in tl.actionBlocks) {
            blocksArr.put(JSONObject().apply {
                put("id", block.id)
                put("name", block.name)
                put("type", block.type.name)
                put("targetNodeId", block.targetNodeId)
                put("startTime", block.startTime.toDouble())
                put("duration", block.duration.toDouble())
                put("trackRow", block.trackRow)
                put("speed", block.speed.toDouble())
                put("enablePositionMove", block.enablePositionMove)
                put("stepSize", block.stepSize.toDouble())
                put("hasCustomSettings", block.hasCustomSettings)
                put("targetPosition", serializeVec3(block.targetPosition))
                block.startPosition?.let { put("startPosition", serializeVec3(it)) }
                put("moveVector", serializeVec3(block.moveVector))
                put("scaleVector", serializeVec3(block.scaleVector))
                block.clipFileName?.let { put("clipFileName", it) }
                put("angle", block.angle.toDouble())
                put("jumpHeight", block.jumpHeight.toDouble())
                put("amplitude", block.amplitude.toDouble())
                block.customJson?.let { put("customJson", it) }
                put("isDeltaBased", block.isDeltaBased)
            })
        }
        put("actionBlocks", blocksArr)
    }

    fun saveAnimationFile(projectId: String, name: String, blocks: List<ActionBlock>): File {
        val dir = if (projectId.isNotBlank()) getProjectDir(projectId) else externalBaseDir
        val animDir = File(dir, "animations").apply { if (!exists()) mkdirs() }
        val sanitized = name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val file = File(animDir, "$sanitized.mcanim")
        val root = JSONObject().apply {
            put("name", name)
            put("version", 1)
            val arr = JSONArray()
            for (block in blocks) {
                arr.put(JSONObject().apply {
                    put("id", block.id)
                    put("name", block.name)
                    put("type", block.type.name)
                    put("targetNodeId", block.targetNodeId)
                    put("startTime", block.startTime.toDouble())
                    put("duration", block.duration.toDouble())
                    put("trackRow", block.trackRow)
                    put("speed", block.speed.toDouble())
                    put("enablePositionMove", block.enablePositionMove)
                    put("stepSize", block.stepSize.toDouble())
                    put("hasCustomSettings", block.hasCustomSettings)
                    put("targetPosition", serializeVec3(block.targetPosition))
                    block.startPosition?.let { put("startPosition", serializeVec3(it)) }
                    put("moveVector", serializeVec3(block.moveVector))
                    put("scaleVector", serializeVec3(block.scaleVector))
                    block.clipFileName?.let { put("clipFileName", it) }
                    put("angle", block.angle.toDouble())
                    put("jumpHeight", block.jumpHeight.toDouble())
                    put("amplitude", block.amplitude.toDouble())
                    block.customJson?.let { put("customJson", it) }
                    put("isDeltaBased", block.isDeltaBased)
                })
            }
            put("blocks", arr)
        }
        file.writeText(root.toString(2))
        return file
    }

    fun saveAnimationFile(name: String, blocks: List<ActionBlock>): File = saveAnimationFile("", name, blocks)

    fun listSavedAnimations(projectId: String = ""): List<File> {
        val result = mutableListOf<File>()
        if (projectId.isNotBlank()) {
            val projDir = getProjectDir(projectId)
            val projAnimDir = File(projDir, "animations")
            if (projAnimDir.exists()) {
                projAnimDir.listFiles()?.filter { it.name.endsWith(".mcanim") }?.let { result.addAll(it) }
            }
        }
        val globalAnimDir = File(externalBaseDir, "saved_animations")
        if (globalAnimDir.exists()) {
            globalAnimDir.listFiles()?.filter { it.name.endsWith(".mcanim") }?.forEach { f ->
                if (result.none { it.name == f.name }) {
                    result.add(f)
                }
            }
        }
        return result.sortedBy { it.name }
    }

    fun loadAnimationFile(file: File): List<ActionBlock> {
        if (!file.exists()) return emptyList()
        val list = mutableListOf<ActionBlock>()
        try {
            val root = JSONObject(file.readText())
            val arr = root.optJSONArray("blocks") ?: return emptyList()
            for (i in 0 until arr.length()) {
                val bObj = arr.getJSONObject(i)
                val type = try {
                    ActionBlockType.valueOf(bObj.optString("type", ActionBlockType.WALK.name))
                } catch (e: Exception) { ActionBlockType.WALK }

                val targetPos = bObj.optJSONObject("targetPosition")?.let { deserializeVec3(it) } ?: Vec3.ZERO
                val startPos = bObj.optJSONObject("startPosition")?.let { deserializeVec3(it) }
                val moveVec = bObj.optJSONObject("moveVector")?.let { deserializeVec3(it) } ?: Vec3(0f, 0f, 2f)
                val scaleVec = bObj.optJSONObject("scaleVector")?.let { deserializeVec3(it) } ?: Vec3.ONE

                val block = ActionBlock(
                    id = UUID.randomUUID().toString(),
                    name = bObj.optString("name", "${type.displayName} Block"),
                    type = type,
                    targetNodeId = bObj.optString("targetNodeId", ""),
                    startTime = bObj.optDouble("startTime", 0.0).toFloat(),
                    duration = bObj.optDouble("duration", type.defaultDuration.toDouble()).toFloat(),
                    trackRow = bObj.optInt("trackRow", 0),
                    speed = bObj.optDouble("speed", 1.0).toFloat(),
                    enablePositionMove = bObj.optBoolean("enablePositionMove", false),
                    stepSize = bObj.optDouble("stepSize", 1.0).toFloat(),
                    hasCustomSettings = bObj.optBoolean("hasCustomSettings", false),
                    targetPosition = targetPos,
                    startPosition = startPos,
                    moveVector = moveVec,
                    scaleVector = scaleVec,
                    clipFileName = bObj.optString("clipFileName").ifEmpty { null },
                    angle = bObj.optDouble("angle", 90.0).toFloat(),
                    jumpHeight = bObj.optDouble("jumpHeight", 1.5).toFloat(),
                    amplitude = bObj.optDouble("amplitude", 1.0).toFloat(),
                    customJson = bObj.optString("customJson").ifEmpty { null },
                    isDeltaBased = bObj.optBoolean("isDeltaBased", false)
                )
                list.add(block)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun listSavedAnimations(): List<File> {
        val animDir = File(externalBaseDir, "saved_animations").apply { if (!exists()) mkdirs() }
        return (animDir.listFiles() ?: emptyArray()).filter { it.name.endsWith(".mcanim") }.sortedBy { it.name }
    }

    fun getSavedAnimationFiles(): List<File> = listSavedAnimations()

    private fun deserializeTimeline(obj: JSONObject): TimelineAsset {
        val id = obj.getString("id")
        val name = obj.optString("name", "Timeline")
        val duration = obj.optDouble("duration", 10.0).toFloat()
        val timeline = TimelineAsset(id = id, name = name, duration = duration)

        val tracksArr = obj.optJSONArray("tracks")
        if (tracksArr != null) {
            for (i in 0 until tracksArr.length()) {
                val tObj = tracksArr.getJSONObject(i)
                val track = AnimationTrack(
                    id = tObj.optString("id", UUID.randomUUID().toString()),
                    targetObjectId = tObj.getString("targetObjectId"),
                    propertyPath = tObj.getString("propertyPath")
                )
                val kfArr = tObj.optJSONArray("keyframes")
                if (kfArr != null) {
                    for (j in 0 until kfArr.length()) {
                        val kObj = kfArr.getJSONObject(j)
                        val interp = try {
                            Interpolation.valueOf(kObj.optString("interpolation", Interpolation.LINEAR.name))
                        } catch (e: Exception) { Interpolation.LINEAR }
                        track.keyframes.add(
                            Keyframe(
                                id = kObj.optString("id", UUID.randomUUID().toString()),
                                time = kObj.optDouble("time", 0.0).toFloat(),
                                value = kObj.optDouble("value", 0.0).toFloat(),
                                interpolation = interp
                            )
                        )
                    }
                }
                timeline.tracks.add(track)
            }
        }

        val blocksArr = obj.optJSONArray("actionBlocks")
        if (blocksArr != null) {
            for (i in 0 until blocksArr.length()) {
                val bObj = blocksArr.getJSONObject(i)
                val type = try {
                    ActionBlockType.valueOf(bObj.optString("type", ActionBlockType.WALK.name))
                } catch (e: Exception) { ActionBlockType.WALK }

                val targetPos = bObj.optJSONObject("targetPosition")?.let { deserializeVec3(it) } ?: Vec3.ZERO
                val startPos = bObj.optJSONObject("startPosition")?.let { deserializeVec3(it) }
                val moveVec = bObj.optJSONObject("moveVector")?.let { deserializeVec3(it) } ?: Vec3(0f, 0f, 2f)
                val scaleVec = bObj.optJSONObject("scaleVector")?.let { deserializeVec3(it) } ?: Vec3.ONE

                val block = ActionBlock(
                    id = bObj.optString("id", UUID.randomUUID().toString()),
                    name = bObj.optString("name", "${type.displayName} Block"),
                    type = type,
                    targetNodeId = bObj.optString("targetNodeId", ""),
                    startTime = bObj.optDouble("startTime", 0.0).toFloat(),
                    duration = bObj.optDouble("duration", type.defaultDuration.toDouble()).toFloat(),
                    trackRow = bObj.optInt("trackRow", 0),
                    speed = bObj.optDouble("speed", 1.0).toFloat(),
                    enablePositionMove = bObj.optBoolean("enablePositionMove", false),
                    stepSize = bObj.optDouble("stepSize", 1.0).toFloat(),
                    hasCustomSettings = bObj.optBoolean("hasCustomSettings", false),
                    targetPosition = targetPos,
                    startPosition = startPos,
                    moveVector = moveVec,
                    scaleVector = scaleVec,
                    clipFileName = bObj.optString("clipFileName").ifEmpty { null },
                    angle = bObj.optDouble("angle", 90.0).toFloat(),
                    jumpHeight = bObj.optDouble("jumpHeight", 1.5).toFloat(),
                    amplitude = bObj.optDouble("amplitude", 1.0).toFloat(),
                    customJson = bObj.optString("customJson").ifEmpty { null },
                    isDeltaBased = bObj.optBoolean("isDeltaBased", false)
                )
                timeline.actionBlocks.add(block)
            }
        }
        return timeline
    }
}
