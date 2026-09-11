package com.star4droid.mc.animation.engine.math

import android.opengl.Matrix
import kotlin.math.cos
import kotlin.math.sin

/**
 * 4x4 Matrix representation compatible with Android OpenGL ES column-major layout.
 */
class Mat4(val values: FloatArray = FloatArray(16)) {

    init {
        if (values.size != 16) throw IllegalArgumentException("Mat4 must have 16 elements")
    }

    operator fun get(index: Int): Float = values[index]
    operator fun set(index: Int, value: Float) { values[index] = value }

    fun copy(): Mat4 = Mat4(values.copyOf())

    fun multiply(other: Mat4): Mat4 {
        val result = FloatArray(16)
        Matrix.multiplyMM(result, 0, this.values, 0, other.values, 0)
        return Mat4(result)
    }

    operator fun times(other: Mat4): Mat4 = multiply(other)

    fun transformPoint(p: Vec3): Vec3 {
        val inVec = floatArrayOf(p.x, p.y, p.z, 1.0f)
        val outVec = FloatArray(4)
        Matrix.multiplyMV(outVec, 0, this.values, 0, inVec, 0)
        val w = if (outVec[3] != 0f) outVec[3] else 1.0f
        return Vec3(outVec[0] / w, outVec[1] / w, outVec[2] / w)
    }

    fun transformVector(v: Vec3): Vec3 {
        val inVec = floatArrayOf(v.x, v.y, v.z, 0.0f)
        val outVec = FloatArray(4)
        Matrix.multiplyMV(outVec, 0, this.values, 0, inVec, 0)
        return Vec3(outVec[0], outVec[1], outVec[2])
    }

    fun inverted(): Mat4? {
        val inv = FloatArray(16)
        val success = Matrix.invertM(inv, 0, this.values, 0)
        return if (success) Mat4(inv) else null
    }

    fun transposed(): Mat4 {
        val trans = FloatArray(16)
        Matrix.transposeM(trans, 0, this.values, 0)
        return Mat4(trans)
    }

    companion object {
        fun identity(): Mat4 {
            val m = FloatArray(16)
            Matrix.setIdentityM(m, 0)
            return Mat4(m)
        }

        fun translation(x: Float, y: Float, z: Float): Mat4 {
            val m = identity()
            Matrix.translateM(m.values, 0, x, y, z)
            return m
        }

        fun translation(v: Vec3): Mat4 = translation(v.x, v.y, v.z)

        fun rotation(angleDegrees: Float, x: Float, y: Float, z: Float): Mat4 {
            val m = identity()
            Matrix.setRotateM(m.values, 0, angleDegrees, x, y, z)
            return m
        }

        fun rotationEuler(rot: Vec3): Mat4 {
            // Euler rotation in Z -> Y -> X order or Y -> X -> Z standard for 3D editors
            val m = FloatArray(16)
            Matrix.setIdentityM(m, 0)
            Matrix.rotateM(m, 0, rot.y, 0f, 1f, 0f)
            Matrix.rotateM(m, 0, rot.x, 1f, 0f, 0f)
            Matrix.rotateM(m, 0, rot.z, 0f, 0f, 1f)
            return Mat4(m)
        }

        fun scaling(x: Float, y: Float, z: Float): Mat4 {
            val m = identity()
            Matrix.scaleM(m.values, 0, x, y, z)
            return m
        }

        fun scaling(v: Vec3): Mat4 = scaling(v.x, v.y, v.z)

        fun lookAt(eye: Vec3, center: Vec3, up: Vec3): Mat4 {
            val m = FloatArray(16)
            Matrix.setLookAtM(
                m, 0,
                eye.x, eye.y, eye.z,
                center.x, center.y, center.z,
                up.x, up.y, up.z
            )
            return Mat4(m)
        }

        fun perspective(fovyDegrees: Float, aspect: Float, near: Float, far: Float): Mat4 {
            val m = FloatArray(16)
            Matrix.perspectiveM(m, 0, fovyDegrees, aspect, near, far)
            return Mat4(m)
        }
    }
}
