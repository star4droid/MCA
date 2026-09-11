package com.star4droid.mc.animation.animation.presets

import com.star4droid.mc.animation.animation.AnimationTrack
import com.star4droid.mc.animation.animation.Interpolation
import com.star4droid.mc.animation.animation.TimelineAsset
import com.star4droid.mc.animation.engine.scene.CharacterPartType
import com.star4droid.mc.animation.engine.scene.SceneGraph
import com.star4droid.mc.animation.engine.scene.SceneNode

enum class PresetType(val displayName: String, val description: String) {
    WALK("Walk", "Classic Minecraft walking cycle (1s)"),
    RUN("Run", "Fast energetic running sprint (0.8s)"),
    JUMP("Jump", "Crouch, spring into air, and land (1.2s)"),
    PLACE_BLOCK("Place Block", "Swing right arm and bend forward to place block (1s)"),
    PUNCH("Punch", "Quick forward jab with right arm (0.5s)"),
    WAVE("Wave", "Raise right arm and wave friendly greeting (1.2s)"),
    TILT_HEAD("Tilt Head", "Playful curious head tilt (1s)"),
    LOOK_LEFT("Look Left", "Turn head to the left and back (1s)"),
    LOOK_RIGHT("Look Right", "Turn head to the right and back (1s)"),
    IDLE("Idle Breathing", "Subtle organic chest and arm idle sway (2s)")
}

object PresetGenerator {

    fun applyPreset(
        sceneGraph: SceneGraph,
        selectedNodeId: String,
        timeline: TimelineAsset,
        presetType: PresetType,
        startTime: Float
    ): Boolean {
        val targetNode = sceneGraph.getNode(selectedNodeId) ?: return false

        // Find character parts (either targetNode is root, or find its parent root)
        val characterParts = findCharacterParts(sceneGraph, targetNode)
        if (characterParts.isEmpty()) return false

        val head = characterParts[CharacterPartType.HEAD]
        val body = characterParts[CharacterPartType.BODY]
        val rightArm = characterParts[CharacterPartType.RIGHT_ARM]
        val leftArm = characterParts[CharacterPartType.LEFT_ARM]
        val rightLeg = characterParts[CharacterPartType.RIGHT_LEG]
        val leftLeg = characterParts[CharacterPartType.LEFT_LEG]
        val root = characterParts[CharacterPartType.ROOT] ?: targetNode

        when (presetType) {
            PresetType.WALK -> {
                // 1 second walking loop: 0.0, 0.25, 0.5, 0.75, 1.0
                leftLeg?.let {
                    addKeyframes(timeline, it.id, "transform.rotation.x", listOf(
                        KeyframeSpec(startTime + 0.0f, -30f),
                        KeyframeSpec(startTime + 0.5f, 30f),
                        KeyframeSpec(startTime + 1.0f, -30f)
                    ))
                }
                rightLeg?.let {
                    addKeyframes(timeline, it.id, "transform.rotation.x", listOf(
                        KeyframeSpec(startTime + 0.0f, 30f),
                        KeyframeSpec(startTime + 0.5f, -30f),
                        KeyframeSpec(startTime + 1.0f, 30f)
                    ))
                }
                leftArm?.let {
                    addKeyframes(timeline, it.id, "transform.rotation.x", listOf(
                        KeyframeSpec(startTime + 0.0f, 30f),
                        KeyframeSpec(startTime + 0.5f, -30f),
                        KeyframeSpec(startTime + 1.0f, 30f)
                    ))
                }
                rightArm?.let {
                    addKeyframes(timeline, it.id, "transform.rotation.x", listOf(
                        KeyframeSpec(startTime + 0.0f, -30f),
                        KeyframeSpec(startTime + 0.5f, 30f),
                        KeyframeSpec(startTime + 1.0f, -30f)
                    ))
                }
                body?.let {
                    val basePosY = it.baseTransform.position.y
                    addKeyframes(timeline, it.id, "transform.position.y", listOf(
                        KeyframeSpec(startTime + 0.0f, basePosY),
                        KeyframeSpec(startTime + 0.25f, basePosY + 0.05f),
                        KeyframeSpec(startTime + 0.5f, basePosY),
                        KeyframeSpec(startTime + 0.75f, basePosY + 0.05f),
                        KeyframeSpec(startTime + 1.0f, basePosY)
                    ))
                }
            }

            PresetType.RUN -> {
                leftLeg?.let {
                    addKeyframes(timeline, it.id, "transform.rotation.x", listOf(
                        KeyframeSpec(startTime + 0.0f, -55f),
                        KeyframeSpec(startTime + 0.4f, 55f),
                        KeyframeSpec(startTime + 0.8f, -55f)
                    ))
                }
                rightLeg?.let {
                    addKeyframes(timeline, it.id, "transform.rotation.x", listOf(
                        KeyframeSpec(startTime + 0.0f, 55f),
                        KeyframeSpec(startTime + 0.4f, -55f),
                        KeyframeSpec(startTime + 0.8f, 55f)
                    ))
                }
                leftArm?.let {
                    addKeyframes(timeline, it.id, "transform.rotation.x", listOf(
                        KeyframeSpec(startTime + 0.0f, 60f),
                        KeyframeSpec(startTime + 0.4f, -60f),
                        KeyframeSpec(startTime + 0.8f, 60f)
                    ))
                }
                rightArm?.let {
                    addKeyframes(timeline, it.id, "transform.rotation.x", listOf(
                        KeyframeSpec(startTime + 0.0f, -60f),
                        KeyframeSpec(startTime + 0.4f, 60f),
                        KeyframeSpec(startTime + 0.8f, -60f)
                    ))
                }
                body?.let {
                    addKeyframes(timeline, it.id, "transform.rotation.x", listOf(
                        KeyframeSpec(startTime + 0.0f, 15f),
                        KeyframeSpec(startTime + 0.8f, 15f)
                    ))
                }
            }

            PresetType.JUMP -> {
                val basePosY = root.baseTransform.position.y
                addKeyframes(timeline, root.id, "transform.position.y", listOf(
                    KeyframeSpec(startTime + 0.0f, basePosY),
                    KeyframeSpec(startTime + 0.2f, basePosY - 0.2f),
                    KeyframeSpec(startTime + 0.6f, basePosY + 1.6f),
                    KeyframeSpec(startTime + 1.0f, basePosY)
                ))
                leftArm?.let {
                    addKeyframes(timeline, it.id, "transform.rotation.x", listOf(
                        KeyframeSpec(startTime + 0.0f, 0f),
                        KeyframeSpec(startTime + 0.2f, 20f),
                        KeyframeSpec(startTime + 0.6f, -120f),
                        KeyframeSpec(startTime + 1.0f, 0f)
                    ))
                }
                rightArm?.let {
                    addKeyframes(timeline, it.id, "transform.rotation.x", listOf(
                        KeyframeSpec(startTime + 0.0f, 0f),
                        KeyframeSpec(startTime + 0.2f, 20f),
                        KeyframeSpec(startTime + 0.6f, -120f),
                        KeyframeSpec(startTime + 1.0f, 0f)
                    ))
                }
                leftLeg?.let {
                    addKeyframes(timeline, it.id, "transform.rotation.x", listOf(
                        KeyframeSpec(startTime + 0.0f, 0f),
                        KeyframeSpec(startTime + 0.2f, 25f),
                        KeyframeSpec(startTime + 0.6f, -30f),
                        KeyframeSpec(startTime + 1.0f, 0f)
                    ))
                }
                rightLeg?.let {
                    addKeyframes(timeline, it.id, "transform.rotation.x", listOf(
                        KeyframeSpec(startTime + 0.0f, 0f),
                        KeyframeSpec(startTime + 0.2f, 25f),
                        KeyframeSpec(startTime + 0.6f, -30f),
                        KeyframeSpec(startTime + 1.0f, 0f)
                    ))
                }
            }

            PresetType.PLACE_BLOCK -> {
                // Animate character performing Minecraft block placement:
                // Head looks down, body leans, right arm swings down with item
                head?.let {
                    addKeyframes(timeline, it.id, "transform.rotation.x", listOf(
                        KeyframeSpec(startTime + 0.0f, 0f),
                        KeyframeSpec(startTime + 0.25f, 25f),
                        KeyframeSpec(startTime + 0.7f, 25f),
                        KeyframeSpec(startTime + 1.0f, 0f)
                    ))
                }
                body?.let {
                    addKeyframes(timeline, it.id, "transform.rotation.x", listOf(
                        KeyframeSpec(startTime + 0.0f, 0f),
                        KeyframeSpec(startTime + 0.25f, 15f),
                        KeyframeSpec(startTime + 0.7f, 15f),
                        KeyframeSpec(startTime + 1.0f, 0f)
                    ))
                }
                rightArm?.let {
                    addKeyframes(timeline, it.id, "transform.rotation.x", listOf(
                        KeyframeSpec(startTime + 0.0f, 0f),
                        KeyframeSpec(startTime + 0.2f, -50f),
                        KeyframeSpec(startTime + 0.45f, 35f),
                        KeyframeSpec(startTime + 0.75f, 15f),
                        KeyframeSpec(startTime + 1.0f, 0f)
                    ))
                }
            }

            PresetType.PUNCH -> {
                rightArm?.let {
                    addKeyframes(timeline, it.id, "transform.rotation.x", listOf(
                        KeyframeSpec(startTime + 0.0f, 0f),
                        KeyframeSpec(startTime + 0.15f, -90f),
                        KeyframeSpec(startTime + 0.35f, -30f),
                        KeyframeSpec(startTime + 0.5f, 0f)
                    ))
                }
            }

            PresetType.WAVE -> {
                rightArm?.let {
                    addKeyframes(timeline, it.id, "transform.rotation.z", listOf(
                        KeyframeSpec(startTime + 0.0f, 0f),
                        KeyframeSpec(startTime + 0.25f, -140f),
                        KeyframeSpec(startTime + 0.95f, -140f),
                        KeyframeSpec(startTime + 1.2f, 0f)
                    ))
                    addKeyframes(timeline, it.id, "transform.rotation.x", listOf(
                        KeyframeSpec(startTime + 0.25f, -20f),
                        KeyframeSpec(startTime + 0.45f, 20f),
                        KeyframeSpec(startTime + 0.65f, -20f),
                        KeyframeSpec(startTime + 0.85f, 20f),
                        KeyframeSpec(startTime + 1.05f, 0f)
                    ))
                }
            }

            PresetType.TILT_HEAD -> {
                head?.let {
                    addKeyframes(timeline, it.id, "transform.rotation.z", listOf(
                        KeyframeSpec(startTime + 0.0f, 0f),
                        KeyframeSpec(startTime + 0.3f, 25f),
                        KeyframeSpec(startTime + 0.7f, -20f),
                        KeyframeSpec(startTime + 1.0f, 0f)
                    ))
                }
            }

            PresetType.LOOK_LEFT -> {
                head?.let {
                    addKeyframes(timeline, it.id, "transform.rotation.y", listOf(
                        KeyframeSpec(startTime + 0.0f, 0f),
                        KeyframeSpec(startTime + 0.3f, -50f),
                        KeyframeSpec(startTime + 0.7f, -50f),
                        KeyframeSpec(startTime + 1.0f, 0f)
                    ))
                }
            }

            PresetType.LOOK_RIGHT -> {
                head?.let {
                    addKeyframes(timeline, it.id, "transform.rotation.y", listOf(
                        KeyframeSpec(startTime + 0.0f, 0f),
                        KeyframeSpec(startTime + 0.3f, 50f),
                        KeyframeSpec(startTime + 0.7f, 50f),
                        KeyframeSpec(startTime + 1.0f, 0f)
                    ))
                }
            }

            PresetType.IDLE -> {
                body?.let {
                    val basePosY = it.baseTransform.position.y
                    addKeyframes(timeline, it.id, "transform.position.y", listOf(
                        KeyframeSpec(startTime + 0.0f, basePosY),
                        KeyframeSpec(startTime + 1.0f, basePosY + 0.03f),
                        KeyframeSpec(startTime + 2.0f, basePosY)
                    ))
                }
                leftArm?.let {
                    addKeyframes(timeline, it.id, "transform.rotation.x", listOf(
                        KeyframeSpec(startTime + 0.0f, 0f),
                        KeyframeSpec(startTime + 1.0f, 5f),
                        KeyframeSpec(startTime + 2.0f, 0f)
                    ))
                }
                rightArm?.let {
                    addKeyframes(timeline, it.id, "transform.rotation.x", listOf(
                        KeyframeSpec(startTime + 0.0f, 0f),
                        KeyframeSpec(startTime + 1.0f, 5f),
                        KeyframeSpec(startTime + 2.0f, 0f)
                    ))
                }
            }
        }

        // Extend timeline duration if needed
        val maxTime = startTime + 3.0f
        if (timeline.duration < maxTime) {
            timeline.duration = maxTime + 2.0f
        }

        return true
    }

    private data class KeyframeSpec(val time: Float, val value: Float)

    private fun addKeyframes(
        timeline: TimelineAsset,
        targetObjectId: String,
        propertyPath: String,
        specs: List<KeyframeSpec>
    ) {
        val track = timeline.getOrCreateTrack(targetObjectId, propertyPath)
        for (spec in specs) {
            track.addOrUpdateKeyframe(spec.time, spec.value, Interpolation.SMOOTH)
        }
    }

    private fun findCharacterParts(
        sceneGraph: SceneGraph,
        node: SceneNode
    ): Map<CharacterPartType, SceneNode> {
        val result = mutableMapOf<CharacterPartType, SceneNode>()

        // Find character root
        var root = node
        while (root.parentId != null && root.characterPartType != CharacterPartType.ROOT) {
            val parent = sceneGraph.getNode(root.parentId!!) ?: break
            root = parent
        }

        // Collect all parts under this root
        fun collect(n: SceneNode) {
            n.characterPartType?.let { result[it] = n }
            for (childId in n.children) {
                val child = sceneGraph.getNode(childId) ?: continue
                collect(child)
            }
        }
        collect(root)
        return result
    }
}
