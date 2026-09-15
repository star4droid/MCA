package com.star4droid.mc.animation.engine.rendering

import android.opengl.GLES20
import android.opengl.Matrix
import com.star4droid.mc.animation.engine.skeleton.Bone
import com.star4droid.mc.animation.engine.skeleton.Skeleton
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class BoneRenderer {

    private var lineShaderId: Int = 0
    private var uMVPLoc: Int = -1
    private var uColorLoc: Int = -1
    private var aPosLoc: Int = -1

    private val vertexBuffer: FloatBuffer

    init {
        val vertexShaderSrc = """
            uniform mat4 uMVPMatrix;
            attribute vec4 aPosition;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
            }
        """.trimIndent()

        val fragmentShaderSrc = """
            precision mediump float;
            uniform vec4 uColor;
            void main() {
                gl_FragColor = uColor;
            }
        """.trimIndent()

        val vs = compile(GLES20.GL_VERTEX_SHADER, vertexShaderSrc)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSrc)

        lineShaderId = GLES20.glCreateProgram().also { prog ->
            GLES20.glAttachShader(prog, vs)
            GLES20.glAttachShader(prog, fs)
            GLES20.glLinkProgram(prog)
        }

        uMVPLoc = GLES20.glGetUniformLocation(lineShaderId, "uMVPMatrix")
        uColorLoc = GLES20.glGetUniformLocation(lineShaderId, "uColor")
        aPosLoc = GLES20.glGetAttribLocation(lineShaderId, "aPosition")

        // Allocate buffer for lines (max 64 lines * 2 vertices * 3 floats)
        vertexBuffer = ByteBuffer.allocateDirect(64 * 2 * 3 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
    }

    fun renderSkeleton(
        skeleton: Skeleton,
        viewProjMatrix: FloatArray,
        modelWorldMatrix: FloatArray,
        selectedBoneIndex: Int = -1
    ) {
        if (skeleton.bones.isEmpty()) return

        GLES20.glUseProgram(lineShaderId)
        GLES20.glEnableVertexAttribArray(aPosLoc)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST) // Show bones on top through model

        val mvp = FloatArray(16)
        Matrix.multiplyMM(mvp, 0, viewProjMatrix, 0, modelWorldMatrix, 0)
        GLES20.glUniformMatrix4fv(uMVPLoc, 1, false, mvp, 0)

        // Draw connections between parent and child bones
        val lineCoords = mutableListOf<Float>()
        for (i in 0 until skeleton.bones.size) {
            val bone = skeleton.bones[i]
            val parent = bone.parentIndex
            val startX = if (parent >= 0) skeleton.bones[parent].worldMatrix[12] else 0f
            val startY = if (parent >= 0) skeleton.bones[parent].worldMatrix[13] else 0f
            val startZ = if (parent >= 0) skeleton.bones[parent].worldMatrix[14] else 0f

            val endX = bone.worldMatrix[12]
            val endY = bone.worldMatrix[13]
            val endZ = bone.worldMatrix[14]

            // Diamond / octahedral bone shape (4 triangular ribs around bone axis)
            val midX = (startX + endX) * 0.5f
            val midY = (startY + endY) * 0.5f
            val midZ = (startZ + endZ) * 0.5f
            val radius = 0.04f

            // Line 1: Main axis
            lineCoords.add(startX); lineCoords.add(startY); lineCoords.add(startZ)
            lineCoords.add(endX); lineCoords.add(endY); lineCoords.add(endZ)

            // Ribs to give diamond 3D feel
            lineCoords.add(startX); lineCoords.add(startY); lineCoords.add(startZ)
            lineCoords.add(midX + radius); lineCoords.add(midY); lineCoords.add(midZ)

            lineCoords.add(midX + radius); lineCoords.add(midY); lineCoords.add(midZ)
            lineCoords.add(endX); lineCoords.add(endY); lineCoords.add(endZ)

            lineCoords.add(startX); lineCoords.add(startY); lineCoords.add(startZ)
            lineCoords.add(midX - radius); lineCoords.add(midY); lineCoords.add(midZ)

            lineCoords.add(midX - radius); lineCoords.add(midY); lineCoords.add(midZ)
            lineCoords.add(endX); lineCoords.add(endY); lineCoords.add(endZ)
        }

        if (lineCoords.isNotEmpty()) {
            vertexBuffer.clear()
            val floats = lineCoords.toFloatArray()
            vertexBuffer.put(floats, 0, minOf(floats.size, vertexBuffer.capacity()))
            vertexBuffer.position(0)

            GLES20.glVertexAttribPointer(aPosLoc, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
            // Normal bone color (Cyan / Light Blue)
            GLES20.glUniform4f(uColorLoc, 0.22f, 0.74f, 0.97f, 0.9f)
            GLES20.glLineWidth(3.0f)
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, lineCoords.size / 3)
        }

        // Highlight selected bone in golden yellow
        if (selectedBoneIndex >= 0 && selectedBoneIndex < skeleton.bones.size) {
            val selBone = skeleton.bones[selectedBoneIndex]
            val parent = selBone.parentIndex
            val startX = if (parent >= 0) skeleton.bones[parent].worldMatrix[12] else 0f
            val startY = if (parent >= 0) skeleton.bones[parent].worldMatrix[13] else 0f
            val startZ = if (parent >= 0) skeleton.bones[parent].worldMatrix[14] else 0f
            val endX = selBone.worldMatrix[12]
            val endY = selBone.worldMatrix[13]
            val endZ = selBone.worldMatrix[14]

            val selLines = floatArrayOf(startX, startY, startZ, endX, endY, endZ)
            vertexBuffer.clear()
            vertexBuffer.put(selLines)
            vertexBuffer.position(0)

            GLES20.glVertexAttribPointer(aPosLoc, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
            // Gold selection color
            GLES20.glUniform4f(uColorLoc, 1.0f, 0.84f, 0.0f, 1.0f)
            GLES20.glLineWidth(6.0f)
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2)
        }

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisableVertexAttribArray(aPosLoc)
    }

    private fun compile(type: Int, src: String): Int {
        return GLES20.glCreateShader(type).also { s ->
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
        }
    }
}
