package com.star4droid.mc.animation.engine.camera

import com.star4droid.mc.animation.engine.math.Mat4
import com.star4droid.mc.animation.engine.math.Vec3
import kotlin.math.cos
import kotlin.math.sin

class EditorCamera {
    var target: Vec3 = Vec3(0f, 1f, 0f)
    var distance: Float = 6.0f
    var yaw: Float = 45.0f      // Degrees
    var pitch: Float = 25.0f    // Degrees (elevation)
    var fov: Float = 60.0f
    var near: Float = 0.1f
    var far: Float = 1000.0f

    var isUsingSceneCamera: Boolean = false
    var activeSceneCameraMatrix: Mat4? = null

    fun getEyePosition(): Vec3 {
        val radYaw = Math.toRadians(yaw.toDouble()).toFloat()
        val radPitch = Math.toRadians(pitch.toDouble()).toFloat()

        val x = target.x + distance * cos(radPitch) * sin(radYaw)
        val y = target.y + distance * sin(radPitch)
        val z = target.z + distance * cos(radPitch) * cos(radYaw)

        return Vec3(x, y, z)
    }

    fun getViewMatrix(): Mat4 {
        if (isUsingSceneCamera && activeSceneCameraMatrix != null) {
            // Scene camera node worldMatrix is camera-to-world.
            // View matrix is inverse of camera world matrix.
            return activeSceneCameraMatrix!!.inverted() ?: Mat4.lookAt(getEyePosition(), target, Vec3.UP)
        }
        val eye = getEyePosition()
        return Mat4.lookAt(eye, target, Vec3.UP)
    }

    fun getProjectionMatrix(aspect: Float): Mat4 {
        return Mat4.perspective(fov, aspect, near, far)
    }

    fun orbit(dYaw: Float, dPitch: Float) {
        yaw = (yaw - dYaw) % 360f
        pitch = (pitch + dPitch).coerceIn(-85f, 85f)
    }

    fun zoom(factor: Float) {
        distance = (distance * factor).coerceIn(1.0f, 50.0f)
    }

    fun pan(deltaX: Float, deltaY: Float) {
        val radYaw = Math.toRadians(yaw.toDouble()).toFloat()
        val right = Vec3(cos(radYaw), 0f, -sin(radYaw)).normalized()
        val up = Vec3.UP

        target = target + right * (-deltaX * distance * 0.002f) + up * (deltaY * distance * 0.002f)
    }

    fun moveFly(forwardBack: Float, leftRight: Float, upDown: Float, speed: Float = 0.15f) {
        val radYaw = Math.toRadians(yaw.toDouble()).toFloat()
        val forward = Vec3(-sin(radYaw), 0f, -cos(radYaw)).normalized()
        val right = Vec3(cos(radYaw), 0f, -sin(radYaw)).normalized()

        val move = (forward * forwardBack + right * leftRight + Vec3.UP * upDown) * speed
        target = target + move
    }

    fun resetToStartAndCenter() {
        isUsingSceneCamera = false
        target = Vec3(0f, 1f, 0f)
        distance = 6.0f
        yaw = 45.0f
        pitch = 25.0f
        fov = 60.0f
    }
}
