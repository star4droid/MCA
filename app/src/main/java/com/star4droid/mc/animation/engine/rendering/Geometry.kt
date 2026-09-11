package com.star4droid.mc.animation.engine.rendering

import com.star4droid.mc.animation.engine.math.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

class Mesh(
    val vertexBuffer: FloatBuffer,
    val indexBuffer: ShortBuffer,
    val indexCount: Int
)

object Geometry {

    fun createCubeMesh(dx: Float = 1.0f, dy: Float = 1.0f, dz: Float = 1.0f): Mesh {
        val hx = dx * 0.5f
        val hy = dy * 0.5f
        val hz = dz * 0.5f

        // 24 vertices (4 per face) with pos (3), normal (3), uv (2) = 8 floats per vertex
        val vertices = floatArrayOf(
            // Front face (Z+) normal (0, 0, 1)
            -hx, -hy,  hz,   0f, 0f, 1f,   0f, 1f,
             hx, -hy,  hz,   0f, 0f, 1f,   1f, 1f,
             hx,  hy,  hz,   0f, 0f, 1f,   1f, 0f,
            -hx,  hy,  hz,   0f, 0f, 1f,   0f, 0f,

            // Back face (Z-) normal (0, 0, -1)
             hx, -hy, -hz,   0f, 0f, -1f,  0f, 1f,
            -hx, -hy, -hz,   0f, 0f, -1f,  1f, 1f,
            -hx,  hy, -hz,   0f, 0f, -1f,  1f, 0f,
             hx,  hy, -hz,   0f, 0f, -1f,  0f, 0f,

            // Top face (Y+) normal (0, 1, 0)
            -hx,  hy,  hz,   0f, 1f, 0f,   0f, 1f,
             hx,  hy,  hz,   0f, 1f, 0f,   1f, 1f,
             hx,  hy, -hz,   0f, 1f, 0f,   1f, 0f,
            -hx,  hy, -hz,   0f, 1f, 0f,   0f, 0f,

            // Bottom face (Y-) normal (0, -1, 0)
            -hx, -hy, -hz,   0f, -1f, 0f,  0f, 1f,
             hx, -hy, -hz,   0f, -1f, 0f,  1f, 1f,
             hx, -hy,  hz,   0f, -1f, 0f,  1f, 0f,
            -hx, -hy,  hz,   0f, -1f, 0f,  0f, 0f,

            // Right face (X+) normal (1, 0, 0)
             hx, -hy,  hz,   1f, 0f, 0f,   0f, 1f,
             hx, -hy, -hz,   1f, 0f, 0f,   1f, 1f,
             hx,  hy, -hz,   1f, 0f, 0f,   1f, 0f,
             hx,  hy,  hz,   1f, 0f, 0f,   0f, 0f,

            // Left face (X-) normal (-1, 0, 0)
            -hx, -hy, -hz,  -1f, 0f, 0f,   0f, 1f,
            -hx, -hy,  hz,  -1f, 0f, 0f,   1f, 1f,
            -hx,  hy,  hz,  -1f, 0f, 0f,   1f, 0f,
            -hx,  hy, -hz,  -1f, 0f, 0f,   0f, 0f
        )

        val indices = shortArrayOf(
            0, 1, 2,  0, 2, 3,       // Front
            4, 5, 6,  4, 6, 7,       // Back
            8, 9, 10, 8, 10, 11,     // Top
            12, 13, 14, 12, 14, 15,  // Bottom
            16, 17, 18, 16, 18, 19,  // Right
            20, 21, 22, 20, 22, 23   // Left
        )

        val vBuf = ByteBuffer.allocateDirect(vertices.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply { put(vertices); position(0) }
        }

        val iBuf = ByteBuffer.allocateDirect(indices.size * 2).run {
            order(ByteOrder.nativeOrder())
            asShortBuffer().apply { put(indices); position(0) }
        }

        return Mesh(vBuf, iBuf, indices.size)
    }

    fun createGridMesh(size: Int = 20, step: Float = 1.0f): Mesh {
        val half = size * step * 0.5f
        val lines = mutableListOf<Float>()
        val indices = mutableListOf<Short>()
        var idx: Short = 0

        for (i in -size / 2..size / 2) {
            val coord = i * step

            // X line (along Z)
            lines.add(coord); lines.add(0f); lines.add(-half)
            lines.add(0f); lines.add(1f); lines.add(0f)
            lines.add(0f); lines.add(0f)
            indices.add(idx++)

            lines.add(coord); lines.add(0f); lines.add(half)
            lines.add(0f); lines.add(1f); lines.add(0f)
            lines.add(0f); lines.add(0f)
            indices.add(idx++)

            // Z line (along X)
            lines.add(-half); lines.add(0f); lines.add(coord)
            lines.add(0f); lines.add(1f); lines.add(0f)
            lines.add(0f); lines.add(0f)
            indices.add(idx++)

            lines.add(half); lines.add(0f); lines.add(coord)
            lines.add(0f); lines.add(1f); lines.add(0f)
            lines.add(0f); lines.add(0f)
            indices.add(idx++)
        }

        val vertArr = lines.toFloatArray()
        val indArr = indices.toShortArray()

        val vBuf = ByteBuffer.allocateDirect(vertArr.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply { put(vertArr); position(0) }
        }

        val iBuf = ByteBuffer.allocateDirect(indArr.size * 2).run {
            order(ByteOrder.nativeOrder())
            asShortBuffer().apply { put(indArr); position(0) }
        }

        return Mesh(vBuf, iBuf, indArr.size)
    }

    fun createBoundingWireframeMesh(dx: Float, dy: Float, dz: Float): Mesh {
        val hx = dx * 0.5f + 0.02f
        val hy = dy * 0.5f + 0.02f
        val hz = dz * 0.5f + 0.02f

        val vertices = floatArrayOf(
            -hx, -hy, -hz,  0f, 1f, 0f, 0f, 0f,
             hx, -hy, -hz,  0f, 1f, 0f, 0f, 0f,
             hx,  hy, -hz,  0f, 1f, 0f, 0f, 0f,
            -hx,  hy, -hz,  0f, 1f, 0f, 0f, 0f,
            -hx, -hy,  hz,  0f, 1f, 0f, 0f, 0f,
             hx, -hy,  hz,  0f, 1f, 0f, 0f, 0f,
             hx,  hy,  hz,  0f, 1f, 0f, 0f, 0f,
            -hx,  hy,  hz,  0f, 1f, 0f, 0f, 0f
        )

        val indices = shortArrayOf(
            0, 1,  1, 2,  2, 3,  3, 0, // back
            4, 5,  5, 6,  6, 7,  7, 4, // front
            0, 4,  1, 5,  2, 6,  3, 7  // sides
        )

        val vBuf = ByteBuffer.allocateDirect(vertices.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply { put(vertices); position(0) }
        }

        val iBuf = ByteBuffer.allocateDirect(indices.size * 2).run {
            order(ByteOrder.nativeOrder())
            asShortBuffer().apply { put(indices); position(0) }
        }

        return Mesh(vBuf, iBuf, indices.size)
    }
}
