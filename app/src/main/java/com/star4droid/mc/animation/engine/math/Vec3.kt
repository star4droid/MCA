package com.star4droid.mc.animation.engine.math

import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec3(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
) {
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float) = Vec3(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Float) = if (scalar != 0f) Vec3(x / scalar, y / scalar, z / scalar) else ZERO
    operator fun unaryMinus() = Vec3(-x, -y, -z)

    fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vec3): Vec3 = Vec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    fun lengthSquared(): Float = x * x + y * y + z * z
    fun length(): Float = sqrt(lengthSquared())

    fun normalized(): Vec3 {
        val len = length()
        return if (len > 1e-6f) this / len else ZERO
    }

    fun distanceTo(other: Vec3): Float = (this - other).length()

    fun lerp(other: Vec3, t: Float): Vec3 = Vec3(
        x + (other.x - x) * t,
        y + (other.y - y) * t,
        z + (other.z - z) * t
    )

    fun toFloatArray(): FloatArray = floatArrayOf(x, y, z)

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val ONE = Vec3(1f, 1f, 1f)
        val UNIT_X = Vec3(1f, 0f, 0f)
        val UNIT_Y = Vec3(0f, 1f, 0f)
        val UNIT_Z = Vec3(0f, 0f, 1f)
        val UP = Vec3(0f, 1f, 0f)
        val FORWARD = Vec3(0f, 0f, -1f)
        val RIGHT = Vec3(1f, 0f, 0f)
    }
}

data class Vec2(val x: Float = 0f, val y: Float = 0f) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(scalar: Float) = Vec2(x * scalar, y * scalar)
    fun length(): Float = sqrt(x * x + y * y)
}
