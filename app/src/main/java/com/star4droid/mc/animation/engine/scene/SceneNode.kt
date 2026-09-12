package com.star4droid.mc.animation.engine.scene

import com.star4droid.mc.animation.engine.math.Mat4
import com.star4droid.mc.animation.engine.math.Vec3
import java.util.UUID

data class SceneNode(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Object",
    val type: SceneNodeType = SceneNodeType.BLOCK,
    var parentId: String? = null,
    val children: MutableList<String> = mutableListOf(),
    var baseTransform: Transform = Transform.DEFAULT,
    var animatedTransform: Transform = Transform.DEFAULT,
    var worldMatrix: Mat4 = Mat4.identity(),
    var visible: Boolean = true,
    var material: Material = Material(),
    var characterPartType: CharacterPartType? = null,
    var cameraData: CameraData? = null,
    var lightData: LightData? = null,
    var boxDimensions: Vec3 = Vec3.ONE,
    var characterSkinId: String = "steve"
) {
    fun getWorldPosition(): Vec3 {
        return Vec3(worldMatrix[12], worldMatrix[13], worldMatrix[14])
    }

    /**
     * Compute approximate world-space Axis-Aligned Bounding Box (AABB)
     * for raycast selection and bounds inspection.
     */
    fun getWorldAABB(): Pair<Vec3, Vec3> {
        val pos = getWorldPosition()
        val scaleX = Vec3(worldMatrix[0], worldMatrix[1], worldMatrix[2]).length()
        val scaleY = Vec3(worldMatrix[4], worldMatrix[5], worldMatrix[6]).length()
        val scaleZ = Vec3(worldMatrix[8], worldMatrix[9], worldMatrix[10]).length()

        val half = Vec3(
            (boxDimensions.x * scaleX * 0.5f).coerceAtLeast(0.1f),
            (boxDimensions.y * scaleY * 0.5f).coerceAtLeast(0.1f),
            (boxDimensions.z * scaleZ * 0.5f).coerceAtLeast(0.1f)
        )
        return Pair(pos - half, pos + half)
    }

    fun deepClone(newParentId: String? = parentId): SceneNode {
        return copy(
            id = UUID.randomUUID().toString(),
            name = "$name Copy",
            parentId = newParentId,
            children = mutableListOf(),
            baseTransform = baseTransform.copyTransform(),
            animatedTransform = animatedTransform.copyTransform(),
            worldMatrix = worldMatrix.copy(),
            material = material.copy(),
            cameraData = cameraData?.copy(),
            lightData = lightData?.copy(),
            boxDimensions = boxDimensions.copy()
        )
    }
}
