package com.star4droid.mc.animation.engine.math

import kotlin.math.max
import kotlin.math.min

data class Ray(
    val origin: Vec3,
    val direction: Vec3
) {
    fun getPoint(distance: Float): Vec3 = origin + direction * distance

    /**
     * Intersect ray with an Axis-Aligned Bounding Box (AABB).
     * Returns distance t along ray, or null if no intersection.
     */
    fun intersectAABB(min: Vec3, max: Vec3): Float? {
        var tmin = Float.NEGATIVE_INFINITY
        var tmax = Float.POSITIVE_INFINITY

        val rayDir = direction
        val rayOrig = origin

        // X axis
        if (rayDir.x != 0f) {
            val invD = 1.0f / rayDir.x
            var t1 = (min.x - rayOrig.x) * invD
            var t2 = (max.x - rayOrig.x) * invD
            if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
            tmin = max(tmin, t1)
            tmax = min(tmax, t2)
            if (tmin > tmax) return null
        } else if (rayOrig.x < min.x || rayOrig.x > max.x) {
            return null
        }

        // Y axis
        if (rayDir.y != 0f) {
            val invD = 1.0f / rayDir.y
            var t1 = (min.y - rayOrig.y) * invD
            var t2 = (max.y - rayOrig.y) * invD
            if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
            tmin = max(tmin, t1)
            tmax = min(tmax, t2)
            if (tmin > tmax) return null
        } else if (rayOrig.y < min.y || rayOrig.y > max.y) {
            return null
        }

        // Z axis
        if (rayDir.z != 0f) {
            val invD = 1.0f / rayDir.z
            var t1 = (min.z - rayOrig.z) * invD
            var t2 = (max.z - rayOrig.z) * invD
            if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
            tmin = max(tmin, t1)
            tmax = min(tmax, t2)
            if (tmin > tmax) return null
        } else if (rayOrig.z < min.z || rayOrig.z > max.z) {
            return null
        }

        if (tmax < 0) return null
        return if (tmin >= 0) tmin else tmax
    }

    /**
     * Intersect ray with a plane defined by a point and a normal.
     */
    fun intersectPlane(planePoint: Vec3, planeNormal: Vec3): Float? {
        val denom = planeNormal.dot(direction)
        if (kotlin.math.abs(denom) > 1e-6f) {
            val t = (planePoint - origin).dot(planeNormal) / denom
            if (t >= 0) return t
        }
        return null
    }

    companion object {
        /**
         * Unproject screen touch coordinates (x, y) with viewport (width, height)
         * to a world-space Ray using inverted ViewProjection matrix.
         */
        fun fromScreen(
            screenX: Float,
            screenY: Float,
            viewportWidth: Float,
            viewportHeight: Float,
            invViewProj: Mat4
        ): Ray {
            // Convert to Normalized Device Coordinates (NDC) [-1, 1]
            val ndcX = (2.0f * screenX) / viewportWidth - 1.0f
            val ndcY = 1.0f - (2.0f * screenY) / viewportHeight // Flip Y

            val nearPoint = invViewProj.transformPoint(Vec3(ndcX, ndcY, -1.0f))
            val farPoint = invViewProj.transformPoint(Vec3(ndcX, ndcY, 1.0f))

            val dir = (farPoint - nearPoint).normalized()
            return Ray(nearPoint, dir)
        }
    }
}
