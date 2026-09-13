package com.star4droid.mc.animation.animation

import com.star4droid.mc.animation.engine.math.Vec3
import com.star4droid.mc.animation.engine.scene.CharacterPartType
import com.star4droid.mc.animation.engine.scene.SceneGraph
import com.star4droid.mc.animation.engine.scene.SceneNode
import com.star4droid.mc.animation.engine.scene.SceneNodeType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

object AnimationEvaluator {

    fun evaluateTimeline(timeline: TimelineAsset, sceneGraph: SceneGraph, time: Float) {
        evaluate(
            sceneGraph = sceneGraph,
            timelines = listOf(timeline),
            timelineInstances = listOf(TimelineInstance(timelineAssetId = timeline.id)),
            currentTime = time
        )
    }

    /**
     * Evaluates animations at [currentTime] and updates the scene graph.
     * Note: Base transforms are preserved; only animatedTransform is updated.
     */
    fun evaluate(
        sceneGraph: SceneGraph,
        timelines: List<TimelineAsset>,
        timelineInstances: List<TimelineInstance>,
        currentTime: Float
    ) {
        // First reset all nodes to base state
        for (node in sceneGraph.nodes.values) {
            node.animatedTransform = node.baseTransform.copyTransform()
        }

        // Map timelines by ID
        val timelineMap = timelines.associateBy { it.id }

        // Evaluate instances in configured order
        for (instance in timelineInstances) {
            val timeline = timelineMap[instance.timelineAssetId] ?: continue

            // Compute local timeline time
            var localTime = (currentTime - instance.startTime) * instance.speed + instance.offset
            if (instance.loop && timeline.duration > 0f) {
                localTime %= timeline.duration
                if (localTime < 0f) localTime += timeline.duration
            }

            // 1. Evaluate Action Blocks (Block-based Animation System)
            val runningTransforms = mutableMapOf<String, com.star4droid.mc.animation.engine.scene.Transform>()
            for (node in sceneGraph.nodes.values) {
                runningTransforms[node.id] = node.baseTransform.copyTransform()
            }

            val sortedBlocks = timeline.actionBlocks.sortedBy { it.startTime }
            for (block in sortedBlocks) {
                val endTime = block.startTime + block.duration
                if (localTime >= block.startTime && localTime <= endTime) {
                    val duration = if (block.duration <= 0.001f) 0.001f else block.duration
                    val progress = ((localTime - block.startTime) / duration).coerceIn(0f, 1f)
                    applyActionBlock(sceneGraph, block, progress, isCompleted = false, runningTransforms = runningTransforms)
                } else if (localTime > endTime) {
                    // Block has finished: maintain the end state without active limb swinging
                    applyActionBlock(sceneGraph, block, 1f, isCompleted = true, runningTransforms = runningTransforms)
                }
            }

            // 2. Evaluate each track in timeline
            for (track in timeline.tracks) {
                val node = sceneGraph.getNode(track.targetObjectId) ?: continue
                val evaluatedValue = track.evaluate(localTime)

                applyPropertyValue(node, track.propertyPath, evaluatedValue)
            }
        }

        // Update hierarchy world matrices
        sceneGraph.updateWorldMatrices()
    }

    private fun findCharacterParts(sceneGraph: SceneGraph, targetNode: SceneNode): Map<CharacterPartType, SceneNode> {
        val map = mutableMapOf<CharacterPartType, SceneNode>()
        var current: SceneNode? = targetNode
        while (current?.parentId != null) {
            val parent = sceneGraph.getNode(current.parentId!!) ?: break
            current = parent
            if (current.type == SceneNodeType.CHARACTER_ROOT) break
        }
        val root = current ?: targetNode
        root.characterPartType?.let { map[it] = root }
        if (root.type == SceneNodeType.CHARACTER_ROOT && !map.containsKey(CharacterPartType.ROOT)) {
            map[CharacterPartType.ROOT] = root
        }

        fun collect(node: SceneNode) {
            node.characterPartType?.let { map[it] = node }
            for (childId in node.children) {
                sceneGraph.getNode(childId)?.let { collect(it) }
            }
        }
        collect(root)
        return map
    }

    private fun resolveNodeIdByName(sceneGraph: SceneGraph, selectedNode: SceneNode, targetNodeName: String): String {
        if (targetNodeName.isBlank()) return selectedNode.id
        if (sceneGraph.nodes.containsKey(targetNodeName)) return targetNodeName

        val norm = targetNodeName.replace("_", "").replace(" ", "").lowercase()
        val partType = when (norm) {
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

        if (partType != null) {
            val parts = findCharacterParts(sceneGraph, selectedNode)
            parts[partType]?.let { return it.id }
        }

        val byName = sceneGraph.nodes.values.firstOrNull {
            it.name.replace("_", "").replace(" ", "").lowercase().contains(norm)
        }
        if (byName != null) return byName.id

        return selectedNode.id
    }

    private fun applyActionBlock(
        sceneGraph: SceneGraph,
        block: ActionBlock,
        progress: Float,
        isCompleted: Boolean = false,
        runningTransforms: MutableMap<String, com.star4droid.mc.animation.engine.scene.Transform>
    ) {
        val node = sceneGraph.getNode(block.targetNodeId) ?: return
        val parts = findCharacterParts(sceneGraph, node)
        val rootNode = parts[CharacterPartType.ROOT] ?: node

        // Helper to compute movement delta from block settings
        fun moveDelta(): Vec3 {
            return if (block.enablePositionMove) {
                if (block.targetPosition != Vec3.ZERO && block.startPosition != null && block.targetPosition != block.startPosition) {
                    block.targetPosition - block.startPosition!!
                } else {
                    block.moveVector * block.stepSize
                }
            } else Vec3.ZERO
        }

        // Helper: apply forearm/lower-leg natural bending during limb swings
        fun bendForearms(armAngleLeft: Float, armAngleRight: Float) {
            parts[CharacterPartType.LEFT_FOREARM]?.let {
                val bendAngle = if (armAngleLeft < -10f) (-armAngleLeft * 0.5f).coerceIn(0f, 90f) else 0f
                it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = bendAngle))
            }
            parts[CharacterPartType.RIGHT_FOREARM]?.let {
                val bendAngle = if (armAngleRight < -10f) (-armAngleRight * 0.5f).coerceIn(0f, 90f) else 0f
                it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = bendAngle))
            }
        }

        fun bendLowerLegs(legAngleLeft: Float, legAngleRight: Float) {
            parts[CharacterPartType.LEFT_LOWER_LEG]?.let {
                val bendAngle = if (legAngleLeft > 10f) (legAngleLeft * 0.6f).coerceIn(0f, 90f) else 0f
                it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = bendAngle))
            }
            parts[CharacterPartType.RIGHT_LOWER_LEG]?.let {
                val bendAngle = if (legAngleRight > 10f) (legAngleRight * 0.6f).coerceIn(0f, 90f) else 0f
                it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = bendAngle))
            }
        }

        fun resetLimbs() {
            parts[CharacterPartType.LEFT_LEG]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
            parts[CharacterPartType.RIGHT_LEG]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
            parts[CharacterPartType.LEFT_ARM]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
            parts[CharacterPartType.RIGHT_ARM]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
            parts[CharacterPartType.HEAD]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
            parts[CharacterPartType.LEFT_FOREARM]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
            parts[CharacterPartType.RIGHT_FOREARM]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
            parts[CharacterPartType.LEFT_LOWER_LEG]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
            parts[CharacterPartType.RIGHT_LOWER_LEG]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
        }

        when (block.type) {
            ActionBlockType.WALK -> {
                val baseT = runningTransforms.getOrPut(rootNode.id) { rootNode.baseTransform.copyTransform() }
                val delta = moveDelta()
                // Move in facing direction: use current yaw to transform moveVector
                val currentPos = baseT.position + delta * progress

                if (isCompleted) {
                    rootNode.animatedTransform = rootNode.animatedTransform.copy(position = baseT.position + delta)
                    runningTransforms[rootNode.id] = baseT.copy(position = baseT.position + delta)
                    resetLimbs()
                } else {
                    val cycle = (progress * block.duration * (PI * 2.0 * 2.0 * block.speed)).toFloat()
                    val legAngle = sin(cycle) * 38f * block.stepSize
                    val armAngle = -legAngle * 0.85f
                    val bob = abs(sin(cycle * 2f)) * 0.06f * block.stepSize

                    rootNode.animatedTransform = rootNode.animatedTransform.copy(
                        position = Vec3(currentPos.x, currentPos.y + bob, currentPos.z)
                    )

                    parts[CharacterPartType.LEFT_LEG]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = legAngle))
                    }
                    parts[CharacterPartType.RIGHT_LEG]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -legAngle))
                    }
                    parts[CharacterPartType.LEFT_ARM]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = armAngle))
                    }
                    parts[CharacterPartType.RIGHT_ARM]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -armAngle))
                    }
                    parts[CharacterPartType.HEAD]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(z = sin(cycle) * 1.5f))
                    }
                    bendLowerLegs(legAngle, -legAngle)
                    bendForearms(armAngle, -armAngle)
                }
            }

            ActionBlockType.RUN -> {
                val baseT = runningTransforms.getOrPut(rootNode.id) { rootNode.baseTransform.copyTransform() }
                val delta = moveDelta()
                val currentPos = baseT.position + delta * progress

                if (isCompleted) {
                    rootNode.animatedTransform = rootNode.animatedTransform.copy(position = baseT.position + delta)
                    runningTransforms[rootNode.id] = baseT.copy(position = baseT.position + delta)
                    resetLimbs()
                } else {
                    val cycle = (progress * block.duration * (PI * 2.0 * 3.2 * block.speed)).toFloat()
                    val legAngle = sin(cycle) * 55f * block.stepSize
                    val armAngle = -legAngle * 0.9f
                    val bob = abs(sin(cycle * 2f)) * 0.1f * block.stepSize

                    rootNode.animatedTransform = rootNode.animatedTransform.copy(
                        position = Vec3(currentPos.x, currentPos.y + bob, currentPos.z)
                    )

                    parts[CharacterPartType.LEFT_LEG]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = legAngle))
                    }
                    parts[CharacterPartType.RIGHT_LEG]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -legAngle))
                    }
                    parts[CharacterPartType.LEFT_ARM]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = armAngle))
                    }
                    parts[CharacterPartType.RIGHT_ARM]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -armAngle))
                    }
                    parts[CharacterPartType.BODY]?.let { body ->
                        body.animatedTransform = body.animatedTransform.copy(rotation = body.baseTransform.rotation.copy(x = -15f))
                    }
                    bendLowerLegs(legAngle, -legAngle)
                    bendForearms(armAngle, -armAngle)
                }
            }

            ActionBlockType.JUMP -> {
                val baseT = runningTransforms.getOrPut(rootNode.id) { rootNode.baseTransform.copyTransform() }
                val delta = moveDelta()
                val currentPos = baseT.position + delta * progress
                val jumpArc = sin(progress * PI.toFloat())
                val jumpHeight = jumpArc * block.jumpHeight * block.stepSize

                if (isCompleted) {
                    rootNode.animatedTransform = rootNode.animatedTransform.copy(position = baseT.position + delta)
                    runningTransforms[rootNode.id] = baseT.copy(position = baseT.position + delta)
                    resetLimbs()
                } else {
                    rootNode.animatedTransform = rootNode.animatedTransform.copy(
                        position = Vec3(currentPos.x, currentPos.y + jumpHeight, currentPos.z)
                    )
                    parts[CharacterPartType.LEFT_LEG]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = jumpArc * 25f))
                    }
                    parts[CharacterPartType.RIGHT_LEG]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = jumpArc * 25f))
                    }
                    parts[CharacterPartType.LEFT_ARM]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -jumpArc * 45f))
                    }
                    parts[CharacterPartType.RIGHT_ARM]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -jumpArc * 45f))
                    }
                    bendLowerLegs(jumpArc * 25f, jumpArc * 25f)
                    bendForearms(-jumpArc * 45f, -jumpArc * 45f)
                }
            }

            ActionBlockType.WAVE -> {
                val waveArm = parts[CharacterPartType.RIGHT_ARM]
                if (isCompleted) {
                    waveArm?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                    parts[CharacterPartType.RIGHT_FOREARM]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                    parts[CharacterPartType.HEAD]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                } else {
                    if (waveArm != null) {
                        val waveCycle = (progress * block.duration * (PI * 2.0 * 3.0 * block.speed)).toFloat()
                        val waveAngle = sin(waveCycle) * 30f
                        waveArm.animatedTransform = waveArm.animatedTransform.copy(rotation = Vec3(-130f, waveAngle, 25f))
                        parts[CharacterPartType.RIGHT_FOREARM]?.let {
                            it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 60f))
                        }
                    }
                    parts[CharacterPartType.HEAD]?.let { head ->
                        head.animatedTransform = head.animatedTransform.copy(rotation = head.baseTransform.rotation.copy(z = -8f))
                    }
                }
            }

            ActionBlockType.SLIDE_TO_POS -> {
                val targetNode = if (parts.containsKey(CharacterPartType.ROOT)) rootNode else node
                val baseT = runningTransforms.getOrPut(targetNode.id) { targetNode.baseTransform.copyTransform() }
                val delta = if (block.targetPosition != Vec3.ZERO && block.startPosition != null && block.targetPosition != block.startPosition) {
                    block.targetPosition - block.startPosition!!
                } else {
                    block.moveVector * block.stepSize
                }
                val smoothT = if (isCompleted) 1f else progress * progress * (3f - 2f * progress)
                val curPos = baseT.position + delta * smoothT
                targetNode.animatedTransform = targetNode.animatedTransform.copy(position = curPos)
                if (isCompleted) {
                    runningTransforms[targetNode.id] = baseT.copy(position = baseT.position + delta)
                }
            }

            ActionBlockType.MOVE_TO_POS -> {
                val targetNode = if (parts.containsKey(CharacterPartType.ROOT)) rootNode else node
                val baseT = runningTransforms.getOrPut(targetNode.id) { targetNode.baseTransform.copyTransform() }
                val destPos = if (!block.isDeltaBased && block.targetPosition != Vec3.ZERO) {
                    block.targetPosition
                } else {
                    val delta = if (block.targetPosition != Vec3.ZERO && block.startPosition != null && block.targetPosition != block.startPosition) {
                        block.targetPosition - block.startPosition!!
                    } else {
                        block.moveVector * block.stepSize
                    }
                    baseT.position + delta
                }
                val curProgress = if (isCompleted) 1f else progress
                val startPos = block.startPosition ?: baseT.position
                val curPos = startPos.lerp(destPos, curProgress)
                targetNode.animatedTransform = targetNode.animatedTransform.copy(position = curPos)
                if (isCompleted) {
                    runningTransforms[targetNode.id] = baseT.copy(position = destPos)
                }
            }

            ActionBlockType.SCALE -> {
                val baseT = runningTransforms.getOrPut(node.id) { node.baseTransform.copyTransform() }
                val targetScale = if (block.scaleVector != Vec3.ONE) {
                    block.scaleVector
                } else {
                    Vec3(baseT.scale.x * block.stepSize, baseT.scale.y * block.stepSize, baseT.scale.z * block.stepSize)
                }
                val smoothT = if (isCompleted) 1f else progress * progress * (3f - 2f * progress)
                val curScale = baseT.scale.lerp(targetScale, smoothT)
                node.animatedTransform = node.animatedTransform.copy(scale = curScale)
                if (isCompleted) {
                    runningTransforms[node.id] = baseT.copy(scale = targetScale)
                }
            }

            ActionBlockType.ANIMATION_CLIP -> {
                if (!block.customJson.isNullOrBlank()) {
                    try {
                        val json = org.json.JSONObject(block.customJson!!)
                        val relTime = progress * block.duration
                        if (json.has("tracks")) {
                            val tracksArr = json.getJSONArray("tracks")
                            for (i in 0 until tracksArr.length()) {
                                val trackObj = tracksArr.getJSONObject(i)
                                val targetNodeName = trackObj.optString("targetNode", "")
                                val targetNodeId = resolveNodeIdByName(sceneGraph, node, targetNodeName)
                                val targetNodeObj = sceneGraph.getNode(targetNodeId) ?: continue

                                if (trackObj.has("keyframes")) {
                                    val kfArr = trackObj.getJSONArray("keyframes")
                                    if (kfArr.length() > 0) {
                                        var pos: Vec3? = null
                                        var rot: Vec3? = null
                                        var scale: Vec3? = null

                                        for (k in 0 until kfArr.length()) {
                                            val kf = kfArr.getJSONObject(k)
                                            val t = kf.optDouble("time", 0.0).toFloat()
                                            val nextKf = if (k < kfArr.length() - 1) kfArr.getJSONObject(k + 1) else null
                                            val nextT = nextKf?.optDouble("time", t.toDouble())?.toFloat() ?: t

                                            if (relTime >= t && (nextKf == null || relTime <= nextT)) {
                                                val factor = if (nextT > t) ((relTime - t) / (nextT - t)).coerceIn(0f, 1f) else 0f

                                                if (kf.has("position")) {
                                                    val p0 = kf.getJSONArray("position")
                                                    val v0 = Vec3(p0.getDouble(0).toFloat(), p0.getDouble(1).toFloat(), p0.getDouble(2).toFloat())
                                                    pos = if (nextKf != null && nextKf.has("position")) {
                                                        val p1 = nextKf.getJSONArray("position")
                                                        val v1 = Vec3(p1.getDouble(0).toFloat(), p1.getDouble(1).toFloat(), p1.getDouble(2).toFloat())
                                                        v0.lerp(v1, factor)
                                                    } else v0
                                                }

                                                if (kf.has("rotation")) {
                                                    val r0 = kf.getJSONArray("rotation")
                                                    val v0 = Vec3(r0.getDouble(0).toFloat(), r0.getDouble(1).toFloat(), r0.getDouble(2).toFloat())
                                                    rot = if (nextKf != null && nextKf.has("rotation")) {
                                                        val r1 = nextKf.getJSONArray("rotation")
                                                        val v1 = Vec3(r1.getDouble(0).toFloat(), r1.getDouble(1).toFloat(), r1.getDouble(2).toFloat())
                                                        v0.lerp(v1, factor)
                                                    } else v0
                                                }

                                                if (kf.has("scale")) {
                                                    val s0 = kf.getJSONArray("scale")
                                                    val v0 = Vec3(s0.getDouble(0).toFloat(), s0.getDouble(1).toFloat(), s0.getDouble(2).toFloat())
                                                    scale = if (nextKf != null && nextKf.has("scale")) {
                                                        val s1 = nextKf.getJSONArray("scale")
                                                        val v1 = Vec3(s1.getDouble(0).toFloat(), s1.getDouble(1).toFloat(), s1.getDouble(2).toFloat())
                                                        v0.lerp(v1, factor)
                                                    } else v0
                                                }
                                                break
                                            }
                                        }

                                        val activeBaseT = runningTransforms.getOrPut(targetNodeObj.id) { targetNodeObj.baseTransform.copyTransform() }
                                        val newPos = pos?.let { activeBaseT.position + it } ?: targetNodeObj.animatedTransform.position
                                        val newRot = rot?.let { activeBaseT.rotation + it } ?: targetNodeObj.animatedTransform.rotation
                                        val newScale = scale?.let { Vec3(activeBaseT.scale.x * it.x, activeBaseT.scale.y * it.y, activeBaseT.scale.z * it.z) } ?: targetNodeObj.animatedTransform.scale

                                        targetNodeObj.animatedTransform = targetNodeObj.animatedTransform.copy(
                                            position = newPos,
                                            rotation = newRot,
                                            scale = newScale
                                        )

                                        if (isCompleted) {
                                            runningTransforms[targetNodeObj.id] = activeBaseT.copy(
                                                position = newPos,
                                                rotation = newRot,
                                                scale = newScale
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    val cycle = (progress * block.duration * (PI * 2.0 * 2.0 * block.speed)).toFloat()
                    val legAngle = sin(cycle) * 28f * block.stepSize
                    val armAngle = -legAngle * 0.75f
                    parts[CharacterPartType.LEFT_LEG]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = legAngle))
                    }
                    parts[CharacterPartType.RIGHT_LEG]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -legAngle))
                    }
                    parts[CharacterPartType.LEFT_ARM]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = armAngle))
                    }
                    parts[CharacterPartType.RIGHT_ARM]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -armAngle))
                    }
                    bendLowerLegs(legAngle, -legAngle)
                    bendForearms(armAngle, -armAngle)
                }
            }

            ActionBlockType.ROTATE -> {
                // Concurrent rotation: only modifies Y rotation, doesn't affect position
                val baseT = runningTransforms.getOrPut(rootNode.id) { rootNode.baseTransform.copyTransform() }
                val smoothT = if (isCompleted) 1f else progress * progress * (3f - 2f * progress)
                val rotAmount = block.angle * smoothT
                val currentRot = rootNode.animatedTransform.rotation
                rootNode.animatedTransform = rootNode.animatedTransform.copy(
                    rotation = currentRot.copy(y = currentRot.y + rotAmount - (if (isCompleted) 0f else block.angle * (if (progress > 0.001f) (progress - 0.001f) * (progress - 0.001f) * (3f - 2f * (progress - 0.001f)) else 0f)))
                )
                if (isCompleted) {
                    val newRot = baseT.rotation.copy(y = baseT.rotation.y + block.angle)
                    runningTransforms[rootNode.id] = baseT.copy(rotation = newRot)
                    rootNode.animatedTransform = rootNode.animatedTransform.copy(rotation = newRot)
                }
            }

            ActionBlockType.TILT_HEAD -> {
                parts[CharacterPartType.HEAD]?.let { head ->
                    if (isCompleted) {
                        head.animatedTransform = head.baseTransform.copyTransform()
                    } else {
                        val smoothT = progress * progress * (3f - 2f * progress)
                        val tilt = sin(smoothT * PI.toFloat()) * block.angle * block.amplitude
                        head.animatedTransform = head.animatedTransform.copy(rotation = head.baseTransform.rotation.copy(z = tilt))
                    }
                }
            }

            ActionBlockType.PUNCH -> {
                val punchArm = parts[CharacterPartType.RIGHT_ARM]
                val punchForearm = parts[CharacterPartType.RIGHT_FOREARM]
                if (isCompleted) {
                    punchArm?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                    punchForearm?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                    parts[CharacterPartType.BODY]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                } else {
                    val strike = sin(progress * PI.toFloat())
                    punchArm?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = Vec3(-90f * strike, 0f, -15f * strike))
                    }
                    punchForearm?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 45f * strike))
                    }
                    parts[CharacterPartType.BODY]?.let { body ->
                        body.animatedTransform = body.animatedTransform.copy(rotation = body.baseTransform.rotation.copy(y = -20f * strike))
                    }
                }
            }

            ActionBlockType.LOOK_LEFT -> {
                parts[CharacterPartType.HEAD]?.let { head ->
                    val baseT = runningTransforms.getOrPut(head.id) { head.baseTransform.copyTransform() }
                    val targetAngle = 60f * block.amplitude
                    val smoothT = if (isCompleted) 1f else progress * progress * (3f - 2f * progress)
                    val angle = targetAngle * smoothT
                    val newRot = head.animatedTransform.rotation.copy(y = baseT.rotation.y + angle)
                    head.animatedTransform = head.animatedTransform.copy(rotation = newRot)
                    if (isCompleted) {
                        runningTransforms[head.id] = head.animatedTransform.copy(rotation = baseT.rotation.copy(y = baseT.rotation.y + targetAngle))
                    }
                }
            }

            ActionBlockType.LOOK_RIGHT -> {
                parts[CharacterPartType.HEAD]?.let { head ->
                    val baseT = runningTransforms.getOrPut(head.id) { head.baseTransform.copyTransform() }
                    val targetAngle = -60f * block.amplitude
                    val smoothT = if (isCompleted) 1f else progress * progress * (3f - 2f * progress)
                    val angle = targetAngle * smoothT
                    val newRot = head.animatedTransform.rotation.copy(y = baseT.rotation.y + angle)
                    head.animatedTransform = head.animatedTransform.copy(rotation = newRot)
                    if (isCompleted) {
                        runningTransforms[head.id] = head.animatedTransform.copy(rotation = baseT.rotation.copy(y = baseT.rotation.y + targetAngle))
                    }
                }
            }

            ActionBlockType.BACKFLIP -> {
                val baseT = runningTransforms.getOrPut(rootNode.id) { rootNode.baseTransform.copyTransform() }
                if (isCompleted) {
                    rootNode.animatedTransform = baseT.copyTransform()
                    resetLimbs()
                } else {
                    // High parabolic jump arc
                    val jumpArc = sin(progress * PI.toFloat())
                    val height = jumpArc * block.jumpHeight * 1.5f * block.amplitude
                    // Full 360 backward rotation on rootNode so legs, arms and head all rotate together!
                    val flipAngle = -360f * progress

                    rootNode.animatedTransform = rootNode.animatedTransform.copy(
                        position = Vec3(baseT.position.x, baseT.position.y + height, baseT.position.z),
                        rotation = baseT.rotation.copy(x = flipAngle)
                    )
                    parts[CharacterPartType.BODY]?.let { body ->
                        body.animatedTransform = body.animatedTransform.copy(rotation = body.baseTransform.rotation.copy(x = -15f * jumpArc))
                    }
                    // Tightly tuck legs during flip
                    parts[CharacterPartType.LEFT_LEG]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -75f * jumpArc))
                    }
                    parts[CharacterPartType.RIGHT_LEG]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -75f * jumpArc))
                    }
                    parts[CharacterPartType.LEFT_LOWER_LEG]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 90f * jumpArc))
                    }
                    parts[CharacterPartType.RIGHT_LOWER_LEG]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 90f * jumpArc))
                    }
                    // Arms pull back & tuck into flip
                    parts[CharacterPartType.LEFT_ARM]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -140f * jumpArc))
                    }
                    parts[CharacterPartType.RIGHT_ARM]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -140f * jumpArc))
                    }
                    parts[CharacterPartType.LEFT_FOREARM]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 75f * jumpArc))
                    }
                    parts[CharacterPartType.RIGHT_FOREARM]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 75f * jumpArc))
                    }
                }
            }

            ActionBlockType.JUMP_FRONT -> {
                val baseT = runningTransforms.getOrPut(rootNode.id) { rootNode.baseTransform.copyTransform() }
                val displacement = if (block.enablePositionMove) block.moveVector else Vec3(0f, 0f, 2.5f * block.stepSize)
                val currentPos = baseT.position + displacement * progress
                val jumpArc = sin(progress * PI.toFloat())
                val height = jumpArc * block.jumpHeight * 1.2f
                if (isCompleted) {
                    rootNode.animatedTransform = rootNode.animatedTransform.copy(position = baseT.position + displacement)
                    runningTransforms[rootNode.id] = baseT.copy(position = baseT.position + displacement)
                    resetLimbs()
                } else {
                    rootNode.animatedTransform = rootNode.animatedTransform.copy(
                        position = Vec3(currentPos.x, currentPos.y + height, currentPos.z)
                    )
                    parts[CharacterPartType.BODY]?.let { body ->
                        body.animatedTransform = body.animatedTransform.copy(rotation = body.baseTransform.rotation.copy(x = -35f * jumpArc))
                    }
                    parts[CharacterPartType.HEAD]?.let { head ->
                        head.animatedTransform = head.animatedTransform.copy(rotation = head.baseTransform.rotation.copy(x = 18f * jumpArc))
                    }
                    parts[CharacterPartType.LEFT_LEG]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -45f * jumpArc)) }
                    parts[CharacterPartType.RIGHT_LEG]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -25f * jumpArc)) }
                    parts[CharacterPartType.LEFT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 75f * jumpArc)) }
                    parts[CharacterPartType.RIGHT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -75f * jumpArc)) }
                    bendLowerLegs(60f * jumpArc, 45f * jumpArc)
                }
            }

            ActionBlockType.SIT_DOWN -> {
                if (isCompleted) {
                    // Stay seated
                    parts[CharacterPartType.LEFT_LEG]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -90f)) }
                    parts[CharacterPartType.RIGHT_LEG]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -90f)) }
                    parts[CharacterPartType.LEFT_LOWER_LEG]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 90f)) }
                    parts[CharacterPartType.RIGHT_LOWER_LEG]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 90f)) }
                    val baseT = runningTransforms.getOrPut(rootNode.id) { rootNode.baseTransform.copyTransform() }
                    rootNode.animatedTransform = rootNode.animatedTransform.copy(position = baseT.position.copy(y = baseT.position.y - 0.35f))
                } else {
                    val smoothT = progress * progress * (3f - 2f * progress)
                    parts[CharacterPartType.LEFT_LEG]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -90f * smoothT)) }
                    parts[CharacterPartType.RIGHT_LEG]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -90f * smoothT)) }
                    parts[CharacterPartType.LEFT_LOWER_LEG]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 90f * smoothT)) }
                    parts[CharacterPartType.RIGHT_LOWER_LEG]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 90f * smoothT)) }
                    val baseT = runningTransforms.getOrPut(rootNode.id) { rootNode.baseTransform.copyTransform() }
                    rootNode.animatedTransform = rootNode.animatedTransform.copy(position = baseT.position.copy(y = baseT.position.y - 0.35f * smoothT))
                }
            }

            ActionBlockType.STAND_UP -> {
                if (isCompleted) {
                    resetLimbs()
                } else {
                    val smoothT = progress * progress * (3f - 2f * progress)
                    parts[CharacterPartType.LEFT_LEG]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -90f * (1f - smoothT))) }
                    parts[CharacterPartType.RIGHT_LEG]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -90f * (1f - smoothT))) }
                    parts[CharacterPartType.LEFT_LOWER_LEG]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 90f * (1f - smoothT))) }
                    parts[CharacterPartType.RIGHT_LOWER_LEG]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 90f * (1f - smoothT))) }
                    val baseT = runningTransforms.getOrPut(rootNode.id) { rootNode.baseTransform.copyTransform() }
                    rootNode.animatedTransform = rootNode.animatedTransform.copy(position = baseT.position.copy(y = baseT.position.y - 0.35f * (1f - smoothT)))
                }
            }

            ActionBlockType.KICK -> {
                val kickLeg = parts[CharacterPartType.RIGHT_LEG]
                if (isCompleted) {
                    kickLeg?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                    parts[CharacterPartType.RIGHT_LOWER_LEG]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                    parts[CharacterPartType.BODY]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                } else {
                    val strike = sin(progress * PI.toFloat())
                    kickLeg?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -80f * strike)) }
                    parts[CharacterPartType.RIGHT_LOWER_LEG]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 20f * strike)) }
                    parts[CharacterPartType.BODY]?.let { body ->
                        body.animatedTransform = body.animatedTransform.copy(rotation = body.baseTransform.rotation.copy(x = -10f * strike))
                    }
                }
            }

            ActionBlockType.NOD_HEAD -> {
                parts[CharacterPartType.HEAD]?.let { head ->
                    if (isCompleted) { head.animatedTransform = head.baseTransform.copyTransform() }
                    else {
                        val cycle = (progress * block.duration * (PI * 2.0 * 3.0 * block.speed)).toFloat()
                        head.animatedTransform = head.animatedTransform.copy(rotation = head.baseTransform.rotation.copy(x = sin(cycle) * 25f * block.amplitude))
                    }
                }
            }

            ActionBlockType.SHAKE_HEAD -> {
                parts[CharacterPartType.HEAD]?.let { head ->
                    if (isCompleted) { head.animatedTransform = head.baseTransform.copyTransform() }
                    else {
                        val cycle = (progress * block.duration * (PI * 2.0 * 3.0 * block.speed)).toFloat()
                        head.animatedTransform = head.animatedTransform.copy(rotation = head.baseTransform.rotation.copy(y = sin(cycle) * 35f * block.amplitude))
                    }
                }
            }

            ActionBlockType.CLAP -> {
                if (isCompleted) {
                    parts[CharacterPartType.LEFT_ARM]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                    parts[CharacterPartType.RIGHT_ARM]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                    parts[CharacterPartType.LEFT_FOREARM]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                    parts[CharacterPartType.RIGHT_FOREARM]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                } else {
                    val cycle = (progress * block.duration * (PI * 2.0 * 4.0 * block.speed)).toFloat()
                    val clapAngle = abs(sin(cycle)) * 90f
                    parts[CharacterPartType.LEFT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = Vec3(-60f, 0f, 40f - clapAngle * 0.4f)) }
                    parts[CharacterPartType.RIGHT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = Vec3(-60f, 0f, -40f + clapAngle * 0.4f)) }
                    parts[CharacterPartType.LEFT_FOREARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 50f)) }
                    parts[CharacterPartType.RIGHT_FOREARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 50f)) }
                }
            }

            ActionBlockType.CHEER -> {
                if (isCompleted) {
                    parts[CharacterPartType.LEFT_ARM]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                    parts[CharacterPartType.RIGHT_ARM]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                } else {
                    val cycle = (progress * block.duration * (PI * 2.0 * 3.0 * block.speed)).toFloat()
                    val sway = sin(cycle) * 15f
                    parts[CharacterPartType.LEFT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = Vec3(-170f, sway, 25f)) }
                    parts[CharacterPartType.RIGHT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = Vec3(-170f, -sway, -25f)) }
                    parts[CharacterPartType.LEFT_FOREARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 20f)) }
                    parts[CharacterPartType.RIGHT_FOREARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 20f)) }
                }
            }

            ActionBlockType.SHRUG -> {
                if (isCompleted) {
                    parts[CharacterPartType.LEFT_ARM]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                    parts[CharacterPartType.RIGHT_ARM]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                    parts[CharacterPartType.BODY]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                } else {
                    val smoothT = sin(progress * PI.toFloat())
                    parts[CharacterPartType.LEFT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = Vec3(-30f * smoothT, 0f, 35f * smoothT)) }
                    parts[CharacterPartType.RIGHT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = Vec3(-30f * smoothT, 0f, -35f * smoothT)) }
                    parts[CharacterPartType.LEFT_FOREARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 60f * smoothT)) }
                    parts[CharacterPartType.RIGHT_FOREARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 60f * smoothT)) }
                }
            }

            ActionBlockType.CROSS_ARMS -> {
                if (isCompleted) {
                    parts[CharacterPartType.LEFT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = Vec3(-45f, 30f, -30f)) }
                    parts[CharacterPartType.RIGHT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = Vec3(-40f, -30f, 30f)) }
                    parts[CharacterPartType.LEFT_FOREARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 85f)) }
                    parts[CharacterPartType.RIGHT_FOREARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 90f)) }
                } else {
                    val smoothT = progress * progress * (3f - 2f * progress)
                    parts[CharacterPartType.LEFT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = Vec3(-45f * smoothT, 30f * smoothT, -30f * smoothT)) }
                    parts[CharacterPartType.RIGHT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = Vec3(-40f * smoothT, -30f * smoothT, 30f * smoothT)) }
                    parts[CharacterPartType.LEFT_FOREARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 85f * smoothT)) }
                    parts[CharacterPartType.RIGHT_FOREARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 90f * smoothT)) }
                }
            }

            ActionBlockType.BOW -> {
                if (isCompleted) {
                    parts[CharacterPartType.BODY]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                    parts[CharacterPartType.HEAD]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                } else {
                    val smoothT = sin(progress * PI.toFloat())
                    parts[CharacterPartType.BODY]?.let { body ->
                        body.animatedTransform = body.animatedTransform.copy(rotation = body.baseTransform.rotation.copy(x = -45f * smoothT))
                    }
                    parts[CharacterPartType.HEAD]?.let { head ->
                        head.animatedTransform = head.animatedTransform.copy(rotation = head.baseTransform.rotation.copy(x = -15f * smoothT))
                    }
                }
            }

            ActionBlockType.DEATH_FALL -> {
                val baseT = runningTransforms.getOrPut(rootNode.id) { rootNode.baseTransform.copyTransform() }
                if (isCompleted) {
                    rootNode.animatedTransform = rootNode.animatedTransform.copy(
                        position = Vec3(baseT.position.x, baseT.position.y - 0.5f, baseT.position.z - 0.4f)
                    )
                    parts[CharacterPartType.BODY]?.let { body ->
                        body.animatedTransform = body.animatedTransform.copy(rotation = body.baseTransform.rotation.copy(x = -90f))
                    }
                    parts[CharacterPartType.HEAD]?.let { head ->
                        head.animatedTransform = head.animatedTransform.copy(rotation = head.baseTransform.rotation.copy(x = -30f))
                    }
                    parts[CharacterPartType.LEFT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = Vec3(-140f, 0f, 40f)) }
                    parts[CharacterPartType.RIGHT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = Vec3(-140f, 0f, -40f)) }
                    parts[CharacterPartType.LEFT_LEG]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -20f)) }
                    parts[CharacterPartType.RIGHT_LEG]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 20f)) }
                } else {
                    val smoothT = progress * progress
                    rootNode.animatedTransform = rootNode.animatedTransform.copy(
                        position = Vec3(baseT.position.x, baseT.position.y - 0.5f * smoothT, baseT.position.z - 0.4f * smoothT)
                    )
                    parts[CharacterPartType.BODY]?.let { body ->
                        body.animatedTransform = body.animatedTransform.copy(rotation = body.baseTransform.rotation.copy(x = -90f * smoothT))
                    }
                    parts[CharacterPartType.HEAD]?.let { head ->
                        head.animatedTransform = head.animatedTransform.copy(rotation = head.baseTransform.rotation.copy(x = -30f * smoothT))
                    }
                    parts[CharacterPartType.LEFT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = Vec3(-140f * smoothT, 0f, 40f * smoothT)) }
                    parts[CharacterPartType.RIGHT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = Vec3(-140f * smoothT, 0f, -40f * smoothT)) }
                    parts[CharacterPartType.LEFT_LEG]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -20f * smoothT)) }
                    parts[CharacterPartType.RIGHT_LEG]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 20f * smoothT)) }
                    bendLowerLegs(45f * smoothT, 30f * smoothT)
                }
            }

            ActionBlockType.SNEAK_WALK -> {
                val baseT = runningTransforms.getOrPut(rootNode.id) { rootNode.baseTransform.copyTransform() }
                val delta = moveDelta()
                val currentPos = baseT.position + delta * progress

                if (isCompleted) {
                    rootNode.animatedTransform = rootNode.animatedTransform.copy(position = baseT.position + delta)
                    runningTransforms[rootNode.id] = baseT.copy(position = baseT.position + delta)
                    resetLimbs()
                } else {
                    val cycle = (progress * block.duration * (PI * 2.0 * 1.5 * block.speed)).toFloat()
                    val legAngle = sin(cycle) * 22f * block.stepSize
                    val armAngle = -legAngle * 0.6f

                    rootNode.animatedTransform = rootNode.animatedTransform.copy(
                        position = Vec3(currentPos.x, currentPos.y - 0.2f, currentPos.z)
                    )
                    parts[CharacterPartType.BODY]?.let { body ->
                        body.animatedTransform = body.animatedTransform.copy(rotation = body.baseTransform.rotation.copy(x = -20f))
                    }
                    parts[CharacterPartType.LEFT_LEG]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = legAngle)) }
                    parts[CharacterPartType.RIGHT_LEG]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -legAngle)) }
                    parts[CharacterPartType.LEFT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = armAngle)) }
                    parts[CharacterPartType.RIGHT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -armAngle)) }
                    bendLowerLegs(legAngle, -legAngle)
                }
            }

            ActionBlockType.SPIN_ATTACK -> {
                val baseT = runningTransforms.getOrPut(rootNode.id) { rootNode.baseTransform.copyTransform() }
                if (isCompleted) {
                    rootNode.animatedTransform = rootNode.animatedTransform.copy(rotation = baseT.rotation.copy(y = baseT.rotation.y + 360f))
                    runningTransforms[rootNode.id] = baseT.copy(rotation = baseT.rotation.copy(y = baseT.rotation.y + 360f))
                    resetLimbs()
                } else {
                    val spinAngle = 360f * progress
                    rootNode.animatedTransform = rootNode.animatedTransform.copy(
                        rotation = baseT.rotation.copy(y = baseT.rotation.y + spinAngle)
                    )
                    parts[CharacterPartType.LEFT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = Vec3(-90f, 0f, 45f)) }
                    parts[CharacterPartType.RIGHT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = Vec3(-90f, 0f, -45f)) }
                }
            }

            ActionBlockType.BLOCK_SHIELD -> {
                if (isCompleted) {
                    parts[CharacterPartType.LEFT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = Vec3(-90f, 0f, 25f)) }
                    parts[CharacterPartType.LEFT_FOREARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 90f)) }
                } else {
                    val smoothT = progress * progress * (3f - 2f * progress)
                    parts[CharacterPartType.LEFT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = Vec3(-90f * smoothT, 0f, 25f * smoothT)) }
                    parts[CharacterPartType.LEFT_FOREARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 90f * smoothT)) }
                }
            }

            ActionBlockType.TAUNT -> {
                if (isCompleted) {
                    resetLimbs()
                } else {
                    val cycle = (progress * block.duration * (PI * 2.0 * 2.5 * block.speed)).toFloat()
                    val beckon = sin(cycle) * 30f
                    // Left hand on hip
                    parts[CharacterPartType.LEFT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = Vec3(-20f, 0f, 35f)) }
                    parts[CharacterPartType.LEFT_FOREARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 70f)) }
                    // Right arm waving/beckoning
                    parts[CharacterPartType.RIGHT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = Vec3(-120f, 0f, -20f)) }
                    parts[CharacterPartType.RIGHT_FOREARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 60f + beckon)) }
                    // Head cocking
                    parts[CharacterPartType.HEAD]?.let { head ->
                        head.animatedTransform = head.animatedTransform.copy(rotation = Vec3(-10f, sin(cycle) * 15f, 10f))
                    }
                }
            }

            ActionBlockType.DISABLE_CAMERA -> {
                // Toggle camera enabled state for the target node during playback/export
                val camNode = sceneGraph.getNode(block.targetNodeId) ?: return
                if (camNode.type == SceneNodeType.CAMERA && camNode.cameraData != null) {
                    camNode.cameraData = camNode.cameraData!!.copy(enabled = false)
                }
            }
        }
    }

    private fun applyPropertyValue(node: SceneNode, propertyPath: String, value: Float) {
        val curTransform = node.animatedTransform
        when (propertyPath) {
            "transform.position.x" -> {
                node.animatedTransform = curTransform.copy(
                    position = curTransform.position.copy(x = value)
                )
            }
            "transform.position.y" -> {
                node.animatedTransform = curTransform.copy(
                    position = curTransform.position.copy(y = value)
                )
            }
            "transform.position.z" -> {
                node.animatedTransform = curTransform.copy(
                    position = curTransform.position.copy(z = value)
                )
            }
            "transform.rotation.x" -> {
                node.animatedTransform = curTransform.copy(
                    rotation = curTransform.rotation.copy(x = value)
                )
            }
            "transform.rotation.y" -> {
                node.animatedTransform = curTransform.copy(
                    rotation = curTransform.rotation.copy(y = value)
                )
            }
            "transform.rotation.z" -> {
                node.animatedTransform = curTransform.copy(
                    rotation = curTransform.rotation.copy(z = value)
                )
            }
            "transform.scale.x" -> {
                node.animatedTransform = curTransform.copy(
                    scale = curTransform.scale.copy(x = value)
                )
            }
            "transform.scale.y" -> {
                node.animatedTransform = curTransform.copy(
                    scale = curTransform.scale.copy(y = value)
                )
            }
            "transform.scale.z" -> {
                node.animatedTransform = curTransform.copy(
                    scale = curTransform.scale.copy(z = value)
                )
            }
            "material.opacity" -> {
                node.material = node.material.copy(opacity = value.coerceIn(0f, 1f))
            }
            "camera.fov" -> {
                node.cameraData?.let {
                    node.cameraData = it.copy(fov = value.coerceIn(10f, 120f))
                }
            }
            "light.intensity" -> {
                node.lightData?.let {
                    node.lightData = it.copy(intensity = value.coerceAtLeast(0f))
                }
            }
        }
    }
}
