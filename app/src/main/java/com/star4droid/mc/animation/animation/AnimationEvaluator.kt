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
                    if (isCompleted) {
                        head.animatedTransform = head.baseTransform.copyTransform()
                    } else {
                        val smoothT = sin(progress * PI.toFloat())
                        head.animatedTransform = head.animatedTransform.copy(
                            rotation = head.baseTransform.rotation.copy(y = 60f * smoothT * block.amplitude)
                        )
                    }
                }
            }

            ActionBlockType.LOOK_RIGHT -> {
                parts[CharacterPartType.HEAD]?.let { head ->
                    if (isCompleted) {
                        head.animatedTransform = head.baseTransform.copyTransform()
                    } else {
                        val smoothT = sin(progress * PI.toFloat())
                        head.animatedTransform = head.animatedTransform.copy(
                            rotation = head.baseTransform.rotation.copy(y = -60f * smoothT * block.amplitude)
                        )
                    }
                }
            }

            ActionBlockType.BACKFLIP -> {
                val baseT = runningTransforms.getOrPut(rootNode.id) { rootNode.baseTransform.copyTransform() }
                if (isCompleted) {
                    rootNode.animatedTransform = rootNode.animatedTransform.copy(position = baseT.position)
                    resetLimbs()
                } else {
                    // Jump arc
                    val jumpArc = sin(progress * PI.toFloat())
                    val height = jumpArc * block.jumpHeight * block.amplitude
                    // Full 360 backward rotation
                    val flipAngle = -360f * progress
                    rootNode.animatedTransform = rootNode.animatedTransform.copy(
                        position = Vec3(baseT.position.x, baseT.position.y + height, baseT.position.z)
                    )
                    parts[CharacterPartType.BODY]?.let { body ->
                        body.animatedTransform = body.animatedTransform.copy(rotation = body.baseTransform.rotation.copy(x = flipAngle))
                    }
                    // Tuck legs during flip
                    parts[CharacterPartType.LEFT_LEG]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -90f * jumpArc))
                    }
                    parts[CharacterPartType.RIGHT_LEG]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -90f * jumpArc))
                    }
                    parts[CharacterPartType.LEFT_LOWER_LEG]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 90f * jumpArc))
                    }
                    parts[CharacterPartType.RIGHT_LOWER_LEG]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 90f * jumpArc))
                    }
                    // Arms pull back
                    parts[CharacterPartType.LEFT_ARM]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -160f * jumpArc))
                    }
                    parts[CharacterPartType.RIGHT_ARM]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -160f * jumpArc))
                    }
                    parts[CharacterPartType.LEFT_FOREARM]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 80f * jumpArc))
                    }
                    parts[CharacterPartType.RIGHT_FOREARM]?.let {
                        it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 80f * jumpArc))
                    }
                }
            }

            ActionBlockType.JUMP_FRONT -> {
                val baseT = runningTransforms.getOrPut(rootNode.id) { rootNode.baseTransform.copyTransform() }
                val delta = moveDelta()
                val currentPos = baseT.position + delta * progress
                val jumpArc = sin(progress * PI.toFloat())
                val height = jumpArc * block.jumpHeight * block.stepSize
                if (isCompleted) {
                    rootNode.animatedTransform = rootNode.animatedTransform.copy(position = baseT.position + delta)
                    runningTransforms[rootNode.id] = baseT.copy(position = baseT.position + delta)
                    resetLimbs()
                } else {
                    rootNode.animatedTransform = rootNode.animatedTransform.copy(
                        position = Vec3(currentPos.x, currentPos.y + height, currentPos.z)
                    )
                    parts[CharacterPartType.LEFT_LEG]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -30f * jumpArc)) }
                    parts[CharacterPartType.RIGHT_LEG]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 45f * jumpArc)) }
                    parts[CharacterPartType.LEFT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -70f * jumpArc)) }
                    parts[CharacterPartType.RIGHT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = -70f * jumpArc)) }
                    bendLowerLegs(-30f * jumpArc, 45f * jumpArc)
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
                    parts[CharacterPartType.LEFT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = Vec3(-50f, -25f, 0f)) }
                    parts[CharacterPartType.RIGHT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = Vec3(-50f, 25f, 0f)) }
                    parts[CharacterPartType.LEFT_FOREARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 90f)) }
                    parts[CharacterPartType.RIGHT_FOREARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 90f)) }
                } else {
                    val smoothT = progress * progress * (3f - 2f * progress)
                    parts[CharacterPartType.LEFT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = Vec3(-50f * smoothT, -25f * smoothT, 0f)) }
                    parts[CharacterPartType.RIGHT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = Vec3(-50f * smoothT, 25f * smoothT, 0f)) }
                    parts[CharacterPartType.LEFT_FOREARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 90f * smoothT)) }
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
                        position = baseT.position.copy(y = baseT.position.y - 0.6f)
                    )
                    parts[CharacterPartType.BODY]?.let { body ->
                        body.animatedTransform = body.animatedTransform.copy(rotation = body.baseTransform.rotation.copy(x = 90f))
                    }
                } else {
                    val smoothT = progress * progress
                    rootNode.animatedTransform = rootNode.animatedTransform.copy(
                        position = baseT.position.copy(y = baseT.position.y - 0.6f * smoothT)
                    )
                    parts[CharacterPartType.BODY]?.let { body ->
                        body.animatedTransform = body.animatedTransform.copy(rotation = body.baseTransform.rotation.copy(x = 90f * smoothT))
                    }
                    parts[CharacterPartType.LEFT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = Vec3(-30f * smoothT, 0f, 45f * smoothT)) }
                    parts[CharacterPartType.RIGHT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = Vec3(-30f * smoothT, 0f, -45f * smoothT)) }
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
                    parts[CharacterPartType.RIGHT_ARM]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                    parts[CharacterPartType.RIGHT_FOREARM]?.let { it.animatedTransform = it.baseTransform.copyTransform() }
                } else {
                    val cycle = (progress * block.duration * (PI * 2.0 * 2.5 * block.speed)).toFloat()
                    val beckon = sin(cycle) * 25f
                    parts[CharacterPartType.RIGHT_ARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = Vec3(-80f, 0f, -20f)) }
                    parts[CharacterPartType.RIGHT_FOREARM]?.let { it.animatedTransform = it.animatedTransform.copy(rotation = it.baseTransform.rotation.copy(x = 60f + beckon)) }
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
