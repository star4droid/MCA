package com.star4droid.mc.animation.engine.scene

import com.star4droid.mc.animation.engine.math.Mat4
import com.star4droid.mc.animation.engine.math.Vec3

data class Transform(
    val position: Vec3 = Vec3.ZERO,
    val rotation: Vec3 = Vec3.ZERO, // Euler angles in degrees (X, Y, Z)
    val scale: Vec3 = Vec3.ONE,
    val pivot: Vec3 = Vec3.ZERO     // Local pivot offset
) {
    fun toLocalMatrix(): Mat4 {
        // T(pos + pivot) * R(rot) * S(scale) * T(-pivot)
        val tPos = Mat4.translation(position + pivot)
        val r = Mat4.rotationEuler(rotation)
        val s = Mat4.scaling(scale)
        val tInvPivot = Mat4.translation(-pivot)
        return tPos * r * s * tInvPivot
    }

    fun copyTransform(): Transform = Transform(
        position = position.copy(),
        rotation = rotation.copy(),
        scale = scale.copy(),
        pivot = pivot.copy()
    )

    fun plus(offset: Transform): Transform = Transform(
        position = position + offset.position,
        rotation = rotation + offset.rotation,
        scale = Vec3(scale.x * offset.scale.x, scale.y * offset.scale.y, scale.z * offset.scale.z),
        pivot = pivot
    )

    fun lerp(target: Transform, t: Float): Transform = Transform(
        position = position.lerp(target.position, t),
        rotation = rotation.lerp(target.rotation, t),
        scale = scale.lerp(target.scale, t),
        pivot = pivot
    )

    companion object {
        val DEFAULT = Transform()
    }
}
