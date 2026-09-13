package com.star4droid.mc.animation.animation

import com.star4droid.mc.animation.engine.math.Vec3
import java.util.UUID
import kotlin.math.abs

enum class ActionBlockType(val displayName: String, val defaultDuration: Float) {
    WALK("Walk", 2.0f),
    RUN("Run", 1.5f),
    JUMP("Jump", 1.0f),
    WAVE("Wave", 1.5f),
    SLIDE_TO_POS("Slide to Pos", 2.0f),
    MOVE_TO_POS("Move to Pos", 1.0f),
    SCALE("Scale", 1.5f),
    ANIMATION_CLIP("Animation Clip", 3.0f),
    ROTATE("Rotate", 1.0f),
    TILT_HEAD("Tilt Head", 1.0f),
    PUNCH("Punch", 0.8f),
    LOOK_LEFT("Look Left", 0.8f),
    LOOK_RIGHT("Look Right", 0.8f),
    BACKFLIP("Backflip", 1.2f),
    JUMP_FRONT("Jump Front", 1.0f),
    SIT_DOWN("Sit Down", 1.2f),
    STAND_UP("Stand Up", 1.0f),
    KICK("Kick", 0.8f),
    NOD_HEAD("Nod Head", 1.0f),
    SHAKE_HEAD("Shake Head", 1.0f),
    CLAP("Clap", 1.5f),
    CHEER("Cheer", 1.5f),
    SHRUG("Shrug", 1.0f),
    CROSS_ARMS("Cross Arms", 1.0f),
    BOW("Bow", 1.5f),
    DEATH_FALL("Death Fall", 1.5f),
    SNEAK_WALK("Sneak Walk", 2.0f),
    SPIN_ATTACK("Spin Attack", 1.0f),
    BLOCK_SHIELD("Block Shield", 1.0f),
    TAUNT("Taunt", 1.2f),
    DISABLE_CAMERA("Disable Camera", 0.1f)
}

data class ActionBlock(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Action Block",
    var type: ActionBlockType = ActionBlockType.WALK,
    var targetNodeId: String = "",
    var startTime: Float = 0f,
    var duration: Float = 2.0f,
    var trackRow: Int = 0,
    var targetPosition: Vec3 = Vec3(0f, 0f, 0f),
    var startPosition: Vec3? = null,
    var speed: Float = 1.0f,
    var enablePositionMove: Boolean = false,
    var moveVector: Vec3 = Vec3(0f, 0f, 2f),
    var stepSize: Float = 1.0f,
    var hasCustomSettings: Boolean = false,
    var scaleVector: Vec3 = Vec3(1f, 1f, 1f),
    var clipFileName: String? = null,
    var angle: Float = 90f,
    var jumpHeight: Float = 1.5f,
    var amplitude: Float = 1.0f,
    var customJson: String? = null,
    var isDeltaBased: Boolean = false
) {
    fun copyBlock(): ActionBlock = copy(id = UUID.randomUUID().toString())
}

enum class Interpolation {
    STEP,
    LINEAR,
    SMOOTH
}

data class Keyframe(
    val id: String = UUID.randomUUID().toString(),
    var time: Float = 0f,
    var value: Float = 0f,
    var interpolation: Interpolation = Interpolation.LINEAR
) {
    fun copyKeyframe(): Keyframe = copy(id = UUID.randomUUID().toString())
}

data class AnimationTrack(
    val id: String = UUID.randomUUID().toString(),
    val targetObjectId: String,
    val propertyPath: String,
    val keyframes: MutableList<Keyframe> = mutableListOf()
) {
    fun evaluate(time: Float): Float {
        if (keyframes.isEmpty()) return 0f
        if (keyframes.size == 1) return keyframes[0].value

        // Sort if needed
        val sorted = keyframes.sortedBy { it.time }

        if (time <= sorted.first().time) return sorted.first().value
        if (time >= sorted.last().time) return sorted.last().value

        // Find surrounding keyframes
        for (i in 0 until sorted.size - 1) {
            val k0 = sorted[i]
            val k1 = sorted[i + 1]
            if (time >= k0.time && time <= k1.time) {
                val span = k1.time - k0.time
                if (span <= 1e-5f) return k0.value
                val t = (time - k0.time) / span

                return when (k0.interpolation) {
                    Interpolation.STEP -> k0.value
                    Interpolation.LINEAR -> {
                        interpolateValue(k0.value, k1.value, t, isRotation())
                    }
                    Interpolation.SMOOTH -> {
                        // Smooth cubic Hermite ease-in-out: 3t^2 - 2t^3
                        val smoothT = t * t * (3f - 2f * t)
                        interpolateValue(k0.value, k1.value, smoothT, isRotation())
                    }
                }
            }
        }
        return sorted.last().value
    }

    private fun isRotation(): Boolean = propertyPath.contains("rotation")

    private fun interpolateValue(v0: Float, v1: Float, t: Float, angle: Boolean): Float {
        return if (!angle) {
            v0 + (v1 - v0) * t
        } else {
            // Shortest path angle interpolation
            var diff = (v1 - v0) % 360f
            if (diff > 180f) diff -= 360f
            if (diff < -180f) diff += 360f
            v0 + diff * t
        }
    }

    fun addOrUpdateKeyframe(time: Float, value: Float, interpolation: Interpolation = Interpolation.LINEAR): Keyframe {
        val existing = keyframes.firstOrNull { abs(it.time - time) < 0.01f }
        return if (existing != null) {
            existing.value = value
            existing.interpolation = interpolation
            existing
        } else {
            val newKf = Keyframe(
                id = UUID.randomUUID().toString(),
                time = time,
                value = value,
                interpolation = interpolation
            )
            keyframes.add(newKf)
            keyframes.sortBy { it.time }
            newKf
        }
    }

    fun removeKeyframe(keyframeId: String): Boolean {
        return keyframes.removeAll { it.id == keyframeId }
    }
}

data class TimelineAsset(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Main Timeline",
    var duration: Float = 10.0f,
    val tracks: MutableList<AnimationTrack> = mutableListOf(),
    val actionBlocks: MutableList<ActionBlock> = mutableListOf()
) {
    fun addActionBlock(block: ActionBlock) {
        actionBlocks.add(block)
        val end = block.startTime + block.duration
        if (end > duration) {
            duration = end + 1.0f
        }
    }

    fun removeActionBlock(blockId: String): Boolean {
        return actionBlocks.removeAll { it.id == blockId }
    }

    fun getActionBlocksForObject(targetObjectId: String): List<ActionBlock> {
        return actionBlocks.filter { it.targetNodeId == targetObjectId }
    }

    fun getTrack(targetObjectId: String, propertyPath: String): AnimationTrack? {
        return tracks.firstOrNull { it.targetObjectId == targetObjectId && it.propertyPath == propertyPath }
    }

    fun getOrCreateTrack(targetObjectId: String, propertyPath: String): AnimationTrack {
        val existing = getTrack(targetObjectId, propertyPath)
        if (existing != null) return existing

        val newTrack = AnimationTrack(
            id = UUID.randomUUID().toString(),
            targetObjectId = targetObjectId,
            propertyPath = propertyPath
        )
        tracks.add(newTrack)
        return newTrack
    }

    fun getTracksForObject(targetObjectId: String): List<AnimationTrack> {
        return tracks.filter { it.targetObjectId == targetObjectId }
    }

    fun removeTrack(trackId: String): Boolean {
        return tracks.removeAll { it.id == trackId }
    }
}

data class TimelineInstance(
    val timelineAssetId: String,
    val targetRootObjectId: String? = null,
    var startTime: Float = 0f,
    var speed: Float = 1.0f,
    var loop: Boolean = false,
    var offset: Float = 0f
)
