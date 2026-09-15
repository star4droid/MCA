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
            try {
                val primaryExt = android.os.Environment.getExternalStorageDirectory()
                if (primaryExt != null && primaryExt.exists() && primaryExt.canWrite()) {
                    val mcaDir = File(primaryExt, "MCA")
                    if (mcaDir.exists() || mcaDir.mkdirs()) {
                        return mcaDir
                    }
                }
            } catch (e: Exception) {
                // Fallback on restricted storage / Android 11+ scoped storage
            }
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

        val appExt = context.getExternalFilesDir(null)
        if (appExt != null && appExt != externalBaseDir) {
            scanDir(appExt)
            val appExtProj = File(appExt, "projects")
            if (appExtProj.exists()) scanDir(appExtProj)
        }

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
        } else if (template == "village") {
            sceneGraph.nodes.clear()
            sceneGraph.rootNodeIds.clear()
            timelines.clear()
            instances.clear()
            populateVillageScene(sceneGraph, timelines, instances)
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

    fun createSampleVillageProject(): String {
        val name = "Sample Village City"
        val id = "sample_village_city"
        val projectDir = getProjectDir(id, name)
        ensureProjectSubdirs(projectDir)

        val sceneGraph = SceneGraph()
        val timelines = mutableListOf<TimelineAsset>()
        val instances = mutableListOf<TimelineInstance>()

        populateVillageScene(sceneGraph, timelines, instances)

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

    fun populateVillageScene(
        sceneGraph: SceneGraph,
        timelines: MutableList<TimelineAsset>,
        instances: MutableList<TimelineInstance>
    ) {

        // 1. Main Camera
        val cameraNode = SceneNode(
            id = "cam_main",
            name = "Camera Main",
            type = SceneNodeType.CAMERA,
            baseTransform = Transform(
                position = Vec3(0f, 14f, 18f),
                rotation = Vec3(-28f, 0f, 0f)
            ),
            animatedTransform = Transform(
                position = Vec3(0f, 14f, 18f),
                rotation = Vec3(-28f, 0f, 0f)
            ),
            cameraData = CameraData(fov = 60f, near = 0.1f, far = 1000f, enabled = true)
        )
        sceneGraph.addNode(cameraNode)

        // 2. Sun Light
        val sunNode = SceneNode(
            id = "sun_light",
            name = "Sun Light",
            type = SceneNodeType.LIGHT,
            baseTransform = Transform(
                position = Vec3(10f, 20f, 10f),
                rotation = Vec3(45f, 30f, 0f)
            ),
            animatedTransform = Transform(
                position = Vec3(10f, 20f, 10f),
                rotation = Vec3(45f, 30f, 0f)
            ),
            lightData = LightData(
                color = 0xFFFFF8E7.toInt(),
                intensity = 1.3f,
                timeOfDay = TimeOfDay.NOON
            )
        )
        sceneGraph.addNode(sunNode)

        // 3. Ground Platform (Grass 60x60)
        val groundNode = SceneNode(
            id = "ground_main",
            name = "Village Ground",
            type = SceneNodeType.GROUND,
            baseTransform = Transform(
                position = Vec3(0f, -0.5f, 0f),
                scale = Vec3(60f, 1f, 60f)
            ),
            animatedTransform = Transform(
                position = Vec3(0f, -0.5f, 0f),
                scale = Vec3(60f, 1f, 60f)
            ),
            material = Material(textureAssetId = "grass"),
            boxDimensions = Vec3(1f, 1f, 1f)
        )
        sceneGraph.addNode(groundNode)

        // 4. Cobblestone Main Roads & Cross Avenue
        val mainRoad = SceneNode(
            id = "road_main",
            name = "Main Cobblestone Avenue",
            type = SceneNodeType.BLOCK,
            baseTransform = Transform(position = Vec3(0f, 0.01f, 0f), scale = Vec3(4f, 0.1f, 40f)),
            animatedTransform = Transform(position = Vec3(0f, 0.01f, 0f), scale = Vec3(4f, 0.1f, 40f)),
            material = Material(textureAssetId = "cobblestone")
        )
        sceneGraph.addNode(mainRoad)

        val crossRoad = SceneNode(
            id = "road_cross",
            name = "Cross Village Street",
            type = SceneNodeType.BLOCK,
            baseTransform = Transform(position = Vec3(0f, 0.01f, 0f), scale = Vec3(40f, 0.1f, 4f)),
            animatedTransform = Transform(position = Vec3(0f, 0.01f, 0f), scale = Vec3(40f, 0.1f, 4f)),
            material = Material(textureAssetId = "cobblestone")
        )
        sceneGraph.addNode(crossRoad)

        // 5. Central Village Square Fountain
        val fountainBase = SceneNode(
            id = "fountain_base",
            name = "Village Fountain Basin",
            type = SceneNodeType.STEP_BLOCK,
            baseTransform = Transform(position = Vec3(0f, 0.25f, 0f), scale = Vec3(3f, 0.5f, 3f)),
            animatedTransform = Transform(position = Vec3(0f, 0.25f, 0f), scale = Vec3(3f, 0.5f, 3f)),
            material = Material(textureAssetId = "stone")
        )
        sceneGraph.addNode(fountainBase)

        // 6. Castle & Fortress (North z = 12..20)
        val castleGateW = SceneNode(
            id = "castle_gate_w",
            name = "Castle Gate Tower West",
            type = SceneNodeType.BLOCK,
            baseTransform = Transform(position = Vec3(-4f, 3.5f, 12f), scale = Vec3(3f, 7f, 3f)),
            animatedTransform = Transform(position = Vec3(-4f, 3.5f, 12f), scale = Vec3(3f, 7f, 3f)),
            material = Material(textureAssetId = "stone")
        )
        sceneGraph.addNode(castleGateW)

        val castleGateE = SceneNode(
            id = "castle_gate_e",
            name = "Castle Gate Tower East",
            type = SceneNodeType.BLOCK,
            baseTransform = Transform(position = Vec3(4f, 3.5f, 12f), scale = Vec3(3f, 7f, 3f)),
            animatedTransform = Transform(position = Vec3(4f, 3.5f, 12f), scale = Vec3(3f, 7f, 3f)),
            material = Material(textureAssetId = "stone")
        )
        sceneGraph.addNode(castleGateE)

        val castleArch = SceneNode(
            id = "castle_gate_arch",
            name = "Castle Gate Archway",
            type = SceneNodeType.HALF_BLOCK,
            baseTransform = Transform(position = Vec3(0f, 5.5f, 12f), scale = Vec3(5f, 1f, 3f)),
            animatedTransform = Transform(position = Vec3(0f, 5.5f, 12f), scale = Vec3(5f, 1f, 3f)),
            material = Material(textureAssetId = "stone")
        )
        sceneGraph.addNode(castleArch)

        val castleWallW = SceneNode(
            id = "castle_wall_w",
            name = "Castle West Wall",
            type = SceneNodeType.BLOCK,
            baseTransform = Transform(position = Vec3(-12f, 2.5f, 12f), scale = Vec3(13f, 5f, 2f)),
            animatedTransform = Transform(position = Vec3(-12f, 2.5f, 12f), scale = Vec3(13f, 5f, 2f)),
            material = Material(textureAssetId = "stone")
        )
        sceneGraph.addNode(castleWallW)

        val castleWallE = SceneNode(
            id = "castle_wall_e",
            name = "Castle East Wall",
            type = SceneNodeType.BLOCK,
            baseTransform = Transform(position = Vec3(12f, 2.5f, 12f), scale = Vec3(13f, 5f, 2f)),
            animatedTransform = Transform(position = Vec3(12f, 2.5f, 12f), scale = Vec3(13f, 5f, 2f)),
            material = Material(textureAssetId = "stone")
        )
        sceneGraph.addNode(castleWallE)

        val watchtowerNW = SceneNode(
            id = "watchtower_nw",
            name = "Watchtower High Keep",
            type = SceneNodeType.BLOCK,
            baseTransform = Transform(position = Vec3(-15f, 5.0f, 18f), scale = Vec3(4f, 10f, 4f)),
            animatedTransform = Transform(position = Vec3(-15f, 5.0f, 18f), scale = Vec3(4f, 10f, 4f)),
            material = Material(textureAssetId = "stone")
        )
        sceneGraph.addNode(watchtowerNW)

        val battlement1 = SceneNode(
            id = "battlement_nw_1",
            name = "Watchtower Stair Battlement",
            type = SceneNodeType.STEP_BLOCK,
            baseTransform = Transform(position = Vec3(-15f, 10.25f, 16.2f), scale = Vec3(4f, 1f, 0.6f)),
            animatedTransform = Transform(position = Vec3(-15f, 10.25f, 16.2f), scale = Vec3(4f, 1f, 0.6f)),
            material = Material(textureAssetId = "cobblestone")
        )
        sceneGraph.addNode(battlement1)

        // 7. Village Houses
        val townHallBody = SceneNode(
            id = "town_hall_body",
            name = "Brick Town Hall",
            type = SceneNodeType.BLOCK,
            baseTransform = Transform(position = Vec3(-12f, 2.0f, -10f), scale = Vec3(6f, 4f, 6f)),
            animatedTransform = Transform(position = Vec3(-12f, 2.0f, -10f), scale = Vec3(6f, 4f, 6f)),
            material = Material(textureAssetId = "brick")
        )
        sceneGraph.addNode(townHallBody)

        val townHallRoof = SceneNode(
            id = "town_hall_roof",
            name = "Town Hall Stair Roof",
            type = SceneNodeType.STEP_BLOCK,
            baseTransform = Transform(position = Vec3(-12f, 4.25f, -10f), scale = Vec3(6.4f, 0.8f, 6.4f)),
            animatedTransform = Transform(position = Vec3(-12f, 4.25f, -10f), scale = Vec3(6.4f, 0.8f, 6.4f)),
            material = Material(textureAssetId = "oak_planks")
        )
        sceneGraph.addNode(townHallRoof)

        val townHallPorch = SceneNode(
            id = "town_hall_porch",
            name = "Town Hall Slab Porch",
            type = SceneNodeType.HALF_BLOCK,
            baseTransform = Transform(position = Vec3(-12f, 0.25f, -6.6f), scale = Vec3(3f, 0.5f, 1.2f)),
            animatedTransform = Transform(position = Vec3(-12f, 0.25f, -6.6f), scale = Vec3(3f, 0.5f, 1.2f)),
            material = Material(textureAssetId = "stone")
        )
        sceneGraph.addNode(townHallPorch)

        val tavernBody = SceneNode(
            id = "tavern_body",
            name = "Oak Tavern",
            type = SceneNodeType.BLOCK,
            baseTransform = Transform(position = Vec3(12f, 2.0f, -10f), scale = Vec3(6f, 4f, 6f)),
            animatedTransform = Transform(position = Vec3(12f, 2.0f, -10f), scale = Vec3(6f, 4f, 6f)),
            material = Material(textureAssetId = "oak_planks")
        )
        sceneGraph.addNode(tavernBody)

        val tavernRoof = SceneNode(
            id = "tavern_roof",
            name = "Tavern Stair Roof",
            type = SceneNodeType.STEP_BLOCK,
            baseTransform = Transform(position = Vec3(12f, 4.25f, -10f), scale = Vec3(6.4f, 0.8f, 6.4f)),
            animatedTransform = Transform(position = Vec3(12f, 4.25f, -10f), scale = Vec3(6.4f, 0.8f, 6.4f)),
            material = Material(textureAssetId = "brick")
        )
        sceneGraph.addNode(tavernRoof)

        val marketTable = SceneNode(
            id = "market_table",
            name = "Market Slab Stall",
            type = SceneNodeType.HALF_BLOCK,
            baseTransform = Transform(position = Vec3(10f, 0.25f, 6f), scale = Vec3(4f, 0.5f, 2f)),
            animatedTransform = Transform(position = Vec3(10f, 0.25f, 6f), scale = Vec3(4f, 0.5f, 2f)),
            material = Material(textureAssetId = "oak_planks")
        )
        sceneGraph.addNode(marketTable)

        // 8. Lamp Posts (6 Lights)
        val lampPositions = listOf(
            Vec3(-2.5f, 2.5f, -8f), Vec3(2.5f, 2.5f, -8f),
            Vec3(-2.5f, 2.5f, 0f), Vec3(2.5f, 2.5f, 0f),
            Vec3(-2.5f, 2.5f, 8f), Vec3(2.5f, 2.5f, 8f)
        )
        lampPositions.forEachIndexed { index, pos ->
            val lampPost = SceneNode(
                id = "lamp_post_$index",
                name = "Lamp Post ${index + 1}",
                type = SceneNodeType.BLOCK,
                baseTransform = Transform(position = Vec3(pos.x, 1f, pos.z), scale = Vec3(0.3f, 2f, 0.3f)),
                animatedTransform = Transform(position = Vec3(pos.x, 1f, pos.z), scale = Vec3(0.3f, 2f, 0.3f)),
                material = Material(textureAssetId = "obsidian")
            )
            sceneGraph.addNode(lampPost)

            val lightNode = SceneNode(
                id = "lamp_light_$index",
                name = "Lamp Light ${index + 1}",
                type = SceneNodeType.LIGHT,
                baseTransform = Transform(position = pos),
                animatedTransform = Transform(position = pos),
                lightData = LightData(color = -40485, intensity = 2.0f, range = 12f)
            )
            sceneGraph.addNode(lightNode)
        }

        // 9. Character Rigs (STEVE & ALEX ONLY)
        val steve1Root = CharacterFactory.addCharacterToScene(
            sceneGraph = sceneGraph,
            name = "Steve Traveler",
            isAlex = false,
            skinId = "steve",
            position = Vec3(0f, 0f, -15f)
        )

        val alex1Root = CharacterFactory.addCharacterToScene(
            sceneGraph = sceneGraph,
            name = "Alex Traveler",
            isAlex = true,
            skinId = "alex",
            position = Vec3(8f, 0f, -6f)
        )

        val steveGuardRoot = CharacterFactory.addCharacterToScene(
            sceneGraph = sceneGraph,
            name = "Steve Tower Guard",
            isAlex = false,
            skinId = "steve",
            position = Vec3(-15f, 10f, 18f)
        )

        val alexGuardRoot = CharacterFactory.addCharacterToScene(
            sceneGraph = sceneGraph,
            name = "Alex Gatekeeper",
            isAlex = true,
            skinId = "alex",
            position = Vec3(0f, 0f, 10f)
        )

        val steveTavernRoot = CharacterFactory.addCharacterToScene(
            sceneGraph = sceneGraph,
            name = "Steve Tavern Host",
            isAlex = false,
            skinId = "steve",
            position = Vec3(8.0f, 0f, -4.5f)
        )

        val alexPatrolRoot = CharacterFactory.addCharacterToScene(
            sceneGraph = sceneGraph,
            name = "Alex Castle Patrol",
            isAlex = true,
            skinId = "alex",
            position = Vec3(-10f, 5.0f, 12f)
        )

        val knightCaptainRoot = CharacterFactory.addCharacterToScene(
            sceneGraph = sceneGraph,
            name = "Knight Captain",
            isAlex = false,
            skinId = "steve",
            position = Vec3(2f, 0f, -3f)
        )

        val minerJoeRoot = CharacterFactory.addCharacterToScene(
            sceneGraph = sceneGraph,
            name = "Miner Joe",
            isAlex = false,
            skinId = "steve",
            position = Vec3(-6f, 0f, -12f)
        )

        val merchantAlexRoot = CharacterFactory.addCharacterToScene(
            sceneGraph = sceneGraph,
            name = "Merchant Alex",
            isAlex = true,
            skinId = "alex",
            position = Vec3(4f, 0f, 4f)
        )

        // 10. Main 30-Second Timeline & Action Blocks
        val mainTimeline = TimelineAsset(
            id = "timeline_main",
            name = "Main Timeline",
            duration = 30.0f
        )

        // Steve Traveler (Walks forward into town towards gate, waves, cheers, backflips, runs, taunts)
        mainTimeline.actionBlocks.add(
            ActionBlock(
                id = "st1_w",
                type = ActionBlockType.WALK,
                targetNodeId = steve1Root,
                startTime = 0f,
                duration = 8f,
                speed = 1f,
                enablePositionMove = true,
                startPosition = Vec3(0f, 0f, -15f),
                targetPosition = Vec3(0f, 0f, -5f),
                moveVector = Vec3(0f, 0f, 1.25f),
                stepSize = 1f
            )
        )
        mainTimeline.actionBlocks.add(ActionBlock(id = "st1_wave", type = ActionBlockType.WAVE, targetNodeId = steve1Root, startTime = 8f, duration = 4f, speed = 1f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "st1_cheer", type = ActionBlockType.CHEER, targetNodeId = steve1Root, startTime = 12f, duration = 4f, speed = 1f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "st1_flip", type = ActionBlockType.BACKFLIP, targetNodeId = steve1Root, startTime = 16f, duration = 2.5f, jumpHeight = 1.2f))
        mainTimeline.actionBlocks.add(
            ActionBlock(
                id = "st1_run",
                type = ActionBlockType.RUN,
                targetNodeId = steve1Root,
                startTime = 19f,
                duration = 7f,
                speed = 1.2f,
                enablePositionMove = true,
                startPosition = Vec3(0f, 0f, -5f),
                targetPosition = Vec3(0f, 0f, 6f),
                moveVector = Vec3(0f, 0f, 1.57f),
                stepSize = 1f
            )
        )
        mainTimeline.actionBlocks.add(ActionBlock(id = "st1_taunt", type = ActionBlockType.TAUNT, targetNodeId = steve1Root, startTime = 26f, duration = 4f, speed = 1f))

        // Alex Traveler (Walks towards Steve, waves back, jumps, walks in circle, flips, bows)
        mainTimeline.actionBlocks.add(
            ActionBlock(
                id = "al1_w",
                type = ActionBlockType.WALK,
                targetNodeId = alex1Root,
                startTime = 0f,
                duration = 6f,
                speed = 1f,
                isDeltaBased = true,
                enablePositionMove = true,
                moveVector = Vec3(0f, 0f, -0.9f),
                stepSize = 1f
            )
        )
        mainTimeline.actionBlocks.add(ActionBlock(id = "al1_wave", type = ActionBlockType.WAVE, targetNodeId = alex1Root, startTime = 6f, duration = 4f, speed = 1f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "al1_jump", type = ActionBlockType.JUMP, targetNodeId = alex1Root, startTime = 10f, duration = 3.5f, speed = 1f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "al1_circle", type = ActionBlockType.CIRCLE_WALK, targetNodeId = alex1Root, startTime = 14f, duration = 8f, speed = 1f, stepSize = 1f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "al1_flip", type = ActionBlockType.BACKFLIP, targetNodeId = alex1Root, startTime = 22.5f, duration = 2.5f, jumpHeight = 1.1f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "al1_bow", type = ActionBlockType.BOW, targetNodeId = alex1Root, startTime = 25.5f, duration = 4.5f, speed = 1f))

        // Tower Guard Steve (Inspects horizon with LOOK_AROUND, gets sleepy nodding off, wakes up to patrol, taunts)
        mainTimeline.actionBlocks.add(ActionBlock(id = "st_g_look", type = ActionBlockType.LOOK_AROUND, targetNodeId = steveGuardRoot, startTime = 0f, duration = 5f, speed = 1f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "st_g_sleepy", type = ActionBlockType.SLEEPY, targetNodeId = steveGuardRoot, startTime = 5f, duration = 9f, speed = 1f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "st_g_look2", type = ActionBlockType.LOOK_RIGHT, targetNodeId = steveGuardRoot, startTime = 14f, duration = 4f, speed = 1f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "st_g_patrol", type = ActionBlockType.PATROL, targetNodeId = steveGuardRoot, startTime = 18f, duration = 8f, speed = 1f, stepSize = 0.6f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "st_g_taunt", type = ActionBlockType.TAUNT, targetNodeId = steveGuardRoot, startTime = 26f, duration = 4f, speed = 1f))

        // Gatekeeper Alex (Patrols the castle entrance, practices sword attacks, sleepy rest, cheers)
        mainTimeline.actionBlocks.add(ActionBlock(id = "al_g_patrol", type = ActionBlockType.PATROL, targetNodeId = alexGuardRoot, startTime = 0f, duration = 9f, speed = 1f, stepSize = 1f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "al_g_sword", type = ActionBlockType.SWORD_ATTACK, targetNodeId = alexGuardRoot, startTime = 9.5f, duration = 5.5f, speed = 1.2f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "al_g_taunt", type = ActionBlockType.TAUNT, targetNodeId = alexGuardRoot, startTime = 15.5f, duration = 4.5f, speed = 1f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "al_g_sleepy", type = ActionBlockType.SLEEPY, targetNodeId = alexGuardRoot, startTime = 20f, duration = 6f, speed = 1f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "al_g_cheer", type = ActionBlockType.CHEER, targetNodeId = alexGuardRoot, startTime = 26f, duration = 4f, speed = 1f))

        // Tavern Host Steve (Welcomes guests, enjoys tavern snacks with EATING, has friendly CONVERSATION, circle dance, bows)
        mainTimeline.actionBlocks.add(ActionBlock(id = "st_t_wave", type = ActionBlockType.WAVE, targetNodeId = steveTavernRoot, startTime = 0f, duration = 5f, speed = 1f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "st_t_eat", type = ActionBlockType.EATING, targetNodeId = steveTavernRoot, startTime = 5f, duration = 5f, speed = 1.1f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "st_t_talk", type = ActionBlockType.CONVERSATION, targetNodeId = steveTavernRoot, startTime = 10.5f, duration = 6f, speed = 1f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "st_t_circle", type = ActionBlockType.CIRCLE_WALK, targetNodeId = steveTavernRoot, startTime = 17f, duration = 8f, speed = 0.9f, stepSize = 0.8f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "st_t_bow", type = ActionBlockType.BOW, targetNodeId = steveTavernRoot, startTime = 25f, duration = 5f, speed = 1f))

        // Castle Wall Patrol Alex (Patrols along rampart, executes sword combat stance, patrols back)
        mainTimeline.actionBlocks.add(ActionBlock(id = "al_p_patrol1", type = ActionBlockType.PATROL, targetNodeId = alexPatrolRoot, startTime = 0f, duration = 14f, speed = 1f, stepSize = 1.2f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "al_p_sword", type = ActionBlockType.SWORD_ATTACK, targetNodeId = alexPatrolRoot, startTime = 14.5f, duration = 4.5f, speed = 1.2f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "al_p_patrol2", type = ActionBlockType.PATROL, targetNodeId = alexPatrolRoot, startTime = 19.5f, duration = 10.5f, speed = 1f, stepSize = 1.2f))

        // Knight Captain (At city square: patrols, attacks with sword, circle walks, backflips, cheers)
        mainTimeline.actionBlocks.add(ActionBlock(id = "kc_patrol", type = ActionBlockType.PATROL, targetNodeId = knightCaptainRoot, startTime = 0f, duration = 8f, speed = 1f, stepSize = 1f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "kc_sword", type = ActionBlockType.SWORD_ATTACK, targetNodeId = knightCaptainRoot, startTime = 8.5f, duration = 5f, speed = 1.2f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "kc_circle", type = ActionBlockType.CIRCLE_WALK, targetNodeId = knightCaptainRoot, startTime = 14f, duration = 8f, speed = 1f, stepSize = 1f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "kc_flip", type = ActionBlockType.BACKFLIP, targetNodeId = knightCaptainRoot, startTime = 22.5f, duration = 2.5f, jumpHeight = 1.3f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "kc_cheer", type = ActionBlockType.CHEER, targetNodeId = knightCaptainRoot, startTime = 25.5f, duration = 4.5f, speed = 1f))

        // Miner Joe (Sneaks, backflips in joy, jumps, walks in circle, cheers)
        mainTimeline.actionBlocks.add(
            ActionBlock(
                id = "mj_sneak",
                type = ActionBlockType.SNEAK_WALK,
                targetNodeId = minerJoeRoot,
                startTime = 0f,
                duration = 8f,
                speed = 0.8f,
                isDeltaBased = true,
                enablePositionMove = true,
                moveVector = Vec3(0.5f, 0f, 0.5f),
                stepSize = 0.8f
            )
        )
        mainTimeline.actionBlocks.add(ActionBlock(id = "mj_flip", type = ActionBlockType.BACKFLIP, targetNodeId = minerJoeRoot, startTime = 8.5f, duration = 2.5f, jumpHeight = 1.1f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "mj_jump", type = ActionBlockType.JUMP, targetNodeId = minerJoeRoot, startTime = 11.5f, duration = 3.5f, speed = 1f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "mj_circle", type = ActionBlockType.CIRCLE_WALK, targetNodeId = minerJoeRoot, startTime = 15.5f, duration = 7.5f, speed = 1f, stepSize = 0.9f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "mj_cheer", type = ActionBlockType.CHEER, targetNodeId = minerJoeRoot, startTime = 23.5f, duration = 6.5f, speed = 1f))

        // Merchant Alex (At the market stalls: greets, walks around stalls in circle, bargains with CONVERSATION, cheers, bows)
        mainTimeline.actionBlocks.add(ActionBlock(id = "ma_wave", type = ActionBlockType.WAVE, targetNodeId = merchantAlexRoot, startTime = 0f, duration = 5f, speed = 1.1f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "ma_circle", type = ActionBlockType.CIRCLE_WALK, targetNodeId = merchantAlexRoot, startTime = 5.5f, duration = 8f, speed = 0.9f, stepSize = 0.9f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "ma_talk", type = ActionBlockType.CONVERSATION, targetNodeId = merchantAlexRoot, startTime = 14f, duration = 5f, speed = 1f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "ma_cheer", type = ActionBlockType.CHEER, targetNodeId = merchantAlexRoot, startTime = 19f, duration = 6f, speed = 1f))
        mainTimeline.actionBlocks.add(ActionBlock(id = "ma_bow", type = ActionBlockType.BOW, targetNodeId = merchantAlexRoot, startTime = 25f, duration = 5f, speed = 1f))

        val camPosX = mainTimeline.getOrCreateTrack("cam_main", "transform.position.x")
        camPosX.addOrUpdateKeyframe(0f, -20f, Interpolation.SMOOTH)
        camPosX.addOrUpdateKeyframe(6f, -4f, Interpolation.SMOOTH)
        camPosX.addOrUpdateKeyframe(12f, 0f, Interpolation.SMOOTH)
        camPosX.addOrUpdateKeyframe(18f, -10f, Interpolation.SMOOTH)
        camPosX.addOrUpdateKeyframe(24f, 8f, Interpolation.SMOOTH)
        camPosX.addOrUpdateKeyframe(30f, 0f, Interpolation.SMOOTH)

        val camPosY = mainTimeline.getOrCreateTrack("cam_main", "transform.position.y")
        camPosY.addOrUpdateKeyframe(0f, 16f, Interpolation.SMOOTH)
        camPosY.addOrUpdateKeyframe(6f, 3.5f, Interpolation.SMOOTH)
        camPosY.addOrUpdateKeyframe(12f, 2.2f, Interpolation.SMOOTH)
        camPosY.addOrUpdateKeyframe(18f, 8.0f, Interpolation.SMOOTH)
        camPosY.addOrUpdateKeyframe(24f, 3.5f, Interpolation.SMOOTH)
        camPosY.addOrUpdateKeyframe(30f, 18f, Interpolation.SMOOTH)

        val camPosZ = mainTimeline.getOrCreateTrack("cam_main", "transform.position.z")
        camPosZ.addOrUpdateKeyframe(0f, 25f, Interpolation.SMOOTH)
        camPosZ.addOrUpdateKeyframe(6f, 10f, Interpolation.SMOOTH)
        camPosZ.addOrUpdateKeyframe(12f, 4.0f, Interpolation.SMOOTH)
        camPosZ.addOrUpdateKeyframe(18f, 14f, Interpolation.SMOOTH)
        camPosZ.addOrUpdateKeyframe(24f, 4f, Interpolation.SMOOTH)
        camPosZ.addOrUpdateKeyframe(30f, 26f, Interpolation.SMOOTH)

        val camRotX = mainTimeline.getOrCreateTrack("cam_main", "transform.rotation.x")
        camRotX.addOrUpdateKeyframe(0f, -28f, Interpolation.SMOOTH)
        camRotX.addOrUpdateKeyframe(6f, -12f, Interpolation.SMOOTH)
        camRotX.addOrUpdateKeyframe(12f, -5f, Interpolation.SMOOTH)
        camRotX.addOrUpdateKeyframe(18f, 18f, Interpolation.SMOOTH)
        camRotX.addOrUpdateKeyframe(24f, -10f, Interpolation.SMOOTH)
        camRotX.addOrUpdateKeyframe(30f, -32f, Interpolation.SMOOTH)

        val camRotY = mainTimeline.getOrCreateTrack("cam_main", "transform.rotation.y")
        camRotY.addOrUpdateKeyframe(0f, -35f, Interpolation.SMOOTH)
        camRotY.addOrUpdateKeyframe(6f, -18f, Interpolation.SMOOTH)
        camRotY.addOrUpdateKeyframe(12f, 0f, Interpolation.SMOOTH)
        camRotY.addOrUpdateKeyframe(18f, -40f, Interpolation.SMOOTH)
        camRotY.addOrUpdateKeyframe(24f, 50f, Interpolation.SMOOTH)
        camRotY.addOrUpdateKeyframe(30f, 0f, Interpolation.SMOOTH)

        timelines.add(mainTimeline)
        instances.add(TimelineInstance(timelineAssetId = mainTimeline.id))
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
                            targetRootObjectId = if (obj.has("targetRootObjectId") && !obj.isNull("targetRootObjectId")) obj.getString("targetRootObjectId") else null,
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
        var deleted = false
        val dir = getProjectDir(id)
        if (dir.exists()) {
            deleted = dir.deleteRecursively() || deleted
        }
        try {
            val directDirs = externalBaseDir.listFiles() ?: emptyArray()
            for (d in directDirs) {
                if (d.isDirectory) {
                    val pf = File(d, "project.json")
                    if (pf.exists()) {
                        try {
                            val json = JSONObject(pf.readText())
                            if (json.optString("id") == id) {
                                deleted = d.deleteRecursively() || deleted
                            }
                        } catch (e: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {}
        return deleted
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
        node.objFilePath?.let { put("objFilePath", it) }
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

        val objFilePath = if (obj.has("objFilePath") && !obj.isNull("objFilePath")) obj.getString("objFilePath") else null

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
            lightData = lightData,
            objFilePath = objFilePath
        )
    }

    private fun serializeTransform(t: Transform): JSONObject = JSONObject().apply {
        put("px", t.position.x.toDouble()); put("py", t.position.y.toDouble()); put("pz", t.position.z.toDouble())
        put("rx", t.rotation.x.toDouble()); put("ry", t.rotation.y.toDouble()); put("rz", t.rotation.z.toDouble())
        put("sx", t.scale.x.toDouble()); put("sy", t.scale.y.toDouble()); put("sz", t.scale.z.toDouble())
        put("pivX", t.pivot.x.toDouble()); put("pivY", t.pivot.y.toDouble()); put("pivZ", t.pivot.z.toDouble())
    }

    private fun deserializeTransform(obj: JSONObject): Transform {
        val posObj = obj.optJSONObject("position")
        val px = if (posObj != null) posObj.optDouble("x", 0.0).toFloat() else obj.optDouble("px", 0.0).toFloat()
        val py = if (posObj != null) posObj.optDouble("y", 0.0).toFloat() else obj.optDouble("py", 0.0).toFloat()
        val pz = if (posObj != null) posObj.optDouble("z", 0.0).toFloat() else obj.optDouble("pz", 0.0).toFloat()

        val rotObj = obj.optJSONObject("rotation")
        val rx = if (rotObj != null) rotObj.optDouble("x", 0.0).toFloat() else obj.optDouble("rx", 0.0).toFloat()
        val ry = if (rotObj != null) rotObj.optDouble("y", 0.0).toFloat() else obj.optDouble("ry", 0.0).toFloat()
        val rz = if (rotObj != null) rotObj.optDouble("z", 0.0).toFloat() else obj.optDouble("rz", 0.0).toFloat()

        val scaleObj = obj.optJSONObject("scale")
        val sx = if (scaleObj != null) scaleObj.optDouble("x", 1.0).toFloat() else obj.optDouble("sx", 1.0).toFloat()
        val sy = if (scaleObj != null) scaleObj.optDouble("y", 1.0).toFloat() else obj.optDouble("sy", 1.0).toFloat()
        val sz = if (scaleObj != null) scaleObj.optDouble("z", 1.0).toFloat() else obj.optDouble("sz", 1.0).toFloat()

        val pivObj = obj.optJSONObject("pivot")
        val pivX = if (pivObj != null) pivObj.optDouble("x", 0.0).toFloat() else obj.optDouble("pivX", 0.0).toFloat()
        val pivY = if (pivObj != null) pivObj.optDouble("y", 0.0).toFloat() else obj.optDouble("pivY", 0.0).toFloat()
        val pivZ = if (pivObj != null) pivObj.optDouble("z", 0.0).toFloat() else obj.optDouble("pivZ", 0.0).toFloat()

        return Transform(
            position = Vec3(px, py, pz),
            rotation = Vec3(rx, ry, rz),
            scale = Vec3(sx, sy, sz),
            pivot = Vec3(pivX, pivY, pivZ)
        )
    }

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
