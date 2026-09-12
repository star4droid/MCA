package com.star4droid.mc.animation.project

import com.star4droid.mc.animation.animation.TimelineAsset
import com.star4droid.mc.animation.animation.TimelineInstance
import com.star4droid.mc.animation.engine.scene.SceneGraph
import java.util.UUID

data class ProjectMetadata(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Untitled Project",
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var nodeCount: Int = 0,
    var timelineCount: Int = 1,
    var sceneCount: Int = 1
)

data class SceneData(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Scene 1",
    val sceneGraph: SceneGraph = SceneGraph(),
    val timelineInstances: MutableList<TimelineInstance> = mutableListOf()
)

data class LoadedProject(
    val metadata: ProjectMetadata,
    val sceneGraph: SceneGraph,
    val timelines: MutableList<TimelineAsset>,
    val timelineInstances: MutableList<TimelineInstance>,
    val scenes: MutableList<SceneData> = mutableListOf(),
    var activeSceneId: String = ""
)
