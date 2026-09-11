package com.star4droid.mc.animation.project

import android.content.Context
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

    private val projectsDir: File
        get() = File(context.filesDir, "projects").apply { if (!exists()) mkdirs() }

    fun listProjects(): List<ProjectMetadata> {
        val list = mutableListOf<ProjectMetadata>()
        val dirs = projectsDir.listFiles() ?: return emptyList()
        for (dir in dirs) {
            if (dir.isDirectory) {
                val metaFile = File(dir, "project.json")
                if (metaFile.exists()) {
                    try {
                        val json = JSONObject(metaFile.readText())
                        list.add(
                            ProjectMetadata(
                                id = json.getString("id"),
                                name = json.optString("name", "Untitled"),
                                version = json.optInt("version", 1),
                                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                                updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
                                nodeCount = json.optInt("nodeCount", 0),
                                timelineCount = json.optInt("timelineCount", 1)
                            )
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        return list.sortedByDescending { it.updatedAt }
    }

    fun createProject(name: String, template: String = "steve"): String {
        val id = UUID.randomUUID().toString()
        val projectDir = File(projectsDir, id).apply { mkdirs() }
        File(projectDir, "timelines").mkdirs()
        File(projectDir, "textures").mkdirs()
        File(projectDir, "sounds").mkdirs()

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
            // Add Steve Character
            val steveId = CharacterFactory.addCharacterToScene(
                sceneGraph = sceneGraph,
                name = "Steve",
                isAlex = false,
                skinId = "steve",
                position = Vec3(0f, 0f, 0f)
            )

            // Add sample Walk keyframes
            PresetGenerator.applyPreset(
                sceneGraph = sceneGraph,
                selectedNodeId = steveId,
                timeline = mainTimeline,
                presetType = PresetType.WALK,
                startTime = 0f
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
        val dir = File(projectsDir, id)
        if (!dir.exists()) return null

        val metaFile = File(dir, "project.json")
        val sceneFile = File(dir, "scene.json")

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
            val timelinesDir = File(dir, "timelines")
            if (timelinesDir.exists()) {
                val tlFiles = timelinesDir.listFiles() ?: emptyArray()
                for (tlFile in tlFiles) {
                    if (tlFile.name.endsWith(".json")) {
                        val tlJson = JSONObject(tlFile.readText())
                        timelines.add(deserializeTimeline(tlJson))
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
        val dir = File(projectsDir, id).apply { if (!exists()) mkdirs() }
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

        // 2. scene.json
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
        File(dir, "scene.json").writeText(sceneJson.toString(2))

        // 3. timelines/
        val tlDir = File(dir, "timelines").apply { if (!exists()) mkdirs() }
        for (tl in timelines) {
            val tlJson = serializeTimeline(tl)
            File(tlDir, "${tl.id}.json").writeText(tlJson.toString(2))
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
        val dir = File(projectsDir, id)
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
    }

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
        return timeline
    }
}
