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

        when (block.type) {
            ActionBlockType.WALK -> {
                val baseT = runningTransforms.getOrPut(rootNode.id) { rootNode.baseTransform.copyTransform() }
                val delta = if (block.enablePositionMove) {
                    if (block.targetPosition != Vec3.ZERO && block.startPosition != null && block.targetPosition != block.startPosition) {
                        block.targetPosition - block.startPosition!!
                    } else {
                        block.moveVector * block.stepSize
                    }
                } else {
                    Vec3.ZERO
                }

                val currentPos = baseT.position + delta * progress
                val moveDistanceSq = delta.x * delta.x + delta.z * delta.z
                val facingYaw = if (block.enablePositionMove && moveDistanceSq > 0.0001f) {
                    Math.toDegrees(kotlin.math.atan2(delta.x.toDouble(), delta.z.toDouble())).toFloat()
                } else {
                    baseT.rotation.y
                }

                if (isCompleted) {
                    rootNode.animatedTransform = rootNode.animatedTransform.copy(
                        position = baseT.position + delta,
                        rotation = baseT.rotation.copy(y = facingYaw)
                    )
                    runningTransforms[rootNode.id] = baseT.copy(
                        position = baseT.position + delta,
                        rotation = baseT.rotation.copy(y = facingYaw)
                    )
                    parts[CharacterPartType.LEFT_LEG]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                    parts[CharacterPartType.RIGHT_LEG]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                    parts[CharacterPartType.LEFT_ARM]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                    parts[CharacterPartType.RIGHT_ARM]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                    parts[CharacterPartType.HEAD]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                } else {
                    val cycle = (progress * block.duration * (PI * 2.0 * 2.0 * block.speed)).toFloat()
                    val legAngle = sin(cycle) * 38f * block.stepSize
                    val armAngle = -legAngle * 0.85f
                    val bob = if (block.enablePositionMove) {
                        abs(sin(cycle * 2f)) * 0.08f * block.stepSize
                    } else {
                        abs(sin(cycle * 2f)) * 0.04f * block.stepSize
                    }

                    rootNode.animatedTransform = rootNode.animatedTransform.copy(
                        position = Vec3(currentPos.x, currentPos.y + bob, currentPos.z),
                        rotation = baseT.rotation.copy(y = facingYaw)
                    )

                    parts[CharacterPartType.LEFT_LEG]?.let {
                        it.animatedTransform = it.animatedTransform.copy(
                            rotation = it.baseTransform.rotation.copy(x = legAngle)
                        )
                    }
                    parts[CharacterPartType.RIGHT_LEG]?.let {
                        it.animatedTransform = it.animatedTransform.copy(
                            rotation = it.baseTransform.rotation.copy(x = -legAngle)
                        )
                    }
                    parts[CharacterPartType.LEFT_ARM]?.let {
                        it.animatedTransform = it.animatedTransform.copy(
                            rotation = it.baseTransform.rotation.copy(x = armAngle)
                        )
                    }
                    parts[CharacterPartType.RIGHT_ARM]?.let {
                        it.animatedTransform = it.animatedTransform.copy(
                            rotation = it.baseTransform.rotation.copy(x = -armAngle)
                        )
                    }
                    parts[CharacterPartType.HEAD]?.let {
                        it.animatedTransform = it.animatedTransform.copy(
                            rotation = it.baseTransform.rotation.copy(z = sin(cycle) * 1.5f * block.stepSize)
                        )
                    }
                }
            }

            ActionBlockType.RUN -> {
                val baseT = runningTransforms.getOrPut(rootNode.id) { rootNode.baseTransform.copyTransform() }
                val delta = if (block.enablePositionMove) {
                    if (block.targetPosition != Vec3.ZERO && block.startPosition != null && block.targetPosition != block.startPosition) {
                        block.targetPosition - block.startPosition!!
                    } else {
                        block.moveVector * block.stepSize
                    }
                } else {
                    Vec3.ZERO
                }

                val currentPos = baseT.position + delta * progress
                val moveDistanceSq = delta.x * delta.x + delta.z * delta.z
                val facingYaw = if (block.enablePositionMove && moveDistanceSq > 0.0001f) {
                    Math.toDegrees(kotlin.math.atan2(delta.x.toDouble(), delta.z.toDouble())).toFloat()
                } else {
                    baseT.rotation.y
                }

                if (isCompleted) {
                    rootNode.animatedTransform = rootNode.animatedTransform.copy(
                        position = baseT.position + delta,
                        rotation = baseT.rotation.copy(y = facingYaw)
                    )
                    runningTransforms[rootNode.id] = baseT.copy(
                        position = baseT.position + delta,
                        rotation = baseT.rotation.copy(y = facingYaw)
                    )
                    parts[CharacterPartType.LEFT_LEG]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                    parts[CharacterPartType.RIGHT_LEG]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                    parts[CharacterPartType.LEFT_ARM]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                    parts[CharacterPartType.RIGHT_ARM]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                    parts[CharacterPartType.BODY]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                    parts[CharacterPartType.HEAD]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                } else {
                    val cycle = (progress * block.duration * (PI * 2.0 * 3.2 * block.speed)).toFloat()
                    val legAngle = sin(cycle) * 55f * block.stepSize
                    val armAngle = -legAngle * 0.9f
                    val bob = if (block.enablePositionMove) {
                        abs(sin(cycle * 2f)) * 0.12f * block.stepSize
                    } else {
                        abs(sin(cycle * 2f)) * 0.06f * block.stepSize
                    }

                    rootNode.animatedTransform = rootNode.animatedTransform.copy(
                        position = Vec3(currentPos.x, currentPos.y + bob, currentPos.z),
                        rotation = baseT.rotation.copy(y = facingYaw)
                    )

                    parts[CharacterPartType.LEFT_LEG]?.let {
                        it.animatedTransform = it.animatedTransform.copy(
                            rotation = it.baseTransform.rotation.copy(x = legAngle)
                        )
                    }
                    parts[CharacterPartType.RIGHT_LEG]?.let {
                        it.animatedTransform = it.animatedTransform.copy(
                            rotation = it.baseTransform.rotation.copy(x = -legAngle)
                        )
                    }
                    parts[CharacterPartType.LEFT_ARM]?.let {
                        it.animatedTransform = it.animatedTransform.copy(
                            rotation = it.baseTransform.rotation.copy(x = armAngle)
                        )
                    }
                    parts[CharacterPartType.RIGHT_ARM]?.let {
                        it.animatedTransform = it.animatedTransform.copy(
                            rotation = it.baseTransform.rotation.copy(x = -armAngle)
                        )
                    }
                    parts[CharacterPartType.BODY]?.let { body ->
                        body.animatedTransform = body.animatedTransform.copy(
                            rotation = body.baseTransform.rotation.copy(x = -15f)
                        )
                    }
                }
            }

            ActionBlockType.JUMP -> {
                // FIXED: Jumps relative to the character's current position without resetting to start
                val baseT = runningTransforms.getOrPut(rootNode.id) { rootNode.baseTransform.copyTransform() }
                val delta = if (block.enablePositionMove) {
                    if (block.targetPosition != Vec3.ZERO && block.startPosition != null && block.targetPosition != block.startPosition) {
                        block.targetPosition - block.startPosition!!
                    } else {
                        block.moveVector * block.stepSize
                    }
                } else {
                    Vec3.ZERO
                }

                val currentPos = baseT.position + delta * progress
                val jumpArc = sin(progress * PI.toFloat())
                val jumpHeight = jumpArc * 1.5f * block.stepSize

                if (isCompleted) {
                    rootNode.animatedTransform = rootNode.animatedTransform.copy(
                        position = baseT.position + delta
                    )
                    runningTransforms[rootNode.id] = baseT.copy(
                        position = baseT.position + delta
                    )
                    parts[CharacterPartType.LEFT_LEG]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                    parts[CharacterPartType.RIGHT_LEG]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                    parts[CharacterPartType.LEFT_ARM]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                    parts[CharacterPartType.RIGHT_ARM]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                } else {
                    rootNode.animatedTransform = rootNode.animatedTransform.copy(
                        position = Vec3(currentPos.x, currentPos.y + jumpHeight, currentPos.z)
                    )
                    parts[CharacterPartType.LEFT_LEG]?.let {
                        it.animatedTransform = it.animatedTransform.copy(
                            rotation = it.baseTransform.rotation.copy(x = jumpArc * 25f * block.stepSize)
                        )
                    }
                    parts[CharacterPartType.RIGHT_LEG]?.let {
                        it.animatedTransform = it.animatedTransform.copy(
                            rotation = it.baseTransform.rotation.copy(x = jumpArc * 25f * block.stepSize)
                        )
                    }
                    parts[CharacterPartType.LEFT_ARM]?.let {
                        it.animatedTransform = it.animatedTransform.copy(
                            rotation = it.baseTransform.rotation.copy(x = -jumpArc * 45f * block.stepSize)
                        )
                    }
                    parts[CharacterPartType.RIGHT_ARM]?.let {
                        it.animatedTransform = it.animatedTransform.copy(
                            rotation = it.baseTransform.rotation.copy(x = -jumpArc * 45f * block.stepSize)
                        )
                    }
                }
            }

            ActionBlockType.WAVE -> {
                val waveArm = parts[CharacterPartType.RIGHT_ARM]
                if (isCompleted) {
                    waveArm?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                    parts[CharacterPartType.HEAD]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                } else {
                    if (waveArm != null) {
                        val waveCycle = (progress * block.duration * (PI * 2.0 * 3.0 * block.speed)).toFloat()
                        val waveAngle = sin(waveCycle) * 30f
                        waveArm.animatedTransform = waveArm.animatedTransform.copy(
                            rotation = Vec3(-130f, waveAngle, 25f)
                        )
                    }
                    parts[CharacterPartType.HEAD]?.let { head ->
                        head.animatedTransform = head.animatedTransform.copy(
                            rotation = head.baseTransform.rotation.copy(z = -8f)
                        )
                    }
                }
            }

            ActionBlockType.SLIDE_TO_POS -> {
                // FIXED: Relative displacement so moving the character later preserves the slide amount
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
                // FIXED: Relative movement from current node position
                val targetNode = if (parts.containsKey(CharacterPartType.ROOT)) rootNode else node
                val baseT = runningTransforms.getOrPut(targetNode.id) { targetNode.baseTransform.copyTransform() }
                val delta = if (block.targetPosition != Vec3.ZERO && block.startPosition != null && block.targetPosition != block.startPosition) {
                    block.targetPosition - block.startPosition!!
                } else {
                    block.moveVector * block.stepSize
                }
                val curProgress = if (isCompleted) 1f else progress
                val curPos = baseT.position + delta * curProgress

                targetNode.animatedTransform = targetNode.animatedTransform.copy(position = curPos)
                if (isCompleted) {
                    runningTransforms[targetNode.id] = baseT.copy(position = baseT.position + delta)
                }
            }

            ActionBlockType.SCALE -> {
                // Scaling Action Block: smoothly animates node scale
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
                // Animation Clip block: applies harmonic loop motion to limbs
                val cycle = (progress * block.duration * (PI * 2.0 * 2.0 * block.speed)).toFloat()
                val legAngle = sin(cycle) * 28f * block.stepSize
                val armAngle = -legAngle * 0.75f
                parts[CharacterPartType.LEFT_LEG]?.let {
                    it.animatedTransform = it.animatedTransform.copy(
                        rotation = it.baseTransform.rotation.copy(x = legAngle)
                    )
                }
                parts[CharacterPartType.RIGHT_LEG]?.let {
                    it.animatedTransform = it.animatedTransform.copy(
                        rotation = it.baseTransform.rotation.copy(x = -legAngle)
                    )
                }
                parts[CharacterPartType.LEFT_ARM]?.let {
                    it.animatedTransform = it.animatedTransform.copy(
                        rotation = it.baseTransform.rotation.copy(x = armAngle)
                    )
                }
                parts[CharacterPartType.RIGHT_ARM]?.let {
                    it.animatedTransform = it.animatedTransform.copy(
                        rotation = it.baseTransform.rotation.copy(x = -armAngle)
                    )
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
